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
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaCompilerKindCoverageTest {
  private static final CompilerConfiguration JAVA_25_CONFIGURATION =
      CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final EnumSet<Tree.Kind> REPRESENTED_KINDS = EnumSet.of(
      Tree.Kind.ANNOTATION,
      Tree.Kind.ANNOTATED_TYPE,
      Tree.Kind.ANNOTATION_TYPE,
      Tree.Kind.AND,
      Tree.Kind.ARRAY_ACCESS,
      Tree.Kind.ARRAY_TYPE,
      Tree.Kind.ASSIGNMENT,
      Tree.Kind.BITWISE_COMPLEMENT,
      Tree.Kind.BLOCK,
      Tree.Kind.COMPILATION_UNIT,
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
      Tree.Kind.EXPORTS,
      Tree.Kind.EXPRESSION_STATEMENT,
      Tree.Kind.EXTENDS_WILDCARD,
      Tree.Kind.FLOAT_LITERAL,
      Tree.Kind.GREATER_THAN,
      Tree.Kind.GREATER_THAN_EQUAL,
      Tree.Kind.IDENTIFIER,
      Tree.Kind.IF,
      Tree.Kind.IMPORT,
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
      Tree.Kind.MODULE,
      Tree.Kind.MULTIPLY,
      Tree.Kind.NEW_ARRAY,
      Tree.Kind.NEW_CLASS,
      Tree.Kind.NOT_EQUAL_TO,
      Tree.Kind.NULL_LITERAL,
      Tree.Kind.PARAMETERIZED_TYPE,
      Tree.Kind.OPENS,
      Tree.Kind.OR,
      Tree.Kind.PACKAGE,
      Tree.Kind.PARENTHESIZED,
      Tree.Kind.PLUS,
      Tree.Kind.PRIMITIVE_TYPE,
      Tree.Kind.PROVIDES,
      Tree.Kind.RECORD,
      Tree.Kind.REMAINDER,
      Tree.Kind.REQUIRES,
      Tree.Kind.RETURN,
      Tree.Kind.RIGHT_SHIFT,
      Tree.Kind.STRING_LITERAL,
      Tree.Kind.SWITCH_EXPRESSION,
      Tree.Kind.SUPER_WILDCARD,
      Tree.Kind.THROW,
      Tree.Kind.TRY,
      Tree.Kind.TYPE_CAST,
      Tree.Kind.TYPE_ANNOTATION,
      Tree.Kind.TYPE_PARAMETER,
      Tree.Kind.UNARY_MINUS,
      Tree.Kind.UNARY_PLUS,
      Tree.Kind.UNSIGNED_RIGHT_SHIFT,
      Tree.Kind.UNBOUNDED_WILDCARD,
      Tree.Kind.USES,
      Tree.Kind.VARIABLE,
      Tree.Kind.XOR,
      Tree.Kind.MULTIPLY_ASSIGNMENT,
      Tree.Kind.DIVIDE_ASSIGNMENT,
      Tree.Kind.REMAINDER_ASSIGNMENT,
      Tree.Kind.PLUS_ASSIGNMENT,
      Tree.Kind.MINUS_ASSIGNMENT,
      Tree.Kind.LEFT_SHIFT_ASSIGNMENT,
      Tree.Kind.RIGHT_SHIFT_ASSIGNMENT,
      Tree.Kind.UNSIGNED_RIGHT_SHIFT_ASSIGNMENT,
      Tree.Kind.AND_ASSIGNMENT,
      Tree.Kind.XOR_ASSIGNMENT,
      Tree.Kind.OR_ASSIGNMENT,
      Tree.Kind.PREFIX_INCREMENT,
      Tree.Kind.PREFIX_DECREMENT,
      Tree.Kind.POSTFIX_INCREMENT,
      Tree.Kind.POSTFIX_DECREMENT,
      Tree.Kind.INTERSECTION_TYPE,
      Tree.Kind.WHILE_LOOP, Tree.Kind.DO_WHILE_LOOP, Tree.Kind.FOR_LOOP, Tree.Kind.ENHANCED_FOR_LOOP,
      Tree.Kind.LABELED_STATEMENT, Tree.Kind.BREAK, Tree.Kind.CONTINUE, Tree.Kind.YIELD, Tree.Kind.ASSERT, Tree.Kind.SYNCHRONIZED);
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
  void givenRepresentedKinds_whenOrdinaryAndModularSourcesAreAnalyzed_thenEachKindHasRetainingEvidence() throws IOException {
    // Arrange
    var ordinaryRoot = Files.createDirectory(temporaryDirectory.resolve("ordinary"));
    Files.writeString(ordinaryRoot.resolve("KindEvidence.java"), """
        package sample;
        import java.util.List;
        @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Mark { }
        @interface Marker { }
        @interface Numbers { int[] value(); }
        interface Contract { }
        enum Choice { ONE }
        record Pair(int value) { }
        @Numbers({1,2}) final class KindEvidence<@Marker T> implements Contract {
          private int field = 1;
          private List<?> any;
          private List<? extends Number> upper;
          private List<? super Number> lower;
          private List<@Mark String> annotated;

          int exercise(int[] values, boolean flag, int value) {
            ;
            assert flag;
            synchronized (this) {}
            boolean booleanLiteral = true;
            char charLiteral = 'c';
            long longLiteral = 1L;
            float floatLiteral = 1.0F;
            double doubleLiteral = 2.0D;
            String stringLiteral = "text";
            Object nullLiteral = null;
            int local = +value;
            value *= 2; value /= 2; value %= 3; value += 2; value -= 1;
            value <<= 1; value >>= 1; value >>>= 1; value &= 7; value ^= 3; value |= 2;
            ++value; --value; value++; value--;
            iteration: while (flag) {
              for (int i = 0; i < value; i++) { if (i == 1) continue iteration; }
              break iteration;
            }
            do { value--; } while (flag);
            for (int element : values) { value += element; }
            Object intersection = (Contract & java.io.Serializable) null;
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
    var modularRoot = Files.createDirectory(temporaryDirectory.resolve("modular"));
    Files.createDirectory(modularRoot.resolve("sample"));
    Files.writeString(modularRoot.resolve("module-info.java"), """
        module sample.module {
          requires static transitive java.sql;
          exports sample;
          opens sample;
          uses java.lang.Runnable;
          provides java.lang.Runnable with sample.Service;
        }
        """);
    Files.writeString(modularRoot.resolve("sample/Service.java"), """
        package sample;
        public class Service implements Runnable {
          public void run() { }
        }
        """);
    var frontend = new JavaFrontend(new JavaSourceLoader());

    // Act
    var result = frontend.analyze(ordinaryRoot, JAVA_25_CONFIGURATION);
    var modularResult = frontend.analyze(modularRoot, JAVA_25_CONFIGURATION);
    var retainedKinds = retainedKindsIn(result);
    retainedKinds.addAll(retainedKindsIn(modularResult));

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().equals("frontend.unsupported-syntax")),
        () -> result.diagnostics().toString());
    assertTrue(modularResult.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")
        || diagnostic.code().equals("frontend.unsupported-module-declaration")),
        () -> modularResult.diagnostics().toString());
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
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.IRRELEVANT,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.REPRESENTED,
        LanguageFeaturePolicy.DIAGNOSED,
        LanguageFeaturePolicy.REPRESENTED), policies);
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
    result.aggregate().orElseThrow().nodes()
        .forEach(node -> retainedKinds.add(Tree.Kind.valueOf(node.sourceKind().name())));
    var typedAnnotationRanges = result.aggregate().orElseThrow().nodes().stream()
        .filter(node -> node.value() instanceof SyntaxValue.Annotation).flatMap(node -> node.origin().stream()).toList();
    if (result.languageFeatureCoverageResult().observations().stream().anyMatch(observation -> observation.classification().feature().identifier()
        .equals("TYPE_ANNOTATION") && observation.origins().stream().anyMatch(typedAnnotationRanges::contains))) {
      retainedKinds.add(Tree.Kind.TYPE_ANNOTATION);
    }
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
    result.methodSyntax().stream().flatMap(syntax -> syntax.root().descendantsAndSelf().stream()).forEach(node -> {
      retainedKinds.add(Tree.Kind.valueOf(node.sourceKind().name()));
      if (node.syntaxKind() == SyntaxKind.CASE) {
        addSwitchLabelEvidence(retainedKinds, node.value(SyntaxValue.SwitchCase.class));
      }
    });
    return retainedKinds;
  }

  private static void addSwitchLabelEvidence(EnumSet<Tree.Kind> retainedKinds, SyntaxValue.SwitchCase switchCase) {
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
