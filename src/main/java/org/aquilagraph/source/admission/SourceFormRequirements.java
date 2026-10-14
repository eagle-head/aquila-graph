package org.aquilagraph.source.admission;

import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.AstProfile;

/** Source-owned form and written-role requirements qualified by the declared profile. */
final class SourceFormRequirements {
  private SourceFormRequirements() {
  }

  static boolean supports(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case INSTANCE_OF -> node.children(SyntaxRole.TYPE_OPERAND).size() + node.children(SyntaxRole.PATTERN).size() == 1;
      case BINDING_PATTERN -> node.value(SyntaxValue.PatternBinding.class).resolvedAccesses().isPresent() && node
          .requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
          .filter(binding -> binding.context() == LocalBindingEvidence.Context.PATTERN && binding.declarationKey().isPresent()).isPresent();
      case MEMBER_REFERENCE -> {
        var reference = node.value(SyntaxValue.MemberReference.class);
        yield node.type() instanceof SyntaxType.Resolved target && target.structure().isPresent()
            && (target.identity().category() == JavaTypeCategory.DECLARED || target.identity().category() == JavaTypeCategory.INTERSECTION)
            && reference.selectedType() instanceof SyntaxType.Resolved selected
            && (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR
                ? selected.structure().filter(JavaTypeStructure.Array.class::isInstance).isPresent()
                : selected.structure().filter(JavaTypeStructure.Executable.class::isInstance).map(JavaTypeStructure.Executable.class::cast)
                    .filter(SourceFormRequirements::completeSignature).isPresent())
            && node.optionalChild(SyntaxRole.QUALIFIER).map(SyntaxNode::type).filter(SyntaxType.Resolved.class::isInstance)
                .map(SyntaxType.Resolved.class::cast).flatMap(SyntaxType.Resolved::structure).isPresent()
            && (reference.qualifier() != SyntaxValue.ReferenceQualifier.ARRAY_TYPE || node.children(SyntaxRole.TYPE_ARGUMENT).isEmpty())
            && (reference.selection() == SyntaxValue.ReferenceSelection.EXECUTABLE
                ? node.semanticReference().isPresent()
                : reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR
                    || (node.requiredChild(SyntaxRole.QUALIFIER).type() instanceof SyntaxType.Resolved array
                        && array.identity().category() == JavaTypeCategory.ARRAY));
      }
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
      case TRY -> (!node.children(SyntaxRole.RESOURCE).isEmpty() || !node.children(SyntaxRole.CATCH_CLAUSE).isEmpty()
          || !node.children(SyntaxRole.FINALLY_BLOCK).isEmpty())
          && node.children(SyntaxRole.RESOURCE).stream().allMatch(
              resource -> resource.syntaxKind() != SyntaxKind.LOCAL_DECLARATION || resource.value(SyntaxValue.LocalDeclaration.class)
                  .bindingEvidence().filter(binding -> binding.context() == LocalBindingEvidence.Context.RESOURCE && binding.initialized()
                      && binding.declarationKey().isPresent())
                  .isPresent());
      case CASE -> true;
      case UPDATE -> isAssignable(node.requiredChild(SyntaxRole.OPERAND));
      case COMPOUND_ASSIGNMENT -> isAssignable(node.requiredChild(SyntaxRole.LOCATION));
      case ENHANCED_FOR_LOOP -> node.requiredChild(SyntaxRole.PARAMETER).value(SyntaxValue.LocalDeclaration.class).bindingEvidence()
          .filter(binding -> binding.context() == LocalBindingEvidence.Context.ENHANCED_FOR && binding.declarationKey().isPresent())
          .isPresent();
      case CLASS_LITERAL -> node.type() instanceof SyntaxType.Resolved result
          && result.structure().filter(JavaTypeStructure.Declared.class::isInstance).map(JavaTypeStructure.Declared.class::cast)
              .filter(declared -> "type:java.lang.Class".equals(declared.declarationKey()) && declared.arguments().size() == 1).isPresent()
          && node.optionalChild(SyntaxRole.TYPE_OPERAND).map(SyntaxNode::type).filter(SyntaxType.Resolved.class::isInstance)
              .map(SyntaxType.Resolved.class::cast).flatMap(SyntaxType.Resolved::structure).isPresent();
      case TYPE_CAST, OBJECT_CREATION, DECONSTRUCTION_PATTERN -> node.optionalChild(SyntaxRole.TYPE_OPERAND).isPresent();
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
              || (binding.typeForm() == LocalBindingEvidence.TypeForm.INFERRED && binding.context() == LocalBindingEvidence.Context.PATTERN)
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
      case ENUM_CONSTANT_DECLARATION -> node.children(SyntaxRole.ARGUMENT).isEmpty() && node.children(SyntaxRole.BODY).isEmpty()
          && node.children(SyntaxRole.CLASS_BODY).isEmpty() && node.semanticReference().isEmpty();
      case COMPILATION_UNIT, ANNOTATION, ANNOTATION_ARRAY, ANNOTATION_ARGUMENT, ANNOTATION_ELEMENT_NAME, ANNOTATION_NAME_REFERENCE,
          ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST, TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, PARAMETER_DECLARATION,
          RECEIVER_PARAMETER, RECEIVER_NAME, TYPE_USE, METHOD_DECLARATION, RECORD_COMPONENT_DECLARATION, BLOCK, EXPRESSION_STATEMENT,
          RETURN, THROW, IF, ASSERT, SYNCHRONIZED, WHILE_LOOP, DO_WHILE_LOOP, FOR_LOOP, LABELED_STATEMENT, BREAK, CONTINUE, CATCH,
          SWITCH_EXPRESSION, SWITCH_STATEMENT, YIELD, VARIABLE_ACCESS, LITERAL, UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT,
          ARRAY_ACCESS, ARRAY_LENGTH, PARENTHESIZED, CONDITIONAL_EXPRESSION, EMPTY, ANY_PATTERN ->
        true;
    };
  }

  static boolean supports(SyntaxNode node, AstProfile profile) {
    if (profile.supportsEnumInitialization() && node.syntaxKind() == SyntaxKind.ENUM_CONSTANT_DECLARATION) {
      var declaration = node.value(SyntaxValue.VariableDeclaration.class);
      return node.children(SyntaxRole.ARGUMENT).size() == declaration.arguments()
          && node.children(SyntaxRole.CLASS_BODY).size() == (declaration.constantBody() ? 1 : 0);
    }
    if (profile.supportsMemberInitialization()) {
      if (node.syntaxKind() == SyntaxKind.STATIC_INITIALIZER_BLOCK || node.syntaxKind() == SyntaxKind.INSTANCE_INITIALIZER_BLOCK) {
        return true;
      }
      if (node.syntaxKind() == SyntaxKind.FIELD_DECLARATION) {
        return node.children(SyntaxRole.INITIALIZER).size() == (node.value(SyntaxValue.VariableDeclaration.class).initializer() ? 1 : 0);
      }
    }
    return supports(node);
  }

  private static boolean isAssignable(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case VARIABLE_ACCESS, FIELD_ACCESS, ARRAY_ACCESS -> true;
      case PARENTHESIZED -> isAssignable(node.requiredChild(SyntaxRole.EXPRESSION));
      default -> false;
    };
  }

  private static boolean completeSignature(JavaTypeStructure.Executable signature) {
    return signature.erasure().isComplete() && signature.parameters().stream().allMatch(SourceFormRequirements::completeSignatureType)
        && completeSignatureType(signature.returnType())
        && signature.receiver().stream().allMatch(SourceFormRequirements::completeSignatureType)
        && signature.thrownTypes().stream().allMatch(SourceFormRequirements::completeSignatureType);
  }

  private static boolean completeSignatureType(SyntaxType type) {
    return type instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent();
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
    return SourcePatternRequirements.supports(root) && SourceSwitchRequirements.supportsBody(root, false);
  }
}
