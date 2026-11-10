package org.aquilagraph.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgOutcome;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.CfgState;
import org.aquilagraph.cfg.api.MethodCfgOutcome;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.internal.SourceValueCapture;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class MethodOutcomeAdmissionTest {
  @TestFactory
  Stream<DynamicTest> givenBodyBearingDeclarationAndNotApplicableOutcome_whenConstructingRepresentation_thenTheAssociationIsRejected() {
    return Stream.of(MethodBodyOrigin.values()).map(origin -> DynamicTest.dynamicTest(origin.name(), () -> assertRepresentationRejects(origin)));
  }

  private static void assertRepresentationRejects(MethodBodyOrigin origin) {
    // Arrange
    var fixture = body(origin);
    var source = AstOutcome.available(fixture.ast());
    var cfg = notApplicable(fixture);

    // Act
    var failure = assertThrows(IllegalArgumentException.class, () -> new ProgramRepresentation(source, Optional.of(cfg)));

    // Assert
    assertBodyConflict(failure, fixture.declaration());
  }

  @TestFactory
  Stream<DynamicTest> givenBodyBearingDeclarationAndNotApplicableOutcome_whenConstructingStandaloneCoverage_thenTheAssociationIsRejected() {
    return Stream.of(MethodBodyOrigin.values()).map(origin -> DynamicTest.dynamicTest(origin.name(), () -> assertCoverageRejects(origin)));
  }

  private static void assertCoverageRejects(MethodBodyOrigin origin) {
    // Arrange
    var fixture = body(origin);
    var declaration = fixture.declaration();
    var outcome = notApplicable(fixture).methods().getFirst();

    // Act
    var failure = assertThrows(IllegalArgumentException.class,
        () -> new MethodCoverage(declaration, MethodCoverageState.NOT_APPLICABLE, Optional.of(outcome)));

    // Assert
    assertBodyConflict(failure, declaration);
  }

  @Test
  void givenBodyWithUnavailablePosition_whenDerivingWithTheStandardPort_thenCoverageRetainsTheNativeRefusal() {
    // Arrange
    var fixture = body(MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION);
    var ast = fixture.ast();
    var key = fixture.declaration().methodKey();
    var request = new CfgRequest(ast.identity(), List.of(key), CfgProfile.JAVA_BOUNDED);
    var derivation = AnalysisCapabilities.existingSourceDerivation(CfgDerivers.standard());

    // Act
    var run = derivation.deriveExisting(AstOutcome.available(ast), request);
    var coverage = run.methodCoverage(key).orElseThrow();

    // Assert
    assertEquals(AnalysisCompletion.PARTIAL, run.completion());
    assertEquals(MethodCoverageState.SOURCE_UNAVAILABLE, coverage.state());
    assertSame(fixture.declaration(), coverage.declaration());
    var outcome = run.representation().cfg().orElseThrow().methods().getFirst();
    assertSame(outcome, coverage.cfg().orElseThrow());
    assertTrue(outcome.body().isEmpty());
    assertTrue(outcome.graph().isEmpty());
  }

  @Test
  void givenContradictoryOutcomeFromTheCfgPort_whenComputingOrDeriving_thenBothEntriesRejectTheAssociation() {
    // Arrange
    var fixture = body(MethodBodyOrigin.SOURCE_AST);
    var ast = fixture.ast();
    var cfg = notApplicable(fixture);
    var production = new AstProductionRequest(ast.snapshot(), CompilerConfiguration.forRelease(JavaRelease.JAVA_25), ast.profile());
    var request = new AnalysisComputationRequest(production,
        Optional.of(new CfgSelection(cfg.request().methodKeys(), cfg.request().profile())));
    var computation = AnalysisCapabilities.computation(new JavaFrontend(new JavaSourceLoader()), (source, selection) -> cfg);
    var derivation = AnalysisCapabilities.existingSourceDerivation((source, selection) -> cfg);
    var established = AstOutcome.available(ast);

    // Act
    var productionFailure = assertThrows(IllegalArgumentException.class, () -> computation.analyze(request));
    var derivationFailure = assertThrows(IllegalArgumentException.class, () -> derivation.deriveExisting(established, cfg.request()));

    // Assert
    assertBodyConflict(productionFailure, fixture.declaration());
    assertBodyConflict(derivationFailure, fixture.declaration());
  }

  private static BodyFixture body(MethodBodyOrigin origin) {
    var original = TestAnalysisRuns.fromSource("class Example { int value() { return 1; } }").representation().ast().result().orElseThrow();
    var methods = original.program().methods().stream();
    var declaration = (origin == MethodBodyOrigin.SOURCE_AST ? methods.filter(method -> method.name().equals("value"))
        : methods.filter(JavaMethod::constructor)).findFirst().orElseThrow();
    var key = declaration.methodKey();
    var ast = origin == MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION ? withoutBodyPosition(original, key) : original;
    assertTrue(declaration.hasBody());
    assertEquals(origin, ast.aggregate().methodBodyOrigin(key).orElseThrow());
    switch (origin) {
      case SOURCE_AST -> assertTrue(ast.bodyRef(key).orElseThrow().sourceOccurrenceKey().isPresent());
      case COMPILER_ATTRIBUTED_BODY -> assertTrue(ast.bodyRef(key).orElseThrow().nonwrittenProvenance().isPresent());
      case UNAVAILABLE_SOURCE_POSITION -> assertTrue(ast.bodyRef(key).isEmpty());
    }
    return new BodyFixture(ast, ast.method(key).orElseThrow());
  }

  private static AstResult withoutBodyPosition(AstResult original, String key) {
    var copied = SourceValueCapture.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> body.methodKey().equals(key)
        ? new CopiedAstValues.Body(body.methodKey(), body.root(), MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION, body.uses(),
            body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())
        : body).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage());
    return AstResultAdmission.admit(input, original.profile(), original.identity().semanticProductionRevision());
  }

  private static CfgOutcome notApplicable(BodyFixture fixture) {
    var identity = fixture.ast().identity();
    var key = fixture.declaration().methodKey();
    var method = new MethodCfgOutcome(identity, key, MethodCfgState.NOT_APPLICABLE, Optional.empty(), Optional.empty(), List.of(),
        Optional.empty(), List.of());
    return new CfgOutcome(new CfgRequest(identity, List.of(key), CfgProfile.JAVA_BOUNDED), CfgState.COMPLETE, List.of(method), List.of());
  }

  private static void assertBodyConflict(IllegalArgumentException failure, JavaMethod declaration) {
    assertTrue(failure.getMessage().contains(declaration.methodKey()), failure::toString);
    assertTrue(failure.getMessage().contains(MethodCfgState.NOT_APPLICABLE.name()), failure::toString);
  }

  private record BodyFixture(AstResult ast, JavaMethod declaration) {}
}
