package org.aquilagraph.frontend.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import org.aquilagraph.source.admission.AstProductionAdmission;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Optional;
import java.nio.file.Files;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import javax.tools.ToolProvider;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.aquilagraph.testsupport.SourceFidelityFixture;
import com.sun.source.tree.UsesTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.SourcePositions;
import javax.tools.Diagnostic;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.admission.ModuleServiceFacts;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.SourceOwnerPath;

class JavaModuleServiceEndpointTest {
  @TempDir Path directory;

  @Test
  void givenImportedAndQualifiedOperands_whenProducedAndDecoded_thenExactDetachedTargetsAndOriginsSurvive() {
    // Arrange
    var descriptor = "import api.Service; module service.sample { uses Service; provides api.Service with impl.Provider; }";
    var snapshot = snapshot(descriptor, "Provider");
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.produce(request(snapshot, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(result), result.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals("java-source-expanded/8", result.profile().version());
    assertEquals("javac-public/14", result.identity().semanticProductionRevision());
    assertEquals("java-source/23", result.identity().sourceModelVersion());
    for (var subject : List.of(result, retained)) {
      var operands = endpoints(subject);
      assertEquals(3, operands.size());
      assertEndpoint(subject, operands.get(0), descriptor, "Service", SyntaxRole.SERVICE_TYPE, "service.sample", "api.Service");
      assertEndpoint(subject, operands.get(1), descriptor, "api.Service", SyntaxRole.SERVICE_TYPE, "service.sample", "api.Service");
      assertEndpoint(subject, operands.get(2), descriptor, "impl.Provider", SyntaxRole.PROVIDER_TYPE, "service.sample", "impl.Provider");
      assertEquals(operands.get(0).semanticReference().orElseThrow().symbolKey(), operands.get(1).semanticReference().orElseThrow().symbolKey());
      assertNotEquals(operands.get(0).nodeId(), operands.get(1).nodeId());
    }
    assertEquals(result.identity(), retained.identity());
    assertEquals(result.aggregate().nodes(), retained.aggregate().nodes());
  }

  @Test
  void givenFactoryProvidersInWrittenOrder_whenProjected_thenDeclaredTypesRemainDistinctFromProductAndService() {
    // Arrange
    var descriptor = "import impl.InterfaceFactory; module service.factories { uses api.Service; provides api.Service with impl.ZuluFactory, InterfaceFactory, impl.AlphaProvider; }";
    var sources = Map.of(
        "module-info.java", descriptor,
        "api/Service.java", "package api; public interface Service {}",
        "impl/Product.java", "package impl; public class Product implements api.Service {}",
        "impl/ZuluFactory.java", "package impl; public final class ZuluFactory { private ZuluFactory() {} public static Product provider() { return new Product(); } }",
        "impl/InterfaceFactory.java", "package impl; public interface InterfaceFactory { public static Product provider() { return new Product(); } }",
        "impl/AlphaProvider.java", "package impl; public class AlphaProvider implements api.Service {}"
    );
    // Act
    var result = produce(sources);
    var reversed = produce(withDescriptor(sources, descriptor.replace("impl.ZuluFactory, InterfaceFactory, impl.AlphaProvider", "impl.AlphaProvider, InterfaceFactory, impl.ZuluFactory")));
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, reversed.completion(), reversed.diagnostics().toString());
    var directive = result.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PROVIDES).findFirst().orElseThrow();
    var providers = directive.children(SyntaxRole.PROVIDER_TYPE);
    assertEquals(List.of("impl.ZuluFactory", "impl.InterfaceFactory", "impl.AlphaProvider"), providers.stream()
        .map(node -> node.semanticReference().orElseThrow().declarationIdentity().orElseThrow().binaryName()).toList());
    assertEquals(List.of(0, 1, 2), providers.stream().map(node -> node.structuralPath().steps().getLast().position()).toList());
    for (var provider : providers.subList(0, 2)) {
      var key = provider.semanticReference().orElseThrow().symbolKey().orElseThrow();
      var declaration = result.program().types().stream().filter(type -> type.typeKey().equals(key)).findFirst().orElseThrow();
      assertTrue(declaration.directInterfaces().isEmpty());
      assertNotEquals("impl.Product", provider.semanticReference().orElseThrow().declarationIdentity().orElseThrow().binaryName());
    }
    assertNotEquals(result.identity(), reversed.identity());
    assertNotEquals(result.identity().canonicalContentDigest(), reversed.identity().canonicalContentDigest());
    for (var subject : List.of(result, AstCodec.decode(AstCodec.encode(result), result.identity()))) {
      assertProviderOrder(subject, List.of("impl.ZuluFactory", "InterfaceFactory", "impl.AlphaProvider"), List.of("impl.ZuluFactory", "impl.InterfaceFactory", "impl.AlphaProvider"));
    }
    for (var subject : List.of(reversed, AstCodec.decode(AstCodec.encode(reversed), reversed.identity()))) {
      assertProviderOrder(subject, List.of("impl.AlphaProvider", "InterfaceFactory", "impl.ZuluFactory"), List.of("impl.AlphaProvider", "impl.InterfaceFactory", "impl.ZuluFactory"));
    }
  }

