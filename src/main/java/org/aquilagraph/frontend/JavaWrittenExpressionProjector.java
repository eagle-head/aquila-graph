package org.aquilagraph.frontend;

import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.DecimalMinimumOperand;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.WrittenExpressionConstants;

/** Shared written-expression emission with explicit observed anchors and derived operand facts. */
final class JavaWrittenExpressionProjector {
  private final SemanticResolver semantics;
  private final ParsedTreeInventory inventory;
  private final JavaConstantExpression constants;

  JavaWrittenExpressionProjector(SemanticResolver semantics, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
    constants = new JavaConstantExpression(semantics);
  }

  SourceProjectionResult<SyntaxNode> project(ParsedTreeInventory.Expression expression, SourceFile source, SyntaxStructuralPath path,
      boolean annotation, CompilerExpressionAnchor compilerAnchor, AnchorProducer anchors, DerivedConsumer derived,
      DiagnosticEmissionCollector diagnostics) {
    if (!(expression.capture() instanceof JavaWrittenExpression.Recovered recovered)) {
      return failure(source, path, diagnostics, "The written region cannot be correlated with complete compiler operand ranges");
    }
    var evaluated = evaluate(recovered.root(), false);
    var compilerType = semantics.attributedMirror(expression.compilerAnchor());
    if (evaluated.isEmpty() || compilerType.isEmpty() || !semantics.sameType(evaluated.orElseThrow().type(), compilerType.orElseThrow())) {
      return failure(source, path, diagnostics, "The written expression has no type derivation corroborated by its compiler region");
    }
    var result = evaluated.orElseThrow();
    var observedValue = constants.value(expression.compilerAnchor());
    var observedStatus = constants.constantStatus(expression.compilerAnchor());
    if (observedStatus.isEmpty() || result.constantStatus().isEmpty()) {
      return failure(source, path, diagnostics,
          "The written expression has no required constant classification corroborated by its compiler region");
    }
    if (!observedValue.equals(result.constant()) || !observedStatus.equals(result.constantStatus())) {
      return failure(source, path, diagnostics, "The written expression value contradicts its observed compiler region");
    }
    var context = new Emission(expression, source, annotation, compilerAnchor, anchors, derived,
        inventory.expressionObservations(expression, semantics));
    return emit(result, path, List.of(), context);
  }

  private Optional<Evaluated> evaluate(JavaWrittenExpression.Node node, boolean immediateMinus) {
    return switch (node) {
      case JavaWrittenExpression.Anchor anchor -> semantics.attributedMirror(anchor.path()).map(type -> new Evaluated(node, type,
          constants.value(anchor.path()), constants.constantStatus(anchor.path()), Optional.empty(), List.of()));
      case JavaWrittenExpression.Literal literal -> JavaSourceLiteral.parse(literal, immediateMinus).flatMap(value -> {
        var type = value.kind() == TypeKind.DECLARED
            ? semantics.namedType("java.lang.String")
            : Optional.of(value.kind() == TypeKind.NULL ? semantics.nullType() : semantics.primitiveType(value.kind()));
        return type.map(mirror -> new Evaluated(node, mirror, value.constant(),
            value.restrictedDecimal().isPresent() ? Optional.empty() : Optional.of(value.kind() != TypeKind.NULL),
            value.restrictedDecimal(), List.of()));
      });
      case JavaWrittenExpression.Unary unary ->
        evaluate(unary.operand(), unary.operator() == SyntaxOperator.UNARY_MINUS).flatMap(value -> unary(unary, value));
      case JavaWrittenExpression.Binary binary ->
        evaluate(binary.left(), false).flatMap(left -> evaluate(binary.right(), false).flatMap(right -> binary(binary, left, right)));
    };
  }

