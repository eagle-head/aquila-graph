package org.aquilagraph.source.internal;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.aquilagraph.source.internal.RetainedSourceFixture.payloadWithAggregate;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CopiedAstValues;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaCoverageTestSupport;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstContractException;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget.SourceOccurrence;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.retention.AstCodec;
import org.aquilagraph.source.admission.AstProductionAdmission;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class LocalBindingAdmissionTest {
  @TempDir Path directory;

  @Test
  void givenAnUnsupportedIdentifier_whenItCarriesAForeignLocalSelection_thenDirectAndRetainedAdmissionRefuse() throws IOException {
    // Arrange
    var original = produce(List.of(
        SourceFile.fromContent(capturedPath(Path.of("First.java")), "class First { int subject() { int value=1; return value; } }"),
        SourceFile.fromContent(capturedPath(Path.of("Other.java")), "class Other { int subject() { int value=2; return value; } }")));
    var declarations = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var own = declarations.getFirst();
    var foreign = declarations.getLast();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream()
        .filter(node -> node.sourceFileKey().equals(own.sourceFileKey())).findFirst().orElseThrow();
    var containingReturn = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN)
        .filter(node -> node.requiredChild(SyntaxRole.EXPRESSION).nodeId().equals(access.nodeId())).findFirst().orElseThrow();
    var values = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(unsupported(node), key(foreign)) : node,
        List.of(occurrenceReference(access, foreign)));
    var supplied = withoutChildUse(values, containingReturn.nodeId());

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var refusal = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(Optional.of(key(own)), retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(refusal.getMessage().contains("unsupported occurrence"), refusal.toString());
    assertTrue(decoding.getMessage().contains("unsupported occurrence"), decoding.toString());
  }

  @Test
  void givenAWrittenFormalAndAResolvedRead_whenTheirKnownTypesConflict_thenDirectAndRetainedAdmissionRefuse() throws IOException {
    // Arrange
    var original = produce("class Example { int read(int value) { return value; } }");
    var method = original.program().methods().stream().filter(candidate -> candidate.name().equals("read")).findFirst().orElseThrow();
    var access = selectedReturnedLocal(original, method.methodKey());
    var declaration = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION)
        .findFirst().orElseThrow();
    var statement = original.body(method.methodKey()).orElseThrow().root().requiredChild(SyntaxRole.STATEMENT);
    var copiedType = new SyntaxType.Resolved(new JavaTypeIdentity(JavaTypeCategory.CHAR, "char", "char"));
    var reference = access.requiredReference();
    var wrongReference = new SemanticReference(reference.referenceKind(), reference.status(), reference.symbolKey(),
        Optional.of("char"), List.of("char"), List.of("char"), Optional.empty());
    var values = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), copiedType, node.value(),
            node.children(), Optional.of(wrongReference), node.origin().orElseThrow())
        : node, List.of(occurrenceReference(access, declaration)));
    var supplied = withoutChildUse(values, statement.nodeId());

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var refusal = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(access.type(), retained.aggregate().node(access.nodeId()).orElseThrow().type());
    assertEquals(Optional.of(method.parameters().getFirst().parameterKey()), access.requiredReference().symbolKey());
    assertTrue(refusal.getMessage().contains("binding access type"), refusal.toString());
    assertTrue(decoding.getMessage().contains("binding access type"), decoding.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenAMultiCatchBinding_whenItsCopiedReadClaimsAnIncompatibleType_thenDirectAndRetainedAdmissionRefuse() {
    // Arrange
    var types = List.of(new JavaTypeIdentity(JavaTypeCategory.CHAR, "char", "char"),
        new JavaTypeIdentity(JavaTypeCategory.ARRAY, "char[]", "char[]"),
        new JavaTypeIdentity(JavaTypeCategory.DECLARED, "java.lang.String", "java.lang.String"),
        new JavaTypeIdentity(JavaTypeCategory.NULL, "null", "null"));

    // Act
    var tests = types.stream().map(type -> DynamicTest.dynamicTest("givenAMultiCatchBinding_whenTheReadClaims" + type.category()
        + "_thenDirectAndRetainedAdmissionRefuse", () -> {
          // Arrange
          var original = multiCatchResult();
          var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
          var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
          var supplied = withBindingAccessType(original, access, new SyntaxType.Resolved(type), ResolutionStatus.RESOLVED);

          // Act
          var admitted = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
          var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
          var refusal = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
          var decoding = assertThrows(AstContractException.class,
              () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
          assertEquals(JavaTypeCategory.UNION, declaration.type().knownIdentity().orElseThrow().category());
          assertEquals("java.lang.RuntimeException", declaration.type().knownIdentity().orElseThrow().erasedName());
          assertEquals("java.lang.RuntimeException", access.type().knownIdentity().orElseThrow().erasedName());
          assertEquals(access.type(), admitted.node(access.nodeId()).orElseThrow().type());
          assertEquals(access.requiredReference(), retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference());
          assertTrue(refusal.getMessage().contains("binding access type"), refusal.toString());
          assertTrue(decoding.getMessage().contains("binding access type"), decoding.toString());
        }));

    // Assert
    return tests;
  }

  @TestFactory
  Stream<DynamicTest> givenAMultiCatchBindingWithMissingOrErroneousReadType_whenAdmittedAndRetained_thenTheEvidenceStateSurvives() {
    // Arrange
    var types = List.of(SyntaxType.Unresolved.INSTANCE, SyntaxType.Erroneous.INSTANCE);

    // Act
    var tests = types.stream().map(type -> DynamicTest.dynamicTest("givenAMultiCatchReadWith" + type.getClass().getSimpleName()
        + "Type_whenAdmittedAndRetained_thenTheEvidenceStateSurvives", () -> {
          // Arrange
          var original = multiCatchResult();
          var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
          var status = type == SyntaxType.Erroneous.INSTANCE ? ResolutionStatus.ERROR : ResolutionStatus.RESOLVED;
          var supplied = withBindingAccessType(original, access, type, status);
          var input = SourceValueCapture.capture(original);

          // Act
          var aggregate = AstValueAdmission.admit(supplied);
          var reconciled = JavaCoverageTestSupport.capture(input, original.profile()).withFacts(original.program(), Optional.of(aggregate));
          var partial = AstProductionAdmission.outcome(reconciled, original.profile(), original.identity().semanticProductionRevision())
              .result().orElseThrow();
          var retained = AstCodec.decode(AstCodec.encode(partial), partial.identity());
          var actual = retained.aggregate().node(access.nodeId()).orElseThrow();

          // Assert
          assertEquals(AstCompletion.COMPLETE, original.completion());
          assertEquals(AstCompletion.INCOMPLETE, retained.completion());
          assertEquals(type, actual.type());
          assertEquals(status, actual.requiredReference().status());
          assertEquals(access.requiredReference().symbolKey(), actual.requiredReference().symbolKey());
        }));

    // Assert
    return tests;
  }

  private AstResult multiCatchResult() throws IOException {
    return produce("""
        class Example { Object read() {
          try { throw new IllegalArgumentException(); }
          catch (IllegalArgumentException | IllegalStateException value) { return value; }
        } }
        """);
  }

  private static CopiedAstValues withBindingAccessType(AstResult original, SyntaxNode access, SyntaxType type, ResolutionStatus status) {
    var statement = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN)
        .filter(node -> node.requiredChild(SyntaxRole.EXPRESSION).nodeId().equals(access.nodeId())).findFirst().orElseThrow();
    var name = type.knownIdentity().map(JavaTypeIdentity::displayName);
    var reference = new SemanticReference(access.requiredReference().referenceKind(), status, access.requiredReference().symbolKey(),
        name, name.stream().toList(), name.stream().toList(), Optional.empty());
    return withoutChildUse(copy(original, node -> node.nodeId().equals(access.nodeId())
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), type, node.value(),
            node.children(), Optional.of(reference), node.origin().orElseThrow()) : node), statement.nodeId());
  }

  @TestFactory
  Stream<DynamicTest> givenAnUnsupportedIdentifierWithoutASelection_whenASeparateEdgeClaimsABinding_thenAdmissionRefuses() {
    // Arrange
    var variants = List.of(true, false);

    // Act
    var tests = variants.stream().map(occurrence -> DynamicTest.dynamicTest(occurrence
        ? "givenAnUnselectedUnsupportedIdentifier_whenAnOccurrenceEdgeClaimsALocal_thenAdmissionRefuses"
        : "givenAnUnselectedUnsupportedIdentifier_whenADeclarationEdgeClaimsALocal_thenAdmissionRefuses", () -> {
          // Arrange
          var original = produce("class Example { int read() { int value=1; return value; } }");
          var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
          var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
          var statement = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN)
              .findFirst().orElseThrow();
          var edge = occurrence ? occurrenceReference(access, declaration) : access.semanticEdges().getFirst();
          UnaryOperator<SyntaxNode> missing = node -> node.nodeId().equals(access.nodeId())
              ? rebuild(unsupported(node), unsupported(node).value(), node.children(), Optional.empty()) : node;
          var positive = withoutChildUse(copy(original, missing), statement.nodeId());
          var supplied = withoutChildUse(copy(original, missing, List.of(edge)), statement.nodeId());

          // Act
          var admitted = AstValueAdmission.admit(positive);
          var refusal = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
          var decoding = assertThrows(AstContractException.class,
              () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

          // Assert
          assertEquals(Optional.empty(), admitted.node(access.nodeId()).orElseThrow().semanticReference());
          assertTrue(refusal.getMessage().contains("binding"), refusal.toString());
          assertTrue(decoding.getMessage().contains("binding"), decoding.toString());
        }));

    // Assert
    return tests;
  }

  @Test
  void givenACloserPatternWithoutFlowEvidence_whenAnotherUseSelectsTheOuterFormal_thenPartialSourceKeepsItsBinding()
      throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject(String value, Object input) {
            class Local {
              int read() {
                if (input instanceof String value) return value.length();
                return value.length();
              }
            }
            return new Local().read();
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var accesses = named(original, "value", SyntaxKind.VARIABLE_ACCESS);
    var inner = accesses.stream().filter(node -> node.requiredReference().referenceKind() == ReferenceKind.LOCAL).findFirst().orElseThrow();
    var outer = accesses.stream().filter(node -> node.requiredReference().referenceKind() == ReferenceKind.PARAMETER).findFirst().orElseThrow();
    var values = copy(original, node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN
        ? replace(node, new SyntaxValue.PatternBinding(Optional.empty())) : node);

    // Act
    var admitted = AstValueAdmission.admit(values);
    var input = SourceValueCapture.capture(original);
    var reconciled = JavaCoverageTestSupport.capture(input, original.profile()).withFacts(original.program(), Optional.of(admitted));
    var incomplete = AstProductionAdmission.outcome(reconciled, original.profile(), original.identity().semanticProductionRevision())
        .result().orElseThrow();
    var retained = AstCodec.decode(AstCodec.encode(incomplete), incomplete.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    assertEquals(inner.requiredReference(), retained.aggregate().node(inner.nodeId()).orElseThrow().requiredReference());
    assertEquals(outer.requiredReference(), retained.aggregate().node(outer.nodeId()).orElseThrow().requiredReference());
  }

  @TestFactory
  Stream<DynamicTest> givenAPatternInALaterStatement_whenAnEarlierAccessClaimsTheBinding_thenDirectAndRetainedAdmissionRefuse() {
    // Arrange
    var cases = List.of(new SiblingPatternCase("givenUnavailablePatternSpans_whenAnEarlierStatementClaimsTheBinding_thenAdmissionRefuses", false),
        new SiblingPatternCase("givenAnEarlierCopiedPatternSpan_whenItsStatementClaimsTheBinding_thenAdmissionRefuses", true));

    // Act
    var tests = cases.stream().map(variant -> DynamicTest.dynamicTest(variant.name(), () -> assertEarlierPatternRejected(variant)));

    // Assert
    return tests;
  }

  private void assertEarlierPatternRejected(SiblingPatternCase variant) throws IOException {
    // Arrange
    var original = produce("""
        class Example { String value; void subject(Object input) {
          value.isBlank();
          if (input instanceof String value) { value.isBlank(); }
        } }
        """);
    var pattern = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN).findFirst().orElseThrow();
    var declaration = pattern.requiredChild(SyntaxRole.PARAMETER);
    var access = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_ACCESS).findFirst().orElseThrow();
    var spans = new ArrayList<>(pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow());
    spans.addFirst(access.origin().orElseThrow());
    var supplied = copy(original, node -> {
      if (node.nodeId().equals(pattern.nodeId())) {
        return replace(node, new SyntaxValue.PatternBinding(variant.withExactSpan() ? Optional.of(spans) : Optional.empty()));
      }
      return node.nodeId().equals(access.nodeId()) ? asLocalAccess(node, declaration, "value") : node;
    }, List.of(occurrenceReference(access, declaration)));

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var refusal = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(ReferenceKind.FIELD, retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference().referenceKind());
    var later = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    assertEquals(Optional.of(key(declaration)), retained.aggregate().node(later.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertPatternScopeRefusal(refusal);
    assertPatternScopeRefusal(decoding);
  }

  @Test
  void givenEnumSelectorOwnsABareCaseConstant_whenCopiedLabelClaimsASameNamedLocal_thenDirectAndRetainedAdmissionRefuse()
      throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        enum Choice { ONE, TWO }
        class Example {
          int subject(Choice choice) {
            Choice ONE = Choice.TWO;
            switch (choice) {
              case ONE: return 1;
              default: return 0;
            }
          }
        }
        """)), AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var local = named(original, "ONE", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var constant = writtenMember(original, "ONE", SyntaxKind.ENUM_CONSTANT_DECLARATION).value(SyntaxValue.VariableDeclaration.class);
    var label = switchLabel(original, SyntaxKind.FIELD_ACCESS);
    var supplied = withoutStaticAccess(copy(original, node -> node.nodeId().equals(label.nodeId()) ? asLocalAccess(node, local, "ONE") : node,
        List.of(occurrenceReference(label, local))), label.nodeId());

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(local.type(), label.type());
    assertEquals(ReferenceKind.ENUM_CONSTANT, label.requiredReference().referenceKind());
    assertEquals(Optional.of(constant.declarationKey()), positive.node(label.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(label.requiredReference(), retained.aggregate().node(label.nodeId()).orElseThrow().requiredReference());
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertTrue(failure.getMessage().contains("enum constant"), failure.toString());
    assertTrue(decoding.getMessage().contains("enum constant"), decoding.toString());
  }

  @Test
  void givenAnOuterProjectionGap_whenNestedAccessClaimsAnUnrepresentedLocal_thenDirectAndRetainedAdmissionRefuse()
      throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject() {
            outer: {
              class Local {
                int read() {
                  int value;
                  value = 2;
                  return value;
                }
              }
              return new Local().read();
            }
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var method = original.program().methods().stream().filter(candidate -> candidate.name().equals("read")).findFirst().orElseThrow();
    var wrapper = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LABELED_STATEMENT)
        .findFirst().orElseThrow();
    var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var reference = selectedReturnedLocal(original, method.methodKey());
    var origin = declaration.origin().orElseThrow();
    var absentKey = method.methodKey() + ":local_variable:" + (origin.startOffset() + 1) + ':' + origin.endOffsetExclusive();
    UnaryOperator<SyntaxNode> withOuterGap = node -> node.nodeId().equals(wrapper.nodeId()) ? unsupported(node) : node;
    var supplied = copy(original, node -> node.nodeId().equals(reference.nodeId()) ? select(node, absentKey) : withOuterGap.apply(node));

    // Act
    var positive = AstValueAdmission.admit(copy(original, withOuterGap));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(SyntaxKind.UNSUPPORTED, positive.node(wrapper.nodeId()).orElseThrow().syntaxKind());
    assertEquals(Optional.of(key(declaration)), positive.node(reference.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(reference.requiredReference(), retained.aggregate().node(reference.nodeId()).orElseThrow().requiredReference());
    assertTrue(failure.getMessage().contains("owned named binding"), failure.toString());
    assertTrue(decoding.getMessage().contains("owned named binding"), decoding.toString());
  }

  @Test
  void givenAProjectionGapRetainsAnExactLocalKey_whenAccessClaimsAnotherKeyInTheSameExecution_thenDirectAndRetainedAdmissionRefuse()
      throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject() {
            class Local {
              int read() {
                inner: {
                  int value;
                  value = 2;
                  return value;
                }
              }
            }
            return new Local().read();
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var method = original.program().methods().stream().filter(candidate -> candidate.name().equals("read")).findFirst().orElseThrow();
    var wrapper = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LABELED_STATEMENT)
        .findFirst().orElseThrow();
    var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var access = selectedReturnedLocal(original, method.methodKey());
    var origin = declaration.origin().orElseThrow();
    var absentKey = method.methodKey() + ":local_variable:" + (origin.startOffset() + 1) + ':' + origin.endOffsetExclusive();
    UnaryOperator<SyntaxNode> withGap = node -> node.nodeId().equals(wrapper.nodeId()) || node.nodeId().equals(declaration.nodeId())
        ? unsupported(node) : node;
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, absentKey) : withGap.apply(node));

    // Act
    var admitted = AstValueAdmission.admit(copy(original, withGap));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(SyntaxKind.UNSUPPORTED, admitted.node(wrapper.nodeId()).orElseThrow().syntaxKind());
    var withheld = admitted.node(declaration.nodeId()).orElseThrow();
    assertEquals(SyntaxKind.UNSUPPORTED, withheld.syntaxKind());
    assertEquals(Optional.of(key(declaration)), withheld.requiredReference().symbolKey());
    assertEquals(Optional.of(key(declaration)), admitted.node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("owned named binding"), failure.toString());
    assertTrue(decoding.getMessage().contains("owned named binding"), decoding.toString());
  }

  @Test
  void givenColonSwitchBindingSelectedByALaterLabel_whenCompilerReportsDefiniteAssignment_thenSourceAndRetentionKeepTheSelection()
      throws IOException {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example { int choose(int input) {
          switch (input) {
            case 0: final int one = 1; break;
            case one: return 1;
            default: break;
          }
          return 0;
        } }
        """);

    // Act
    var outcome = produceOutcome(List.of(source), AstProfile.JAVA_SOURCE_EXPANDED);
    var original = outcome.result().orElseThrow(() -> new AssertionError("The lexical source result was withheld: " + outcome.diagnostics()));
    var copied = AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate()));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(AstCompletion.INCOMPLETE, retained.completion());
    var binding = named(original, "one", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var label = switchLabel(original, SyntaxKind.VARIABLE_ACCESS);
    assertEquals(ReferenceKind.LOCAL, label.requiredReference().referenceKind());
    assertEquals(Optional.of(key(binding)), label.requiredReference().symbolKey());
    assertEquals(label.requiredReference(), copied.node(label.nodeId()).orElseThrow().requiredReference());
    assertEquals(label.requiredReference(), retained.aggregate().node(label.nodeId()).orElseThrow().requiredReference());
    assertTrue(outcome.diagnostics().stream().anyMatch(diagnostic ->
        diagnostic.code().equals("compiler.err.var.might.not.have.been.initialized")
            && diagnostic.origins().contains(label.origin().orElseThrow())));
    assertTrue(outcome.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unavailable-source-structure")));
    assertEquals(original.diagnostics(), retained.diagnostics());
  }

  @Test
  void givenColonSwitchLabelSelectsAFieldBeforeTheGroupLocal_whenCopiedLabelClaimsTheLocal_thenAdmissionRejectsTheSelection()
      throws IOException {
    // Arrange
    var source = SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          static final int one = 0;
          int choose(int input) {
            switch (input) {
              case one: final int one = 1; return one;
              default: return 0;
            }
          }
        }
        """);
    var original = produce(List.of(source), AstProfile.JAVA_SOURCE_INITIALIZATION);
    var local = named(original, "one", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var field = writtenMember(original, "one", SyntaxKind.FIELD_DECLARATION).value(SyntaxValue.VariableDeclaration.class);
    var label = switchLabel(original, SyntaxKind.FIELD_ACCESS);
    var supplied = copy(original, node -> node.nodeId().equals(label.nodeId()) ? asLocalAccess(node, local, "one") : node,
        List.of(occurrenceReference(label, local)));

    // Act
    var admitted = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(Optional.of(field.declarationKey()), admitted.node(label.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertTrue(failure.getMessage().contains("lexical scope") || failure.getMessage().contains("precede its declaration"), failure.toString());
  }

  @Test
  void givenAnnotationRefersToAnOwnSourceConstant_whenCopiedSelectionClaimsTheForeignConstant_thenDirectAndRetainedAdmissionRefuse()
      throws IOException {
    // Arrange
    var declaration = """
        class %s {
          int subject() {
            final int value = 7;
            @Marker(value) int marked = 1;
            return marked;
          }
        }
        """;
    var original = produce(List.of(
        SourceFile.fromContent(capturedPath(Path.of("Marker.java")), """
            import java.lang.annotation.ElementType;
            import java.lang.annotation.Target;
            @Target(ElementType.LOCAL_VARIABLE) @interface Marker { int value(); }
            """),
        SourceFile.fromContent(capturedPath(Path.of("First.java")), declaration.formatted("First")),
        SourceFile.fromContent(capturedPath(Path.of("Other.java")), declaration.formatted("Other"))));
    var bindings = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var own = bindings.getFirst();
    var foreign = bindings.getLast();
    var name = named(original, "value", SyntaxKind.ANNOTATION_NAME_REFERENCE).stream()
        .filter(node -> node.sourceFileKey().equals(own.sourceFileKey())).findFirst().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(name.nodeId()) ? select(node, key(foreign)) : node);

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(own.structuralPath(), foreign.structuralPath());
    assertEquals(own.type(), foreign.type());
    assertEquals(own.requiredChild(SyntaxRole.INITIALIZER).value(), foreign.requiredChild(SyntaxRole.INITIALIZER).value());
    assertEquals(ReferenceKind.LOCAL, name.requiredReference().referenceKind());
    assertEquals(Optional.of(key(own)), positive.node(name.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(name.requiredReference(), retained.aggregate().node(name.nodeId()).orElseThrow().requiredReference());
    assertTrue(failure.getMessage().contains("binding's captured source"), failure.toString());
    assertTrue(decoding.getMessage().contains("binding's captured source"), decoding.toString());
  }

  @Test
  void givenMatchingLocalBindingsInTwoSources_whenCopiedAccessSelectsTheForeignBinding_thenAdmissionRejectsItsSource()
      throws IOException {
    // Arrange
    var original = produce(List.of(
        SourceFile.fromContent(capturedPath(Path.of("First.java")), "class First { int subject() { int value=1; return value; } }"),
        SourceFile.fromContent(capturedPath(Path.of("Other.java")), "class Other { int subject() { int value=2; return value; } }")));
    var bindings = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var local = bindings.getFirst();
    var foreign = bindings.getLast();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream()
        .filter(node -> node.sourceFileKey().equals(local.sourceFileKey())).findFirst().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(foreign)) : node);

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(local.structuralPath(), foreign.structuralPath());
    assertEquals(local.type(), foreign.type());
    var admitted = positive.nodes().stream().filter(node -> node.nodeId().equals(access.nodeId())).findFirst().orElseThrow();
    assertEquals(Optional.of(key(local)), admitted.semanticReference().flatMap(SemanticReference::symbolKey));
    assertEquals(access.semanticEdges(), admitted.semanticEdges());
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertTrue(failure.getMessage().contains("binding's captured source"), failure.toString());
  }

  @Test
  void givenMatchingInitializerBindingsInTwoSources_whenCopiedAccessSelectsTheForeignBinding_thenAdmissionRejectsItsSource()
      throws IOException {
    // Arrange
    var original = produce(List.of(
        SourceFile.fromContent(capturedPath(Path.of("First.java")), "class First { { int value=1; int other=value; } }"),
        SourceFile.fromContent(capturedPath(Path.of("Other.java")), "class Other { { int value=2; int other=value; } }")),
        AstProfile.JAVA_SOURCE_INITIALIZATION);
    var bindings = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var local = bindings.getFirst();
    var foreign = bindings.getLast();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream()
        .filter(node -> node.sourceFileKey().equals(local.sourceFileKey())).findFirst().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(foreign)) : node);

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(local.structuralPath(), foreign.structuralPath());
    assertEquals(local.type(), foreign.type());
    assertEquals(original.aggregate().executionScopes(), positive.executionScopes());
    assertEquals(Optional.of(key(local)), positive.node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("binding's captured source"), failure.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenARealBindingAcrossAnOrderedRegionBoundary_whenCopiedReferenceIsRetargeted_thenScopeRejectsIt() {
    // Arrange
    var cases = List.of(
        new ScopeCase("givenBasicForInitializers_whenEarlierAccessClaimsTheLaterLocal_thenAdmissionRejects", """
            class Example { int value; void subject() {
              for (int first=value, value=2; first<value; first++) { }
            } }
            """, "value", "value", "basic-for initializer cannot refer to a later binding"),
        new ScopeCase("givenEnhancedForLoop_whenIterableAccessClaimsTheIterationLocal_thenAdmissionRejects", """
            class Example { int[] value; void subject() {
              for (int value : value) { int second=value; }
            } }
            """, "value", "value", "enhanced-for binding is visible only within its loop body"),
        new ScopeCase("givenSiblingCatchHandlers_whenAccessClaimsTheOtherBinding_thenAdmissionRejects", """
            class Example { void subject(boolean alternate) {
              try { if (alternate) throw new IllegalStateException(); throw new IllegalArgumentException(); }
              catch (IllegalStateException value) { value.getMessage(); }
              catch (IllegalArgumentException value) { value.getMessage(); }
            } }
            """, "value", "value", "binding's lexical scope"),
        new ScopeCase("givenSiblingLambdas_whenAccessClaimsTheOtherParameter_thenAdmissionRejects", """
            class Example { void subject() {
              java.util.function.IntUnaryOperator first = value -> value + 1;
              java.util.function.IntUnaryOperator other = value -> value + 2;
            } }
            """, "value", "value", "binding's lexical scope"),
        new ScopeCase("givenPatternWithAnElseBranch_whenElseAccessClaimsTheMatchedBinding_thenAdmissionRejects", """
            class Example { String value; void subject(Object input) {
              if (input instanceof String value && !value.isEmpty()) { value.trim(); } else { value.isEmpty(); }
            } }
            """, "value", "value", "compiler-established flow scope"));

    // Act
    var tests = cases.stream().map(scope -> DynamicTest.dynamicTest(scope.name(), () -> assertRetargetingRejected(scope)));

    // Assert
    return tests;
  }

  private void assertRetargetingRejected(ScopeCase scope) throws IOException {
    // Arrange
    var original = produce(scope.source());
    var binding = named(original, scope.bindingName(), SyntaxKind.LOCAL_DECLARATION).getLast();
    var access = original.aggregate().nodes().stream()
        .filter(node -> switch (node.value()) {
          case SyntaxValue.SourceSymbol symbol -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS && symbol.name().equals(scope.accessName());
          case SyntaxValue.FieldAccess field -> field.name().equals(scope.accessName());
          default -> false;
        })
        .filter(node -> node.semanticReference().flatMap(SemanticReference::symbolKey).filter(key(binding)::equals).isEmpty())
        .findFirst().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? asLocalAccess(node, binding, scope.bindingName()) : node);

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(access.semanticReference(), positive.node(access.nodeId()).orElseThrow().semanticReference());
    assertTrue(failure.getMessage().contains(scope.failure()), failure.toString());
  }

  @Test
  void givenFormalBindingsInSiblingMethods_whenCopiedAccessAndEdgeSelectTheOtherFormal_thenAdmissionRejectsItsBody()
      throws IOException {
    // Arrange
    var original = produce("class Example { int first(int value) { return value; } int other(int value) { return value; } }");
    var formals = original.aggregate().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ParameterDeclaration parameter && parameter.name().equals("value")).toList();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var foreignKey = formals.getLast().value(SyntaxValue.ParameterDeclaration.class).declarationKey();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, foreignKey) : node,
        List.of(occurrenceReference(access, formals.getLast())));

    // Act
    var positive = AstValueAdmission.admit(copy(original, UnaryOperator.identity(), List.of(occurrenceReference(access, formals.getFirst()))));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(positive.semanticEdges().contains(occurrenceReference(access, formals.getFirst())));
    assertTrue(failure.getMessage().contains("within its declaration's executable body"), failure.toString());
  }

  @Test
  void givenNestedLocalAnonymousAndLambdaCaptures_whenRetainedAndReadmitted_thenExactBindingsRemainSelected() throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject(int input) {
            int value = 2;
            class Local { int get() { return value + input; } }
            var anonymous = new Object() { int get() { return value + input; } };
            java.util.function.IntUnaryOperator lambda = amount -> amount + value + input;
            return new Local().get() + anonymous.get() + lambda.applyAsInt(3);
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var local = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var lambda = named(original, "amount", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var formal = original.aggregate().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ParameterDeclaration parameter && parameter.name().equals("input"))
        .findFirst().orElseThrow().value(SyntaxValue.ParameterDeclaration.class);

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var admitted = AstValueAdmission.admit(SourceValueCapture.capture(retained.aggregate()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(retained.aggregate(), admitted);
    assertEquals(List.of(key(local), key(local), key(local)), keys(named(retained, "value", SyntaxKind.VARIABLE_ACCESS)));
    assertEquals(List.of(formal.declarationKey(), formal.declarationKey(), formal.declarationKey()),
        keys(named(retained, "input", SyntaxKind.VARIABLE_ACCESS)));
    assertEquals(List.of(key(lambda)), keys(named(retained, "amount", SyntaxKind.VARIABLE_ACCESS)));
    assertEquals(LocalBindingEvidence.TypeForm.IMPLICIT,
        lambda.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().typeForm());
  }

  @Test
  void givenNestedTypeDeclaresANearerBinding_whenCopiedAccessSelectsTheOuterBinding_thenAdmissionRejectsShadowing()
      throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject(int value) {
            class Local { int get() { int value = 2; return value; } }
            return new Local().get() + value;
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var local = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var formal = original.aggregate().nodes().stream().filter(node -> node.value() instanceof SyntaxValue.ParameterDeclaration)
        .findFirst().orElseThrow().value(SyntaxValue.ParameterDeclaration.class);
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream()
        .filter(node -> node.requiredReference().symbolKey().equals(Optional.of(key(local)))).findFirst().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? select(node, ReferenceKind.PARAMETER, formal.declarationKey()) : node);

    // Act
    var admitted = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(Optional.of(key(local)), admitted.node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("nearer binding in the nested type"), failure.toString());
  }

  @Test
  void givenInheritedFieldShadowsAnOuterLocal_whenCopiedAccessClaimsCapture_thenAdmissionRejectsTheSelection() throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Base { int value = 2; }
        class Example {
          int subject() {
            int value = 1;
            class Local extends Base { int get() { return value; } }
            return new Local().get();
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var outer = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var field = writtenMember(original, "value", SyntaxKind.FIELD_DECLARATION);
    var access = memberAccess(original, field.value(SyntaxValue.VariableDeclaration.class).declarationKey());
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? asLocalAccess(node, outer, "value") : node,
        List.of(occurrenceReference(access, outer)));

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(access.requiredReference(), retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference());
    assertTrue(failure.getMessage().contains("member shadows its binding"), failure.toString());
    assertTrue(decoding.getMessage().contains("member shadows its binding"), decoding.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenAccessibleInheritedFieldsInDifferentTypeShapes_whenAccessClaimsCapture_thenAdmissionRejectsTheSelection() {
    // Arrange
    var cases = List.of(
        new CapturedHierarchy("givenTransitiveProtectedFieldAcrossPackages_whenAccessClaimsOuterLocal_thenAdmissionRejects", List.of(
            SourceFile.fromContent(capturedPath(Path.of("foundation", "Base.java")), """
                package foundation;
                public class Base<T> { protected int value = 2; }
                """),
            SourceFile.fromContent(capturedPath(Path.of("bridge", "Middle.java")), """
                package bridge;
                public class Middle extends foundation.Base<String> {}
                """),
            SourceFile.fromContent(capturedPath(Path.of("clients", "Example.java")), """
                package clients;
                class Example {
                  int subject() {
                    int value = 1;
                    class Local extends bridge.Middle { int get() { return value; } }
                    return new Local().get();
                  }
                }
                """))),
        new CapturedHierarchy("givenPackageFieldAcrossCompilationUnits_whenAccessClaimsOuterLocal_thenAdmissionRejects", List.of(
            SourceFile.fromContent(capturedPath(Path.of("sample", "Base.java")), "package sample; class Base { int value = 2; }"),
            SourceFile.fromContent(capturedPath(Path.of("sample", "Example.java")), """
                package sample;
                class Example {
                  int subject() {
                    int value = 1;
                    class Local extends Base { int get() { return value; } }
                    return new Local().get();
                  }
                }
                """))),
        new CapturedHierarchy("givenAnonymousCreationInheritsInterfaceField_whenAccessClaimsOuterLocal_thenAdmissionRejects", List.of(
            SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
                interface Base { int value = 2; }
                interface Middle extends Base { int get(); }
                class Example {
                  int subject() {
                    int value = 1;
                    return new Middle() { public int get() { return value; } }.get();
                  }
                }
                """))));

    // Act
    var tests = cases.stream().map(hierarchy -> DynamicTest.dynamicTest(hierarchy.name(), () -> assertInheritedFieldShadowing(hierarchy)));

    // Assert
    return tests;
  }

  private void assertInheritedFieldShadowing(CapturedHierarchy hierarchy) throws IOException {
    // Arrange
    var original = produce(hierarchy.sources(), AstProfile.JAVA_SOURCE_NESTED);
    var outer = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var field = writtenMember(original, "value", SyntaxKind.FIELD_DECLARATION).value(SyntaxValue.VariableDeclaration.class);
    var access = memberAccess(original, field.declarationKey());
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? asLocalAccess(node, outer, "value") : node);

    // Act
    var valid = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(Optional.of(field.declarationKey()), valid.node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("member shadows its binding"), failure.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenNoEstablishedInheritedField_whenNestedClassCapturesOuterLocal_thenExactBindingRemainsSelected() {
    // Arrange
    var cases = List.of(
        new CapturedHierarchy("givenPrivateFieldHidesFartherPublicField_whenSourceIsRetained_thenOuterCaptureRemainsSelected", List.of(
            SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
                class Base { public int value = 2; }
                class Middle extends Base { private int value = 3; }
                class Example {
                  int subject() {
                    int value = 1;
                    class Local extends Middle { int get() { return value; } }
                    return new Local().get();
                  }
                }
                """))),
        new CapturedHierarchy("givenPackageFieldAcrossAnInterveningPackage_whenSourceIsRetained_thenOuterCaptureRemainsSelected", List.of(
            SourceFile.fromContent(capturedPath(Path.of("sample", "Base.java")), "package sample; public class Base { int value = 2; }"),
            SourceFile.fromContent(capturedPath(Path.of("bridge", "Middle.java")), "package bridge; public class Middle extends sample.Base {}"),
            SourceFile.fromContent(capturedPath(Path.of("sample", "Example.java")), """
                package sample;
                class Example {
                  int subject() {
                    int value = 1;
                    class Local extends bridge.Middle { int get() { return value; } }
                    return new Local().get();
                  }
                }
                """))),
        new CapturedHierarchy("givenSuperclassOutsideCapturedSource_whenSourceIsRetained_thenOuterCaptureRemainsSelected", List.of(
            SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
                class Example {
                  int subject() {
                    int value = 1;
                    class Local extends Object { int get() { return value; } }
                    return new Local().get();
                  }
                }
                """))));

    // Act
    var tests = cases.stream().map(hierarchy -> DynamicTest.dynamicTest(hierarchy.name(), () -> assertLegalCapture(hierarchy)));

    // Assert
    return tests;
  }

  private void assertLegalCapture(CapturedHierarchy hierarchy) throws IOException {
    // Arrange
    var original = produce(hierarchy.sources(), AstProfile.JAVA_SOURCE_NESTED);
    var local = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(Optional.of(key(local)), retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
  }

  @Test
  void givenNestedFieldShadowsAnOuterLocal_whenCopiedAccessClaimsCapture_thenAdmissionRejectsTheSelection() throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject() {
            int value = 1;
            class Local { int value = 2; int get() { return value; } }
            class Capture { int get() { return value; } }
            return new Local().get() + new Capture().get();
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var local = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var captured = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var member = writtenMember(original, "value", SyntaxKind.FIELD_DECLARATION);
    var access = memberAccess(original, member.value(SyntaxValue.VariableDeclaration.class).declarationKey());
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? asLocalAccess(node, local, "value") : node,
        List.of(occurrenceReference(access, local)));

    // Act
    var valid = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, valid.completion(), valid.diagnostics().toString());
    assertEquals(access.semanticEdges(), valid.aggregate().node(access.nodeId()).orElseThrow().semanticEdges());
    assertEquals(Optional.of(key(local)), valid.aggregate().node(captured.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("member shadows its binding"), failure.toString());
  }

  @Test
  void givenAnInterveningTypeOwnsAField_whenCopiedInnerAccessClaimsTheOuterFormal_thenAdmissionRejectsTheSelection() throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), """
        class Example {
          int subject(int value) {
            class Middle {
              int value = 2;
              class Inner { int get() { return value; } int formal(int value) { return value; } }
              int get() { return new Inner().get(); }
            }
            return new Middle().get();
          }
        }
        """)), AstProfile.JAVA_SOURCE_NESTED);
    var formals = original.aggregate().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.ParameterDeclaration parameter && parameter.name().equals("value")).toList();
    var outerMethod = original.program().methods().stream().filter(method -> method.name().equals("subject")).findFirst().orElseThrow();
    var innerMethod = original.program().methods().stream().filter(method -> method.name().equals("formal")).findFirst().orElseThrow();
    var key = outerMethod.parameters().getFirst().parameterKey();
    var outerFormal = formals.stream().filter(node -> node.value(SyntaxValue.ParameterDeclaration.class).declarationKey().equals(key))
        .findFirst().orElseThrow();
    var ownAccess = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var member = writtenMember(original, "value", SyntaxKind.FIELD_DECLARATION);
    var access = memberAccess(original, member.value(SyntaxValue.VariableDeclaration.class).declarationKey());
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? asVariableAccess(node, "value", ReferenceKind.PARAMETER, key) : node,
        List.of(occurrenceReference(access, outerFormal)));

    // Act
    var valid = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(access.semanticEdges(), valid.node(access.nodeId()).orElseThrow().semanticEdges());
    assertEquals(Optional.of(innerMethod.parameters().getFirst().parameterKey()),
        valid.node(ownAccess.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(failure.getMessage().contains("member shadows its binding"), failure.toString());
  }

  @TestFactory
  Stream<DynamicTest> givenARecordComponentOrEnumConstant_whenCopiedAccessClaimsAnOuterFormal_thenAdmissionRejectsTheSelection() {
    // Arrange
    var cases = List.of(
        new MemberCase("givenRecordComponentField_whenAccessClaimsOuterFormal_thenAdmissionRejects",
            SyntaxKind.RECORD_COMPONENT_DECLARATION, ReferenceKind.FIELD, """
            class Example {
              int subject(int value) {
                record Local(int value) { int get() { return value; } }
                return new Local(2).get();
              }
            }
            """),
        new MemberCase("givenEnumConstantField_whenAccessClaimsOuterFormal_thenAdmissionRejects",
            SyntaxKind.ENUM_CONSTANT_DECLARATION, ReferenceKind.ENUM_CONSTANT, """
            class Example {
              Object subject(Object value) {
                enum Local { value; Object get() { return value; } }
                return Local.value.get();
              }
            }
            """));

    // Act
    var tests = cases.stream().map(member -> DynamicTest.dynamicTest(member.name(), () -> assertImplicitFieldScope(member)));

    // Assert
    return tests;
  }

  private void assertImplicitFieldScope(MemberCase member) throws IOException {
    // Arrange
    var original = produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), member.source())),
        AstProfile.JAVA_SOURCE_ENUM_INITIALIZATION);
    var parameter = writtenMember(original, "value", SyntaxKind.PARAMETER_DECLARATION);
    var declared = writtenMember(original, "value", member.declarationKind());
    var fieldKey = switch (declared.value()) {
      case SyntaxValue.RecordComponentDeclaration component -> component.counterparts().backingFieldKey();
      case SyntaxValue.VariableDeclaration variable -> variable.declarationKey();
      default -> throw new AssertionError(declared.value());
    };
    var access = memberAccess(original, fieldKey);
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? asVariableAccess(node, "value", ReferenceKind.PARAMETER, parameter.value(SyntaxValue.ParameterDeclaration.class).declarationKey())
        : node);

    // Act
    var valid = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var selection = valid.node(access.nodeId()).orElseThrow().requiredReference();
    assertEquals(member.referenceKind(), selection.referenceKind());
    assertEquals(Optional.of(fieldKey), selection.symbolKey());
    assertTrue(failure.getMessage().contains("member shadows its binding"), failure.toString());
  }

  @Test
  void givenACompactRecordConstructor_whenItsImplicitParameterIsRetained_thenTheMandatedBindingRemainsSelected() throws IOException {
    // Arrange
    var original = produce("record Example(int value) { Example { int observed=value; } }");
    var constructor = original.program().methods().stream()
        .filter(method -> method.parameters().stream().anyMatch(parameter -> parameter.name().equals("value") && !parameter.sourceDeclared())
            && method.hasBody()).findFirst().orElseThrow();
    var parameter = constructor.parameters().getFirst();

    // Act
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var admitted = AstValueAdmission.admit(SourceValueCapture.capture(retained.aggregate()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, retained.completion(), retained.diagnostics().toString());
    assertEquals(List.of(parameter.parameterKey()), keys(named(retained, "value", SyntaxKind.VARIABLE_ACCESS)));
    assertEquals(List.of(ReferenceKind.PARAMETER), named(retained, "value", SyntaxKind.VARIABLE_ACCESS).stream()
        .map(node -> node.requiredReference().referenceKind()).toList());
    assertTrue(admitted.nodes().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION));
  }

  @Test
  void givenPureLegacyBindings_whenDeclarationAndAllAccessesSelectAForeignExecutable_thenAdmissionRejectsThem() throws IOException {
    assertForeignLegacyOwnerRejected(true);
  }

  @Test
  void givenMixedLegacyBindings_whenDeclarationAndAllAccessesSelectAForeignExecutable_thenAdmissionRejectsThem() throws IOException {
    assertForeignLegacyOwnerRejected(false);
  }

  private void assertForeignLegacyOwnerRejected(boolean pureLegacy) throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int value=1; int total=value; return value+total; } }");
    var originalKey = key(named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst());
    var supplied = copy(original, node -> {
      var copied = node.value() instanceof SyntaxValue.LocalDeclaration local && (pureLegacy || local.name().equals("value"))
          ? legacy(node, local.name()) : node;
      return copied.semanticReference().flatMap(SemanticReference::symbolKey).filter(originalKey::equals).isPresent()
          ? select(copied, "type:Foreign#subject():local_variable:0:1") : copied;
    });

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("declared executable owner"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenOrdinaryLocal_whenContextClaimsCatchAttachment_thenPublicAdmissionRejectsIt() throws IOException {
    assertContextContradictionRejected("class Example { int subject() { int value=1; return value; } }", LocalBindingEvidence.Context.LOCAL);
  }

  @Test
  void givenCatchParameter_whenContextClaimsOrdinaryLocalAttachment_thenPublicAdmissionRejectsIt() throws IOException {
    assertContextContradictionRejected(
        "class Example { int subject() { try { return 1; } catch (RuntimeException value) { return value.hashCode(); } } }",
        LocalBindingEvidence.Context.CATCH);
  }

  private void assertContextContradictionRejected(String source, LocalBindingEvidence.Context expected) throws IOException {
    // Arrange
    var original = produce(source);
    var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var evidence = declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow();
    var contradictory = expected == LocalBindingEvidence.Context.LOCAL ? LocalBindingEvidence.Context.CATCH : LocalBindingEvidence.Context.LOCAL;
    var supplied = copy(original, node -> {
      if (!node.nodeId().equals(declaration.nodeId())) {
        return node;
      }
      var local = node.value(SyntaxValue.LocalDeclaration.class);
      return replace(node, new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
          Optional.of(new LocalBindingEvidence(evidence.declarationKey(), evidence.typeForm(), contradictory,
              evidence.initialized(), evidence.annotations(), evidence.sharedAnnotationKeys()))));
    });

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var positive = AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate()));

    // Assert
    assertTrue(failure.getMessage().contains("context must agree with its source attachment"), failure.toString());
    assertEquals(expected, evidence.context());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(original.aggregate(), positive);
  }

  @Test
  void givenOuterPatternMatches_whenNestedLambdaCapturesIt_thenFrontendAndRetainedSourceKeepItsExactBinding() throws IOException {
    // Arrange
    var fixture = siblingLambdaPattern();
    var access = fixture.nestedCapture();
    var expectedKey = key(fixture.pattern().requiredChild(SyntaxRole.PARAMETER));

    // Act
    var original = fixture.result();
    var admitted = AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate()));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(Optional.of(expectedKey), admitted.node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(Optional.of(expectedKey), retained.aggregate().node(access.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertTrue(fixture.pattern().value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow()
        .contains(access.origin().orElseThrow()));
  }

  @TestFactory
  Stream<DynamicTest> givenSiblingLambdas_whenAccessClaimsAnOutOfScopePattern_thenDirectAndRetainedAdmissionRefuse() {
    // Arrange
    var cases = List.of(
        new SiblingPatternCase("givenUnavailablePatternAccessSpans_whenSiblingLambdaClaimsTheBinding_thenDirectAndRetainedAdmissionRefuse",
            false),
        new SiblingPatternCase("givenExactButOutOfScopePatternSpan_whenSiblingLambdaClaimsTheBinding_thenDirectAndRetainedAdmissionRefuse",
            true));

    // Act
    var tests = cases.stream().map(patternCase -> DynamicTest.dynamicTest(patternCase.name(), () -> assertSiblingPatternRejected(patternCase)));

    // Assert
    return tests;
  }

  private void assertSiblingPatternRejected(SiblingPatternCase patternCase) throws IOException {
    // Arrange
    var fixture = siblingLambdaPattern();
    var original = fixture.result();
    var pattern = fixture.pattern();
    var declaration = pattern.requiredChild(SyntaxRole.PARAMETER);
    var use = fixture.siblingAccess();
    var spans = new ArrayList<>(pattern.value(SyntaxValue.PatternBinding.class).resolvedAccesses().orElseThrow());
    spans.add(use.origin().orElseThrow());
    Optional<List<SourceSpan>> suppliedSpans = patternCase.withExactSpan() ? Optional.of(spans) : Optional.empty();
    var supplied = copy(original, node -> {
      if (node.nodeId().equals(pattern.nodeId())) {
        return replace(node, new SyntaxValue.PatternBinding(suppliedSpans));
      }
      return node.nodeId().equals(use.nodeId()) ? select(node, key(declaration)) : node;
    }, List.of(occurrenceReference(use, declaration)));

    // Act
    var admitted = AstValueAdmission.admit(copy(original, UnaryOperator.identity()));
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(declaration.type(), use.type());
    assertEquals(use.requiredReference(), admitted.node(use.nodeId()).orElseThrow().requiredReference());
    assertPatternScopeRefusal(failure);
    assertPatternScopeRefusal(decoding);
  }

  private static void assertPatternScopeRefusal(AstContractException failure) {
    assertTrue(failure.getMessage().contains("lexical scope") || failure.getMessage().contains("Copied pattern scope")
        || failure.getMessage().contains("declaring lambda"), failure.toString());
  }

  private SiblingPatternFixture siblingLambdaPattern() throws IOException {
    var result = produce("""
        import java.util.function.BooleanSupplier;
        import java.util.function.Predicate;
        class Example {
          boolean subject() {
            Predicate<Object> first = input -> {
              if (input instanceof String value && value.isEmpty()) {
                BooleanSupplier nested = () -> value.isBlank();
                return nested.getAsBoolean();
              }
              return false;
            };
            Predicate<String> second = value -> value.isEmpty();
            return first.test("") && second.test("");
          }
        }
        """);
    var pattern = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.BINDING_PATTERN)
        .findFirst().orElseThrow();
    var lambdas = result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.LAMBDA_EXPRESSION).toList();
    var sibling = lambdas.stream().filter(lambda -> lambda.children(SyntaxRole.PARAMETER).stream()
        .anyMatch(parameter -> parameter.value(SyntaxValue.LocalDeclaration.class).name().equals("value"))).findFirst().orElseThrow();
    var nested = lambdas.stream().filter(lambda -> lambda.children(SyntaxRole.PARAMETER).isEmpty()).findFirst().orElseThrow();
    return new SiblingPatternFixture(result, pattern, lambdaAccess(sibling), lambdaAccess(nested));
  }

  private static SyntaxNode lambdaAccess(SyntaxNode lambda) {
    return lambda.requiredChild(SyntaxRole.BODY).descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
        .findFirst().orElseThrow();
  }

  @Test
  void givenFinallyAndPatternBindings_whenCopiedAdmissionRuns_thenCompleteSourceRemainsCanonical() throws IOException {
    // Arrange
    var original = finallyWithPattern();

    // Act
    var admitted = AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(original.aggregate(), admitted);
    assertTrue(original.diagnostics().stream().noneMatch(diagnostic -> diagnostic.isError()));
  }

  @Test
  void givenFinallyAndPatternBindings_whenCopiedAccessSelectsAForeignOwner_thenAdmissionRejectsForeignOwnership()
      throws IOException {
    // Arrange
    var original = finallyWithPattern();
    var access = named(original, "text", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? select(node, "type:Foreign#subject():binding_variable:0:1") : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("owned named binding"));
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenSameNamedSiblingBindings_whenAccessSelectsTheOtherScope_thenPublicAdmissionRejectsIt() throws IOException {
    // Arrange
    var original = siblingBindings();
    var bindings = named(original, "value", SyntaxKind.LOCAL_DECLARATION);
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(bindings.getLast())) : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("lexical scope"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertEquals(original.aggregate(), AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate())));
  }

  @Test
  void givenOneLegacyLocalAndExpandedBindings_whenAccessSelectsAForeignOwner_thenPublicAdmissionStillRejectsIt() throws IOException {
    // Arrange
    var original = siblingBindings();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? select(node, "type:Foreign#subject():local_variable:0:1") : legacy(node, "total"));

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("owned named binding"), failure.toString());
    assertEquals(AstCompletion.COMPLETE, original.completion());
  }

  @Test
  void givenLegalNestedReferences_whenLegacyAndExpandedValuesAreMixed_thenBothBindingIdentitiesRemainAdmitted() throws IOException {
    // Arrange
    var original = siblingBindings();
    var supplied = copy(original, node -> legacy(node, "total"));

    // Act
    var admitted = AstValueAdmission.admit(supplied);

    // Assert
    assertEquals(original.aggregate().nodes().size(), admitted.nodes().size());
    assertEquals(named(original, "value", SyntaxKind.VARIABLE_ACCESS).stream().map(SyntaxNode::semanticReference).toList(),
        admitted.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS)
            .filter(node -> node.value(SyntaxValue.SourceSymbol.class).name().equals("value")).map(SyntaxNode::semanticReference).toList());
    assertTrue(admitted.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals("total"))
        .allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isEmpty()));
  }

  @Test
  void givenPureLegacyNamedBindings_whenReadmitted_thenRetainedReferencesRemainValid() throws IOException {
    // Arrange
    var original = siblingBindings();
    var supplied = copy(original, node -> node.value() instanceof SyntaxValue.LocalDeclaration local ? legacy(node, local.name()) : node);

    // Act
    var admitted = AstValueAdmission.admit(supplied);

    // Assert
    assertEquals(original.aggregate().semanticEdges(), admitted.semanticEdges());
    assertTrue(admitted.nodes().stream().filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration)
        .allMatch(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().isEmpty()));
  }

  @Test
  void givenEarlierOccurrenceAndLaterBinding_whenCopiedAccessSelectsTheLaterDeclaration_thenAdmissionRejectsDeclarationOrder()
      throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int first=1; int second=first; int later=2; return second; } }");
    var access = named(original, "first", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var declaration = named(original, "later", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId())
        ? replace(select(node, key(declaration)), new SyntaxValue.SourceSymbol("later")) : node);

    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));

    // Assert
    assertTrue(failure.getMessage().contains("precede its binding declaration"), failure.toString());
  }

  @Test
  void givenSelfInitializerWithinDeclarationScope_whenCompilerReportsDefiniteAssignmentError_thenCopiedScopeAdmissionPreservesIt()
      throws IOException {
    // Arrange
    var original = produce("class Example { int subject() { int value=value; return value; } }");

    // Act
    var admitted = AstValueAdmission.admit(SourceValueCapture.capture(original.aggregate()));

    // Assert
    assertEquals(AstCompletion.INCOMPLETE, original.completion());
    assertEquals(original.aggregate(), admitted);
    assertTrue(original.diagnostics().stream().anyMatch(diagnostic -> diagnostic.code().equals("compiler.err.var.might.not.have.been.initialized")));
  }

  private AstResult siblingBindings() throws IOException {
    return produce("class Example { int subject() { int total=0; { int value=1; total=value; } { int value=2; total=value; } return total; } }");
  }

  @Test
  void givenResourceBinding_whenContextClaimsOrdinaryLocalAttachment_thenPublicAdmissionRejectsIt() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() throws Exception { try (AutoCloseable value = null) {} } }");
    var declaration = named(original, "value", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var local = declaration.value(SyntaxValue.LocalDeclaration.class);
    var evidence = local.bindingEvidence().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(declaration.nodeId())
        ? replace(node, new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(),
            Optional.of(new LocalBindingEvidence(evidence.declarationKey(), evidence.typeForm(), LocalBindingEvidence.Context.LOCAL,
                evidence.initialized(), evidence.annotations(), evidence.sharedAnnotationKeys())))) : node);
    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(failure.getMessage().contains("context must agree with its source attachment"), failure.toString());
  }

  @Test
  void givenAProjectedTryRetainsItsResourceAttachment_whenCatchAccessClaimsTheResource_thenDirectAndRetainedAdmissionRefuse()
      throws IOException {
    // Arrange
    var original = produce("""
        class Example {
          void subject() throws Exception {
            try (AutoCloseable value = null) { value.toString(); }
            catch (Exception failure) { AutoCloseable value = null; value.toString(); }
          }
        }
        """);
    var statement = original.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TRY).findFirst().orElseThrow();
    var resource = statement.requiredChild(SyntaxRole.RESOURCE);
    var accesses = named(original, "value", SyntaxKind.VARIABLE_ACCESS);
    var own = accesses.stream().filter(node -> node.requiredReference().symbolKey().equals(Optional.of(key(resource))))
        .findFirst().orElseThrow();
    var catchLocal = named(original, "value", SyntaxKind.LOCAL_DECLARATION).stream()
        .filter(node -> !node.nodeId().equals(resource.nodeId())).findFirst().orElseThrow();
    var catchAccess = accesses.stream().filter(node -> node.requiredReference().symbolKey().equals(Optional.of(key(catchLocal))))
        .findFirst().orElseThrow();
    UnaryOperator<SyntaxNode> withProjectedTry = node -> node.nodeId().equals(statement.nodeId()) ? unsupported(node) : node;
    var supplied = copy(original,
        node -> node.nodeId().equals(catchAccess.nodeId()) ? select(node, key(resource)) : withProjectedTry.apply(node));

    // Act
    var admitted = AstValueAdmission.admit(copy(original, withProjectedTry));
    var retained = AstCodec.decode(AstCodec.encode(original), original.identity());
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    var decoding = assertThrows(AstContractException.class,
        () -> AstCodec.decode(payloadWithAggregate(original, supplied), original.identity()));

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    var partial = admitted.node(statement.nodeId()).orElseThrow();
    assertEquals(SyntaxKind.UNSUPPORTED, partial.syntaxKind());
    assertEquals(JavaSourceKind.TRY, partial.sourceKind());
    assertEquals(Optional.of(key(resource)), admitted.node(own.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(Optional.of(key(catchLocal)), admitted.node(catchAccess.nodeId()).orElseThrow().requiredReference().symbolKey());
    assertEquals(own.requiredReference(), retained.aggregate().node(own.nodeId()).orElseThrow().requiredReference());
    assertTrue(failure.getMessage().contains("following resources and try body"), failure.toString());
    assertTrue(decoding.getMessage().contains("following resources and try body"), decoding.toString());
  }

  @Test
  void givenResourceAndCatchLocalWithSameName_whenCatchAccessSelectsResourceBinding_thenPublicAdmissionRejectsTheScope() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() throws Exception { try (AutoCloseable value = null) {} catch (Exception failure) { AutoCloseable value = null; value.toString(); } } }");
    var resource = named(original, "value", SyntaxKind.LOCAL_DECLARATION).stream()
        .filter(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().context() == LocalBindingEvidence.Context.RESOURCE)
        .findFirst().orElseThrow();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(resource)) : node);
    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(failure.getMessage().contains("following resources and try body"), failure.toString());
  }

  @Test
  void givenResourceAndFinallyLocalWithSameName_whenFinallyAccessSelectsResourceBinding_thenPublicAdmissionRejectsTheScope() throws IOException {
    // Arrange
    var original = produce("class Example { void subject() throws Exception { try (AutoCloseable value = null) {} finally { AutoCloseable value = null; value.toString(); } } }");
    var resource = named(original, "value", SyntaxKind.LOCAL_DECLARATION).stream()
        .filter(node -> node.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().context() == LocalBindingEvidence.Context.RESOURCE)
        .findFirst().orElseThrow();
    var access = named(original, "value", SyntaxKind.VARIABLE_ACCESS).getFirst();
    var supplied = copy(original, node -> node.nodeId().equals(access.nodeId()) ? select(node, key(resource)) : node);
    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(failure.getMessage().contains("following resources and try body"), failure.toString());
  }

  @Test
  void givenEarlierResourceInitializerUsingAField_whenCopiedAccessSelectsALaterResource_thenAdmissionRejectsTheForwardBinding() throws IOException {
    // Arrange
    var original = produce("class Example { AutoCloseable later; void subject() throws Exception { try (AutoCloseable first = later; AutoCloseable later = null) {} } }");
    var resource = named(original, "later", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var first = named(original, "first", SyntaxKind.LOCAL_DECLARATION).getFirst();
    var initializer = first.requiredChild(SyntaxRole.INITIALIZER);
    var reference = resource.semanticReference().orElseThrow();
    var supplied = copy(original, node -> node.nodeId().equals(initializer.nodeId())
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), SyntaxKind.VARIABLE_ACCESS, node.type(),
            new SyntaxValue.SourceSymbol("later"), node.children(), Optional.of(reference), node.origin().orElseThrow()) : node);
    // Act
    var failure = assertThrows(AstContractException.class, () -> AstValueAdmission.admit(supplied));
    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion());
    assertTrue(failure.getMessage().contains("following resources and try body"), failure.toString());
  }

  private AstResult finallyWithPattern() throws IOException {
    return produce("class Example { int subject(Object input) { int total=0; try {} finally {} return switch(input) { case String text when !text.isEmpty() -> total+1; default -> total; }; } }");
  }

  private AstResult produce(String source) throws IOException {
    return produce(List.of(SourceFile.fromContent(capturedPath(Path.of("Example.java")), source)));
  }

  private AstResult produce(List<SourceFile> sources) throws IOException {
    return produce(sources, AstProfile.JAVA_SOURCE_EXPANDED);
  }

  private AstResult produce(List<SourceFile> sources, AstProfile profile) throws IOException {
    return produceOutcome(sources, profile).result().orElseThrow();
  }

  private AstOutcome produceOutcome(List<SourceFile> sources, AstProfile profile) throws IOException {
    var snapshot = InputSnapshot.fromSources(capturedAbsolute(directory), sources);
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile);
  }

  private static List<SyntaxNode> named(AstResult result, String name, SyntaxKind kind) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind)
        .filter(node -> node.value() instanceof SyntaxValue.LocalDeclaration local ? local.name().equals(name)
            : node.value(SyntaxValue.SourceSymbol.class).name().equals(name)).toList();
  }

  private static String key(SyntaxNode declaration) {
    return declaration.value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow().declarationKey().orElseThrow();
  }

  private static List<String> keys(List<SyntaxNode> accesses) {
    return accesses.stream().map(node -> node.requiredReference().symbolKey().orElseThrow()).toList();
  }

  private static SyntaxNode switchLabel(AstResult result, SyntaxKind kind) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.CASE)
        .flatMap(node -> node.children(SyntaxRole.LABEL).stream()).filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow();
  }

  private static SyntaxNode selectedReturnedLocal(AstResult result, String methodKey) {
    return result.body(methodKey).orElseThrow().root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.RETURN)
        .findFirst().orElseThrow().requiredChild(SyntaxRole.EXPRESSION);
  }

  private static SyntaxNode unsupported(SyntaxNode node) {
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), SyntaxKind.UNSUPPORTED, node.type(),
        new SyntaxValue.Unsupported(node.sourceKind().name()), node.children(), node.semanticReference(), node.origin().orElseThrow());
  }

  private static CopiedAstValues withoutStaticAccess(CopiedAstValues values, String nodeId) {
    var bodies = values.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses().stream().filter(access -> !access.accessNodeId().equals(nodeId)).toList(),
        body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes());
  }

  private static CopiedAstValues withoutChildUse(CopiedAstValues values, String consumerNodeId) {
    var bodies = values.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(),
        body.uses().stream().filter(use -> !use.consumerNodeId().equals(consumerNodeId)).toList(), body.invocations(), body.staticAccesses(),
        body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(values.snapshot(), values.sources(), bodies, values.memberOrders(), values.associations(),
        values.declarationTypes(), values.scopes());
  }

  private static SyntaxNode writtenMember(AstResult result, String name, SyntaxKind kind) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == kind)
        .filter(node -> node.value(SyntaxValue.Symbol.class).name().equals(name)).findFirst().orElseThrow();
  }

  private static SyntaxNode memberAccess(AstResult result, String key) {
    return result.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.FIELD_ACCESS)
        .filter(node -> node.sourceKind() == JavaSourceKind.IDENTIFIER)
        .filter(node -> node.requiredReference().symbolKey().equals(Optional.of(key))).findFirst().orElseThrow();
  }

  private static SyntaxNode asVariableAccess(SyntaxNode node, String name, ReferenceKind kind, String key) {
    var reference = node.requiredReference();
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), SyntaxKind.VARIABLE_ACCESS, node.type(),
        new SyntaxValue.SourceSymbol(name), node.children(), Optional.of(new SemanticReference(kind, reference.status(),
            Optional.of(key), reference.typeName(), reference.typeAlternativeNames(), reference.assignableToTypeNames(), Optional.empty())),
        node.origin().orElseThrow());
  }

  private static SyntaxNode asLocalAccess(SyntaxNode node, SyntaxNode binding, String name) {
    if (node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS) {
      return select(node, key(binding));
    }
    return SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), SyntaxKind.VARIABLE_ACCESS, node.type(),
        new SyntaxValue.SourceSymbol(name), node.children(), Optional.of(binding.requiredReference()), node.origin().orElseThrow());
  }

  private static SyntaxSemanticEdge occurrenceReference(SyntaxNode source, SyntaxNode declaration) {
    return new SyntaxSemanticEdge(source.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE, new SourceOccurrence(declaration.nodeId()));
  }

  private static SyntaxNode legacy(SyntaxNode node, String name) {
    if (node.value() instanceof SyntaxValue.LocalDeclaration local && local.name().equals(name)) {
      return replace(node, new SyntaxValue.LocalDeclaration(local.name(), local.writtenType(), local.writtenModifiers(), Optional.empty()));
    }
    return node;
  }

  private static SyntaxNode replace(SyntaxNode node, SyntaxValue value) {
    return rebuild(node, value, node.children(), node.semanticReference());
  }

  private static SyntaxNode select(SyntaxNode node, String bindingKey) {
    return select(node, node.requiredReference().referenceKind(), bindingKey);
  }

  private static SyntaxNode select(SyntaxNode node, ReferenceKind kind, String bindingKey) {
    var reference = node.semanticReference().orElseThrow();
    return rebuild(node, node.value(), node.children(), Optional.of(new SemanticReference(kind, reference.status(),
        Optional.of(bindingKey), reference.typeName(), reference.typeAlternativeNames(), reference.assignableToTypeNames(), Optional.empty())));
  }

  private static CopiedAstValues copy(AstResult result, UnaryOperator<SyntaxNode> change) {
    return copy(result, change, List.of());
  }

  private static CopiedAstValues copy(AstResult result, UnaryOperator<SyntaxNode> change, List<SyntaxSemanticEdge> suppliedEdges) {
    var original = SourceValueCapture.capture(result.aggregate());
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = original.sources().stream().map(source -> {
      var root = copy(source.root(), change, replacements);
      var nodes = root.descendantsAndSelf();
      var edges = new ArrayList<>(source.semanticEdges().stream()
          .filter(edge -> nodes.stream().noneMatch(node -> node.nodeId().equals(edge.sourceKey()) && hasCopiedBindingReference(node)))
          .toList());
      nodes.stream().filter(LocalBindingAdmissionTest::hasCopiedBindingReference).forEach(node -> edges.addAll(node.semanticEdges()));
      suppliedEdges.stream().filter(edge -> nodes.stream().anyMatch(node -> node.nodeId().equals(edge.sourceKey()))).forEach(edges::add);
      return new SyntaxSourceContribution(source.sourceFileKey(), root, nodes,
          nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList(), edges);
    }).toList();
    var bodies = original.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(),
        copy(body.root(), change, replacements), body.origin(), body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(),
        body.constructors(), body.attributedOccurrences())).toList();
    var scopes = original.scopes().stream().map(scope -> new CopiedAstValues.Scope(scope.scope(), copy(scope.root(), change, replacements),
        scope.uses(), scope.invocations(), scope.staticAccesses(), scope.concatenations(), scope.attributedOccurrences())).toList();
    return new CopiedAstValues(original.snapshot(), sources, bodies, original.memberOrders(), original.associations(), original.declarationTypes(),
        scopes);
  }

  private static boolean hasCopiedBindingReference(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case VARIABLE_ACCESS, LOCAL_DECLARATION, ANNOTATION_NAME_REFERENCE -> true;
      case UNSUPPORTED -> node.sourceKind() == JavaSourceKind.IDENTIFIER;
      default -> false;
    };
  }

  private static SyntaxNode copy(SyntaxNode node, UnaryOperator<SyntaxNode> change, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream().map(child -> new SyntaxChild(child.role(), child.position(),
        copy(child.node(), change, replacements))).toList();
    var copied = change.apply(rebuild(node, node.value(), children, node.semanticReference()));
    replacements.put(node, copied);
    return copied;
  }

  private static SyntaxNode rebuild(SyntaxNode node, SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> reference) {
    return node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value, children,
            reference, node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), value,
            children, reference);
  }

  private record ScopeCase(String name, String source, String bindingName, String accessName, String failure) {
  }

  private record MemberCase(String name, SyntaxKind declarationKind, ReferenceKind referenceKind, String source) {
  }

  private record CapturedHierarchy(String name, List<SourceFile> sources) {
  }

  private record SiblingPatternCase(String name, boolean withExactSpan) {
  }

  private record SiblingPatternFixture(AstResult result, SyntaxNode pattern, SyntaxNode siblingAccess, SyntaxNode nestedCapture) {
  }
}
