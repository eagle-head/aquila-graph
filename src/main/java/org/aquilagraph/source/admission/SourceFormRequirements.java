package org.aquilagraph.source.admission;

import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.JavaTypeCategory;

/** Source-owned form and written-role requirements shared by the initial two profile versions. */
final class SourceFormRequirements {
  private SourceFormRequirements() {
  }

  static boolean supports(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case LAMBDA_EXPRESSION -> {
        var lambda = node.value(SyntaxValue.Lambda.class);
        var forms = lambda.parameters();
        var legal = lambda.parenthesized() || (forms.size() == 1 && forms.getFirst().typeForm() == LocalBindingEvidence.TypeForm.IMPLICIT);
        for (var index = 0; index < forms.size(); index++) {
          if (forms.get(index).variableArity()
              && (forms.get(index).typeForm() != LocalBindingEvidence.TypeForm.DECLARED || index != forms.size() - 1)) {
            legal = false;
          }
        }
        yield legal && node.type() instanceof SyntaxType.Resolved target
            && (target.identity().category() == JavaTypeCategory.DECLARED || target.identity().category() == JavaTypeCategory.INTERSECTION)
            && usableLambdaReturnType(lambda.returnType())
            && (forms.isEmpty() || forms.stream().allMatch(parameter -> parameter.typeForm() == forms.getFirst().typeForm()));
      }
      case UNSUPPORTED, STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK -> false;
      case TRY -> node.children(SyntaxRole.RESOURCE).isEmpty() && node.children(SyntaxRole.FINALLY_BLOCK).isEmpty();
      case CASE -> node.value(SyntaxValue.SwitchCase.class).form() == SyntaxValue.SwitchCaseForm.RULE;
      case UPDATE -> isAssignable(node.requiredChild(SyntaxRole.OPERAND));
      case COMPOUND_ASSIGNMENT -> isAssignable(node.requiredChild(SyntaxRole.LOCATION));
      case ENHANCED_FOR_LOOP -> node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
          .filter(binding -> binding.context() == LocalBindingEvidence.Context.ENHANCED_FOR && binding.declarationKey().isPresent())
          .isPresent();
      case TYPE_CAST, OBJECT_CREATION -> node.optionalChild(SyntaxRole.TYPE_OPERAND).isPresent();
      case ARRAY_CREATION -> {
        var array = node.value(SyntaxValue.ArrayCreation.class);
        yield node.optionalChild(SyntaxRole.TYPE_OPERAND).isPresent() == array.writtenType()
            && node.children(SyntaxRole.ANNOTATION).size() == array.dimensionAnnotationCounts().stream().mapToInt(Integer::intValue).sum();
      }
      case LOCAL_DECLARATION -> node.optionalChild(SyntaxRole.DECLARED_TYPE).isPresent() || node.value(SyntaxValue.LocalDeclaration.class)
          .bindingEvidence()
          .filter(binding -> (binding.typeForm() == LocalBindingEvidence.TypeForm.INFERRED
              && (binding.initialized() || binding.context() == LocalBindingEvidence.Context.ENHANCED_FOR
                  || binding.context() == LocalBindingEvidence.Context.LAMBDA))
              || (binding.typeForm() == LocalBindingEvidence.TypeForm.IMPLICIT && binding.context() == LocalBindingEvidence.Context.LAMBDA))
          .isPresent();
      case INVOCATION ->
        !node.value(SyntaxValue.Invocation.class).form().hasWrittenQualifier() || node.optionalChild(SyntaxRole.QUALIFIER).isPresent();
      case FIELD_ACCESS ->
        !hasWrittenQualifier(node.value(SyntaxValue.FieldAccess.class).receiver()) || node.optionalChild(SyntaxRole.QUALIFIER).isPresent();
      case RECEIVER -> switch (node.value(SyntaxValue.Receiver.class).kind()) {
        case QUALIFIED_THIS, QUALIFIED_SUPER -> node.optionalChild(SyntaxRole.QUALIFIER).isPresent();
        default -> true;
      };
      case FIELD_DECLARATION -> node.children(SyntaxRole.INITIALIZER).isEmpty();
      case ENUM_CONSTANT_DECLARATION -> node.children(SyntaxRole.ARGUMENT).isEmpty() && node.children(SyntaxRole.BODY).isEmpty();
      case COMPILATION_UNIT, ANNOTATION, ANNOTATION_ARRAY, ANNOTATION_ARGUMENT, ANNOTATION_ELEMENT_NAME, ANNOTATION_NAME_REFERENCE,
          ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST, TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, PARAMETER_DECLARATION,
          RECEIVER_PARAMETER, RECEIVER_NAME, TYPE_USE, METHOD_DECLARATION, RECORD_COMPONENT_DECLARATION, BLOCK, EXPRESSION_STATEMENT,
          RETURN, THROW, IF, ASSERT, SYNCHRONIZED, WHILE_LOOP, DO_WHILE_LOOP, FOR_LOOP, LABELED_STATEMENT, BREAK, CONTINUE, CATCH,
          SWITCH_EXPRESSION, YIELD, VARIABLE_ACCESS, LITERAL, UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT, ARRAY_ACCESS,
          ARRAY_LENGTH, PARENTHESIZED, CONDITIONAL_EXPRESSION, EMPTY ->
        true;
    };
  }

  private static boolean isAssignable(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case VARIABLE_ACCESS, FIELD_ACCESS, ARRAY_ACCESS -> true;
      case PARENTHESIZED -> isAssignable(node.requiredChild(SyntaxRole.EXPRESSION));
      default -> false;
    };
  }

  private static boolean usableLambdaReturnType(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved && switch (resolved.identity().category()) {
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, VOID, ARRAY, DECLARED, TYPE_VARIABLE, WILDCARD, INTERSECTION -> true;
      case NULL, ERROR, EXECUTABLE, UNION, OTHER -> false;
    };
  }

  private static boolean hasWrittenQualifier(SyntaxValue.FieldReceiver receiver) {
    return switch (receiver) {
      case STATIC_TYPE, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS, SUPER, QUALIFIED_SUPER -> true;
      default -> false;
    };
  }

  static boolean supportsBody(SyntaxNode root) {
    return root.descendantsAndSelf().stream().noneMatch(node -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION);
  }
}
