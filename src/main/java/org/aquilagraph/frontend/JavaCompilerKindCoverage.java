package org.aquilagraph.frontend;

import com.sun.source.tree.Tree;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureCoverage;

/** Defines and verifies the closed compiler-tree-kind coverage contract for Java 25. */
final class JavaCompilerKindCoverage {
  private static final String TYPE_DECLARATION_BOUNDARY = "JavaType declaration kind";
  private static final String METHOD_SYNTAX_BOUNDARY = "MethodSyntax source occurrence";
  private static final String NAME_BOUNDARY = "MethodSyntax expression or JavaAstAggregate written type-use occurrence";
  private static final String OPERATOR_BOUNDARY = "MethodSyntax operator occurrence";
  private static final String LITERAL_BOUNDARY = "MethodSyntax literal occurrence and compiler type";
  private static final String SWITCH_LABEL_BOUNDARY = "MethodSyntax switch-label aggregate";
  private static final String TYPE_USE_BOUNDARY = "JavaAstAggregate recursive written type-use occurrence";
  private static final String DIAGNOSTIC_PREFIX = "frontend.unsupported-tree-kind.";
  private static final String TREE_API_ROOT = "https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/tree/";
  private static final String OTHER_SOURCE = TREE_API_ROOT + "Tree.Kind.html#OTHER";

  private JavaCompilerKindCoverage() {
  }

  static LanguageFeatureCoverage fromConfiguration(CompilerConfiguration configuration) {
    return fromClassifications(configuration, classifications());
  }

  static LanguageFeatureCoverage fromClassifications(CompilerConfiguration configuration,
      List<LanguageFeatureClassification> classifications) {
    Objects.requireNonNull(configuration, "configuration");
    var coverage = LanguageFeatureCoverageFactory.fromConfiguration(configuration, classifications);
    var declaredKinds = EnumSet.noneOf(Tree.Kind.class);
    var unrecognizedKinds = new TreeSet<String>();
    for (var classification : coverage.classifications()) {
      var feature = classification.feature();
      if (feature.category() != LanguageFeatureCategory.COMPILER_TREE_KIND) {
        throw new IllegalArgumentException("Java compiler kind coverage contains a non-kind feature: " + feature.identifier());
      }
      try {
        declaredKinds.add(Tree.Kind.valueOf(feature.identifier()));
      } catch (IllegalArgumentException exception) {
        unrecognizedKinds.add(feature.identifier());
      }
    }
    if (!unrecognizedKinds.isEmpty()) {
      throw new IllegalArgumentException("Unrecognized Java compiler tree kind: " + String.join(", ", unrecognizedKinds));
    }
    validateCompleteUniverse(declaredKinds);
    return coverage;
  }

  private static void validateCompleteUniverse(Set<Tree.Kind> declaredKinds) {
    var expectedKinds = EnumSet.allOf(Tree.Kind.class);
    var missingKinds = EnumSet.copyOf(expectedKinds);
    missingKinds.removeAll(declaredKinds);
    var extraKinds = EnumSet.copyOf(declaredKinds);
    extraKinds.removeAll(expectedKinds);
    if (!missingKinds.isEmpty() || !extraKinds.isEmpty()) {
      throw new IllegalArgumentException("Java compiler kind coverage mismatch: missing=" + missingKinds + ", extra=" + extraKinds);
    }
  }

  private static List<LanguageFeatureClassification> classifications() {
    var classifications = new ArrayList<LanguageFeatureClassification>();
    classifications.addAll(unitAndDeclarationClassifications());
    classifications.addAll(statementClassifications());
    classifications.addAll(expressionClassifications());
    classifications.addAll(typePatternAndSpecialClassifications());
    return List.copyOf(classifications);
  }

  private static final String MODULE_DIRECTIVE_BOUNDARY = "JavaAstAggregate module directive";