  @Test
  void givenEqualCanonicalNamesInSeparateModules_whenSameFrontendProducesSuccessively_thenOwnedTargetsRemainModuleQualified() {
    // Arrange
    var frontend = new JavaFrontend(new JavaSourceLoader());
    var descriptor = "module service.alpha { uses api.Service; provides api.Service with impl.Provider; }";
    var alpha = snapshot(descriptor, "Provider");
    var beta = snapshot(descriptor.replace("service.alpha", "service.beta"), "Provider");
    // Act
    var first = frontend.produce(request(alpha, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var second = frontend.produce(request(beta, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var repeated = frontend.produce(request(alpha, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion(), first.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, second.completion(), second.diagnostics().toString());
    assertEquals(first.identity(), repeated.identity());
    for (var index = 0; index < 3; index++) {
      var left = endpoints(first).get(index).semanticReference().orElseThrow();
      var right = endpoints(second).get(index).semanticReference().orElseThrow();
      assertEquals(left.typeName(), right.typeName());
      assertEquals("service.alpha", left.declarationIdentity().orElseThrow().moduleName().orElseThrow());
      assertEquals("service.beta", right.declarationIdentity().orElseThrow().moduleName().orElseThrow());
      assertNotEquals(left.symbolKey(), right.symbolKey());
      assertTrue(first.program().types().stream().anyMatch(type -> Optional.of(type.typeKey()).equals(left.symbolKey())));
      assertTrue(second.program().types().stream().anyMatch(type -> Optional.of(type.typeKey()).equals(right.symbolKey())));
      var occurrence = first.occurrence(endpoints(first).get(index)).orElseThrow();
      assertThrows(IllegalArgumentException.class, () -> second.node(occurrence));
    }
  }

  @Test
  void givenNestedPublicTypes_whenProjected_thenCanonicalSpellingAndBinaryTargetsRemainDistinct() {
    // Arrange
    var sources = Map.of("module-info.java", "module service.nested { uses api.Types.Service; provides api.Types.Service with api.Types.Provider; }",
        "api/Types.java", "package api; public class Types { public interface Service {} public static class Provider implements Service {} }");
    // Act
    var result = produce(sources);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(List.of("api.Types$Service", "api.Types$Service", "api.Types$Provider"), endpoints(result).stream()
        .map(node -> node.semanticReference().orElseThrow().declarationIdentity().orElseThrow().binaryName()).toList());
    assertEquals(List.of("api.Types.Service", "api.Types.Service", "api.Types.Provider"), endpoints(result).stream()
        .map(node -> node.value(SyntaxValue.ModuleTypeReference.class).name().dottedName()).toList());
  }

  @Test
  void givenMissingAndInvalidProviders_whenProduced_thenCompilerDiagnosticsPreventCompletionAndWrittenOperandsRemain() {
    // Arrange
    var descriptors = List.of(
        "module service.invalid { uses missing.Service; }",
        "module service.invalid { provides api.Service with missing.Provider; }",
        "module service.invalid { provides api.Service with impl.Provider; }");
    // Act
    var results = descriptors.stream().map(descriptor -> produce(Map.of("module-info.java", descriptor,
        "api/Service.java", "package api; public interface Service {}",
        "impl/Provider.java", "package impl; public class Provider { private Provider() {} }"))).toList();
    // Assert
    for (var result : results) {
      assertEquals(AstCompletion.INCOMPLETE, result.completion(), result.diagnostics().toString());
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
      assertTrue(!endpoints(result).isEmpty());
    }
    var codes = List.of("compiler.err.doesnt.exist", "compiler.err.doesnt.exist", "compiler.err.service.implementation.must.be.subtype.of.service.interface");
    var names = List.of("missing.Service", "missing.Provider", "impl.Provider");
    for (var index = 0; index < results.size(); index++) {
      assertCompilerErrors(results.get(index), List.of(codes.get(index)), List.of("module-info.java"), List.of(names.get(index)));
    }
    assertEquals(List.of(ResolutionStatus.ERROR), endpoints(results.getFirst()).stream().map(node -> node.semanticReference().orElseThrow().status()).toList());
    assertEquals(List.of(ResolutionStatus.UNRESOLVED, ResolutionStatus.ERROR), endpoints(results.get(1)).stream().map(node -> node.semanticReference().orElseThrow().status()).toList());
    assertResolvedLegalityFailure(results.get(2));
    assertTrue(endpoints(results.get(1)).stream().anyMatch(node -> node.value(SyntaxValue.ModuleTypeReference.class).name().dottedName().equals("missing.Provider")));
  }

  @Test
  void givenInaccessibleAmbiguousAndInvalidFactoryInputs_whenProduced_thenAvailableWrittenFactsRetainActualCompilerErrors() {
    // Arrange
    var base = Map.of("module-info.java", "module service.invalid { provides api.Service with impl.Provider; }",
        "api/Service.java", "package api; public interface Service {}",
        "impl/Provider.java", "package impl; public class Provider implements api.Service { private Provider() {} }");
    var inaccessible = new HashMap<>(base);
    inaccessible.put("api/Service.java", "package api; interface Service {}");
    var invalidFactory = new HashMap<>(base);
    invalidFactory.put("impl/Provider.java", "package impl; public class Provider { private Provider() {} public static String provider() { return \"text\"; } }");
    var ambiguous = Map.of("module-info.java", "import a.*; import b.*; module service.invalid { uses Service; }",
        "a/Service.java", "package a; public interface Service {}", "b/Service.java", "package b; public interface Service {}");
    // Act
    var results = List.of(base, inaccessible, invalidFactory, ambiguous).stream().map(this::produce).toList();
    // Assert
    for (var result : results) {
      assertEquals(AstCompletion.INCOMPLETE, result.completion(), result.diagnostics().toString());
      assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.isError()));
      assertTrue(!endpoints(result).isEmpty());
      var retained = AstCodec.decode(AstCodec.encode(result), result.identity());
      assertEquals(endpoints(result), endpoints(retained));
      assertEquals(result.diagnostics(), retained.diagnostics());
    }
    assertCompilerErrors(results.get(0), List.of("compiler.err.service.implementation.no.args.constructor.not.public"), List.of("module-info.java"), List.of("impl.Provider"));
    assertCompilerErrors(results.get(1), List.of("compiler.err.not.def.public.cant.access", "compiler.err.not.def.public.cant.access", "compiler.err.service.implementation.no.args.constructor.not.public"), List.of("impl/Provider.java", "module-info.java", "module-info.java"), List.of("api.Service", "api.Service", "impl.Provider"));
    assertCompilerErrors(results.get(2), List.of("compiler.err.service.implementation.provider.return.must.be.subtype.of.service.interface"), List.of("module-info.java"), List.of("impl.Provider"));
    assertCompilerErrors(results.get(3), List.of("compiler.err.ref.ambiguous"), List.of("module-info.java"), List.of("Service"));
    assertResolvedLegalityFailure(results.get(0));
    assertResolvedLegalityFailure(results.get(2));
    assertEquals(List.of(ResolutionStatus.ERROR, ResolutionStatus.UNRESOLVED), endpoints(results.get(1)).stream().map(node -> node.semanticReference().orElseThrow().status()).toList());
    assertEquals(List.of(ResolutionStatus.ERROR), endpoints(results.get(3)).stream().map(node -> node.semanticReference().orElseThrow().status()).toList());
  }

  @Test
  void givenLegacyProfileAndModuleWithoutDirectives_whenProduced_thenHistoricalPolicyAndTrueInapplicabilityRemain() {
    // Arrange
    var snapshot = snapshot("module service.sample { uses api.Service; provides api.Service with impl.Provider; }", "Provider");
    var frontend = new JavaFrontend(new JavaSourceLoader());
    // Act
    var old = frontend.produce(request(snapshot, AstProfile.JAVA_SOURCE_FIDELITY)).result().orElseThrow();
    var absent = produce(Map.of("module-info.java", "module service.empty {}"));
    // Assert
    assertEquals(AstCompletion.COMPLETE, old.completion(), old.diagnostics().toString());
    assertEquals("java-source/23", old.identity().sourceModelVersion());
    assertEquals("javac-public/14", old.identity().semanticProductionRevision());
    assertTrue(endpoints(old).isEmpty());
    assertTrue(old.aggregate().nodes().stream().flatMap(node -> node.semanticReference().stream()).allMatch(reference -> reference.declarationIdentity().isEmpty()));
    assertEquals(AstCompletion.COMPLETE, absent.completion(), absent.diagnostics().toString());
    assertTrue(endpoints(absent).isEmpty());
  }

  @Test
  void givenUnchangedConsumerUnderTwoCompiledModuleEnvironments_whenProduced_thenExactExternalTargetsFollowCapturedDependencies() throws IOException {
    // Arrange
    var alpha = compileModule("alpha", Map.of("module-info.java", "module service.alpha { exports same.api; exports same.impl; }",
        "same/api/Service.java", "package same.api; public interface Service {}",
        "same/impl/ExternalProvider.java", "package same.impl; public class ExternalProvider implements same.api.Service {}"), List.of());
    var beta = compileModule("beta", Map.of("module-info.java", "module service.beta { exports same.api; }",
        "same/api/Service.java", "package same.api; public interface Service {}"), List.of());
    var facadeAlpha = compileModule("facade-alpha", Map.of("module-info.java", "module service.facade { requires transitive service.alpha; }"), List.of(alpha));
    var facadeBeta = compileModule("facade-beta", Map.of("module-info.java", "module service.facade { requires transitive service.beta; }"), List.of(beta));
    var descriptor = "import same.api.Service; module service.consumer { requires service.facade; uses Service; provides same.api.Service with impl.Provider; }";
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory.resolve("consumer")), List.of(SourceFile.fromContent(capturedPath(Path.of("module-info.java")), descriptor),
        SourceFile.fromContent(capturedPath(Path.of("impl/Provider.java")), "package impl; public class Provider implements same.api.Service {}")));
    var frontend = new JavaFrontend(new JavaSourceLoader());
    // Act
    var first = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot), configuration(List.of(alpha, facadeAlpha)), AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var second = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot), configuration(List.of(beta, facadeBeta)), AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var foreignSnapshot = InputSnapshot.fromSources(capturedAbsolute(directory.resolve("consumer")), snapshot.sourceFiles().stream().map(source ->
        SourceFile.fromContent(source.relativePath(), source.content().replace("with impl.Provider", "with same.impl.ExternalProvider"))).toList());
    var foreign = frontend.produce(new AstProductionRequest(new SourceInput.Snapshot(foreignSnapshot), configuration(List.of(alpha, facadeAlpha)), AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    // Assert
    assertEquals(AstCompletion.COMPLETE, first.completion(), first.diagnostics().toString());
    assertEquals(AstCompletion.COMPLETE, second.completion(), second.diagnostics().toString());
    assertEquals(first.snapshot(), second.snapshot());
    assertNotEquals(first.identity(), second.identity());
    for (var index = 0; index < 2; index++) {
      var left = endpoints(first).get(index);
      var right = endpoints(second).get(index);
      assertEquals(left.origin(), right.origin());
      assertEquals(left.structuralPath(), right.structuralPath());
      assertEquals(left.value(), right.value());
      assertEquals(new JavaTypeDeclarationIdentity(Optional.of("service.alpha"), "same.api.Service"), left.semanticReference().orElseThrow().declarationIdentity().orElseThrow());
      assertEquals(new JavaTypeDeclarationIdentity(Optional.of("service.beta"), "same.api.Service"), right.semanticReference().orElseThrow().declarationIdentity().orElseThrow());
      assertNotEquals(left.semanticReference().orElseThrow().symbolKey(), right.semanticReference().orElseThrow().symbolKey());
      assertTrue(first.program().types().stream().noneMatch(type -> Optional.of(type.typeKey()).equals(left.semanticReference().orElseThrow().symbolKey())));
      assertTrue(second.program().types().stream().noneMatch(type -> Optional.of(type.typeKey()).equals(right.semanticReference().orElseThrow().symbolKey())));
    }
    assertEquals(endpoints(first).get(2).semanticReference(), endpoints(second).get(2).semanticReference());
    assertEquals(AstCompletion.INCOMPLETE, foreign.completion(), foreign.diagnostics().toString());
    assertCompilerErrors(foreign, List.of("compiler.err.service.implementation.not.in.right.module"), List.of("module-info.java"), List.of("provides same.api.Service with same.impl.ExternalProvider;"));
    assertResolvedLegalityFailure(foreign);
    assertEquals("same.impl.ExternalProvider", endpoints(foreign).get(2).value(SyntaxValue.ModuleTypeReference.class).name().dottedName());
    assertEquals(new JavaTypeDeclarationIdentity(Optional.of("service.alpha"), "same.impl.ExternalProvider"), endpoints(foreign).get(2).semanticReference().orElseThrow().declarationIdentity().orElseThrow());
  }

  @Test
  void givenPlatformTypesAndNestedExecution_whenSuccessorProduced_thenBoundsAnnotationsBoxingAndOwnerKeysRemainConsistent() {
    // Arrange
    var sources = Map.of("module-info.java", "module service.platform { uses api.Service; provides api.Service with impl.Provider; }",
        "api/Service.java", "package api; public interface Service {}",
        "impl/Provider.java", """
            package impl;
            @interface Mark { String value() default "a" + "b"; Class<?> type() default int.class; }
            @Mark(type=String.class) public class Provider<T> implements api.Service {
              public Provider() {}
              public Class<?> literal() { return int.class; }
              public String text() { return "a" + "b"; }
              public Object local() { class Local { Object value; } return new Local(); }
              public Object anonymous() { return new Object() {}; }
              public Object boxed() { Integer number = 3; return number; }
              public Object generic() { return new Box("text"); }
            }
            class Box { public <U> Box(U value) {} }
            """);
    // Act
    var result = produce(sources);
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    assertEquals(3, endpoints(result).size());
    assertTrue(result.program().types().stream().filter(type -> !type.typeParameters().isEmpty()).allMatch(type -> type.typeKey().startsWith("java-type-declaration:")));
    var generic = result.program().methods().stream().filter(method -> method.name().equals("generic")).findFirst().orElseThrow();
    var execution = result.aggregate().executionSyntax(generic.methodKey()).orElseThrow();
    var creation = execution.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION).findFirst().orElseThrow();
    var constructor = result.program().types().stream().filter(type -> type.qualifiedName().equals("impl.Box")).findFirst().orElseThrow().methods().getFirst();
    assertEquals(constructor.methodKey(), creation.semanticReference().orElseThrow().symbolKey().orElseThrow());
    assertTrue(execution.useEvidence(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow().conversion().knownValue().isPresent());
    assertEquals(result.identity(), AstCodec.decode(AstCodec.encode(result), result.identity()).identity());
  }

  @Test
  void givenAcceptedSourceFidelityInputsInUnnamedModule_whenSuccessorProduced_thenWrittenFactsAndExplicitUnnamedTargetsRemain() {
    // Arrange
    var snapshot = SourceFidelityFixture.snapshot(directory);
    var frontend = new JavaFrontend(new JavaSourceLoader());
    // Act
    var result = frontend.produce(request(snapshot, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(result), result.identity());
    // Assert
    assertEquals(AstCompletion.COMPLETE, result.completion(), result.diagnostics().toString());
    SourceFidelityFixture.assertSourceFacts(result);
    SourceFidelityFixture.assertSourceFacts(retained);
    assertTrue(endpoints(result).isEmpty());
    assertTrue(result.aggregate().nodes().stream().flatMap(node -> node.semanticReference().stream())
        .flatMap(reference -> reference.declarationIdentity().stream()).anyMatch(identity -> identity.moduleName().isEmpty() && identity.binaryName().equals("Tag")));
    assertEquals(result.identity(), retained.identity());
  }

  @Test
  void givenControlledMissingOperandPosition_whenCapturedAndTranslated_thenRequiredRootIsWithheldWithTruthfulDiagnostic() throws IOException {
    // Arrange
    var snapshot = snapshot("module service.sample { uses api.Service; provides api.Service with impl.Provider; }", "Provider");
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
    var profile = AstProfile.JAVA_SOURCE_MODULE_SERVICES;
    var baseline = new JavaFrontend(new JavaSourceLoader()).produce(request(snapshot, profile)).result().orElseThrow();
    var sources = new SnapshotSourceCatalog(snapshot);
    var policy = JavaTranslationPolicy.forProfile(profile);
    try (var session = CompilerSessionAcquisition.open(ToolProvider.getSystemJavaCompiler(), JavaCompilerInvocation.from(configuration), sources)) {
      var parsed = session.parse();
      var module = parsed.units().stream().filter(unit -> unit.getModule() != null).findFirst().orElseThrow().getModule();
      var absent = ((UsesTree) module.getDirectives().getFirst()).getServiceName();
      var evidence = new JavaSourceEvidence(new SourcePositions() {
        @Override public long getStartPosition(CompilationUnitTree unit, Tree tree) {
          return JavaProjectionIdentity.sameInstance(tree, absent) ? Diagnostic.NOPOS
              : parsed.sourceEvidence().origin(unit, sources.find(unit).orElseThrow(), tree).map(origin -> origin.startOffset()).orElse(Diagnostic.NOPOS);
        }
        @Override public long getEndPosition(CompilationUnitTree unit, Tree tree) {
          return JavaProjectionIdentity.sameInstance(tree, absent) ? Diagnostic.NOPOS
              : parsed.sourceEvidence().origin(unit, sources.find(unit).orElseThrow(), tree).map(origin -> origin.endOffsetExclusive()).orElse(Diagnostic.NOPOS);
        }
      });
      // Act
      var captured = JavaSourceCoverageCapture.capture(snapshot, new ParsedJavaSource(parsed.units(), evidence, parsed.treeInventory()), sources,
          JavaLanguageFeatureCoverage.fromConfiguration(configuration), policy);
      var compilation = session.analyze(parsed, policy);
      var translated = new JavaProgramTranslator(sources, compilation.semantics(), evidence, parsed.treeInventory(), policy).translate(snapshot, compilation.units());
      var input = new SyntaxAnalysisInput(baseline.snapshot(), baseline.environment(), baseline.program(), Optional.of(baseline.aggregate()), baseline.diagnostics(), baseline.coverage());
      var altered = JavaCoverageTestSupport.capture(input, profile).withFacts(translated.value().program(), translated.value().aggregate(), translated.emissions());
      var outcome = AstProductionAdmission.outcome(altered, profile, policy.semanticRevision());
      // Assert
      assertEquals(1, captured.inventory().contexts().stream().filter(context -> context.address().partition() == SourceContext.Partition.MODULE_ENDPOINT && context.origins().isEmpty()).count());
      assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
      assertTrue(translated.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("frontend.missing-source-position")));
    }
  }

  @Test
  void givenContradictoryResolvedModuleExpectation_whenFailedCoverageIsSupplied_thenIntrinsicAdmissionStillRejectsTheKnownConflict() {
    // Arrange
    var original = new JavaFrontend(new JavaSourceLoader()).produce(request(snapshot(
        "module service.sample { uses api.Service; provides api.Service with impl.Provider; }", "Provider"),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
    var evidence = original.coverage().validationEvidence();
    var other = new JavaTypeDeclarationIdentity(Optional.of("service.other"), "api.Service");
    var projections = evidence.attributedExpectations().projections().stream().map(projection ->
        projection.address().partition() == SourceContext.Partition.MODULE_ENDPOINT
            ? new AttributedCoverageExpectations.Projection(projection.address(), ModuleServiceFacts.targetValue(other.declarationKey()), Optional.empty())
            : projection).toList();
    var attributed = new AttributedCoverageExpectations(evidence.writtenInventory(), projections);
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(original.aggregate()),
        original.diagnostics(), original.coverage());
    var captured = JavaCoverageTestSupport.capture(input, original.profile());
    var reconciled = captured.reconcile(evidence.writtenInventory(), attributed, original.program(), Optional.of(original.aggregate()), captured.emissions());
    var endpoints = reconciled.coverageResult().validationEvidence().outcomes().stream()
        .filter(outcome -> outcome.address().partition() == SourceContext.Partition.MODULE_ENDPOINT).toList();
    assertEquals(3, endpoints.size());
    assertTrue(endpoints.stream().allMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FAILED));
    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new SyntaxAnalysisInput(original.snapshot(), original.environment(),
        original.program(), Optional.of(original.aggregate()), reconciled.diagnostics(), reconciled.coverageResult()));
    // Assert
    assertTrue(failure.getMessage().contains("module") && failure.getMessage().contains("contradict"), failure.toString());
  }

