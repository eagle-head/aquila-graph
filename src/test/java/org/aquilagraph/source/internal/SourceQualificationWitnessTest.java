package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.frontend.internal.SourcePositionFixtures;
import com.sun.source.tree.Tree;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SourceQualificationReason;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.SourceQualificationReason.Category;
import org.aquilagraph.source.api.SourceQualificationReason.EvidenceRole;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class SourceQualificationWitnessTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenLegalBoundedProfileRestrictions_whenProducingAndDecoding_thenNativeReasonsRetainTheirActualSubjects() {
    var cases = List.of(
        new Restriction("UnnamedLocal", "class Subject { void run() { int _ = 1; } }", EvidenceRole.LOCAL_NAME),
        new Restriction("LocalAnnotation", "class Subject { void run() { @Deprecated int value = 1; } }", EvidenceRole.LOCAL_ANNOTATIONS),
        new Restriction("SharedAnnotation", "class Subject { void run() { @Deprecated int left = 1, right = 2; } }", EvidenceRole.LOCAL_SHARED_ANNOTATIONS),
        new Restriction("LocalClass", "class Subject { void run() { class Nested {} } }", EvidenceRole.SOURCE_FORM),
        new Restriction("AnonymousClass", "class Subject { Object run() { return new Object() {}; } }", EvidenceRole.SOURCE_FORM),
        new Restriction("NestedPattern", "record Box(Object value) {} class Subject { boolean run(Object value) { return value instanceof Box(_); } }", EvidenceRole.PROFILE_FORM));
    return cases.stream().map(example -> DynamicTest.dynamicTest(
        "given" + example.name() + "_whenProducingAndDecoding_thenTheProfileCauseRetainsItsSubject", () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(directory,
              List.of(SourceFile.fromContent(Path.of("Subject.java"), example.source())));
          var frontend = new JavaFrontend(new JavaSourceLoader());

          // Act
          var original = frontend.produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
              AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertTrue(original.diagnostics().stream().noneMatch(diagnostic -> diagnostic.outcomeKind()
              == AnalysisOutcomeKind.COMPILER_ERROR), original.diagnostics().toString());
          assertTrue(decoded.qualification().reasons().stream()
              .anyMatch(reason -> reason.category() == (example.role() == EvidenceRole.SOURCE_FORM
                  ? Category.STRUCTURAL_COVERAGE_GAP : Category.PROFILE_RESTRICTION) && reason.role() == example.role()),
              decoded.qualification().reasons().toString());
          assertEquals(original.qualification(), decoded.qualification());
          assertEquals(original.identity(), decoded.identity());
        }));
  }

  @Test
  void givenAnEmptyCopiedCapture_whenAdmittingProduction_thenCaptureAndRootAbsenceRemainSeparateNativeCauses() throws IOException {
    // Arrange
    var original = expanded("class Subject {}");
    var snapshot = InputSnapshot.fromSources(directory, List.of());
    var contract = original.coverage().coverage();
    var inventory = new SourceCoverageInventory(snapshot, JavaRelease.JAVA_25, contract.contractIdentity(), List.of());
    var coverage = CoverageAdmission.fromEvidence(contract, inventory, new AttributedCoverageExpectations(inventory, List.of()),
        List.of(), List.of(), List.of());
    var input = new SyntaxAnalysisInput(snapshot, original.environment(), new JavaProgram(List.of()), Optional.empty(), List.of(), coverage);

    // Act
    var outcome = AstResultAdmission.outcome(input, original.profile(), original.identity().semanticProductionRevision());

    // Assert
    assertEquals(AstCompletion.UNAVAILABLE, outcome.completion());
    assertEquals(List.of(EvidenceRole.SOURCE_CAPTURE, EvidenceRole.SOURCE_ROOTS),
        outcome.qualification().reasons().stream().map(SourceQualificationReason::role).toList());
    assertTrue(outcome.qualification().reasons().stream().allMatch(reason -> reason.origins().isEmpty()));
  }

  @TestFactory
  Stream<DynamicTest> givenIncompleteCopiedDeclarationMetadata_whenAdmittingAndDecoding_thenDeclarationReasonsKeepTheirExactOwner() {
    return Stream.of(false, true).map(erroneous -> DynamicTest.dynamicTest(
        "given" + (erroneous ? "ErroneousType" : "MissingLexicalOwnerDetails")
            + "_whenAdmittingAndDecoding_thenTheExactDeclarationCauseSurvives", () -> {
          // Arrange
          var original = expanded("class Subject { class First {} class Second {} }");
          var targets = original.program().types().stream().filter(type -> type.nesting().lexicalOwnerKey().isPresent()).toList();
          var program = new JavaProgram(original.program().types().stream().map(target -> {
            if (!targets.contains(target)) {
              return target;
            }
            return new JavaType(target.typeKey(), target.qualifiedName(), target.kind(), target.modifiers(), target.enclosingType(),
              target.directSuperclass(), target.directInterfaces(), target.permittedSubclasses(), target.typeParameters(), target.sourceDeclared(),
              target.fields(), target.recordComponents(), target.methods(), target.synthesizedDeclarations(), target.origins(),
              erroneous ? target.nesting() : new JavaTypeNesting(target.nesting().kind(), target.nesting().lexicalOwnerKey()),
              erroneous ? new JavaTypeIdentity(JavaTypeCategory.ERROR, "<error>", "<error>") : target.staticType(), target.displayName());
          }).toList());
          var copied = SourceValueCapture.capture(original.aggregate());
          var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
              copied.associations(), List.of(), copied.scopes()));
          var input = JavaCoverageTestSupport.withFacts(SourceValueCapture.capture(original), program, Optional.of(aggregate),
              original.coverage().validationEvidence().diagnosticEmissions());

          // Act
          var result = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var role = erroneous ? EvidenceRole.DECLARED_TYPE : EvidenceRole.LEXICAL_OWNER;
          var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == role).toList();

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(2, reasons.size());
          assertEquals(targets.stream().map(target -> new SourceQualificationReason.Declaration(target.typeKey())).toList(),
              reasons.stream().map(SourceQualificationReason::subject).toList());
          assertEquals(targets.stream().map(JavaType::origins).toList(), reasons.stream().map(SourceQualificationReason::origins).toList());
          assertEquals(erroneous ? Category.ERRONEOUS_EVIDENCE : Category.MISSING_REQUIRED_EVIDENCE, reasons.getFirst().category());
          assertEquals(result.qualification(), decoded.qualification());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenMultipleUnpositionedMethodBodies_whenProducingAndDecoding_thenDeclarationReasonsRetainWrittenOrder() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_MODULE_SERVICES).map(profile -> DynamicTest.dynamicTest(
        "givenUnavailableBodiesIn" + profile.version() + "_whenProducingAndDecoding_thenReasonsKeepTheirMethodOrder", () -> {
          // Arrange
          var source = "class Example { int first() { return 1; } int second() { return 2; } }";

          // Act
          var original = SourcePositionFixtures.produce(source, tree -> tree.getKind() == Tree.Kind.BLOCK, profile);
          var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
          var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.METHOD_BODY).toList();

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(List.of("first", "second"), reasons.stream()
              .map(reason -> decoded.method(((SourceQualificationReason.Declaration) reason.subject()).key()).orElseThrow().name()).toList());
          assertEquals(original.qualification(), decoded.qualification());
        }));
  }

  @Test
  void givenCopiedUnavailableBodiesWithoutWrittenDeclarationTrees_whenAdmittingAndDecoding_thenReasonsUseTheRetainedBodySource() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Subject.java"),
        "interface Subject { static int first() { return 1; } static int second() { return 2; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var copied = SourceValueCapture.capture(original.aggregate());
    var sources = copied.sources().stream().map(source -> {
      var root = SyntaxNode.source(source.sourceFileKey(), source.root().structuralPath(), source.root().sourceKind(), source.root().syntaxKind(),
          source.root().type(), source.root().value(), List.of(), source.root().semanticReference(), source.root().origin().orElseThrow());
      return new SyntaxSourceContribution(source.sourceFileKey(), root, List.of(root), List.of(), root.semanticEdges());
    }).toList();
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), withoutPositions(body.root()),
        MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, List.of(), List.of(), List.of(), List.of(), List.of(), List.of())).toList();
    var rootKeys = sources.stream().map(source -> source.root().nodeId()).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), sources, bodies, List.of(),
        copied.associations().stream().filter(value -> rootKeys.contains(value.nodeKey())).toList(), List.of()));
    var input = JavaCoverageTestSupport.withFacts(SourceValueCapture.capture(original), original.program(), Optional.of(aggregate),
        original.coverage().validationEvidence().diagnosticEmissions());

    // Act
    var result = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.METHOD_BODY).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(List.of("first", "second"), decoded.program().methods().stream().map(method -> method.name()).toList());
    assertEquals(original.program().methods().stream().map(method -> new SourceQualificationReason.Declaration(method.methodKey())).toList(),
        reasons.stream().map(SourceQualificationReason::subject).toList());
    assertTrue(decoded.aggregate().nodes().stream().noneMatch(node -> node.value() instanceof SyntaxValue.TypeDeclaration));
    assertEquals(result.qualification(), decoded.qualification());
  }

  @Test
  void givenErroneousCopiedImplicitTypesInSeveralFiles_whenAdmittingAndDecoding_thenReasonsKeepTheirCapturedFileOrder() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(
        SourceFile.fromContent(Path.of("First.java"), "void main() {}"), SourceFile.fromContent(Path.of("Second.java"), "void main() {}")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
    var program = new JavaProgram(original.program().types().stream().map(type -> new JavaType(type.typeKey(), type.qualifiedName(), type.kind(),
        type.modifiers(), type.enclosingType(), type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(), type.typeParameters(),
        type.sourceDeclared(), type.fields(), type.recordComponents(), type.methods(), type.synthesizedDeclarations(), type.origins(), type.nesting(),
        new JavaTypeIdentity(JavaTypeCategory.ERROR, "<error>", "<error>"), type.displayName())).toList());
    var copied = SourceValueCapture.capture(original.aggregate());
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
        copied.associations(), List.of(), copied.scopes()));
    var input = JavaCoverageTestSupport.withFacts(SourceValueCapture.capture(original), program, Optional.of(aggregate),
        original.coverage().validationEvidence().diagnosticEmissions());

    // Act
    var result = AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.DECLARED_TYPE).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(original.program().types().stream().map(type -> new SourceQualificationReason.Declaration(type.typeKey())).toList(),
        reasons.stream().map(SourceQualificationReason::subject).toList());
    assertEquals(result.qualification(), decoded.qualification());
  }

  @Test
  void givenCopiedNestedSourceUnderAnEarlierExecutionProfile_whenAdmittingAndDecoding_thenEveryNestedFormKeepsItsRestriction() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Subject.java"),
        "class Subject { Object run() { class Local {} return new Object() {}; } }")));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_NESTED).result().orElseThrow();

    // Act
    var result = AstResultAdmission.admit(SourceValueCapture.capture(original), AstProfile.JAVA_SOURCE_EXPANDED,
        original.identity().semanticProductionRevision());
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.PROFILE_FORM).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
    assertEquals(List.of(SyntaxKind.TYPE_DECLARATION, SyntaxKind.OBJECT_CREATION, SyntaxKind.TYPE_DECLARATION),
        reasons.stream().map(reason -> decoded.aggregate().node(((SourceQualificationReason.Node) reason.subject()).nodeKey())
            .orElseThrow().syntaxKind()).toList());
    assertTrue(reasons.stream().allMatch(reason -> reason.category() == Category.PROFILE_RESTRICTION));
    assertEquals(result.qualification(), decoded.qualification());
  }

  @Test
  void givenCopiedDeclarationsWithoutExecutionRegistrations_whenAdmittingAndRetaining_thenEveryMissingScopeIsExplained() throws IOException {
    // Arrange
    var original = expanded("enum Subject { VALUE; int field = 1; static {} {} }");
    var copied = SourceValueCapture.capture(original.aggregate());
    var values = new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
        copied.associations(), copied.declarationTypes(), List.of());

    // Act
    var result = readmit(original, values);
    var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
    var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.DECLARATION_SCOPE).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, result.completion());
    assertEquals(4, reasons.size());
    assertTrue(reasons.stream().allMatch(reason -> reason.category() == Category.MISSING_REQUIRED_EVIDENCE));
    assertEquals(result.qualification(), decoded.qualification());
  }

  @TestFactory
  Stream<DynamicTest> givenCopiedLocalBindingsWithoutAttribution_whenAdmittingAndDecoding_thenMissingRequiredBindingFactsAreExplained() {
    return Stream.of(false, true).map(unnamed -> DynamicTest.dynamicTest(
        "given" + (unnamed ? "UnnamedLocalWithoutBinding" : "NamedLocalWithoutDeclaration")
            + "_whenAdmittingAndDecoding_thenTheMissingFactIsExplained", () -> {
          // Arrange
          var original = expanded("class Subject { void run() { int " + (unnamed ? "_" : "value") + " = 1; } }");
          var values = rewrite(original, node -> {
            if (!(node.value() instanceof SyntaxValue.LocalDeclaration local)) {
              return node;
            }
            var binding = local.bindingEvidence().orElseThrow();
            var value = new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
                unnamed ? Optional.empty() : Optional.of(new LocalBindingEvidence(Optional.empty(), binding.typeForm(), binding.context(),
                    binding.initialized(), binding.annotations(), binding.sharedAnnotationKeys())));
            var reference = unnamed ? node.semanticReference() : Optional.of(new SemanticReference(ReferenceKind.LOCAL,
                ResolutionStatus.UNRESOLVED, Optional.empty(), Optional.empty(), List.of(), List.of()));
            return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(),
                value, node.children(), reference, node.origin().orElseThrow());
          });

          // Act
          var result = readmit(original, values);
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var role = unnamed ? EvidenceRole.LOCAL_BINDING : EvidenceRole.LOCAL_DECLARATION;
          var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == role).toList();

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(1, reasons.size());
          assertEquals(Category.MISSING_REQUIRED_EVIDENCE, reasons.getFirst().category());
          assertEquals(result.qualification(), decoded.qualification());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenAbsentOrErroneousCopiedEnumConstructor_whenAdmittingAndRetaining_thenItsEvidenceStateIsExplained() {
    return Stream.of(false, true).map(erroneous -> DynamicTest.dynamicTest(
        "given" + (erroneous ? "Erroneous" : "Absent") + "EnumConstructor_whenAdmittingAndRetaining_thenItsNativeCauseSurvives", () -> {
          // Arrange
          var original = expanded("enum Subject { VALUE }");
          var copied = SourceValueCapture.capture(original.aggregate());
          var scopes = copied.scopes().stream().map(scope -> {
            if (!(scope.scope() instanceof ExecutionScope.EnumConstant constant)) {
              return scope;
            }
            var reference = erroneous ? Optional.of(new SemanticReference(ReferenceKind.CONSTRUCTOR, ResolutionStatus.ERROR,
                Optional.empty(), Optional.empty(), List.of(), List.of())) : Optional.<SemanticReference>empty();
            return new CopiedAstValues.Scope(new ExecutionScope.EnumConstant(constant.key(), constant.ownerKey(), reference),
                scope.root(), scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences());
          }).toList();
          var values = new CopiedAstValues(copied.snapshot(), copied.sources(), copied.bodies(), copied.memberOrders(),
              copied.associations(), copied.declarationTypes(), scopes);

          // Act
          var result = readmit(original, values);
          var decoded = AstCodec.decode(AstCodec.encode(result), result.identity());
          var reasons = decoded.qualification().reasons().stream().filter(reason -> reason.role() == EvidenceRole.ENUM_CONSTRUCTOR).toList();

          // Assert
          assertEquals(AstCompletion.INCOMPLETE, decoded.completion());
          assertEquals(1, reasons.size());
          assertEquals(erroneous ? Category.ERRONEOUS_EVIDENCE : Category.MISSING_REQUIRED_EVIDENCE, reasons.getFirst().category());
          assertEquals(result.qualification(), decoded.qualification());
        }));
  }

  private AstResult expanded(String source) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Subject.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        AstProfile.JAVA_SOURCE_MODULE_SERVICES).result().orElseThrow();
  }

  private static AstResult readmit(AstResult original, CopiedAstValues copied) {
    var aggregate = AstValueAdmission.admit(copied);
    var input = JavaCoverageTestSupport.withFacts(SourceValueCapture.capture(original), original.program(), Optional.of(aggregate),
        original.coverage().validationEvidence().diagnosticEmissions());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }

  private static CopiedAstValues rewrite(AstResult original, UnaryOperator<SyntaxNode> change) {
    var copied = SourceValueCapture.capture(original.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = copied.sources().stream().map(source -> {
      var root = rewriteNode(source.root(), replacements, change);
      var nodes = source.nodes().stream().map(node -> rewriteNode(node, replacements, change)).toList();
      var edges = new ArrayList<>(source.semanticEdges());
      for (var node : source.nodes()) {
        edges.removeAll(node.semanticEdges());
      }
      nodes.forEach(node -> edges.addAll(node.semanticEdges()));
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes, source.structuralEdges(), edges);
    }).toList();
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), rewriteNode(body.root(), replacements, change),
        body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var scopes = copied.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), rewriteNode(scope.root(), replacements, change),
        scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(), copied.associations(), copied.declarationTypes(), scopes);
  }

  private static SyntaxNode rewriteNode(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements, UnaryOperator<SyntaxNode> change) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream()
        .map(child -> new SyntaxChild(child.role(), child.position(), rewriteNode(child.node(), replacements, change))).toList();
    var copy = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
            children, node.semanticReference());
    var result = change.apply(copy);
    replacements.put(node, result);
    return result;
  }

  private static SyntaxNode withoutPositions(SyntaxNode node) {
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(), withoutPositions(child.node()))).toList();
    return SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
        children, node.semanticReference());
  }

  private record Restriction(String name, String source, EvidenceRole role) {}
}
