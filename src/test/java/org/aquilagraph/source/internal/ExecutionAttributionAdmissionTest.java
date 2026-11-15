package org.aquilagraph.source.internal;

import org.aquilagraph.application.AnalysisCapabilities;
import org.aquilagraph.application.AstPublicationRequest;
import org.aquilagraph.application.PublicationIntent;
import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.Trees;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.ExecutionSyntax;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.application.CommitKnowledge;
import org.aquilagraph.application.PublicationStatus;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ExecutionAttributionAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenDeclarationScopesWithoutCompilerRootAttribution_whenQualifiedDecodedAndOfferedForRetention_thenIncompleteValuesPreserveQueriesAndRefuseAcquisition() {
    return Stream.of(
        new AttributionGapCase("Field", "class Example { int field = 1; }"),
        new AttributionGapCase("AbsentFieldInitializer", "class Example { int field; }"),
        new AttributionGapCase("EmptyFieldInitializer", "class Example { int[] field = {}; }"),
        new AttributionGapCase("StaticInitializer", "class Example { static { int local = 2; } }"),
        new AttributionGapCase("InstanceInitializer", "class Example { { int local = 3; } }"),
        new AttributionGapCase("CompactField", "int field = 1; void main() {}"))
        .map(test -> DynamicTest.dynamicTest("given" + test.name() + "WithoutCompilerRootAttribution_whenQualifiedDecodedAndOfferedForRetention_thenIncompleteValuesRefuseAcquisition", () -> {
          // Arrange
          var snapshot = InputSnapshot.fromSources(directory,
              List.of(SourceFile.fromContent(Path.of("Example.java"), test.source())));
          var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_INITIALIZATION).result().orElseThrow();
          var values = SourceValueCapture.capture(original.aggregate());
          var scopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses(),
              scope.invocations(), scope.staticAccesses(), scope.concatenations(), List.of())).toList();
          var supplied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), values.memberOrders(),
              values.associations(), values.declarationTypes(), scopes);
          var publication = AnalysisCapabilities.astRetention(context -> {
            throw new AssertionError("Missing declaration-root attribution must prevent repository acquisition");
          });

          // Act
          var aggregate = AstValueAdmission.admit(supplied);
          var input = new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
              original.diagnostics(), original.coverage());
          var outcome = AstProductionAdmission.outcome(input, original.profile(), original.identity().semanticProductionRevision());
          var qualified = outcome.result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(qualified), qualified.identity());
          var attempt = publication.retainAst(new AstPublicationRequest(outcome, PublicationIntent.SAVE_COMPLETE_AST));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertEquals(1, values.scopes().size());
          var originalScope = original.aggregate().executionSyntax(scopes.getFirst().scope().key()).orElseThrow();
          var retainedScope = retained.aggregate().executionSyntax(scopes.getFirst().scope().key()).orElseThrow();
          assertTrue(originalScope.attributedOccurrences().stream().anyMatch(address -> address.nodeKey().equals(originalScope.root().nodeId())));
          assertEquals(AstCompletion.INCOMPLETE, outcome.completion());
          assertEquals(AstCompletion.INCOMPLETE, retained.completion());
          assertEquals(originalScope.root(), retainedScope.root());
          assertEquals(originalScope.evidence(), retainedScope.evidence());
          assertTrue(retainedScope.attributedOccurrences().isEmpty());
          assertEquals(original.aggregate().nodes(), retained.aggregate().nodes());
          assertEquals(original.aggregate().declarationMemberOrders(), retained.aggregate().declarationMemberOrders());
          assertEquals(original.diagnostics(), retained.diagnostics());
          assertEquals(qualified.aggregate(), retained.aggregate());
          assertEquals(qualified.identity(), retained.identity());
          assertEquals(PublicationStatus.NOT_ELIGIBLE, attempt.status());
          assertEquals(CommitKnowledge.NOT_STARTED, attempt.knowledge());
        }));
  }

  private record AttributionGapCase(String name, String source) {
  }

  @TestFactory
  Stream<DynamicTest> givenDeclarationScopesWithDifferentCompilerMemberLayouts_whenProducedAndRetained_thenActualCompilerAddressesPreserveTheirCorrespondenceToCanonicalSourcePaths() {
    return Stream.of(
        "class Example { int first = 1; static { int local = 2; } { int local = 3; } int second = 4; }",
        "int first = 1; int second = 2; void main() {}",
        "class Example { int first = 1; { int local = 2; } Example() { super(); } }")
        .map(source -> DynamicTest.dynamicTest("given" + declarationLayout(source)
            + "_whenProducedAndRetained_thenActualCompilerAddressesMatchIndependentJavac", () -> {
          // Arrange
          var expected = compilerDeclarationRoots(source);
          var original = produce(source);
          var copied = SourceValueCapture.capture(original.aggregate());

          // Act
          var admitted = qualify(original, copied);
          var retained = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());

          // Assert
          assertEquals(AstCompletion.COMPLETE, retained.completion());
          var declarations = retained.aggregate().executionScopes().stream().filter(scope -> !(scope instanceof ExecutionScope.Method)).toList();
          assertEquals(expected.size(), declarations.size());
          for (var scope : declarations) {
            var syntax = retained.aggregate().executionSyntax(scope.key()).orElseThrow();
            var rawRoot = expected.get(syntax.root().origin().orElseThrow().startOffset());
            var rootAddress = syntax.attributedOccurrences().stream().filter(address -> address.nodeKey().equals(syntax.root().nodeId())).findFirst().orElseThrow();
            assertEquals(rawRoot, rootAddress.compilerPath());
            var literalPath = rawRoot;
            if (scope instanceof ExecutionScope.Initializer) literalPath = literalPath.child(SyntaxRole.STATEMENT, 0);
            assertEquals(literalPath.child(SyntaxRole.INITIALIZER, 0), literal(syntax).compilerPath());
            if (!source.contains("Example()")) assertNotEquals(syntax.root().structuralPath(), rootAddress.compilerPath());
          }
          assertEquals(original.aggregate(), retained.aggregate());
        }));
  }

  @TestFactory
  Stream<DynamicTest> givenForeignDeclarationAttribution_whenPublicValuesAreQualified_thenTheContradictoryPathIsRejected() {
    return Stream.of(
        new AddressCase("ordinary fields", "class Example { int first = 1; int second = 2; }", field("first"), field("second")),
        new AddressCase("compact fields", "int first = 1; int second = 2; void main() {}", field("first"), field("second")),
        new AddressCase("static blocks", "class Example { static { int first = 1; } static { int second = 2; } }", initializer(0), initializer(1)),
        new AddressCase("instance blocks", "class Example { { int first = 1; } { int second = 2; } }", initializer(0), initializer(1)),
        new AddressCase("distinct scope variants", "class Example { int first = 1; { int second = 2; } }", field("first"), initializer(1)))
        .map(test -> DynamicTest.dynamicTest("given" + test.name().replace(" ", "")
            + "_whenQualifiedOrDecoded_thenForeignCompilerPathIsRejected", () -> {
          // Arrange
          var original = produce(test.source());
          var scopes = original.aggregate().executionScopes().stream().map(scope -> original.aggregate().executionSyntax(scope.key()).orElseThrow()).toList();
          var first = scopes.stream().filter(test.first()).findFirst().orElseThrow();
          var second = scopes.stream().filter(test.second()).findFirst().orElseThrow();
          var address = literal(first);
          var supplied = replace(original, address, literal(second).compilerPath());
          var malformed = replaceFrames(AstCodec.encode(original), address, new AttributedBodyOccurrence(address.scopeKey(), address.nodeKey(),
              address.sourceFileKey(), address.sourceKind(), literal(second).compilerPath()), true);

          // Act
          var failure = assertThrows(AstContractException.class, () -> qualify(original, supplied));
          var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, original.identity()));
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("attribut"), failure.getMessage());
          assertTrue(decoding.getMessage().toLowerCase(Locale.ROOT).contains("attribut"), decoding.getMessage());
          assertEquals(AstCompletion.COMPLETE, retained.completion());
          assertEquals(first.attributedOccurrences(), retained.aggregate().executionSyntax(first.scope().key()).orElseThrow().attributedOccurrences());
        }));
  }

  @Test
  void givenTwoMethodBodies_whenOneCompleteAttributionSetClaimsTheOtherBody_thenConflictingCompilerOwnershipIsRejected() throws IOException {
    // Arrange
    var original = produce("class Example { int first() { return 1; } int second() { return 2; } }");
    var values = SourceValueCapture.capture(original.aggregate());
    var first = values.bodies().stream().filter(body -> body.methodKey().equals("type:Example#first()")).findFirst().orElseThrow();
    var second = values.bodies().stream().filter(body -> body.methodKey().equals("type:Example#second()")).findFirst().orElseThrow();
    var firstRoot = first.attributedOccurrences().stream().filter(address -> address.nodeKey().equals(first.root().nodeId())).findFirst().orElseThrow().compilerPath();
    var secondRoot = second.attributedOccurrences().stream().filter(address -> address.nodeKey().equals(second.root().nodeId())).findFirst().orElseThrow().compilerPath();
    var bodies = values.bodies().stream().map(body -> body.equals(first) ? new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences().stream().map(address ->
            new AttributedBodyOccurrence(address.scopeKey(), address.nodeKey(), address.sourceFileKey(), address.sourceKind(), rebase(address.compilerPath(), firstRoot, secondRoot))).toList()) : body).toList();
    var supplied = new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());

    // Act
    var failure = assertThrows(AstContractException.class, () -> qualify(original, supplied));
    var recovered = AstValueAdmission.admit(values);

    // Assert
    assertTrue(failure.getMessage().contains("multiple execution occurrences"), failure.getMessage());
    assertTrue(failure.getCause() instanceof IllegalArgumentException, failure.toString());
    assertSame(first.root(), recovered.methodSyntax(first.methodKey()).orElseThrow().root());
    assertSame(second.root(), recovered.methodSyntax(second.methodKey()).orElseThrow().root());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenNestedAttributedBodies_whenEnclosingBodyClaimsAnInnerOccurrence_thenCopiedAndRetainedAdmissionRefuse() throws IOException {
    // Arrange
    for (var expression : List.of("\"a\" + \"b\" + null", "\"ab\"")) {
      var original = produce("class Example { String outer() { class Inner { int inner() { return 1; } } "
          + "return " + expression + "; } }", AstProfile.JAVA_SOURCE_UNITS);
      var values = SourceValueCapture.capture(original.aggregate());
      var outer = body(values, "#outer(");
      var inner = body(values, "#inner(");
      var statement = inner.root().requiredChild(SyntaxRole.STATEMENT);
      var wrong = new AttributedBodyOccurrence(outer.methodKey(), statement.nodeId(), statement.sourceFileKey(), statement.sourceKind(),
          SyntaxStructuralPath.sourceFileRoot());
      var changed = new ArrayList<>(outer.attributedOccurrences());
      changed.add(wrong);
      var supplied = replaceBody(values, new CopiedAstValues.Body(outer.methodKey(), outer.root(), outer.origin(), outer.uses(),
          outer.invocations(), outer.staticAccesses(), outer.concatenations(), outer.constructors(), changed));
      var malformed = replaceFrames(AstCodec.encode(original), outer.attributedOccurrences(), changed, true);

      // Act
      var failure = assertThrows(AstContractException.class, () -> qualify(original, supplied));
      var decode = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, original.identity()));
      var recovered = AstValueAdmission.admit(values);

      // Assert
      assertEquals(AstCompletion.COMPLETE, original.completion());
      assertEquals(expression.contains(" + "), values.associations().stream()
          .anyMatch(association -> association.normalizedExpression().isPresent()));
      assertTrue(failure.getMessage().contains("owned occurrences"), failure.toString());
      assertTrue(decode.getMessage().contains("owned occurrences"), decode.toString());
      assertEquals(outer.attributedOccurrences(), recovered.executionSyntax(outer.methodKey()).orElseThrow().attributedOccurrences());
      assertEquals(inner.attributedOccurrences(), recovered.executionSyntax(inner.methodKey()).orElseThrow().attributedOccurrences());
    }
  }

  @Test
  void givenDetachedBodyWithWrittenCoordinates_whenAdmittedAndDecoded_thenItsOwnAddressesRemainIndependent() throws IOException {
    // Arrange
    var original = produce("class Example { int member; String subject() { return \"a\" + \"b\" + null; } }",
        AstProfile.JAVA_SOURCE_UNITS);
    var values = SourceValueCapture.capture(original.aggregate());
    var written = body(values, "#subject(");
    var auxiliary = body(values, "#<init>(");
    var detachedRoot = detachedCopy(written.root());
    var copiedRoot = new AttributedBodyOccurrence(auxiliary.methodKey(), detachedRoot.nodeId(), detachedRoot.sourceFileKey(),
        detachedRoot.sourceKind(), detachedRoot.structuralPath());
    var supplied = replaceBody(values, new CopiedAstValues.Body(auxiliary.methodKey(), detachedRoot,
        MethodBodyOrigin.COMPILER_ATTRIBUTED_BODY, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(copiedRoot)));

    // Act
    var isolated = AstValueAdmission.admit(supplied);
    var qualified = qualify(original, supplied);
    var decoded = AstCodec.decode(AstCodec.encode(qualified), qualified.identity());
    var wrong = copiedRoot.compilerPath().child(SyntaxRole.STATEMENT, 0);
    var malformed = replaceFrames(AstCodec.encode(qualified), copiedRoot,
        new AttributedBodyOccurrence(copiedRoot.scopeKey(), copiedRoot.nodeKey(), copiedRoot.sourceFileKey(), copiedRoot.sourceKind(), wrong),
        true);
    var invalid = assertThrows(AstContractException.class, () -> qualify(original, replace(supplied, copiedRoot, wrong)));
    var decodeFailure = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, qualified.identity()));

    // Assert
    assertTrue(values.associations().stream().anyMatch(association -> association.normalizedExpression().isPresent()));
    assertEquals(written.root().nodeId(), detachedRoot.nodeId());
    assertNotSame(written.root(), detachedRoot);
    assertSame(written.root(), isolated.node(detachedRoot.nodeId()).orElseThrow());
    assertSame(detachedRoot, isolated.methodSyntax(auxiliary.methodKey()).orElseThrow().root());
    assertEquals(List.of(copiedRoot), decoded.aggregate().executionSyntax(auxiliary.methodKey()).orElseThrow().attributedOccurrences());
    assertEquals(written.attributedOccurrences(), decoded.aggregate().executionSyntax(written.methodKey()).orElseThrow().attributedOccurrences());
    assertTrue(invalid.getMessage().contains("cannot be rebased"), invalid.toString());
    assertTrue(decodeFailure.getMessage().contains("cannot be rebased"), decodeFailure.toString());
  }

  @Test
  void givenWrittenConstructorDelegation_whenItsEntireStatementAddressSetIsShifted_thenNoInsertedStatementCanJustifyTheChange() throws IOException {
    // Arrange
    var original = produce("class Example { Example() { super(); int local = 1; } }");
    var values = SourceValueCapture.capture(original.aggregate());
    var body = values.bodies().getFirst();
    var root = body.attributedOccurrences().stream().filter(address -> address.nodeKey().equals(body.root().nodeId())).findFirst().orElseThrow().compilerPath();
    var shifted = body.attributedOccurrences().stream().map(address -> {
      if (address.compilerPath().steps().size() == root.steps().size()) return address;
      var steps = new ArrayList<>(address.compilerPath().steps());
      var first = steps.get(root.steps().size());
      steps.set(root.steps().size(), new SyntaxStructuralPath.Step(first.role(), first.position() + 1));
      return new AttributedBodyOccurrence(address.scopeKey(), address.nodeKey(), address.sourceFileKey(), address.sourceKind(), new SyntaxStructuralPath(steps));
    }).toList();
    var changed = new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), shifted);
    var supplied = new CopiedAstValues(values.snapshot(), values.sources(), List.of(changed), values.memberOrders(), values.associations(), values.declarationTypes(), values.scopes());

    // Act
    var failure = assertThrows(AstContractException.class, () -> qualify(original, supplied));

    // Assert
    assertTrue(failure.getMessage().contains("constructor correspondence"), failure.getMessage());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @TestFactory
  Stream<DynamicTest> givenIncorrectSameScopeAttribution_whenPublicValuesAreQualified_thenTheWrongRelativeOccurrenceIsRejected() {
    return Stream.of(
        new AddressCase("field", "class Example { int[] first = {1, 2}; }", field("first"), field("first")),
        new AddressCase("static block", "class Example { static { int first = 1; int second = 2; } }", initializer(0), initializer(0)),
        new AddressCase("instance block", "class Example { { int first = 1; int second = 2; } }", initializer(0), initializer(0)),
        new AddressCase("method", "class Example { int subject() { return 1; } }", method("subject"), method("subject")))
        .map(test -> DynamicTest.dynamicTest("given" + test.name().replace(" ", "")
            + "_whenPublicValuesAreQualified_thenWrongRelativeCompilerPathIsRejected", () -> {
          // Arrange
          var original = produce(test.source());
          var scope = original.aggregate().executionScopes().stream().map(value -> original.aggregate().executionSyntax(value.key()).orElseThrow())
              .filter(test.first()).findFirst().orElseThrow();
          var address = literal(scope);
          var steps = new ArrayList<>(address.compilerPath().steps());
          var last = steps.getLast();
          steps.set(steps.size() - 1, new SyntaxStructuralPath.Step(last.role(), last.position() + 2));
          var supplied = replace(original, address, new SyntaxStructuralPath(steps));

          // Act
          var failure = assertThrows(AstContractException.class, () -> qualify(original, supplied));

          // Assert
          assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("attribut"), failure.getMessage());
          assertEquals(AstCompletion.COMPLETE, original.completion());
        }));
  }

  @Test
  void givenWrittenConstructorWithInsertedCallAndDeclarationScopes_whenReAdmittedAndDecoded_thenActualCompilerAddressesSurviveRebasing() throws IOException {
    // Arrange
    var original = produce("class Example { int field = 1; static { int value = 2; } { int value = 3; } Example() { int local = 4; } int subject() { return 5; } }");
    var copied = SourceValueCapture.capture(original.aggregate());
    var constructor = original.program().methods().stream().filter(value -> value.constructor()).findFirst().orElseThrow();
    var body = original.body(constructor.methodKey()).orElseThrow();
    var statement = body.root().requiredChild(SyntaxRole.STATEMENT);
    var address = body.attributedOccurrences().stream().filter(value -> value.nodeKey().equals(statement.nodeId())).findFirst().orElseThrow();

    // Act
    var admitted = qualify(original, copied);
    var retained = AstCodec.decode(AstCodec.encode(admitted), admitted.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion());
    assertNotEquals(statement.structuralPath(), address.compilerPath());
    assertEquals(1, address.compilerPath().steps().getLast().position());
    assertEquals(0, statement.structuralPath().steps().getLast().position());
    assertEquals(body.attributedOccurrences(), retained.body(constructor.methodKey()).orElseThrow().attributedOccurrences());
    assertEquals(original.aggregate(), retained.aggregate());
  }

  @TestFactory
  Stream<DynamicTest> givenCompleteExecutionAttributionSets_whenMovedToNonexistentTypeOrMemberRoots_thenEveryPublicAdmissionBoundaryRejectsThem() {
    return Stream.of(
        new AddressCase("Field", "class Example { int first=1; }", field("first"), field("first")),
        new AddressCase("StaticBlock", "class Example { static { int first=1; } }", initializer(0), initializer(0)),
        new AddressCase("InstanceBlock", "class Example { { int first=1; } }", initializer(0), initializer(0)),
        new AddressCase("Method", "class Example { int subject() { return 1; } }", method("subject"), method("subject")),
        new AddressCase("CompactField", "int first=1; void main() {}", field("first"), field("first")),
        new AddressCase("NestedField", "class Outer { class Example { int first=1; } }", field("first"), field("first")),
        new AddressCase("NestedMethod", "class Outer { class Example { int subject() { return 1; } } }", method("subject"), method("subject")))
        .flatMap(test -> Stream.of(true, false).map(foreignType -> DynamicTest.dynamicTest("given" + test.name()
            + "_whenAllAddressesMoveTo" + (foreignType ? "ForeignType" : "UnusedMember") + "_thenAdmissionQualificationAndDecodeReject", () -> {
          // Arrange
          var original = produce(test.source());
          var scope = original.aggregate().executionScopes().stream().map(value -> original.aggregate().executionSyntax(value.key()).orElseThrow())
              .filter(test.first()).findFirst().orElseThrow();
          var root = scope.attributedOccurrences().stream().filter(address -> address.nodeKey().equals(scope.root().nodeId())).findFirst().orElseThrow().compilerPath();
          var steps = new ArrayList<>(root.steps());
          var changedPosition = foreignType ? 1 : steps.size() - (scope.scope() instanceof ExecutionScope.Method ? 2 : 1);
          var previous = steps.get(changedPosition);
          steps.set(changedPosition, new SyntaxStructuralPath.Step(previous.role(), 99));
          var replacementRoot = new SyntaxStructuralPath(steps);
          var supplied = SourceValueCapture.capture(original.aggregate());
          var malformed = AstCodec.encode(original);
          for (var address : scope.attributedOccurrences()) {
            var replacement = new AttributedBodyOccurrence(address.scopeKey(), address.nodeKey(), address.sourceFileKey(), address.sourceKind(),
                rebase(address.compilerPath(), root, replacementRoot));
            supplied = replace(supplied, address, replacement.compilerPath());
            malformed = replaceFrames(malformed, address, replacement, true);
          }
          var changedValues = supplied;
          var changedBytes = malformed;

          // Act
          var intrinsic = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(changedValues));
          var qualification = assertThrows(AstContractException.class, () -> qualify(original, changedValues));
          var decoded = assertThrows(AstContractException.class, () -> AstCodec.decode(changedBytes, original.identity()));
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

          // Assert
          for (var failure : List.of(intrinsic, qualification, decoded)) {
            assertTrue(failure.getMessage().contains("independently copied declaration owner and member order"), failure.getMessage());
          }
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertEquals(original.aggregate(), retained.aggregate());
        })));
  }

  @Test
  void givenDeclarationAttributionsWithIndependentTreeOrder_whenTheirOrderIsOmitted_thenIntrinsicAdmissionCannotTrustTheSuppliedRoot() throws IOException {
    // Arrange
    var original = produce("class Example { int first=1; static {} {} }");
    var values = SourceValueCapture.capture(original.aggregate());
    var orders = values.memberOrders().stream().map(order -> new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), order.members())).toList();
    var supplied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), orders, values.associations(), values.declarationTypes(), values.scopes());

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("independently copied declaration owner and member order"), failure.getMessage());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  private AstResult produce(String source) throws IOException {
    return produce(source, AstProfile.JAVA_SOURCE_INITIALIZATION);
  }

  private AstResult produce(String source, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Example.java"), source)));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25),
        profile).result().orElseThrow();
  }

  private static CopiedAstValues.Body body(CopiedAstValues values, String name) {
    return values.bodies().stream().filter(body -> body.methodKey().contains(name)).findFirst().orElseThrow();
  }

  private static CopiedAstValues replaceBody(CopiedAstValues values, CopiedAstValues.Body replacement) {
    var bodies = values.bodies().stream().map(body -> body.methodKey().equals(replacement.methodKey()) ? replacement : body).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes());
  }

  private static SyntaxNode detachedCopy(SyntaxNode node) {
    var children = node.children().stream()
        .map(child -> new SyntaxChild(child.role(), child.position(), detachedCopy(child.node()))).toList();
    return node.origin().map(origin -> SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(),
        node.type(), node.value(), children, node.semanticReference(), origin)).orElseGet(() -> SyntaxNode.withoutDirectSource(
            node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(), children,
            node.semanticReference()));
  }

  @TestFactory
  Stream<DynamicTest> givenIndependentDeclarationTreeOrder_whenItsOwnerOrWrittenMembershipIsContradictory_thenAdmissionAndDecodeRejectIt() {
    return Stream.of(true, false).map(foreignOwner -> DynamicTest.dynamicTest("givenIndependentDeclarationOrder_when"
        + (foreignOwner ? "OwnerMovesToForeignType" : "WrittenMemberIsOmitted") + "_thenAdmissionAndDecodeReject", () -> {
      // Arrange
      var original = produce("class Example { int first=1; int second=2; }");
      var values = SourceValueCapture.capture(original.aggregate());
      var order = values.memberOrders().getFirst();
      var tree = order.attributedTreeOrder().orElseThrow();
      var owner = foreignOwner ? SourceOwnerPath.root().child(SourceOwnerPath.Role.TYPE_DECLARATION, 99) : tree.ownerPath();
      var members = foreignOwner
          ? tree.members().stream().map(member -> member.map(path -> owner.child(SourceOwnerPath.Role.MEMBER, path.lastStep().orElseThrow().ordinal()))).toList()
          : tree.members().stream().filter(member -> member.isEmpty() || member.orElseThrow().lastStep().orElseThrow().ordinal() != 0).toList();
      var changed = new DeclarationMemberOrder(order.sourceFileKey(), order.ownerTypeKey(), order.members(), Optional.of(new DeclarationMemberOrder.TreeOrder(owner, members)));
      var supplied = new CopiedAstValues(values.snapshot(), values.sources(), values.bodies(), List.of(changed), values.associations(), values.declarationTypes(), values.scopes());
      var malformed = replaceFrames(AstCodec.encode(original), order, changed, false);

      // Act
      var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
      var decoding = assertThrows(AstContractException.class, () -> AstCodec.decode(malformed, original.identity()));

      // Assert
      assertTrue(failure.getMessage().contains("Attributed tree order"), failure.getMessage());
      assertTrue(decoding.getMessage().contains("Attributed tree order"), decoding.getMessage());
      assertEquals(AstCompletion.COMPLETE, original.completion());
    }));
  }

  private static String declarationLayout(String source) {
    if (source.startsWith("int")) return "CompactClass";
    return source.contains("Example()") ? "WrittenConstructor" : "GeneratedConstructor";
  }

  private Map<Long, SyntaxStructuralPath> compilerDeclarationRoots(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var roots = new HashMap<Long, SyntaxStructuralPath>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null,
          List.of(SimpleJavaFileObject.forSource(directory.resolve("Example.java").toUri(), source)));
      var unit = task.parse().iterator().next();
      task.analyze();
      var positions = Trees.instance(task).getSourcePositions();
      var type = (ClassTree) unit.getTypeDecls().getFirst();
      for (var index = 0; index < type.getMembers().size(); index++) {
        var member = type.getMembers().get(index);
        if (member instanceof VariableTree || member instanceof BlockTree) {
          roots.put(positions.getStartPosition(unit, member), SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0).child(SyntaxRole.MEMBER, index));
        }
      }
    }
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    return Map.copyOf(roots);
  }

  private static Predicate<ExecutionSyntax> field(String name) {
    return value -> value.scope() instanceof ExecutionScope.Field && value.scope().key().endsWith("#field:" + name);
  }

  private static Predicate<ExecutionSyntax> method(String name) {
    return value -> value.scope() instanceof ExecutionScope.Method && value.scope().key().contains("#" + name + "(");
  }

  private static Predicate<ExecutionSyntax> initializer(int position) {
    return value -> value.scope() instanceof ExecutionScope.Initializer && value.root().structuralPath().steps().getLast().position() == position;
  }

  private static AttributedBodyOccurrence literal(ExecutionSyntax scope) {
    return scope.attributedOccurrences().stream().filter(value -> value.sourceKind() == JavaSourceKind.INT_LITERAL).findFirst().orElseThrow();
  }

  private static CopiedAstValues replace(AstResult original, AttributedBodyOccurrence changed, SyntaxStructuralPath path) {
    return replace(SourceValueCapture.capture(original.aggregate()), changed, path);
  }

  private static CopiedAstValues replace(CopiedAstValues values, AttributedBodyOccurrence changed, SyntaxStructuralPath path) {
    var bodies = values.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), addresses(body.attributedOccurrences(), changed, path))).toList();
    var scopes = values.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), scope.root(), scope.uses(), scope.invocations(),
        scope.staticAccesses(), scope.concatenations(), addresses(scope.attributedOccurrences(), changed, path))).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(), values.declarationTypes(), scopes);
  }

  private static List<AttributedBodyOccurrence> addresses(List<AttributedBodyOccurrence> addresses, AttributedBodyOccurrence changed, SyntaxStructuralPath path) {
    return addresses.stream().map(address -> address.equals(changed)
        ? new AttributedBodyOccurrence(address.scopeKey(), address.nodeKey(), address.sourceFileKey(), address.sourceKind(), path) : address).toList();
  }

  private static AstResult qualify(AstResult original, CopiedAstValues values) {
    var aggregate = AstValueAdmission.admit(values);
    return AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(), Optional.of(aggregate),
        original.diagnostics(), original.coverage()), original.profile(), original.identity().semanticProductionRevision());
  }

  private static SyntaxStructuralPath rebase(SyntaxStructuralPath path, SyntaxStructuralPath from, SyntaxStructuralPath to) {
    var result = to;
    for (var step : path.steps().subList(from.steps().size(), path.steps().size())) result = result.child(step.role(), step.position());
    return result;
  }

  private static byte[] replaceFrames(byte[] payload, Object original, Object replacement, boolean unique) {
    var originalWriter = new CanonicalValueWriter("");
    var replacementWriter = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false);
    codec.write(originalWriter, original);
    codec.write(replacementWriter, replacement);
    var originalFrame = Arrays.copyOfRange(originalWriter.toByteArray(), Integer.BYTES, originalWriter.toByteArray().length);
    var replacementFrame = Arrays.copyOfRange(replacementWriter.toByteArray(), Integer.BYTES, replacementWriter.toByteArray().length);
    var matches = new ArrayList<Integer>();
    for (var index = 0; index <= payload.length - originalFrame.length; index++) {
      if (Arrays.equals(payload, index, index + originalFrame.length, originalFrame, 0, originalFrame.length)) {
        matches.add(index);
      }
    }
    assertTrue(!matches.isEmpty(), "Retained frame is absent");
    if (unique) assertEquals(1, matches.size(), "Attribution frame must be unique");
    var output = new ByteArrayOutputStream();
    var offset = 0;
    for (var match : matches) {
      output.write(payload, offset, match - offset);
      output.writeBytes(replacementFrame);
      offset = match + originalFrame.length;
    }
    output.write(payload, offset, payload.length - offset);
    return output.toByteArray();
  }

  private record AddressCase(String name, String source, Predicate<ExecutionSyntax> first, Predicate<ExecutionSyntax> second) {}
}