  private Optional<Evaluated> unary(JavaWrittenExpression.Unary unary, Evaluated operand) {
    var primitive = operand.type().getKind().isPrimitive()
        ? Optional.of(operand.type())
        : semantics.unboxedType(operand.type()).map(TypeMirror.class::cast);
    if (primitive.isEmpty()) {
      return Optional.empty();
    }
    var kind = primitive.orElseThrow().getKind();
    if (unary.operator() == SyntaxOperator.LOGICAL_COMPLEMENT ? kind != TypeKind.BOOLEAN : kind == TypeKind.BOOLEAN) {
      return Optional.empty();
    }
    var promoted = kind == TypeKind.BYTE || kind == TypeKind.CHAR || kind == TypeKind.SHORT ? TypeKind.INT : kind;
    var constant = operand.restrictedDecimal().isPresent()
        ? JavaConstantExpression.runtimeScalar(WrittenExpressionConstants.minimum(operand.restrictedDecimal().orElseThrow()))
        : operand
            .constant().flatMap(JavaConstantExpression::copyScalar).flatMap(value -> WrittenExpressionConstants.unary(unary.operator(),
                value, semantics.typeIdentity(semantics.primitiveType(promoted)).category()))
            .flatMap(JavaConstantExpression::runtimeScalar);
    return Optional.of(new Evaluated(unary, semantics.primitiveType(promoted), constant,
        operand.restrictedDecimal().isPresent() ? Optional.of(true) : operand.constantStatus(), Optional.empty(), List.of(operand)));
  }

  private Optional<Evaluated> binary(JavaWrittenExpression.Binary binary, Evaluated left, Evaluated right) {
    var string = binary.operator() == SyntaxOperator.PLUS && (isString(left.type()) || isString(right.type()));
    var type = string ? semantics.namedType("java.lang.String") : promoted(left.type(), right.type());
    if (type.isEmpty()) {
      return Optional.empty();
    }
    var constant = left.constant().flatMap(JavaConstantExpression::copyScalar)
        .flatMap(first -> right.constant().flatMap(JavaConstantExpression::copyScalar).flatMap(second -> WrittenExpressionConstants
            .additive(binary.operator(), first, second, semantics.typeIdentity(type.orElseThrow()).category())))
        .flatMap(JavaConstantExpression::runtimeScalar);
    var status = left.constantStatus().equals(Optional.of(false)) || right.constantStatus().equals(Optional.of(false))
        ? Optional.of(false)
        : left.constantStatus().isPresent() && right.constantStatus().isPresent() ? Optional.of(true) : Optional.<Boolean>empty();
    return Optional.of(new Evaluated(binary, type.orElseThrow(), constant, status, Optional.empty(), List.of(left, right)));
  }

  private Optional<TypeMirror> promoted(TypeMirror left, TypeMirror right) {
    var first = left.getKind().isPrimitive() ? Optional.of(left) : semantics.unboxedType(left).map(TypeMirror.class::cast);
    var second = right.getKind().isPrimitive() ? Optional.of(right) : semantics.unboxedType(right).map(TypeMirror.class::cast);
    if (first.isEmpty() || second.isEmpty() || first.orElseThrow().getKind() == TypeKind.BOOLEAN
        || second.orElseThrow().getKind() == TypeKind.BOOLEAN) {
      return Optional.empty();
    }
    var kinds = List.of(first.orElseThrow().getKind(), second.orElseThrow().getKind());
    var kind = kinds.contains(TypeKind.DOUBLE)
        ? TypeKind.DOUBLE
        : kinds.contains(TypeKind.FLOAT) ? TypeKind.FLOAT : kinds.contains(TypeKind.LONG) ? TypeKind.LONG : TypeKind.INT;
    return Optional.of(semantics.primitiveType(kind));
  }

  private boolean isString(TypeMirror type) {
    return type.getKind() == TypeKind.DECLARED && "java.lang.String".equals(semantics.typeIdentity(type).erasedName());
  }