  private Path compileModule(String name, Map<String, String> sources, List<Path> dependencies) throws IOException {
    var root = directory.resolve(name);
    var output = root.resolve("classes");
    Files.createDirectories(output);
    var arguments = new ArrayList<>(List.of("--release", "25", "-d", output.toString()));
    if (!dependencies.isEmpty()) {
      arguments.addAll(List.of("--module-path", String.join(File.pathSeparator, dependencies.stream().map(Path::toString).toList())));
    }
    for (var entry : sources.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
      var source = root.resolve("source").resolve(entry.getKey());
      Files.createDirectories(source.getParent());
      Files.writeString(source, entry.getValue());
      arguments.add(source.toString());
    }
    var diagnostics = new ByteArrayOutputStream();
    assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics, arguments.toArray(String[]::new)), diagnostics.toString(StandardCharsets.UTF_8));
    return output;
  }

  private static void assertProviderOrder(AstResult result, List<String> spellings, List<String> binaries) {
    var descriptor = result.snapshot().sourceFiles().stream().filter(source -> source.canonicalRelativePath().equals("module-info.java")).findFirst().orElseThrow();
    var directive = result.aggregate().nodes().stream().filter(node -> node.sourceKind() == JavaSourceKind.PROVIDES).findFirst().orElseThrow();
    var providers = directive.children(SyntaxRole.PROVIDER_TYPE);
    assertEquals(spellings.size(), providers.size());
    var cursor = descriptor.content().indexOf("with ") + "with ".length();
    for (var index = 0; index < spellings.size(); index++) {
      var written = spellings.get(index);
      var start = descriptor.content().indexOf(written, cursor);
      assertTrue(start >= cursor);
      var expectedOrigin = new SourceSpan(descriptor.sourceFileKey(), start, start + written.length());
      var actual = providers.get(index);
      var expectedIdentity = new JavaTypeDeclarationIdentity(Optional.of("service.factories"), binaries.get(index));
      assertEquals(written, actual.value(SyntaxValue.ModuleTypeReference.class).name().dottedName());
      assertEquals(expectedOrigin, actual.origin().orElseThrow());
      assertEquals(SyntaxRole.PROVIDER_TYPE, actual.structuralPath().steps().getLast().role());
      assertEquals(index, actual.structuralPath().steps().getLast().position());
      assertEquals(ResolutionStatus.RESOLVED, actual.semanticReference().orElseThrow().status());
      assertEquals(expectedIdentity, actual.semanticReference().orElseThrow().declarationIdentity().orElseThrow());
      assertEquals(expectedIdentity.declarationKey(), actual.semanticReference().orElseThrow().symbolKey().orElseThrow());
      var association = result.aggregate().writtenSourceAssociation(actual.nodeId()).orElseThrow();
      assertEquals(SourceOwnerPath.Role.IMPLEMENTATION_NAME, association.ownerPath().steps().getLast().role());
      assertEquals(index, association.ownerPath().steps().getLast().ordinal());
      assertEquals(Optional.of(expectedOrigin), association.origin());
      assertEquals(actual, result.node(result.occurrence(actual).orElseThrow()).orElseThrow());
      cursor = start + written.length();
    }
  }

  private static void assertCompilerErrors(AstResult result, List<String> codes, List<String> files, List<String> fragments) {
    var errors = result.diagnostics().stream().filter(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR).toList();
    assertEquals(codes, errors.stream().map(AnalysisDiagnostic::code).toList(), result.diagnostics().toString());
    var events = result.coverage().validationEvidence().diagnosticEmissions().stream().filter(event -> event.cause() == AnalysisOutcomeKind.COMPILER_ERROR).toList();
    assertEquals(errors, events.stream().map(event -> event.diagnostic()).toList());
    for (var index = 0; index < errors.size(); index++) {
      var fileName = files.get(index);
      var source = result.snapshot().sourceFiles().stream().filter(file -> file.canonicalRelativePath().equals(fileName)).findFirst().orElseThrow();
      var start = source.content().indexOf(fragments.get(index));
      assertTrue(start >= 0);
      var diagnostic = errors.get(index);
      assertEquals(DiagnosticSeverity.ERROR, diagnostic.severity());
      assertEquals(DiagnosticStage.FRONTEND, diagnostic.stage());
      assertEquals(List.of(new SourceSpan(source.sourceFileKey(), start, start + fragments.get(index).length())), diagnostic.origins());
      assertEquals("CompilerDiagnosticTranslator", events.get(index).address().producer());
      assertEquals(result.diagnostics().indexOf(diagnostic), events.get(index).slot());
    }
  }

  private static void assertResolvedLegalityFailure(AstResult result) {
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertTrue(endpoints(result).stream().allMatch(node -> node.semanticReference().orElseThrow().status() == ResolutionStatus.RESOLVED));
    assertTrue(result.coverage().validationEvidence().outcomes().stream().filter(outcome -> outcome.address().partition() == SourceContext.Partition.MODULE_ENDPOINT).allMatch(outcome -> outcome.support().mode() == FeatureContextOutcome.Mode.FACTS));
    assertTrue(result.diagnostics().stream().filter(AnalysisDiagnostic::isError).allMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
    assertTrue(result.diagnostics().stream().anyMatch(diagnostic -> diagnostic.outcomeKind() == AnalysisOutcomeKind.COMPILER_ERROR));
  }

  private static CompilerConfiguration configuration(List<Path> modules) {
    return new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), modules.stream().map(path -> capturedAbsolute(path)).toList(), List.of());
  }

  private AstResult produce(Map<String, String> sources) {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), sources.entrySet().stream().sorted(Map.Entry.comparingByKey())
        .map(entry -> SourceFile.fromContent(capturedPath(Path.of(entry.getKey())), entry.getValue())).toList());
    return new JavaFrontend(new JavaSourceLoader()).produce(request(snapshot, AstProfile.JAVA_SOURCE_MODULE_SERVICES)).result().orElseThrow();
  }

  private static Map<String, String> withDescriptor(Map<String, String> sources, String descriptor) {
    var changed = new HashMap<>(sources);
    changed.put("module-info.java", descriptor);
    return Map.copyOf(changed);
  }

  private InputSnapshot snapshot(String descriptor, String provider) {
    return InputSnapshot.fromSources(capturedAbsolute(directory), List.of(
        SourceFile.fromContent(capturedPath(Path.of("module-info.java")), descriptor),
        SourceFile.fromContent(capturedPath(Path.of("api/Service.java")), "package api; public interface Service {}"),
        SourceFile.fromContent(capturedPath(Path.of("impl/" + provider + ".java")), "package impl; public class " + provider + " implements api.Service { public " + provider + "() {} public int value() { return 7; } }")));
  }

  static AstProductionRequest request(InputSnapshot snapshot, AstProfile profile) {
    return new AstProductionRequest(new SourceInput.Snapshot(snapshot), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
  }

  static List<SyntaxNode> endpoints(AstResult result) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.MODULE_TYPE_REFERENCE)
        .sorted(Comparator.comparingLong(node -> node.origin().orElseThrow().startOffset())).toList();
  }

  static void assertEndpoint(AstResult result, SyntaxNode node, String descriptor, String spelling, SyntaxRole role, String module, String binary) {
    var reference = node.semanticReference().orElseThrow();
    var target = reference.declarationIdentity().orElseThrow();
    assertEquals(ResolutionStatus.RESOLVED, reference.status());
    assertEquals(module, target.moduleName().orElseThrow());
    assertEquals(binary, target.binaryName());
    assertEquals(target.declarationKey(), reference.symbolKey().orElseThrow());
    assertEquals(spelling, node.value(SyntaxValue.ModuleTypeReference.class).name().dottedName());
    assertEquals(role, node.structuralPath().steps().getLast().role());
    var origin = node.origin().orElseThrow();
    assertEquals(spelling, descriptor.substring(Math.toIntExact(origin.startOffset()), Math.toIntExact(origin.endOffsetExclusive())));
    assertTrue(result.aggregate().writtenSourceAssociation(node.nodeId()).isPresent());
    assertTrue(result.program().types().stream().anyMatch(type -> type.typeKey().equals(reference.symbolKey().orElseThrow())));
    assertEquals(node, result.node(result.occurrence(node).orElseThrow()).orElseThrow());
  }
}
