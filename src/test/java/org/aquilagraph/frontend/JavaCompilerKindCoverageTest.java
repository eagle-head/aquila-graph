package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.Tree;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureClassification;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.LanguageFeaturePolicy;
import org.aquilagraph.syntax.SyntaxAnalysisInput;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JavaCompilerKindCoverageTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION =
      CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final EnumSet<Tree.Kind> REPRESENTED_KINDS = EnumSet.of(
      Tree.Kind.ANNOTATION_TYPE,
      Tree.Kind.AND,
      Tree.Kind.ARRAY_ACCESS,
      Tree.Kind.ASSIGNMENT,
      Tree.Kind.BITWISE_COMPLEMENT,
      Tree.Kind.BLOCK,
      Tree.Kind.BOOLEAN_LITERAL,
      Tree.Kind.CASE,
      Tree.Kind.CATCH,
      Tree.Kind.CHAR_LITERAL,
      Tree.Kind.CLASS,
      Tree.Kind.CONDITIONAL_AND,
      Tree.Kind.CONDITIONAL_EXPRESSION,
      Tree.Kind.CONDITIONAL_OR,
      Tree.Kind.CONSTANT_CASE_LABEL,
      Tree.Kind.DEFAULT_CASE_LABEL,
      Tree.Kind.DIVIDE,
      Tree.Kind.DOUBLE_LITERAL,
      Tree.Kind.EMPTY_STATEMENT,
      Tree.Kind.ENUM,
      Tree.Kind.EQUAL_TO,
      Tree.Kind.EXPRESSION_STATEMENT,
      Tree.Kind.FLOAT_LITERAL,
      Tree.Kind.GREATER_THAN,
      Tree.Kind.GREATER_THAN_EQUAL,
      Tree.Kind.IDENTIFIER,
      Tree.Kind.IF,
      Tree.Kind.INT_LITERAL,
      Tree.Kind.INTERFACE,
      Tree.Kind.LEFT_SHIFT,
      Tree.Kind.LESS_THAN,
      Tree.Kind.LESS_THAN_EQUAL,
      Tree.Kind.LOGICAL_COMPLEMENT,
      Tree.Kind.LONG_LITERAL,
      Tree.Kind.MEMBER_SELECT,
      Tree.Kind.METHOD,
      Tree.Kind.METHOD_INVOCATION,
      Tree.Kind.MINUS,
      Tree.Kind.MODIFIERS,
      Tree.Kind.MULTIPLY,
      Tree.Kind.NEW_CLASS,
      Tree.Kind.NOT_EQUAL_TO,
      Tree.Kind.NULL_LITERAL,
      Tree.Kind.OR,
      Tree.Kind.PARENTHESIZED,
      Tree.Kind.PLUS,
      Tree.Kind.RECORD,
      Tree.Kind.REMAINDER,
      Tree.Kind.RETURN,
      Tree.Kind.RIGHT_SHIFT,
      Tree.Kind.STRING_LITERAL,
      Tree.Kind.SWITCH_EXPRESSION,
      Tree.Kind.THROW,
      Tree.Kind.TRY,
      Tree.Kind.TYPE_CAST,
      Tree.Kind.TYPE_PARAMETER,
      Tree.Kind.UNARY_MINUS,
      Tree.Kind.UNARY_PLUS,
      Tree.Kind.UNSIGNED_RIGHT_SHIFT,
      Tree.Kind.VARIABLE,
      Tree.Kind.XOR,
      Tree.Kind.YIELD);
  @TempDir Path temporaryDirectory;

  @Test
  void givenJava25Compiler_whenKindCoverageIsCreated_thenEveryRuntimeKindHasExactlyOneReviewedPolicy() {
    // Arrange
    var expectedKinds = EnumSet.allOf(Tree.Kind.class);
    var expectedIrrelevantKinds = EnumSet.of(Tree.Kind.OTHER);
    var expectedDiagnosedKinds = EnumSet.copyOf(expectedKinds);
    expectedDiagnosedKinds.removeAll(REPRESENTED_KINDS);
    expectedDiagnosedKinds.removeAll(expectedIrrelevantKinds);

    // Act
    var coverage = JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION);

    // Assert
    assertEquals(JavaRelease.JAVA_25, coverage.release());
    assertEquals(25, coverage.releaseNumber());
    assertEquals(expectedKinds, kindsIn(coverage));
    assertEquals(REPRESENTED_KINDS, kindsWithPolicy(coverage, LanguageFeaturePolicy.REPRESENTED));
    assertEquals(expectedIrrelevantKinds, kindsWithPolicy(coverage, LanguageFeaturePolicy.IRRELEVANT));
    assertEquals(expectedDiagnosedKinds, kindsWithPolicy(coverage, LanguageFeaturePolicy.DIAGNOSED));
  }

  @Test
  void givenReviewedPolicies_whenCoverageIsInspected_thenEveryEntryRetainsRequiredEvidence() {
    // Arrange
    var coverage = JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION);

    // Act
    var irrelevant = coverage.classifications().stream()
        .filter(classification -> classification.policy() == LanguageFeaturePolicy.IRRELEVANT).toList();
    var diagnosed = coverage.classifications().stream()
        .filter(classification -> classification.policy() == LanguageFeaturePolicy.DIAGNOSED).toList();
    var diagnosticCodes = diagnosed.stream().map(classification -> classification.diagnosticCode().orElseThrow()).toList();

    // Assert
    assertEquals(1, irrelevant.size());
    assertEquals("OTHER", irrelevant.getFirst().feature().identifier());
    assertEquals("Tree.Kind.OTHER is reserved for implementation-defined trees and does not identify Java source syntax",
        irrelevant.getFirst().irrelevanceRationale().orElseThrow());
    assertEquals("https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/Tree.Kind.html#OTHER",
        irrelevant.getFirst().sourceLocator().orElseThrow());
    assertEquals(diagnosticCodes.size(), new HashSet<>(diagnosticCodes).size());
    assertTrue(diagnosed.stream().allMatch(classification -> classification.diagnosticCode().orElseThrow()
        .equals("frontend.unsupported-tree-kind."
            + classification.feature().identifier().toLowerCase(Locale.ROOT).replace('_', '-'))));
  }

  @Test
  void givenRepresentedKinds_whenCompositeSourceIsAnalyzed_thenEachKindHasRetainingEvidence() throws IOException {
    // Arrange
    Files.writeString(temporaryDirectory.resolve("KindEvidence.java"), """
        @interface Marker { }
        interface Contract { }
        enum Choice { ONE }
        record Pair(int value) { }
        final class KindEvidence<T> implements Contract {
          private int field = 1;

          int exercise(int[] values, boolean flag, int value) {
            ;
            boolean booleanLiteral = true;
            char charLiteral = 'c';
            long longLiteral = 1L;
            float floatLiteral = 1.0F;
            double doubleLiteral = 2.0D;
            String stringLiteral = "text";
            Object nullLiteral = null;
            int local = +value;
            int arithmetic = (-value) + (~value) + value * 2 + value / 2 + value % 2 - 1
                + (value << 1) + (value >> 1) + (value >>> 1) + (value & 1) + (value ^ 1) + (value | 1);
            boolean comparison = (!flag) && (value < 2) && (value > -2) && (value <= 2)
                && (value >= -2) && (value == 0) && (value != 1) || flag;
            int selected = values[0];
            local = this.field;
            local = Math.max(local, selected);
            Object created = new Object();
            int conditional = flag ? (int) longLiteral : arithmetic;
            try {
              if (comparison) {
                throw new IllegalArgumentException();
              }
            } catch (IllegalArgumentException exception) {
              local = exception == null ? conditional : local;
            }
            return switch (local) {
              case 0 -> { yield conditional; }
              default -> local;
            };
          }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(temporaryDirectory, JAVA_25_CONFIGURATION);
    var retainedKinds = retainedKindsIn(result);

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")),
        () -> result.diagnostics().toString());
    assertTrue(result.methodSyntax().stream().flatMap(syntax -> syntax.root().descendantsAndSelf().stream())
        .noneMatch(node -> node.syntaxKind() == SyntaxKind.UNSUPPORTED));
    assertTrue(retainedKinds.containsAll(REPRESENTED_KINDS),
        () -> "Missing retaining evidence for " + difference(REPRESENTED_KINDS, retainedKinds));
  }

  @Test
  void givenBoundaryAndFamilyKinds_whenCoverageIsQueried_thenPoliciesMatchTheCurrentSourceAstContract() {
    // Arrange
    var coverage = JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION);

    // Act
    var policies = List.of(
        policyOf(coverage, Tree.Kind.values()[0]),
        policyOf(coverage, Tree.Kind.values()[Tree.Kind.values().length - 1]),
        policyOf(coverage, Tree.Kind.ERRONEOUS),
        policyOf(coverage, Tree.Kind.OTHER),
        policyOf(coverage, Tree.Kind.CLASS),
        policyOf(coverage, Tree.Kind.PARAMETERIZED_TYPE),
        policyOf(coverage, Tree.Kind.IF),
        policyOf(coverage, Tree.Kind.PLUS),
        policyOf(coverage, Tree.Kind.DECONSTRUCTION_PATTERN),
        policyOf(coverage, Tree.Kind.MODULE));

    // Assert
    assertEquals(List.of(
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.IRRELEVANT,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.DIAGNOSED), policies);
  }

  @Test
  void givenMissingKind_whenCoverageIsValidated_thenDeterministicDifferenceIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier().equals("PLUS"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCompilerKindCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Java compiler kind coverage mismatch: missing=[PLUS], extra=[]", exception.getMessage());
  }

  @Test
  void givenDuplicateKind_whenCoverageIsValidated_thenDuplicateIsRejectedBeforeKeyNormalization() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.add(classifications.stream()
        .filter(classification -> classification.feature().identifier().equals("PLUS")).findFirst().orElseThrow());

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCompilerKindCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Duplicate language feature classification: COMPILER_TREE_KIND:PLUS", exception.getMessage());
  }

  @Test
  void givenUnknownKind_whenCoverageIsValidated_thenUnrecognizedIdentifierIsRejected() {
    // Arrange
    var classifications = mutableClassifications();
    classifications.removeIf(classification -> classification.feature().identifier().equals("PLUS"));
    classifications.add(LanguageFeatureClassification.diagnosed(
        new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "FUTURE_TREE_KIND"),
        "frontend.unsupported-tree-kind.future-tree-kind"));

    // Act
    var exception = assertThrows(IllegalArgumentException.class,
        () -> JavaCompilerKindCoverage.fromClassifications(JAVA_25_CONFIGURATION, classifications));

    // Assert
    assertEquals("Unrecognized Java compiler tree kind: FUTURE_TREE_KIND", exception.getMessage());
  }

  @Test
  void givenWildcardOrDefaultKind_whenFeatureIdentityIsCreated_thenCatchAllIdentifierIsRejected() {
    // Arrange
    var invalidIdentifiers = List.of("*", "DEFAULT", "UNKNOWN");

    // Act
    var failures = invalidIdentifiers.stream().map(identifier -> assertThrows(IllegalArgumentException.class,
        () -> new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, identifier))).toList();

    // Assert
    assertEquals(invalidIdentifiers.size(), failures.size());
  }

  @Test
  void givenPolicylessKind_whenClassificationIsCreated_thenAbsentPolicyIsRejected() {
    // Arrange
    var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, "PLUS");

    // Act
    var exception = assertThrows(NullPointerException.class,
        () -> new LanguageFeatureClassification(feature, null, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

    // Assert
    assertEquals("policy", exception.getMessage());
  }

  @Test
  void givenSameConfiguration_whenKindCoverageIsCreatedTwice_thenOrderingAndMetadataAreDeterministic() {
    // Arrange
    var configuration = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);

    // Act
    var first = JavaCompilerKindCoverage.fromConfiguration(configuration);
    var second = JavaCompilerKindCoverage.fromConfiguration(configuration);

    // Assert
    assertEquals(first, second);
    assertEquals(first.toString(), second.toString());
    assertEquals(first.classifications(), second.classifications());
  }

  private static ArrayList<LanguageFeatureClassification> mutableClassifications() {
    return new ArrayList<>(JavaCompilerKindCoverage.fromConfiguration(JAVA_25_CONFIGURATION).classifications());
  }

  private static Set<Tree.Kind> kindsIn(LanguageFeatureCoverage coverage) {
    return coverage.classifications().stream().map(classification -> Tree.Kind.valueOf(classification.feature().identifier()))
        .collect(Collectors.toUnmodifiableSet());
  }

  private static EnumSet<Tree.Kind> kindsWithPolicy(LanguageFeatureCoverage coverage, LanguageFeaturePolicy policy) {
    var kinds = EnumSet.noneOf(Tree.Kind.class);
    coverage.classifications().stream().filter(classification -> classification.policy() == policy)
        .map(classification -> Tree.Kind.valueOf(classification.feature().identifier())).forEach(kinds::add);
    return kinds;
  }

  private static LanguageFeaturePolicy policyOf(LanguageFeatureCoverage coverage, Tree.Kind kind) {
    var feature = new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, kind.name());
    return coverage.classificationOf(feature).orElseThrow().policy();
  }

  private static EnumSet<Tree.Kind> retainedKindsIn(SyntaxAnalysisInput result) {
    var retainedKinds = EnumSet.noneOf(Tree.Kind.class);
    for (var type : result.program().types()) {
      retainedKinds.add(switch (type.kind()) {
        case ANNOTATION -> Tree.Kind.ANNOTATION_TYPE;
        case CLASS -> Tree.Kind.CLASS;
        case ENUM -> Tree.Kind.ENUM;
        case INTERFACE -> Tree.Kind.INTERFACE;
        case RECORD -> Tree.Kind.RECORD;
      });
      if (!type.modifiers().isEmpty()) {
        retainedKinds.add(Tree.Kind.MODIFIERS);
      }
      if (!type.typeParameters().isEmpty()) {
        retainedKinds.add(Tree.Kind.TYPE_PARAMETER);
      }
      if (!type.fields().isEmpty()) {
        retainedKinds.add(Tree.Kind.VARIABLE);
      }
      for (var method : type.methods()) {
        retainedKinds.add(Tree.Kind.METHOD);
        if (!method.modifiers().isEmpty()) {
          retainedKinds.add(Tree.Kind.MODIFIERS);
        }
        if (!method.parameters().isEmpty()) {
          retainedKinds.add(Tree.Kind.VARIABLE);
        }
      }
    }
    result.methodSyntax().stream().flatMap(syntax -> syntax.root().descendantsAndSelf().stream())
        .forEach(node -> addSyntaxEvidence(retainedKinds, node.syntaxKind(), node.value(), node.resolvedType()
            .map(identity -> identity.category())));
    return retainedKinds;
  }

  private static void addSyntaxEvidence(EnumSet<Tree.Kind> retainedKinds, SyntaxKind syntaxKind, SyntaxValue value,
      Optional<JavaTypeCategory> typeCategory) {
    switch (syntaxKind) {
      case BLOCK -> retainedKinds.add(Tree.Kind.BLOCK);
      case LOCAL_DECLARATION -> retainedKinds.add(Tree.Kind.VARIABLE);
      case EXPRESSION_STATEMENT -> retainedKinds.add(Tree.Kind.EXPRESSION_STATEMENT);
      case RETURN -> retainedKinds.add(Tree.Kind.RETURN);
      case THROW -> retainedKinds.add(Tree.Kind.THROW);
      case IF -> retainedKinds.add(Tree.Kind.IF);
      case TRY -> retainedKinds.add(Tree.Kind.TRY);
      case CATCH -> retainedKinds.add(Tree.Kind.CATCH);
      case SWITCH_EXPRESSION -> retainedKinds.add(Tree.Kind.SWITCH_EXPRESSION);
      case CASE -> addCaseEvidence(retainedKinds, value);
      case YIELD -> retainedKinds.add(Tree.Kind.YIELD);
      case INVOCATION -> retainedKinds.add(Tree.Kind.METHOD_INVOCATION);
      case OBJECT_CREATION -> retainedKinds.add(Tree.Kind.NEW_CLASS);
      case FIELD_ACCESS, FIELD_LOCATION -> addFieldAccessEvidence(retainedKinds, value);
      case VARIABLE_ACCESS, VARIABLE_LOCATION, RECEIVER -> retainedKinds.add(Tree.Kind.IDENTIFIER);
      case LITERAL -> addLiteralEvidence(retainedKinds, typeCategory.orElseThrow());
      case UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR ->
        retainedKinds.add(Tree.Kind.valueOf(((SyntaxValue.Operator) value).operator().name()));
      case ASSIGNMENT -> retainedKinds.add(Tree.Kind.ASSIGNMENT);
      case ARRAY_LOCATION, ARRAY_ACCESS -> retainedKinds.add(Tree.Kind.ARRAY_ACCESS);
      case ARRAY_LENGTH -> retainedKinds.add(Tree.Kind.MEMBER_SELECT);
      case PARENTHESIZED -> retainedKinds.add(Tree.Kind.PARENTHESIZED);
      case TYPE_CAST -> retainedKinds.add(Tree.Kind.TYPE_CAST);
      case CONDITIONAL_EXPRESSION -> retainedKinds.add(Tree.Kind.CONDITIONAL_EXPRESSION);
      case EMPTY -> retainedKinds.add(Tree.Kind.EMPTY_STATEMENT);
      case UNSUPPORTED -> {
        // The test rejects unsupported nodes before accepting this evidence set.
      }
    }
  }

  private static void addFieldAccessEvidence(EnumSet<Tree.Kind> retainedKinds, SyntaxValue value) {
    var receiver = ((SyntaxValue.FieldAccess) value).receiver();
    if (receiver == SyntaxValue.FieldReceiver.INSTANCE_IMPLICIT || receiver == SyntaxValue.FieldReceiver.STATIC_UNQUALIFIED) {
      retainedKinds.add(Tree.Kind.IDENTIFIER);
    } else {
      retainedKinds.add(Tree.Kind.MEMBER_SELECT);
    }
  }

  private static void addLiteralEvidence(EnumSet<Tree.Kind> retainedKinds, JavaTypeCategory typeCategory) {
    retainedKinds.add(switch (typeCategory) {
      case BOOLEAN -> Tree.Kind.BOOLEAN_LITERAL;
      case CHAR -> Tree.Kind.CHAR_LITERAL;
      case INT -> Tree.Kind.INT_LITERAL;
      case LONG -> Tree.Kind.LONG_LITERAL;
      case FLOAT -> Tree.Kind.FLOAT_LITERAL;
      case DOUBLE -> Tree.Kind.DOUBLE_LITERAL;
      case NULL -> Tree.Kind.NULL_LITERAL;
      case DECLARED -> Tree.Kind.STRING_LITERAL;
      default -> throw new AssertionError("Unexpected literal type category: " + typeCategory);
    });
  }

  private static void addCaseEvidence(EnumSet<Tree.Kind> retainedKinds, SyntaxValue value) {
    retainedKinds.add(Tree.Kind.CASE);
    var switchCase = (SyntaxValue.SwitchCase) value;
    for (var label : switchCase.labels()) {
      if (label instanceof SyntaxValue.SwitchLabel.Expression) {
        retainedKinds.add(Tree.Kind.CONSTANT_CASE_LABEL);
      } else if (label == SyntaxValue.SwitchLabel.Default.INSTANCE) {
        retainedKinds.add(Tree.Kind.DEFAULT_CASE_LABEL);
      }
    }
  }

  private static Set<Tree.Kind> difference(Set<Tree.Kind> expected, Set<Tree.Kind> actual) {
    var difference = EnumSet.copyOf(expected);
    difference.removeAll(actual);
    return difference;
  }
}