  private static List<LanguageFeatureClassification> unitAndDeclarationClassifications() {
    return List.of(represented("ANNOTATION_TYPE", TYPE_DECLARATION_BOUNDARY), represented("CLASS", TYPE_DECLARATION_BOUNDARY),
        represented("COMPILATION_UNIT", "JavaAstAggregate source root"), represented("ENUM", TYPE_DECLARATION_BOUNDARY),
        represented("EXPORTS", MODULE_DIRECTIVE_BOUNDARY), represented("IMPORT", "JavaAstAggregate ordered import declaration"),
        represented("INTERFACE", TYPE_DECLARATION_BOUNDARY), represented("METHOD", "JavaMethod declaration aggregate"),
        represented("MODIFIERS", "Java declaration modifier sets"), represented("MODULE", "JavaAstAggregate module declaration"),
        represented("OPENS", MODULE_DIRECTIVE_BOUNDARY), represented("PACKAGE", "JavaAstAggregate package declaration"),
        represented("PROVIDES", MODULE_DIRECTIVE_BOUNDARY), represented("RECORD", TYPE_DECLARATION_BOUNDARY),
        represented("REQUIRES", MODULE_DIRECTIVE_BOUNDARY), represented("TYPE_PARAMETER", "JavaType type-parameter aggregate"),
        represented("USES", MODULE_DIRECTIVE_BOUNDARY),
        represented("VARIABLE", "JavaField, JavaParameter, or MethodSyntax declaration aggregate"));
  }

  private static List<LanguageFeatureClassification> statementClassifications() {
    return List.of(diagnosed("ASSERT"), represented("BLOCK", METHOD_SYNTAX_BOUNDARY), diagnosed("BREAK"),
        represented("CASE", METHOD_SYNTAX_BOUNDARY), represented("CATCH", METHOD_SYNTAX_BOUNDARY), diagnosed("CONTINUE"),
        diagnosed("DO_WHILE_LOOP"), represented("EMPTY_STATEMENT", METHOD_SYNTAX_BOUNDARY), diagnosed("ENHANCED_FOR_LOOP"),
        represented("EXPRESSION_STATEMENT", METHOD_SYNTAX_BOUNDARY), diagnosed("FOR_LOOP"), represented("IF", METHOD_SYNTAX_BOUNDARY),
        diagnosed("LABELED_STATEMENT"), represented("RETURN", METHOD_SYNTAX_BOUNDARY), diagnosed("SWITCH"), diagnosed("SYNCHRONIZED"),
        represented("THROW", METHOD_SYNTAX_BOUNDARY), represented("TRY", METHOD_SYNTAX_BOUNDARY), diagnosed("WHILE_LOOP"),
        represented("YIELD", METHOD_SYNTAX_BOUNDARY));
  }