  private SourceProjectionResult<SyntaxNode> emit(Evaluated value, SyntaxStructuralPath path, List<SyntaxStructuralPath.Step> relative,
      Emission context) {
    if (value.written() instanceof JavaWrittenExpression.Anchor anchor) {
      return context.anchors().project(anchor.path(), path);
    }
    var children = new ArrayList<SyntaxChild>();
    var roles = value.written() instanceof JavaWrittenExpression.Unary
        ? List.of(SyntaxRole.OPERAND)
        : value.written() instanceof JavaWrittenExpression.Binary
            ? List.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND)
            : List.<SyntaxRole>of();
    for (var index = 0; index < value.children().size(); index++) {
      var role = roles.get(index);
      var child = emit(value.children().get(index), path.child(role, 0), JavaWrittenExpression.child(relative, role), context);
      if (child.failure().isPresent()) {
        return child;
      }
      children.add(new SyntaxChild(role, 0, child.value().orElseThrow()));
    }
    var sourceKind = switch (value.written()) {
      case JavaWrittenExpression.Literal literal -> literal.sourceKind();
      case JavaWrittenExpression.Unary unary -> JavaSourceKind.valueOf(unary.operator().name());
      case JavaWrittenExpression.Binary binary -> JavaSourceKind.valueOf(binary.operator().name());
      case JavaWrittenExpression.Anchor _ -> throw new IllegalStateException("An anchored operand emits through its own producer");
    };
    var kind = value.written() instanceof JavaWrittenExpression.Literal
        ? SyntaxKind.LITERAL
        : value.written() instanceof JavaWrittenExpression.Unary ? SyntaxKind.UNARY : SyntaxKind.BINARY;
    SyntaxValue syntaxValue = switch (value.written()) {
      case JavaWrittenExpression.Literal _ -> new SyntaxValue.Literal(context.annotation()
          ? raw(context.source(), value.written())
          : value.restrictedDecimal().map(DecimalMinimumOperand::magnitude)
              .orElseGet(() -> value.constant().map(String::valueOf).orElse("null")),
          value.restrictedDecimal());
      case JavaWrittenExpression.Unary unary -> new SyntaxValue.Operator(unary.operator());
      case JavaWrittenExpression.Binary binary -> new SyntaxValue.Operator(binary.operator());
      case JavaWrittenExpression.Anchor _ -> throw new IllegalStateException("An anchor preserves its existing syntax value");
    };
    var node = SyntaxNode.source(context.source().sourceFileKey(), path, sourceKind, kind, semantics.declarationType(value.type()),
        syntaxValue, children, Optional.empty(), value.written().origin());
    var expression = context.expression();
    var region = expression.region().orElseThrow();
    var correspondence = new NormalizedExpressionCorrespondence(region, relative, context.compilerAnchor(),
        JavaSourceKind.valueOf(expression.compilerAnchor().getLeaf().getKind().name()), semantics.type(expression.compilerAnchor()),
        value.constant().flatMap(JavaConstantExpression::copyScalar), value.constantStatus(), context.observations());
    var owner = correspondence.writtenOwner();
    var association = new WrittenSourceAssociation(node.nodeId(), node.sourceFileKey(), owner, sourceKind, node.origin(),
        Optional.of(correspondence));
    context.derived().accept(node, new Facts(value.type(), value.constant(), value.constantStatus()), association);
    return SourceProjectionResult.success(node);
  }

  private static String raw(SourceFile source, JavaWrittenExpression.Node node) {
    return source.content().substring(Math.toIntExact(node.origin().startOffset()), Math.toIntExact(node.origin().endOffsetExclusive()));
  }

  private static SourceProjectionResult<SyntaxNode> failure(SourceFile source, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics, String message) {
    return SourceProjectionResult.failure(diagnostics.emit("JavaWrittenExpressionProjector", FrontendDiagnosticFactory
        .capabilityError(source.sourceFileKey() + ':' + path, "frontend.missing-written-expression-evidence", message, Optional.empty())));
  }

  @FunctionalInterface
  interface AnchorProducer {
    SourceProjectionResult<SyntaxNode> project(TreePath path, SyntaxStructuralPath structuralPath);
  }

  @FunctionalInterface
  interface DerivedConsumer {
    void accept(SyntaxNode node, Facts facts, WrittenSourceAssociation association);
  }

  record Facts(TypeMirror type, Optional<Object> constant, Optional<Boolean> constantStatus) {
  }

  private record Evaluated(JavaWrittenExpression.Node written, TypeMirror type, Optional<Object> constant, Optional<Boolean> constantStatus,
      Optional<DecimalMinimumOperand> restrictedDecimal, List<Evaluated> children) {
  }

  private record Emission(ParsedTreeInventory.Expression expression, SourceFile source, boolean annotation,
      CompilerExpressionAnchor compilerAnchor, AnchorProducer anchors, DerivedConsumer derived,
      List<NormalizedExpressionCorrespondence.Observation> observations) {
  }
}
