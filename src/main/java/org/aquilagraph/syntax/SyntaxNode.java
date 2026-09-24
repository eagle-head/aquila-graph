package org.aquilagraph.syntax;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeIdentity;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceSpan;

/** Immutable, compiler-neutral facts for one durable Java source occurrence. */
public final class SyntaxNode {
  private static final int SINGLE_CHILD_COUNT = 1;
  private static final JavaTypeStructure IMPLICIT_OBJECT_BOUND = new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(),
      List.of());

  private final SyntaxNodeKey key;
  private final String sourceOwner;
  private final SyntaxStructuralPath path;
  private final JavaSourceKind exactKind;
  private final SyntaxKind kind;
  private final SyntaxType syntaxType;
  private final SyntaxValue syntaxValue;
  private final List<SyntaxChild> childNodes;
  private final Optional<SemanticReference> reference;
  private final Optional<SourceSpan> sourceOrigin;

  private SyntaxNode(String sourceFileKey, SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind, SyntaxKind syntaxKind,
      SyntaxType type, SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> semanticReference,
      Optional<SourceSpan> origin) {
    sourceOwner = requireText(sourceFileKey, "Source-file key");
    path = Objects.requireNonNull(structuralPath, "structuralPath");
    exactKind = Objects.requireNonNull(sourceKind, "sourceKind");
    key = SyntaxNodeKey.from(sourceOwner, path, exactKind);
    kind = Objects.requireNonNull(syntaxKind, "syntaxKind");
    syntaxType = Objects.requireNonNull(type, "type");
    syntaxValue = Objects.requireNonNull(value, "value");
    childNodes = List.copyOf(Objects.requireNonNull(children, "children"));
    reference = Objects.requireNonNull(semanticReference, "semanticReference");
    sourceOrigin = Objects.requireNonNull(origin, "origin");
    validateSourceKind(syntaxKind, sourceKind);
    validateSourceOwner(sourceOwner, sourceOrigin);
    validateType(syntaxKind, type, value);
    validateReference(syntaxKind, semanticReference);
    validateChildPositions(childNodes);
    validateShape(syntaxKind, sourceKind, value, childNodes);
    validateBoundSourceRanges(syntaxKind, sourceKind, sourceOrigin, childNodes);
    if (syntaxKind == SyntaxKind.TYPE_USE) {
      validateTypeUseAttribution(path, sourceKind, type, value, semanticReference, childNodes);
    }
  }

  /**
   * Creates syntax evidence with one direct source origin.
   *
   * @param sourceFileKey
   *          captured source owner
   * @param structuralPath
   *          root-relative typed path
   * @param sourceKind
   *          exact Java source kind
   * @param syntaxKind
   *          retained syntax category
   * @param type
   *          compiler type outcome
   * @param value
   *          typed scalar syntax facts
   * @param children
   *          role-bearing structural children
   * @param semanticReference
   *          optional declaration reference
   * @param origin
   *          direct source range
   * @return validated syntax evidence
   */
  public static SyntaxNode source(String sourceFileKey, SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind,
      SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children, Optional<SemanticReference> semanticReference,
      SourceSpan origin) {
    return new SyntaxNode(sourceFileKey, structuralPath, sourceKind, syntaxKind, type, value, children, semanticReference,
        Optional.of(Objects.requireNonNull(origin, "origin")));
  }

  /**
   * Creates syntax evidence for a compiler tree that has no direct source range.
   *
   * @param sourceFileKey
   *          captured source owner
   * @param structuralPath
   *          root-relative typed path
   * @param sourceKind
   *          exact Java source kind
   * @param syntaxKind
   *          retained syntax category
   * @param type
   *          compiler type outcome
   * @param value
   *          typed scalar syntax facts
   * @param children
   *          role-bearing structural children
   * @param semanticReference
   *          optional declaration reference
   * @return validated syntax evidence
   */
  public static SyntaxNode withoutDirectSource(String sourceFileKey, SyntaxStructuralPath structuralPath, JavaSourceKind sourceKind,
      SyntaxKind syntaxKind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> semanticReference) {
    return new SyntaxNode(sourceFileKey, structuralPath, sourceKind, syntaxKind, type, value, children, semanticReference,
        Optional.empty());
  }

  /**
   * Returns the stable syntax-evidence identifier.
   *
   * @return stable syntax identity
   */
  public String nodeId() {
    return key.value();
  }

  /**
   * Returns the typed stable occurrence key.
   *
   * @return stable occurrence key
   */
  public SyntaxNodeKey nodeKey() {
    return key;
  }

  /**
   * Returns the captured source owner.
   *
   * @return source-file key
   */
  public String sourceFileKey() {
    return sourceOwner;
  }

  /**
   * Returns the root-relative typed structural path.
   *
   * @return structural path
   */
  public SyntaxStructuralPath structuralPath() {
    return path;
  }

  /**
   * Returns the exact compiler-neutral Java source kind.
   *
   * @return exact source kind
   */
  public JavaSourceKind sourceKind() {
    return exactKind;
  }

  /**
   * Returns the retained syntax category.
   *
   * @return retained syntax category
   */
  public SyntaxKind syntaxKind() {
    return kind;
  }

  /**
   * Returns the compiler type outcome.
   *
   * @return compiler type outcome
   */
  public SyntaxType type() {
    return syntaxType;
  }

  /**
   * Returns the typed scalar syntax facts.
   *
   * @return typed scalar syntax facts
   */
  public SyntaxValue value() {
    return syntaxValue;
  }

  /**
   * Returns structural children. Physical list order is not an evaluation-order contract; callers must select typed roles.
   *
   * @return immutable structural children
   */
  public List<SyntaxChild> children() {
    return childNodes;
  }

  /**
   * Returns the zero-or-one semantic declaration reference owned by this tree.
   *
   * @return optional declaration reference
   */
  public Optional<SemanticReference> semanticReference() {
    return reference;
  }

  /**
   * Returns the zero-or-one direct source range owned by this tree.
   *
   * @return optional direct source range
   */
  public Optional<SourceSpan> origin() {
    return sourceOrigin;
  }

  /**
   * Returns the compiler-resolved type independently of symbol references.
   *
   * @return resolved type when the compiler supplied one
   */
  public Optional<JavaTypeIdentity> resolvedType() {
    return syntaxType instanceof SyntaxType.Resolved resolved ? Optional.of(resolved.identity()) : Optional.empty();
  }

  /**
   * Returns the required resolved declaration reference.
   *
   * @return declaration reference required by this syntax kind
   */
  public SemanticReference requiredReference() {
    return reference.orElseThrow(() -> new IllegalStateException(kind + " requires a semantic reference"));
  }

  /**
   * Returns the single required child in a structural role.
   *
   * @param role
   *          structural role to select
   * @return the single child in that role
   */
  public SyntaxNode requiredChild(SyntaxRole role) {
    var matches = children(role);
    if (matches.size() != SINGLE_CHILD_COUNT) {
      throw new IllegalStateException(kind + " requires exactly one " + role + " child");
    }
    return matches.getFirst();
  }

  /**
   * Returns the optional child in a structural role.
   *
   * @param role
   *          structural role to select
   * @return optional child in that role
   */
  public Optional<SyntaxNode> optionalChild(SyntaxRole role) {
    var matches = children(role);
    if (matches.size() > SINGLE_CHILD_COUNT) {
      throw new IllegalStateException(kind + " permits at most one " + role + " child");
    }
    return matches.stream().findFirst();
  }

  /**
   * Returns children in one structural role and their role-local source order.
   *
   * @param role
   *          structural role to select
   * @return immutable children in role-local order
   */
  public List<SyntaxNode> children(SyntaxRole role) {
    Objects.requireNonNull(role, "role");
    return childNodes.stream().filter(child -> child.role() == role).map(SyntaxChild::node).toList();
  }

  /**
   * Returns the canonical occurrence identity projection.
   *
   * @return exact node tuple
   */
  public SyntaxNodeTuple nodeTuple() {
    return new SyntaxNodeTuple(nodeId(), sourceOwner, path, exactKind);
  }

  /**
   * Returns the direct range projection when this occurrence has source evidence.
   *
   * @return optional exact range tuple
   */
  public Optional<SyntaxRangeTuple> rangeTuple() {
    return sourceOrigin.map(range -> new SyntaxRangeTuple(nodeId(), range));
  }

  /**
   * Returns immutable structural edge tuples in the stored role/position order.
   *
   * @return exact structural tuples
   */
  public List<SyntaxStructuralEdge> structuralEdges() {
    return childNodes.stream().map(child -> new SyntaxStructuralEdge(nodeId(), child.role(), child.position(), child.node().nodeId()))
        .toList();
  }

  /**
   * Returns the current declaration-reference edge as a typed semantic tuple.
   *
   * @return zero-or-one exact semantic tuples
   */
  public List<SyntaxSemanticEdge> semanticEdges() {
    return reference.stream().map(target -> new SyntaxSemanticEdge(nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(target))).toList();
  }

  /**
   * Returns the typed value required by a lowering operation.
   *
   * @param <T>
   *          requested syntax-value type
   * @param valueType
   *          requested syntax-value class
   * @return this node's value cast to the requested type
   */
  public <T extends SyntaxValue> T value(Class<T> valueType) {
    Objects.requireNonNull(valueType, "valueType");
    if (!valueType.isInstance(syntaxValue)) {
      throw new IllegalStateException(kind + " does not carry " + valueType.getSimpleName());
    }
    return valueType.cast(syntaxValue);
  }

  /**
   * Traverses this node and descendants in structural preorder while validating unique ownership.
   *
   * @return immutable structural-preorder traversal
   */
  public List<SyntaxNode> descendantsAndSelf() {
    var result = new ArrayList<SyntaxNode>();
    collect(this, result, new HashSet<>());
    return List.copyOf(result);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SyntaxNode that)) {
      return false;
    }
    return key.equals(that.key) && sourceOwner.equals(that.sourceOwner) && path.equals(that.path) && exactKind == that.exactKind
        && kind == that.kind && syntaxType.equals(that.syntaxType) && syntaxValue.equals(that.syntaxValue)
        && childNodes.equals(that.childNodes) && reference.equals(that.reference) && sourceOrigin.equals(that.sourceOrigin);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, sourceOwner, path, exactKind, kind, syntaxType, syntaxValue, childNodes, reference, sourceOrigin);
  }

  @Override
  public String toString() {
    return "SyntaxNode[nodeKey=" + key + ", sourceFileKey=" + sourceOwner + ", structuralPath=" + path + ", sourceKind=" + exactKind
        + ", syntaxKind=" + kind + ", type=" + syntaxType + ", value=" + syntaxValue + ", children=" + childNodes + ", semanticReference="
        + reference + ", origin=" + sourceOrigin + ']';
  }

  private static void collect(SyntaxNode node, List<SyntaxNode> result, Set<String> seen) {
    if (!seen.add(node.nodeId())) {
      throw new IllegalArgumentException("A syntax node may have only one parent: " + node.nodeId());
    }
    result.add(node);
    for (var child : node.children()) {
      collect(child.node(), result, seen);
    }
  }

  private static void validateChildPositions(List<SyntaxChild> children) {
    var nextPositions = new EnumMap<SyntaxRole, Integer>(SyntaxRole.class);
    for (var child : children) {
      Objects.requireNonNull(child, "child");
      var expected = nextPositions.getOrDefault(child.role(), 0);
      if (child.position() != expected) {
        throw new IllegalArgumentException("Syntax child positions must be contiguous within each role");
      }
      nextPositions.put(child.role(), expected + 1);
    }
  }

  private static void validateBoundSourceRanges(SyntaxKind kind, JavaSourceKind sourceKind, Optional<SourceSpan> parentOrigin,
      List<SyntaxChild> children) {
    if (kind == SyntaxKind.TYPE_PARAMETER_DECLARATION) {
      validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.TYPE_BOUND);
    } else if (kind == SyntaxKind.TYPE_USE) {
      switch (sourceKind) {
        case EXTENDS_WILDCARD -> validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.UPPER_BOUND);
        case SUPER_WILDCARD -> validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.LOWER_BOUND);
        case INTERSECTION_TYPE -> validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.INTERSECTION_BOUND);
        case UNION_TYPE -> validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.UNION_ALTERNATIVE);
        default -> {
        }
      }
    }
  }

  private static void validateWrittenComponentRanges(Optional<SourceSpan> parentOrigin, List<SyntaxChild> children, SyntaxRole role) {
    if (parentOrigin.isEmpty()) {
      return;
    }
    var parent = parentOrigin.orElseThrow();
    var previousEnd = parent.startOffset();
    for (var child : children) {
      if (child.role() != role) {
        continue;
      }
      var range = requiredWrittenComponentRange(child.node());
      if (!range.sourceFileKey().equals(parent.sourceFileKey()) || range.startOffset() < previousEnd
          || range.endOffsetExclusive() > parent.endOffsetExclusive() || range.length() == 0) {
        throw new IllegalArgumentException("Written type component ranges must be contained and ordered");
      }
      previousEnd = range.endOffsetExclusive();
    }
  }

  private static SourceSpan requiredWrittenComponentRange(SyntaxNode node) {
    return node.origin().orElseThrow(() -> new IllegalArgumentException("A written type component requires a source range"));
  }

  private static void validateSourceKind(SyntaxKind syntaxKind, JavaSourceKind sourceKind) {
    if (!syntaxKind.supports(sourceKind)) {
      throw new IllegalArgumentException(syntaxKind + " cannot represent Java source kind " + sourceKind);
    }
  }

  private static void validateSourceOwner(String sourceFileKey, Optional<SourceSpan> origin) {
    if (origin.isPresent() && !sourceFileKey.equals(origin.orElseThrow().sourceFileKey())) {
      throw new IllegalArgumentException("Syntax source range must belong to the node's source file");
    }
  }

  private static void validateType(SyntaxKind kind, SyntaxType type, SyntaxValue value) {
    if (kind == SyntaxKind.UNSUPPORTED) {
      return;
    }
    if (kind == SyntaxKind.TYPE_USE && value instanceof SyntaxValue.PackageQualifier && type == SyntaxType.NotApplicable.INSTANCE) {
      return;
    }
    if (kind.requiresType() && type == SyntaxType.NotApplicable.INSTANCE) {
      throw new IllegalArgumentException(kind + " requires a compiler type outcome");
    }
    if (!kind.requiresType() && type != SyntaxType.NotApplicable.INSTANCE) {
      throw new IllegalArgumentException(kind + " cannot carry a compiler type outcome");
    }
  }

  private static void validateReference(SyntaxKind kind, Optional<SemanticReference> reference) {
    if (kind == SyntaxKind.UNSUPPORTED) {
      return;
    }
    if (kind == SyntaxKind.TYPE_USE) {
      if (reference.isPresent()
          && !Set.of(ReferenceKind.TYPE, ReferenceKind.TYPE_PARAMETER).contains(reference.orElseThrow().referenceKind())) {
        throw new IllegalArgumentException("TYPE_USE permits only a type or type-parameter declaration reference");
      }
      return;
    }
    if (kind == SyntaxKind.TYPE_CAST) {
      if (reference.isPresent() && reference.orElseThrow().referenceKind() != ReferenceKind.TYPE) {
        throw new IllegalArgumentException("TYPE_CAST permits only a type declaration reference");
      }
      return;
    }
    var expectedKinds = switch (kind) {
      case LOCAL_DECLARATION -> Set.of(ReferenceKind.LOCAL);
      case VARIABLE_ACCESS -> Set.of(ReferenceKind.LOCAL, ReferenceKind.PARAMETER, ReferenceKind.ENUM_CONSTANT);
      case VARIABLE_LOCATION -> Set.of(ReferenceKind.LOCAL, ReferenceKind.PARAMETER);
      case FIELD_ACCESS, FIELD_LOCATION -> Set.of(ReferenceKind.FIELD, ReferenceKind.ENUM_CONSTANT);
      case INVOCATION -> Set.of(ReferenceKind.METHOD, ReferenceKind.CONSTRUCTOR);
      case OBJECT_CREATION -> Set.of(ReferenceKind.CONSTRUCTOR);
      case TYPE_CAST -> throw new IllegalStateException("TYPE_CAST reference validation is handled separately");
      default -> Set.<ReferenceKind>of();
    };
    if (expectedKinds.isEmpty()) {
      if (reference.isPresent()) {
        throw new IllegalArgumentException(kind + " cannot carry a semantic reference");
      }
      return;
    }
    if (reference.isEmpty() || !expectedKinds.contains(reference.orElseThrow().referenceKind())) {
      throw new IllegalArgumentException(kind + " requires a semantic reference of kind " + expectedKinds);
    }
  }

  private static void validateShape(SyntaxKind kind, JavaSourceKind sourceKind, SyntaxValue value, List<SyntaxChild> children) {
    var expectedValue = switch (kind) {
      case COMPILATION_UNIT -> switch (sourceKind) {
        case COMPILATION_UNIT -> SyntaxValue.CompilationUnit.class;
        case PACKAGE -> SyntaxValue.PackageDeclaration.class;
        case IMPORT -> SyntaxValue.ImportDeclaration.class;
        case ANNOTATION -> SyntaxValue.None.class;
        case MODULE -> SyntaxValue.ModuleDeclaration.class;
        case REQUIRES -> SyntaxValue.Requires.class;
        case EXPORTS -> SyntaxValue.Exports.class;
        case OPENS -> SyntaxValue.Opens.class;
        case USES -> SyntaxValue.Uses.class;
        case PROVIDES -> SyntaxValue.Provides.class;
        default -> throw new IllegalStateException("Unexpected compilation-unit source kind");
      };
      case TYPE_DECLARATION -> SyntaxValue.TypeDeclaration.class;
      case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION -> SyntaxValue.VariableDeclaration.class;
      case RECORD_COMPONENT_DECLARATION -> SyntaxValue.RecordComponentDeclaration.class;
      case TYPE_PARAMETER_DECLARATION -> SyntaxValue.TypeParameterDeclaration.class;
      case PARAMETER_DECLARATION -> SyntaxValue.ParameterDeclaration.class;
      case RECEIVER_PARAMETER -> SyntaxValue.ReceiverParameter.class;
      case RECEIVER_NAME -> SyntaxValue.SourceSymbol.class;
      case TYPE_USE -> switch (sourceKind) {
        case PRIMITIVE_TYPE -> SyntaxValue.PrimitiveType.class;
        case IDENTIFIER, MEMBER_SELECT -> SyntaxValue.Symbol.class;
        default -> SyntaxValue.None.class;
      };
      case METHOD_DECLARATION -> SyntaxValue.ExecutableDeclaration.class;
      case LOCAL_DECLARATION -> SyntaxValue.LocalDeclaration.class;
      case VARIABLE_ACCESS, VARIABLE_LOCATION, ARRAY_LENGTH -> SyntaxValue.SourceSymbol.class;
      case FIELD_ACCESS, FIELD_LOCATION -> SyntaxValue.FieldAccess.class;
      case INVOCATION, OBJECT_CREATION -> SyntaxValue.Invocation.class;
      case RECEIVER -> SyntaxValue.Receiver.class;
      case LITERAL -> SyntaxValue.Literal.class;
      case UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR -> SyntaxValue.Operator.class;
      case TYPE_CAST -> SyntaxValue.TypeName.class;
      case CASE -> SyntaxValue.SwitchCase.class;
      case UNSUPPORTED -> SyntaxValue.Unsupported.class;
      default -> SyntaxValue.None.class;
    };
    if (!expectedValue.isInstance(value)) {
      throw new IllegalArgumentException(kind + " requires syntax value " + expectedValue.getSimpleName());
    }

    switch (kind) {
      case COMPILATION_UNIT -> {
        switch (sourceKind) {
          case COMPILATION_UNIT -> {
            var form = SyntaxValue.CompilationUnit.class.cast(value).form();
            var roles = switch (form) {
              case ORDINARY -> Set.of(SyntaxRole.PACKAGE_DECLARATION, SyntaxRole.IMPORT_DECLARATION, SyntaxRole.TYPE_DECLARATION);
              case COMPACT -> Set.of(SyntaxRole.IMPORT_DECLARATION, SyntaxRole.COMPACT_MEMBER);
              case MODULAR -> Set.of(SyntaxRole.IMPORT_DECLARATION, SyntaxRole.MODULE_DECLARATION);
            };
            requireShape(children, roles);
            if (form == SyntaxValue.CompilationUnitForm.ORDINARY) {
              requireCount(children, SyntaxRole.PACKAGE_DECLARATION, 0, 1);
            } else if (form == SyntaxValue.CompilationUnitForm.MODULAR) {
              requireCount(children, SyntaxRole.MODULE_DECLARATION, 1, 1);
            } else if (form == SyntaxValue.CompilationUnitForm.COMPACT && children.stream().noneMatch(
                child -> child.role() == SyntaxRole.COMPACT_MEMBER && child.node().syntaxKind() == SyntaxKind.METHOD_DECLARATION)) {
              throw new IllegalArgumentException("A compact compilation unit requires a written method member");
            }
            for (var child : children) {
              var valid = switch (child.role()) {
                case PACKAGE_DECLARATION ->
                  child.node().sourceKind() == JavaSourceKind.PACKAGE && child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT;
                case IMPORT_DECLARATION ->
                  child.node().sourceKind() == JavaSourceKind.IMPORT && child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT;
                case MODULE_DECLARATION ->
                  child.node().sourceKind() == JavaSourceKind.MODULE && child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT;
                case TYPE_DECLARATION -> child.node().syntaxKind() == SyntaxKind.TYPE_DECLARATION;
                case COMPACT_MEMBER -> Set.of(SyntaxKind.TYPE_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.FIELD_DECLARATION)
                    .contains(child.node().syntaxKind());
                default -> throw new IllegalStateException("Unexpected compilation-unit child role");
              };
              if (!valid) {
                throw new IllegalArgumentException("Compilation-unit child kind does not match its role");
              }
            }
          }
          case PACKAGE -> {
            requireShape(children, Set.of(SyntaxRole.ANNOTATION));
            if (children.stream().anyMatch(child -> child.node().sourceKind() != JavaSourceKind.ANNOTATION
                || child.node().syntaxKind() != SyntaxKind.COMPILATION_UNIT)) {
              throw new IllegalArgumentException("A package annotation child requires an annotation attachment");
            }
          }
          case IMPORT, ANNOTATION, REQUIRES, EXPORTS, OPENS, USES, PROVIDES -> requireShape(children, Set.of());
          case MODULE -> {
            requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.MODULE_DIRECTIVE));
            for (var child : children) {
              var valid = switch (child.role()) {
                case ANNOTATION ->
                  child.node().sourceKind() == JavaSourceKind.ANNOTATION && child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT;
                case MODULE_DIRECTIVE -> child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT && Set
                    .of(JavaSourceKind.REQUIRES, JavaSourceKind.EXPORTS, JavaSourceKind.OPENS, JavaSourceKind.USES, JavaSourceKind.PROVIDES)
                    .contains(child.node().sourceKind());
                default -> throw new IllegalStateException("Unexpected module child role");
              };
              if (!valid) {
                throw new IllegalArgumentException("Module child kind does not match its role");
              }
            }
          }
          default -> throw new IllegalStateException("Unexpected compilation-unit source kind");
        }
      }
      case PARAMETER_DECLARATION -> validateParameterDeclaration(SyntaxValue.ParameterDeclaration.class.cast(value), children);
      case RECEIVER_PARAMETER -> validateReceiverParameter(SyntaxValue.ReceiverParameter.class.cast(value), children);
      case TYPE_DECLARATION -> validateTypeDeclaration(sourceKind, SyntaxValue.TypeDeclaration.class.cast(value), children);
      case FIELD_DECLARATION, ENUM_CONSTANT_DECLARATION ->
        validateVariableDeclaration(kind, SyntaxValue.VariableDeclaration.class.cast(value), children);
      case RECORD_COMPONENT_DECLARATION ->
        validateRecordComponentDeclaration(SyntaxValue.RecordComponentDeclaration.class.cast(value), children);
      case METHOD_DECLARATION -> validateExecutableDeclaration(SyntaxValue.ExecutableDeclaration.class.cast(value), children);
      case BLOCK -> requireShape(children, Set.of(SyntaxRole.STATEMENT));
      case LOCAL_DECLARATION -> requireShape(children, Set.of(SyntaxRole.INITIALIZER), SyntaxRole.INITIALIZER, 0, 1);
      case EXPRESSION_STATEMENT, THROW, PARENTHESIZED, TYPE_CAST ->
        requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 1, 1);
      case RETURN -> requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 0, 1);
      case IF -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.THEN_BRANCH, 1, 1);
        requireCount(children, SyntaxRole.ELSE_BRANCH, 0, 1);
      }
      case TRY -> {
        requireShape(children, Set.of(SyntaxRole.RESOURCE, SyntaxRole.TRY_BLOCK, SyntaxRole.CATCH_CLAUSE, SyntaxRole.FINALLY_BLOCK));
        requireCount(children, SyntaxRole.TRY_BLOCK, 1, 1);
        requireCount(children, SyntaxRole.FINALLY_BLOCK, 0, 1);
      }
      case CATCH -> {
        requireShape(children, Set.of(SyntaxRole.PARAMETER, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.PARAMETER, 1, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
      }
      case SWITCH_EXPRESSION -> {
        requireShape(children, Set.of(SyntaxRole.SELECTOR, SyntaxRole.CASE));
        requireCount(children, SyntaxRole.SELECTOR, 1, 1);
      }
      case CASE -> validateSwitchCase(SyntaxValue.SwitchCase.class.cast(value), children);
      case YIELD -> requireShape(children, Set.of(SyntaxRole.VALUE), SyntaxRole.VALUE, 1, 1);
      case INVOCATION -> validateInvocation(SyntaxValue.Invocation.class.cast(value), children);
      case OBJECT_CREATION -> validateObjectCreation(SyntaxValue.Invocation.class.cast(value), children);
      case FIELD_ACCESS, FIELD_LOCATION -> validateFieldAccess(SyntaxValue.FieldAccess.class.cast(value), children);
      case ARRAY_LENGTH -> requireShape(children, Set.of(SyntaxRole.QUALIFIER), SyntaxRole.QUALIFIER, 1, 1);
      case UNARY -> requireShape(children, Set.of(SyntaxRole.OPERAND), SyntaxRole.OPERAND, 1, 1);
      case BINARY, CONDITIONAL_AND, CONDITIONAL_OR -> {
        requireShape(children, Set.of(SyntaxRole.LEFT_OPERAND, SyntaxRole.RIGHT_OPERAND));
        requireCount(children, SyntaxRole.LEFT_OPERAND, 1, 1);
        requireCount(children, SyntaxRole.RIGHT_OPERAND, 1, 1);
      }
      case ASSIGNMENT -> {
        requireShape(children, Set.of(SyntaxRole.LOCATION, SyntaxRole.VALUE));
        requireCount(children, SyntaxRole.LOCATION, 1, 1);
        requireCount(children, SyntaxRole.VALUE, 1, 1);
      }
      case ARRAY_ACCESS, ARRAY_LOCATION -> {
        requireShape(children, Set.of(SyntaxRole.ARRAY, SyntaxRole.INDEX));
        requireCount(children, SyntaxRole.ARRAY, 1, 1);
        requireCount(children, SyntaxRole.INDEX, 1, 1);
      }
      case CONDITIONAL_EXPRESSION -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.TRUE_EXPRESSION, SyntaxRole.FALSE_EXPRESSION));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.TRUE_EXPRESSION, 1, 1);
        requireCount(children, SyntaxRole.FALSE_EXPRESSION, 1, 1);
      }
      case TYPE_USE -> validateTypeUse(sourceKind, value, children);
      case STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK, VARIABLE_ACCESS, VARIABLE_LOCATION, RECEIVER, LITERAL, EMPTY ->
        requireShape(children, Set.of());
      case RECEIVER_NAME -> {
        requireShape(children, Set.of());
        validateReceiverName(sourceKind, SyntaxValue.SourceSymbol.class.cast(value));
      }
      case TYPE_PARAMETER_DECLARATION -> validateTypeParameterDeclaration(SyntaxValue.TypeParameterDeclaration.class.cast(value), children);
      case UNSUPPORTED -> {
      }
    }
  }

  private static void validateTypeUse(JavaSourceKind sourceKind, SyntaxValue value, List<SyntaxChild> children) {
    if ((sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT)
        && !(value instanceof SyntaxValue.SourceSymbol || value instanceof SyntaxValue.PackageQualifier)) {
      throw new IllegalArgumentException("A written type name requires a source symbol or package qualifier");
    }
    switch (sourceKind) {
      case PRIMITIVE_TYPE, IDENTIFIER, UNBOUNDED_WILDCARD -> requireShape(children, Set.of());
      case MEMBER_SELECT -> requireShape(children, Set.of(SyntaxRole.TYPE_QUALIFIER), SyntaxRole.TYPE_QUALIFIER, 1, 1);
      case PARAMETERIZED_TYPE -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_NAME, SyntaxRole.TYPE_ARGUMENT));
        requireCount(children, SyntaxRole.TYPE_NAME, 1, 1);
        requireCount(children, SyntaxRole.TYPE_ARGUMENT, 1, Integer.MAX_VALUE);
      }
      case ARRAY_TYPE -> requireShape(children, Set.of(SyntaxRole.COMPONENT_TYPE), SyntaxRole.COMPONENT_TYPE, 1, 1);
      case EXTENDS_WILDCARD -> requireShape(children, Set.of(SyntaxRole.UPPER_BOUND), SyntaxRole.UPPER_BOUND, 1, 1);
      case SUPER_WILDCARD -> requireShape(children, Set.of(SyntaxRole.LOWER_BOUND), SyntaxRole.LOWER_BOUND, 1, 1);
      case INTERSECTION_TYPE ->
        requireShape(children, Set.of(SyntaxRole.INTERSECTION_BOUND), SyntaxRole.INTERSECTION_BOUND, 2, Integer.MAX_VALUE);
      case UNION_TYPE -> requireShape(children, Set.of(SyntaxRole.UNION_ALTERNATIVE), SyntaxRole.UNION_ALTERNATIVE, 2, Integer.MAX_VALUE);
      case ANNOTATED_TYPE -> {
        requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.UNDERLYING_TYPE));
        requireCount(children, SyntaxRole.ANNOTATION, 1, Integer.MAX_VALUE);
        requireCount(children, SyntaxRole.UNDERLYING_TYPE, 1, 1);
      }
      default -> requireShape(children, Set.of());
    }
    for (var child : children) {
      var valid = switch (child.role()) {
        case TYPE_NAME, TYPE_ARGUMENT, TYPE_QUALIFIER, COMPONENT_TYPE, UNDERLYING_TYPE, UPPER_BOUND, LOWER_BOUND, INTERSECTION_BOUND,
            UNION_ALTERNATIVE ->
          child.node().syntaxKind() == SyntaxKind.TYPE_USE;
        case ANNOTATION ->
          child.node().sourceKind() == JavaSourceKind.ANNOTATION && child.node().syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Type-use child kind does not match its role");
      }
    }
  }

  private static void validateTypeUseAttribution(SyntaxStructuralPath path, JavaSourceKind sourceKind, SyntaxType type, SyntaxValue value,
      Optional<SemanticReference> reference, List<SyntaxChild> children) {
    if (value instanceof SyntaxValue.PackageQualifier) {
      if (path.steps().getLast().role() != SyntaxRole.TYPE_QUALIFIER || type != SyntaxType.NotApplicable.INSTANCE
          || reference.isPresent()) {
        throw new IllegalArgumentException("A package qualifier is only a type qualifier without attribution or reference");
      }
      return;
    }
    if (!(type instanceof SyntaxType.Resolved resolved)) {
      return;
    }
    var structure = resolved.structure()
        .orElseThrow(() -> new IllegalArgumentException("A resolved type use requires recursive attributed structure"));
    var matchingSource = switch (sourceKind) {
      case PRIMITIVE_TYPE ->
        structure instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == ((SyntaxValue.PrimitiveType) value).kind();
      case ARRAY_TYPE -> structure instanceof JavaTypeStructure.Array;
      case PARAMETERIZED_TYPE, MEMBER_SELECT -> structure instanceof JavaTypeStructure.Declared;
      case IDENTIFIER -> structure instanceof JavaTypeStructure.Declared || structure instanceof JavaTypeStructure.Variable;
      case UNBOUNDED_WILDCARD ->
        structure instanceof JavaTypeStructure.Wildcard wildcard && wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED;
      case EXTENDS_WILDCARD ->
        structure instanceof JavaTypeStructure.Wildcard wildcard && wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.EXTENDS;
      case SUPER_WILDCARD ->
        structure instanceof JavaTypeStructure.Wildcard wildcard && wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.SUPER;
      case INTERSECTION_TYPE -> structure instanceof JavaTypeStructure.Intersection;
      case UNION_TYPE -> structure instanceof JavaTypeStructure.Union;
      default -> true;
    };
    if (!matchingSource) {
      throw new IllegalArgumentException("Written type-use kind conflicts with attributed structure");
    }
    if ((sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT)
        && structure instanceof JavaTypeStructure.Declared declared && !declared.arguments().isEmpty()) {
      throw new IllegalArgumentException("A written raw type name cannot carry attributed type arguments");
    }
    if (sourceKind == JavaSourceKind.ARRAY_TYPE
        && !structure.equals(new JavaTypeStructure.Array(requiredTypeUseStructure(children, SyntaxRole.COMPONENT_TYPE, 0)))) {
      throw new IllegalArgumentException("Array source component conflicts with attributed component");
    }
    if (structure instanceof JavaTypeStructure.Wildcard wildcard) {
      var role = switch (wildcard.kind()) {
        case UNBOUNDED -> null;
        case EXTENDS -> SyntaxRole.UPPER_BOUND;
        case SUPER -> SyntaxRole.LOWER_BOUND;
      };
      if (role != null && !wildcard.bound().equals(Optional.of(requiredTypeUseStructure(children, role, 0)))) {
        throw new IllegalArgumentException("Written wildcard bound conflicts with attributed bound");
      }
    }
    if (sourceKind == JavaSourceKind.INTERSECTION_TYPE && structure instanceof JavaTypeStructure.Intersection intersection) {
      var written = children.stream().map(child -> requiredTypeUseStructure(children, SyntaxRole.INTERSECTION_BOUND, child.position()))
          .toList();
      if (!written.equals(intersection.bounds())) {
        throw new IllegalArgumentException("Written intersection bounds conflict with attributed bounds");
      }
    }
    if (sourceKind == JavaSourceKind.UNION_TYPE && structure instanceof JavaTypeStructure.Union union) {
      var written = children.stream().map(child -> requiredTypeUseStructure(children, SyntaxRole.UNION_ALTERNATIVE, child.position()))
          .toList();
      if (!written.equals(union.alternatives())) {
        throw new IllegalArgumentException("Written union alternatives conflict with attributed alternatives");
      }
    }
    if (sourceKind == JavaSourceKind.ANNOTATED_TYPE
        && !structure.equals(requiredTypeUseStructure(children, SyntaxRole.UNDERLYING_TYPE, 0))) {
      throw new IllegalArgumentException("Annotated source type conflicts with its attributed underlying type");
    }
    if (sourceKind == JavaSourceKind.MEMBER_SELECT && structure instanceof JavaTypeStructure.Declared declared) {
      var qualifier = children.getFirst().node();
      if (qualifier.value() instanceof SyntaxValue.PackageQualifier) {
        if (declared.lexicalOwnerKey().isPresent() || declared.enclosingType().isPresent()) {
          throw new IllegalArgumentException("A package-qualified type cannot carry an enclosing type owner");
        }
      } else {
        var written = requiredTypeUseStructure(children, SyntaxRole.TYPE_QUALIFIER, 0);
        if (!(written instanceof JavaTypeStructure.Declared || written instanceof JavaTypeStructure.Variable)) {
          throw new IllegalArgumentException("A written type qualifier requires a declared or type-variable type");
        }
        if (written instanceof JavaTypeStructure.Declared selected
            && declared.lexicalOwnerKey().equals(Optional.of(selected.declarationKey())) && declared.enclosingType().isPresent()
            && (path.steps().getLast().role() == SyntaxRole.TYPE_NAME
                ? !((JavaTypeStructure.Declared) declared.enclosingType().orElseThrow()).declarationKey().equals(selected.declarationKey())
                : !declared.enclosingType().orElseThrow().equals(selected))) {
          throw new IllegalArgumentException("Written type qualifier conflicts with selected enclosing declaration");
        }
      }
    }
    if (sourceKind == JavaSourceKind.PARAMETERIZED_TYPE && structure instanceof JavaTypeStructure.Declared declared) {
      var name = requiredTypeUseStructure(children, SyntaxRole.TYPE_NAME, 0);
      if (!(name instanceof JavaTypeStructure.Declared selected) || !selected.declarationKey().equals(declared.declarationKey())) {
        throw new IllegalArgumentException("Written type name conflicts with selected declaration");
      }
      if (!declared.lexicalOwnerKey().equals(selected.lexicalOwnerKey())) {
        throw new IllegalArgumentException("Written type name conflicts with selected lexical owner");
      }
      var writtenName = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_NAME).findFirst().orElseThrow().node();
      if (writtenName.sourceKind() == JavaSourceKind.MEMBER_SELECT) {
        var qualifier = writtenName.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_QUALIFIER).findFirst().orElseThrow()
            .node();
        if (!(qualifier.value() instanceof SyntaxValue.PackageQualifier) && declared.enclosingType().isPresent()) {
          var writtenQualifier = requiredTypeUseStructure(writtenName.children(), SyntaxRole.TYPE_QUALIFIER, 0);
          if (writtenQualifier instanceof JavaTypeStructure.Declared qualifierType
              && declared.lexicalOwnerKey().equals(Optional.of(qualifierType.declarationKey()))
              && !declared.enclosingType().equals(Optional.of(writtenQualifier))) {
            throw new IllegalArgumentException("Written type qualifier conflicts with attributed enclosing type");
          }
        }
      }
      var arguments = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
          .map(child -> requiredTypeUseStructure(children, SyntaxRole.TYPE_ARGUMENT, child.position())).toList();
      if (!arguments.equals(declared.arguments())) {
        throw new IllegalArgumentException("Written type arguments conflict with attributed arguments");
      }
    }
    if (structure instanceof JavaTypeStructure.Declared declared) {
      requireTypeUseTarget(reference, ReferenceKind.TYPE, declared.declarationKey());
    } else if (structure instanceof JavaTypeStructure.Variable variable) {
      requireTypeUseTarget(reference, ReferenceKind.TYPE_PARAMETER, variable.parameterKey());
    }
  }

  private static JavaTypeStructure requiredTypeUseStructure(List<SyntaxChild> children, SyntaxRole role, int position) {
    return children.stream().filter(child -> child.role() == role && child.position() == position).findFirst().map(SyntaxChild::node)
        .map(SyntaxNode::type).filter(SyntaxType.Resolved.class::isInstance).map(SyntaxType.Resolved.class::cast)
        .flatMap(SyntaxType.Resolved::structure)
        .orElseThrow(() -> new IllegalArgumentException("A written type component requires recursive attributed structure"));
  }

  private static void requireTypeUseTarget(Optional<SemanticReference> reference, ReferenceKind kind, String key) {
    if (reference.isEmpty() || reference.orElseThrow().status() != ResolutionStatus.RESOLVED
        || reference.orElseThrow().referenceKind() != kind || !reference.orElseThrow().symbolKey().equals(Optional.of(key))) {
      throw new IllegalArgumentException("A type use requires its compiler-selected declaration target");
    }
  }

  private static void validateTypeDeclaration(JavaSourceKind sourceKind, SyntaxValue.TypeDeclaration declaration,
      List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.TYPE_PARAMETER, SyntaxRole.SUPERCLASS, SyntaxRole.INTERFACE,
        SyntaxRole.PERMITTED_SUBCLASS, SyntaxRole.RECORD_COMPONENT, SyntaxRole.MEMBER));
    var shape = declaration.shape();
    if ((sourceKind == JavaSourceKind.ENUM || sourceKind == JavaSourceKind.ANNOTATION_TYPE) && shape.typeParameters() != 0) {
      throw new IllegalArgumentException("This type form cannot own type parameters");
    }
    if (sourceKind != JavaSourceKind.RECORD && shape.recordComponents() != 0) {
      throw new IllegalArgumentException("Only a record can own record-header components");
    }
    requireCount(children, SyntaxRole.ANNOTATION, shape.annotations(), shape.annotations());
    requireCount(children, SyntaxRole.TYPE_PARAMETER, shape.typeParameters(), shape.typeParameters());
    requireCount(children, SyntaxRole.SUPERCLASS, shape.superclass() ? 1 : 0, shape.superclass() ? 1 : 0);
    requireCount(children, SyntaxRole.INTERFACE, shape.interfaces(), shape.interfaces());
    requireCount(children, SyntaxRole.PERMITTED_SUBCLASS, shape.permittedSubclasses(), shape.permittedSubclasses());
    requireCount(children, SyntaxRole.RECORD_COMPONENT, shape.recordComponents(), shape.recordComponents());
    requireCount(children, SyntaxRole.MEMBER, shape.members(), shape.members());
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.sourceKind() == JavaSourceKind.ANNOTATION && node.syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        case TYPE_PARAMETER ->
          node.sourceKind() == JavaSourceKind.TYPE_PARAMETER && node.syntaxKind() == SyntaxKind.TYPE_PARAMETER_DECLARATION
              && node.value(SyntaxValue.TypeParameterDeclaration.class).ownerKey().equals(declaration.declarationKey())
              && node.value(SyntaxValue.TypeParameterDeclaration.class).ordinal() == child.position();
        case SUPERCLASS, INTERFACE, PERMITTED_SUBCLASS ->
          node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        case RECORD_COMPONENT ->
          node.syntaxKind() == SyntaxKind.RECORD_COMPONENT_DECLARATION && node.sourceKind() == JavaSourceKind.VARIABLE
              && node.value(SyntaxValue.RecordComponentDeclaration.class).ownerKey().equals(declaration.declarationKey())
              && node.value(SyntaxValue.RecordComponentDeclaration.class).ordinal() == child.position();
        case MEMBER -> switch (node.syntaxKind()) {
          case TYPE_DECLARATION, STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK -> true;
          case FIELD_DECLARATION -> node.value(SyntaxValue.VariableDeclaration.class).kind() == SyntaxValue.VariableDeclaration.Kind.FIELD
              && node.value(SyntaxValue.VariableDeclaration.class).ownerKey().equals(declaration.declarationKey());
          case ENUM_CONSTANT_DECLARATION -> sourceKind == JavaSourceKind.ENUM
              && node.value(SyntaxValue.VariableDeclaration.class).kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT
              && node.value(SyntaxValue.VariableDeclaration.class).ownerKey().equals(declaration.declarationKey());
          case METHOD_DECLARATION -> executableOwnerAllows(sourceKind, declaration.name(), declaration.declarationKey(),
              node.value(SyntaxValue.ExecutableDeclaration.class));
          default -> false;
        };
        default -> throw new IllegalStateException("Unexpected type declaration child role");
      };
      if (!valid) {
        throw new IllegalArgumentException("Type declaration child kind does not match its role");
      }
    }
  }

  private static void validateVariableDeclaration(SyntaxKind kind, SyntaxValue.VariableDeclaration declaration,
      List<SyntaxChild> children) {
    if ((kind == SyntaxKind.FIELD_DECLARATION) != (declaration.kind() == SyntaxValue.VariableDeclaration.Kind.FIELD)) {
      throw new IllegalArgumentException("Variable declaration form does not match its syntax kind");
    }
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, declaration.type() ? 1 : 0, declaration.type() ? 1 : 0);
    for (var child : children) {
      if (child.role() == SyntaxRole.ANNOTATION
          && (child.node().sourceKind() != JavaSourceKind.ANNOTATION || child.node().syntaxKind() != SyntaxKind.COMPILATION_UNIT)) {
        throw new IllegalArgumentException("A variable annotation requires a written annotation attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && (child.node().syntaxKind() != SyntaxKind.TYPE_USE || !child.node().sourceKind().identifiesTypeUseSyntax())) {
        throw new IllegalArgumentException("A field type requires a written type-use attachment");
      }
    }
  }

  private static void validateRecordComponentDeclaration(SyntaxValue.RecordComponentDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, 1, 1);
    for (var child : children) {
      if (child.role() == SyntaxRole.ANNOTATION
          && (child.node().sourceKind() != JavaSourceKind.ANNOTATION || child.node().syntaxKind() != SyntaxKind.COMPILATION_UNIT)) {
        throw new IllegalArgumentException("A record component annotation requires a written annotation attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && (child.node().syntaxKind() != SyntaxKind.TYPE_USE || !child.node().sourceKind().identifiesTypeUseSyntax())) {
        throw new IllegalArgumentException("A record component requires a written type-use attachment");
      }
    }
  }

  private static boolean executableOwnerAllows(JavaSourceKind sourceKind, String ownerName, String ownerKey,
      SyntaxValue.ExecutableDeclaration executable) {
    if (!executable.ownerKey().equals(ownerKey)) {
      return false;
    }
    return switch (executable.kind()) {
      case METHOD -> sourceKind != JavaSourceKind.ANNOTATION_TYPE;
      case ANNOTATION_ELEMENT -> sourceKind == JavaSourceKind.ANNOTATION_TYPE;
      case CONSTRUCTOR -> Set.of(JavaSourceKind.CLASS, JavaSourceKind.ENUM, JavaSourceKind.RECORD).contains(sourceKind)
          && executable.name().equals(ownerName);
      case COMPACT_CONSTRUCTOR -> sourceKind == JavaSourceKind.RECORD && executable.name().equals(ownerName);
    };
  }

  private static void validateTypeParameterDeclaration(SyntaxValue.TypeParameterDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.TYPE_BOUND));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.TYPE_BOUND, declaration.bounds(), declaration.bounds());
    var writtenBounds = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_BOUND)
        .map(child -> requiredTypeUseStructure(children, SyntaxRole.TYPE_BOUND, child.position())).toList();
    if (writtenBounds.isEmpty() && !declaration.upperBound().equals(IMPLICIT_OBJECT_BOUND)) {
      throw new IllegalArgumentException("An unwritten type-parameter bound must be implicit Object");
    }
    if (writtenBounds.size() == 1 && !declaration.upperBound().equals(writtenBounds.getFirst())) {
      throw new IllegalArgumentException("The attributed type-parameter bound conflicts with its written bound");
    }
    if (writtenBounds.size() > 1 && !declaration.upperBound().equals(new JavaTypeStructure.Intersection(writtenBounds))) {
      throw new IllegalArgumentException("The attributed intersection conflicts with ordered written bounds");
    }
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.sourceKind() == JavaSourceKind.ANNOTATION && node.syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        case TYPE_BOUND -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Type parameter child kind does not match its role");
      }
    }
  }

  private static void validateExecutableDeclaration(SyntaxValue.ExecutableDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.TYPE_PARAMETER, SyntaxRole.RECEIVER_PARAMETER, SyntaxRole.PARAMETER,
        SyntaxRole.RETURN_TYPE, SyntaxRole.THROWN_TYPE, SyntaxRole.BODY, SyntaxRole.DEFAULT_VALUE));
    var shape = declaration.shape();
    requireCount(children, SyntaxRole.ANNOTATION, shape.annotations(), shape.annotations());
    requireCount(children, SyntaxRole.TYPE_PARAMETER, shape.typeParameters(), shape.typeParameters());
    requireCount(children, SyntaxRole.RECEIVER_PARAMETER, shape.receiver() ? 1 : 0, shape.receiver() ? 1 : 0);
    requireCount(children, SyntaxRole.PARAMETER, shape.parameters(), shape.parameters());
    requireCount(children, SyntaxRole.RETURN_TYPE, shape.returnType() ? 1 : 0, shape.returnType() ? 1 : 0);
    requireCount(children, SyntaxRole.THROWN_TYPE, shape.thrownTypes(), shape.thrownTypes());
    requireCount(children, SyntaxRole.BODY, shape.body() ? 1 : 0, shape.body() ? 1 : 0);
    requireCount(children, SyntaxRole.DEFAULT_VALUE, shape.defaultValue() ? 1 : 0, shape.defaultValue() ? 1 : 0);
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.sourceKind() == JavaSourceKind.ANNOTATION && node.syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        case TYPE_PARAMETER -> node.syntaxKind() == SyntaxKind.TYPE_PARAMETER_DECLARATION
            && node.value(SyntaxValue.TypeParameterDeclaration.class).ownerKey().equals(declaration.declarationKey())
            && node.value(SyntaxValue.TypeParameterDeclaration.class).ordinal() == child.position();
        case RECEIVER_PARAMETER -> node.sourceKind() == JavaSourceKind.VARIABLE && node.syntaxKind() == SyntaxKind.RECEIVER_PARAMETER
            && node.value() instanceof SyntaxValue.ReceiverParameter receiver && receiver.ownerKey().equals(declaration.declarationKey())
            && receiverMatchesExecutable(declaration, node.requiredChild(SyntaxRole.QUALIFIER));
        case PARAMETER -> node.sourceKind() == JavaSourceKind.VARIABLE && node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION
            && node.value() instanceof SyntaxValue.ParameterDeclaration parameter
            && parameter.declarationKey().equals(declaration.declarationKey() + ":parameter:" + child.position())
            && parameter.varArgs() == (shape.varArgs() && child.position() == shape.parameters() - 1);
        case RETURN_TYPE, THROWN_TYPE -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        case BODY -> node.syntaxKind() == SyntaxKind.BLOCK;
        case DEFAULT_VALUE -> node.syntaxKind() == SyntaxKind.UNSUPPORTED;
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Executable declaration child kind does not match its role");
      }
    }
  }

  private static boolean receiverMatchesExecutable(SyntaxValue.ExecutableDeclaration declaration, SyntaxNode nameExpression) {
    return switch (declaration.kind()) {
      case METHOD -> nameExpression.sourceKind() == JavaSourceKind.IDENTIFIER;
      case CONSTRUCTOR -> nameExpression.sourceKind() == JavaSourceKind.MEMBER_SELECT
          && declaration.receiverType() instanceof SyntaxType.Resolved resolved && nameExpression.value(SyntaxValue.SourceSymbol.class)
              .name().equals(resolved.identity().erasedName().substring(resolved.identity().erasedName().lastIndexOf('.') + 1) + ".this");
      case COMPACT_CONSTRUCTOR, ANNOTATION_ELEMENT -> false;
    };
  }

  private static void validateParameterDeclaration(SyntaxValue.ParameterDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, declaration.type() ? 1 : 0, declaration.type() ? 1 : 0);
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.sourceKind() == JavaSourceKind.ANNOTATION && node.syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        case DECLARED_TYPE -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Parameter declaration child kind does not match its role");
      }
    }
  }

  private static void validateReceiverParameter(SyntaxValue.ReceiverParameter receiver, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE, SyntaxRole.QUALIFIER));
    requireCount(children, SyntaxRole.ANNOTATION, receiver.annotations(), receiver.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, 1, 1);
    requireCount(children, SyntaxRole.QUALIFIER, 1, 1);
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.sourceKind() == JavaSourceKind.ANNOTATION && node.syntaxKind() == SyntaxKind.COMPILATION_UNIT;
        case DECLARED_TYPE -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        case QUALIFIER -> node.syntaxKind() == SyntaxKind.RECEIVER_NAME;
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Receiver parameter child kind does not match its role");
      }
    }
  }

  private static void validateReceiverName(JavaSourceKind sourceKind, SyntaxValue.SourceSymbol symbol) {
    var name = symbol.name();
    var valid = switch (sourceKind) {
      case IDENTIFIER -> "this".equals(name);
      case MEMBER_SELECT ->
        name.endsWith(".this") && SyntaxValueSupport.isTypeIdentifier(name.substring(0, name.length() - ".this".length()));
      default -> false;
    };
    if (!valid) {
      throw new IllegalArgumentException("Receiver name does not match Java source kind " + sourceKind);
    }
  }

  private static void validateInvocation(SyntaxValue.Invocation invocation, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.RECEIVER, SyntaxRole.ARGUMENT));
    requireCount(children, SyntaxRole.RECEIVER, invocation.form().evaluatesExpression() ? 1 : 0,
        invocation.form().evaluatesExpression() ? 1 : 0);
  }

  private static void validateObjectCreation(SyntaxValue.Invocation invocation, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ENCLOSING_INSTANCE, SyntaxRole.ARGUMENT));
    var expectedEnclosingInstances = invocation.form() == InvocationForm.QUALIFIED_OBJECT_CREATION ? 1 : 0;
    if (invocation.form() != InvocationForm.OBJECT_CREATION && invocation.form() != InvocationForm.QUALIFIED_OBJECT_CREATION) {
      throw new IllegalArgumentException("OBJECT_CREATION requires a construction invocation form");
    }
    requireCount(children, SyntaxRole.ENCLOSING_INSTANCE, expectedEnclosingInstances, expectedEnclosingInstances);
  }

  private static void validateFieldAccess(SyntaxValue.FieldAccess access, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.RECEIVER));
    requireCount(children, SyntaxRole.RECEIVER, access.receiver().evaluatesExpression() ? 1 : 0,
        access.receiver().evaluatesExpression() ? 1 : 0);
  }

  private static void requireShape(List<SyntaxChild> children, Set<SyntaxRole> roles) {
    if (children.stream().map(SyntaxChild::role).anyMatch(role -> !roles.contains(role))) {
      throw new IllegalArgumentException("Syntax node contains a child role that its kind does not permit");
    }
  }

  private static void validateSwitchCase(SyntaxValue.SwitchCase switchCase, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.LABEL, SyntaxRole.BODY, SyntaxRole.STATEMENT));
    var expressionLabelCount = switchCase.labels().stream().filter(SyntaxValue.SwitchLabel.Expression.class::isInstance).count();
    requireCount(children, SyntaxRole.LABEL, (int) expressionLabelCount, (int) expressionLabelCount);
    for (var label : children.stream().filter(child -> child.role() == SyntaxRole.LABEL).map(SyntaxChild::node).toList()) {
      if (!isSupportedSwitchLabel(label)) {
        throw new IllegalArgumentException("A supported switch case label must be a literal or enum constant");
      }
    }
    switch (switchCase.form()) {
      case RULE -> {
        requireCount(children, SyntaxRole.BODY, 1, 1);
        requireCount(children, SyntaxRole.STATEMENT, 0, 0);
      }
      case STATEMENT_GROUP -> requireCount(children, SyntaxRole.BODY, 0, 0);
    }
  }

  private static boolean isSupportedSwitchLabel(SyntaxNode label) {
    if (label.syntaxKind() == SyntaxKind.LITERAL) {
      return label.resolvedType().map(JavaTypeIdentity::category).filter(category -> category != JavaTypeCategory.NULL).isPresent();
    }
    if (label.syntaxKind() != SyntaxKind.VARIABLE_ACCESS && label.syntaxKind() != SyntaxKind.FIELD_ACCESS) {
      return false;
    }
    return label.semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.ENUM_CONSTANT)
        .flatMap(SemanticReference::symbolKey).isPresent();
  }

  private static void requireShape(List<SyntaxChild> children, Set<SyntaxRole> roles, SyntaxRole countedRole, int minimum, int maximum) {
    requireShape(children, roles);
    requireCount(children, countedRole, minimum, maximum);
  }

  private static void requireCount(List<SyntaxChild> children, SyntaxRole role, int minimum, int maximum) {
    var count = children.stream().filter(child -> child.role() == role).count();
    if (count < minimum || count > maximum) {
      throw new IllegalArgumentException("Syntax role " + role + " has invalid cardinality for its parent kind");
    }
  }

  private static String requireText(String value, String label) {
    Objects.requireNonNull(value, label);
    if (value.isBlank()) {
      throw new IllegalArgumentException(label + " cannot be blank");
    }
    return value;
  }
}