  private static List<LanguageFeatureClassification> expressionClassifications() {
    return List.of(represented("AND", OPERATOR_BOUNDARY), represented("AND_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("ARRAY_ACCESS", METHOD_SYNTAX_BOUNDARY), represented("ASSIGNMENT", METHOD_SYNTAX_BOUNDARY),
        represented("BITWISE_COMPLEMENT", OPERATOR_BOUNDARY), represented("BOOLEAN_LITERAL", LITERAL_BOUNDARY),
        represented("CHAR_LITERAL", LITERAL_BOUNDARY), represented("CONDITIONAL_AND", OPERATOR_BOUNDARY),
        represented("CONDITIONAL_EXPRESSION", METHOD_SYNTAX_BOUNDARY), represented("CONDITIONAL_OR", OPERATOR_BOUNDARY),
        represented("DIVIDE", OPERATOR_BOUNDARY), represented("DIVIDE_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("DOUBLE_LITERAL", LITERAL_BOUNDARY), represented("EQUAL_TO", OPERATOR_BOUNDARY),
        represented("FLOAT_LITERAL", LITERAL_BOUNDARY), represented("GREATER_THAN", OPERATOR_BOUNDARY),
        represented("GREATER_THAN_EQUAL", OPERATOR_BOUNDARY), represented("IDENTIFIER", NAME_BOUNDARY), diagnosed("INSTANCE_OF"),
        represented("INT_LITERAL", LITERAL_BOUNDARY), diagnosed("LAMBDA_EXPRESSION"), represented("LEFT_SHIFT", OPERATOR_BOUNDARY),
        represented("LEFT_SHIFT_ASSIGNMENT", OPERATOR_BOUNDARY), represented("LESS_THAN", OPERATOR_BOUNDARY),
        represented("LESS_THAN_EQUAL", OPERATOR_BOUNDARY), represented("LOGICAL_COMPLEMENT", OPERATOR_BOUNDARY),
        represented("LONG_LITERAL", LITERAL_BOUNDARY), diagnosed("MEMBER_REFERENCE"), represented("MEMBER_SELECT", NAME_BOUNDARY),
        represented("METHOD_INVOCATION", METHOD_SYNTAX_BOUNDARY), represented("MINUS", OPERATOR_BOUNDARY),
        represented("MINUS_ASSIGNMENT", OPERATOR_BOUNDARY), represented("MULTIPLY", OPERATOR_BOUNDARY),
        represented("MULTIPLY_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("NEW_ARRAY",
            "JavaAstAggregate executable array dimensions, type, annotations and initializers; preserved annotation array values"),
        represented("NEW_CLASS", METHOD_SYNTAX_BOUNDARY), represented("NOT_EQUAL_TO", OPERATOR_BOUNDARY),
        represented("NULL_LITERAL", LITERAL_BOUNDARY), represented("OR", OPERATOR_BOUNDARY),
        represented("OR_ASSIGNMENT", OPERATOR_BOUNDARY), represented("PARENTHESIZED", METHOD_SYNTAX_BOUNDARY),
        represented("PLUS", OPERATOR_BOUNDARY), represented("PLUS_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("POSTFIX_DECREMENT", OPERATOR_BOUNDARY), represented("POSTFIX_INCREMENT", OPERATOR_BOUNDARY),
        represented("PREFIX_DECREMENT", OPERATOR_BOUNDARY), represented("PREFIX_INCREMENT", OPERATOR_BOUNDARY),
        represented("REMAINDER", OPERATOR_BOUNDARY), represented("REMAINDER_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("RIGHT_SHIFT", OPERATOR_BOUNDARY), represented("RIGHT_SHIFT_ASSIGNMENT", OPERATOR_BOUNDARY),
        represented("STRING_LITERAL", LITERAL_BOUNDARY), represented("SWITCH_EXPRESSION", METHOD_SYNTAX_BOUNDARY),
        represented("TYPE_CAST", METHOD_SYNTAX_BOUNDARY), represented("UNARY_MINUS", OPERATOR_BOUNDARY),
        represented("UNARY_PLUS", OPERATOR_BOUNDARY), represented("UNSIGNED_RIGHT_SHIFT", OPERATOR_BOUNDARY),
        represented("UNSIGNED_RIGHT_SHIFT_ASSIGNMENT", OPERATOR_BOUNDARY), represented("XOR", OPERATOR_BOUNDARY),
        represented("XOR_ASSIGNMENT", OPERATOR_BOUNDARY));
  }

  private static List<LanguageFeatureClassification> typePatternAndSpecialClassifications() {
    return List.of(represented("ANNOTATED_TYPE", TYPE_USE_BOUNDARY),
        represented("ANNOTATION", "JavaAstAggregate written annotation occurrence"), diagnosed("ANY_PATTERN"),
        represented("ARRAY_TYPE", TYPE_USE_BOUNDARY), diagnosed("BINDING_PATTERN"),
        represented("CONSTANT_CASE_LABEL", SWITCH_LABEL_BOUNDARY), diagnosed("DECONSTRUCTION_PATTERN"),
        represented("DEFAULT_CASE_LABEL", SWITCH_LABEL_BOUNDARY), diagnosed("ERRONEOUS"),
        represented("EXTENDS_WILDCARD", TYPE_USE_BOUNDARY), represented("INTERSECTION_TYPE", TYPE_USE_BOUNDARY),
        irrelevant("OTHER", "Tree.Kind.OTHER is reserved for implementation-defined trees and does not identify Java source syntax",
            OTHER_SOURCE),
        represented("PARAMETERIZED_TYPE", TYPE_USE_BOUNDARY), diagnosed("PATTERN_CASE_LABEL"),
        represented("PRIMITIVE_TYPE", TYPE_USE_BOUNDARY), represented("SUPER_WILDCARD", TYPE_USE_BOUNDARY),
        represented("TYPE_ANNOTATION", "JavaAstAggregate written annotation occurrence"),
        represented("UNBOUNDED_WILDCARD", TYPE_USE_BOUNDARY), diagnosed("UNION_TYPE"));
  }

  private static LanguageFeatureClassification represented(String identifier, String boundary) {
    return LanguageFeatureClassification.represented(feature(identifier), boundary);
  }

  private static LanguageFeatureClassification irrelevant(String identifier, String rationale, String sourceLocator) {
    return LanguageFeatureClassification.irrelevant(feature(identifier), rationale, sourceLocator);
  }

  private static LanguageFeatureClassification diagnosed(String identifier) {
    var codeSuffix = identifier.toLowerCase(Locale.ROOT).replace('_', '-');
    return LanguageFeatureClassification.diagnosed(feature(identifier), DIAGNOSTIC_PREFIX + codeSuffix);
  }

  private static LanguageFeature feature(String identifier) {
    return new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, identifier);
  }
}
