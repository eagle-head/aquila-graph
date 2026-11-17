package org.aquilagraph.source.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.function.Function;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;

final class MethodDecisionFixtures {
  static final String METHOD = "method";
  static final String SOURCE = "source";
  static final String MEMBER = "selected-member";
  static final String SOURCE_FIELD = "owner#field:field";
  static final SyntaxStructuralPath BODY_PATH = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.BODY, 0);
  static final JavaTypeIdentity INT = type(JavaTypeCategory.INT, "int");
  static final JavaTypeIdentity LONG = type(JavaTypeCategory.LONG, "long");
  static final JavaTypeIdentity BOOLEAN = type(JavaTypeCategory.BOOLEAN, "boolean");
  static final JavaTypeIdentity STRING = type(JavaTypeCategory.DECLARED, "java.lang.String");
  static final JavaTypeIdentity OBJECT = type(JavaTypeCategory.DECLARED, "java.lang.Object");
  static final JavaTypeIdentity OWNER = type(JavaTypeCategory.DECLARED, "Owner");

  private MethodDecisionFixtures() {
  }

  static AggregateFixture builder() {
    var content = "class Source {}";
    var source = new SourceFile(SOURCE, capturedPath(Path.of("Source.java")), "digest", content);
    var snapshot = new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "snapshot", List.of(source));
    var root = SyntaxNode.source(SOURCE, SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(), Optional.empty(),
        new SourceSpan(SOURCE, 0, content.length()));
    return AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution(SOURCE, root, List.of(root), List.of(), List.of()));
  }

  static AggregateFixture builderWithField(Set<JavaDeclarationModifier> modifiers, boolean initializer,
      JavaTypeIdentity declaredType) {
    var content = "class Owner { static int field; }";
    var source = new SourceFile(SOURCE, capturedPath(Path.of("Source.java")), "digest", content);
    var snapshot = new InputSnapshot(capturedAbsolute(Path.of("sources")), "SHA-256", "snapshot", List.of(source));
    var typePath = SyntaxStructuralPath.sourceFileRoot().child(SyntaxRole.TYPE_DECLARATION, 0);
    var fieldPath = typePath.child(SyntaxRole.MEMBER, 0);
    var typeUse = SyntaxNode.source(SOURCE, fieldPath.child(SyntaxRole.DECLARED_TYPE, 0), JavaSourceKind.PRIMITIVE_TYPE, SyntaxKind.TYPE_USE,
        new SyntaxType.Resolved(declaredType, new JavaTypeStructure.Primitive(declaredType.category())),
        new SyntaxValue.PrimitiveType(declaredType.category()), List.of(), Optional.empty(),
        new SourceSpan(SOURCE, 20, 23));
    var fieldValue = new SyntaxValue.VariableDeclaration("field", "owner", SOURCE_FIELD, SyntaxValue.VariableDeclaration.Kind.FIELD,
        modifiers, modifiers, declaredType, 0, true, initializer, false, 0, false, List.of());
    var field = SyntaxNode.source(SOURCE, fieldPath, JavaSourceKind.VARIABLE, SyntaxKind.FIELD_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        fieldValue, List.of(new SyntaxChild(SyntaxRole.DECLARED_TYPE, 0, typeUse)), Optional.empty(), new SourceSpan(SOURCE, 14, 31));
    var typeValue = new SyntaxValue.TypeDeclaration("Owner", "owner", Set.of(), Set.of(),
        new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 1));
    var typeNode = SyntaxNode.source(SOURCE, typePath, JavaSourceKind.CLASS, SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE,
        typeValue, List.of(new SyntaxChild(SyntaxRole.MEMBER, 0, field)), Optional.empty(), new SourceSpan(SOURCE, 0, content.length()));
    var root = SyntaxNode.source(SOURCE, SyntaxStructuralPath.sourceFileRoot(), JavaSourceKind.COMPILATION_UNIT,
        SyntaxKind.COMPILATION_UNIT, SyntaxType.NotApplicable.INSTANCE,
        new SyntaxValue.CompilationUnit(SyntaxValue.CompilationUnitForm.ORDINARY, SyntaxValue.CompilationUnitRange.LEGACY_PROFILE), List.of(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, 0, typeNode)),
        Optional.empty(), new SourceSpan(SOURCE, 0, content.length()));
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = List.of(new SyntaxSemanticEdge(typeNode.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference(ReferenceKind.TYPE, "owner"))),
        new SyntaxSemanticEdge(field.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
            new SyntaxSemanticTarget.Declaration(reference(ReferenceKind.FIELD, SOURCE_FIELD))));
    return AggregateFixture.builder(snapshot).add(new SyntaxSourceContribution(SOURCE, root, nodes, structural, semantic));
  }

  static SyntaxNode expressionBody(Function<SyntaxStructuralPath, SyntaxNode> expression) {
    var statementPath = BODY_PATH.child(SyntaxRole.STATEMENT, 0);
    var value = expression.apply(statementPath.child(SyntaxRole.EXPRESSION, 0));
    var statement = node(statementPath, JavaSourceKind.EXPRESSION_STATEMENT, SyntaxKind.EXPRESSION_STATEMENT,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, value)),
        Optional.empty());
    return block(BODY_PATH, List.of(statement));
  }

  static SyntaxNode returnedBody(SyntaxType expressionType) {
    var returnPath = BODY_PATH.child(SyntaxRole.STATEMENT, 0);
    var expression = literal(returnPath.child(SyntaxRole.EXPRESSION, 0), expressionType);
    var statement = node(returnPath, JavaSourceKind.RETURN, SyntaxKind.RETURN, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, List.of(new SyntaxChild(SyntaxRole.EXPRESSION, 0, expression)), Optional.empty());
    return block(BODY_PATH, List.of(statement));
  }

  static SyntaxNode block(SyntaxStructuralPath path, List<SyntaxNode> statements) {
    var children = new ArrayList<SyntaxChild>();
    for (var index = 0; index < statements.size(); index++) {
      children.add(new SyntaxChild(SyntaxRole.STATEMENT, index, statements.get(index)));
    }
    return node(path, JavaSourceKind.BLOCK, SyntaxKind.BLOCK, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children,
        Optional.empty());
  }

  static SyntaxNode expression(SyntaxNode body) {
    return body.requiredChild(SyntaxRole.STATEMENT).requiredChild(SyntaxRole.EXPRESSION);
  }

  static SyntaxNode literal(SyntaxStructuralPath path, JavaTypeIdentity type) {
    return literal(path, new SyntaxType.Resolved(type));
  }

  static SyntaxNode literal(SyntaxStructuralPath path, SyntaxType type) {
    var sourceKind = type instanceof SyntaxType.Resolved resolved && resolved.identity().equals(STRING) ? JavaSourceKind.STRING_LITERAL
        : type instanceof SyntaxType.Resolved resolved && resolved.identity().equals(BOOLEAN) ? JavaSourceKind.BOOLEAN_LITERAL
            : JavaSourceKind.INT_LITERAL;
    return node(path, sourceKind, SyntaxKind.LITERAL, type, new SyntaxValue.Literal("value"), List.of(), Optional.empty());
  }

  static SyntaxNode binary(SyntaxStructuralPath path, SyntaxOperator operator, JavaTypeIdentity result, JavaTypeIdentity left,
      JavaTypeIdentity right) {
    var leftNode = literal(path.child(SyntaxRole.LEFT_OPERAND, 0), left);
    var rightNode = literal(path.child(SyntaxRole.RIGHT_OPERAND, 0), right);
    return node(path, operator == SyntaxOperator.PLUS ? JavaSourceKind.PLUS : JavaSourceKind.MINUS, SyntaxKind.BINARY,
        new SyntaxType.Resolved(result), new SyntaxValue.Operator(operator),
        List.of(new SyntaxChild(SyntaxRole.LEFT_OPERAND, 0, leftNode), new SyntaxChild(SyntaxRole.RIGHT_OPERAND, 0, rightNode)),
        Optional.empty());
  }

  static SyntaxNode invocation(SyntaxStructuralPath path, InvocationForm form, JavaTypeReference owner, List<JavaTypeIdentity> arguments) {
    var children = new ArrayList<SyntaxChild>();
    if (form.hasReceiverExpression()) {
      children.add(new SyntaxChild(SyntaxRole.RECEIVER, 0, literal(path.child(SyntaxRole.RECEIVER, 0), OBJECT)));
    }
    for (var index = 0; index < arguments.size(); index++) {
      children.add(new SyntaxChild(SyntaxRole.ARGUMENT, index, literal(path.child(SyntaxRole.ARGUMENT, index), arguments.get(index))));
    }
    var semantics = new InvocationSemantics(form == InvocationForm.STATIC_UNQUALIFIED || form == InvocationForm.STATIC_TYPE
        || form == InvocationForm.STATIC_EXPRESSION ? InvocationMode.STATIC : InvocationMode.VIRTUAL, owner, form,
        InvocationSelection.NAMED_EXECUTABLE);
    return node(path, JavaSourceKind.METHOD_INVOCATION, SyntaxKind.INVOCATION, new SyntaxType.Resolved(INT),
        new SyntaxValue.Invocation("call", form, Optional.of(semantics)), children, Optional.of(reference(ReferenceKind.METHOD, MEMBER)));
  }

  static SyntaxNode creation(SyntaxStructuralPath path, JavaTypeReference owner, JavaTypeIdentity result) {
    var semantics = new InvocationSemantics(InvocationMode.CONSTRUCTOR, owner, InvocationForm.OBJECT_CREATION,
        InvocationSelection.NAMED_EXECUTABLE);
    return node(path, JavaSourceKind.NEW_CLASS, SyntaxKind.OBJECT_CREATION, new SyntaxType.Resolved(result),
        new SyntaxValue.Invocation("Owner", InvocationForm.OBJECT_CREATION, Optional.of(semantics)), List.of(),
        Optional.of(reference(ReferenceKind.CONSTRUCTOR, MEMBER)));
  }

  static SyntaxNode field(SyntaxStructuralPath path, SyntaxKind kind, SyntaxValue.FieldReceiver receiver, ReferenceKind referenceKind) {
    var children = receiver.hasReceiverExpression()
        ? List.of(new SyntaxChild(SyntaxRole.RECEIVER, 0, literal(path.child(SyntaxRole.RECEIVER, 0), OBJECT))) : List.<SyntaxChild>of();
    return node(path, JavaSourceKind.IDENTIFIER, kind, new SyntaxType.Resolved(INT), new SyntaxValue.FieldAccess("field", receiver),
        children, Optional.of(reference(referenceKind, MEMBER)));
  }

  static SyntaxNode node(SyntaxStructuralPath path, JavaSourceKind sourceKind, SyntaxKind kind, SyntaxType type, SyntaxValue value,
      List<SyntaxChild> children, Optional<SemanticReference> reference) {
    return SyntaxNode.withoutDirectSource(SOURCE, path, sourceKind, kind, type, value, children, reference);
  }

  static ConversionEvidence use(SyntaxNode consumer, SyntaxRole role, int position, JavaTypeIdentity target) {
    return use(METHOD, consumer.nodeId(), role, position, consumer.children(role).get(position).resolvedType().orElseThrow(), target);
  }

  static ConversionEvidence use(String method, String consumer, SyntaxRole role, int position, JavaTypeIdentity source,
      JavaTypeIdentity target) {
    return new ConversionEvidence(method, consumer, role, position, source, target, Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty());
  }

  static InvocationApplicability fixed(SyntaxNode invocation, int count) {
    return new InvocationApplicability(METHOD, invocation.nodeId(), InvocationApplicabilityMode.FIXED, count, Optional.empty(), Optional.empty(),
        Optional.empty());
  }

  static InvocationApplicability variable(SyntaxNode invocation, int count, JavaTypeIdentity element) {
    var array = new JavaTypeIdentity(JavaTypeCategory.ARRAY, element.displayName() + "[]", element.erasedName() + "[]");
    var erased = new JavaTypeIdentity(JavaTypeCategory.ARRAY, element.erasedName() + "[]", element.erasedName() + "[]");
    return new InvocationApplicability(METHOD, invocation.nodeId(), InvocationApplicabilityMode.VARIABLE, count, Optional.of(array),
        Optional.of(erased), Optional.of(element));
  }

  static StaticMemberEvidence access(SyntaxNode node, StaticMemberKind kind, boolean constant, Optional<String> ownerKey) {
    return StaticMemberEvidence.known(METHOD, node.nodeId(), MEMBER, kind, new JavaTypeReference(OWNER, ownerKey), constant, Optional.empty());
  }

  static JavaTypeReference owner(Optional<String> key) {
    return new JavaTypeReference(OWNER, key);
  }

  static SemanticReference reference(ReferenceKind kind, String key) {
    return new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of(), Optional.empty());
  }

  static JavaTypeIdentity type(JavaTypeCategory category, String name) {
    return new JavaTypeIdentity(category, name, name);
  }
}
