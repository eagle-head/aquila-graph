package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.SourceSpan;

/** Immutable, compiler-neutral facts for one durable Java source occurrence. */
public final class SyntaxNode {
  private static final int SINGLE_CHILD_COUNT = 1;
  private static final int NAMED_ANNOTATION_CHILD_COUNT = 2;
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
    validateReference(syntaxKind, syntaxValue, semanticReference);
    validateChildPositions(childNodes);
    validateAnnotationChildLocations(childNodes);
    validateShape(syntaxKind, sourceKind, value, childNodes, semanticReference);
    if (value instanceof SyntaxValue.TypeDeclaration declaration) {
      validateTypePlacement(structuralPath, sourceKind, declaration);
    }
    validateKnownWrittenBodyType(syntaxKind, type, childNodes);
    validateUpdateType(syntaxKind, type, childNodes);
    validateArrayType(syntaxKind, type, value, childNodes);
    validateExecutableChildren(syntaxKind, childNodes);
    IterationSyntax.validate(syntaxKind, childNodes);
    if (value instanceof SyntaxValue.Operator operator && !operator.operator().name().equals(sourceKind.name())) {
      throw new IllegalArgumentException("The retained operator contradicts its exact Java source kind");
    }
    if (value instanceof SyntaxValue.LocalDeclaration local) {
      validateWrittenLocalType(local, sourceOwner, sourceOrigin, type);
      if ("_".equals(local.name()) && semanticReference.isPresent()) {
        throw new IllegalArgumentException("An unnamed binding cannot publish a referable identifier");
      }
      local.bindingEvidence().ifPresent(binding -> {
        var selected = semanticReference.flatMap(SemanticReference::symbolKey);
        if (selected.isPresent() && !selected.equals(binding.declarationKey())) {
          throw new IllegalArgumentException("A local reference must select its exact binding identity");
        }
      });
    }
    if (syntaxKind == SyntaxKind.ANNOTATION_ARGUMENT) {
      validateAnnotationArgumentType((SyntaxValue.AnnotationArgument) value, type, childNodes);
      validateAnnotationArgumentSource((SyntaxValue.AnnotationArgument) value, sourceKind, sourceOrigin, childNodes);
    }
    if (sourceKind == JavaSourceKind.ANNOTATION && value instanceof SyntaxValue.Annotation && semanticReference.isEmpty()) {
      throw new IllegalArgumentException("A typed annotation occurrence requires a type resolution outcome");
    }
    if (sourceKind == JavaSourceKind.ANNOTATION && value instanceof SyntaxValue.Annotation
        && semanticReference.flatMap(SemanticReference::symbolKey).isPresent()) {
      var typeKey = semanticReference.flatMap(SemanticReference::symbolKey).orElseThrow();
      for (var child : childNodes) {
        var argument = child.node().value(SyntaxValue.AnnotationArgument.class);
        var selectedName = argument.explicitName().orElse("value");
        if (!argument.selectedElementKey().equals(typeKey + '#' + selectedName + "()")) {
          throw new IllegalArgumentException("Annotation argument selects an element outside its annotation type");
        }
      }
    }
    validateBoundSourceRanges(path, syntaxKind, sourceKind, sourceOrigin, childNodes);
    if (syntaxKind == SyntaxKind.TYPE_USE) {
      validateTypeUseAttribution(path, sourceKind, type, value, semanticReference, childNodes);
    }
  }

  private static void validateTypePlacement(SyntaxStructuralPath path, JavaSourceKind sourceKind, SyntaxValue.TypeDeclaration declaration) {
    var anonymous = !path.steps().isEmpty() && path.steps().getLast().role() == SyntaxRole.CLASS_BODY;
    if (anonymous != declaration.name().isEmpty()) {
      throw new IllegalArgumentException("Only an anonymous creation body can have a nameless type declaration");
    }
    var shape = declaration.shape();
    if (anonymous && ((sourceKind != JavaSourceKind.CLASS && sourceKind != JavaSourceKind.ENUM) || !declaration.writtenModifiers().isEmpty()
        || shape.annotations() != 0 || shape.typeParameters() != 0 || shape.superclass() || shape.interfaces() != 0
        || shape.permittedSubclasses() != 0 || shape.recordComponents() != 0)) {
      throw new IllegalArgumentException("An anonymous creation body cannot invent a written type header");
    }
  }

  private static void validateUpdateType(SyntaxKind kind, SyntaxType type, List<SyntaxChild> children) {
    if ((kind == SyntaxKind.UPDATE || kind == SyntaxKind.COMPOUND_ASSIGNMENT) && type instanceof SyntaxType.Resolved) {
      var role = kind == SyntaxKind.UPDATE ? SyntaxRole.OPERAND : SyntaxRole.LOCATION;
      var location = children.stream().filter(child -> child.role() == role).findFirst().orElseThrow().node();
      if (location.type() instanceof SyntaxType.Resolved && !type.equals(location.type())) {
        throw new IllegalArgumentException("An update result must retain its storage location type");
      }
    }
  }

  private static void validateArrayType(SyntaxKind kind, SyntaxType type, SyntaxValue value, List<SyntaxChild> children) {
    if (kind != SyntaxKind.ARRAY_CREATION || !(type instanceof SyntaxType.Resolved resolved)) {
      return;
    }
    if (resolved.identity().category() != JavaTypeCategory.ARRAY) {
      throw new IllegalArgumentException("An executable array requires an attributed array type");
    }
    var written = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_OPERAND).findFirst();
    if (resolved.structure().isEmpty() || written.isEmpty()
        || !(written.orElseThrow().node().type() instanceof SyntaxType.Resolved component) || component.structure().isEmpty()) {
      return;
    }
    var structure = resolved.structure().orElseThrow();
    var dimensions = ((SyntaxValue.ArrayCreation) value).initializer()
        ? 1L
        : children.stream().filter(child -> child.role() == SyntaxRole.DIMENSION).count();
    for (var ordinal = 0L; ordinal < dimensions; ordinal++) {
      if (!(structure instanceof JavaTypeStructure.Array array)) {
        throw new IllegalArgumentException("Written array dimensions contradict its attributed rank");
      }
      structure = array.component();
    }
    if (!structure.equals(component.structure().orElseThrow())) {
      throw new IllegalArgumentException("The written array component contradicts its attributed type");
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

  private static void validateAnnotationChildLocations(List<SyntaxChild> children) {
    for (var child : children) {
      if (!(child.node().value() instanceof SyntaxValue.Annotation annotation)) {
        continue;
      }
      if ((child.role() == SyntaxRole.ANNOTATION && annotation.location() == SyntaxValue.AnnotationLocation.VALUE)
          || (child.role() == SyntaxRole.ANNOTATION && child.node().syntaxKind() == SyntaxKind.ANNOTATION
              && children.stream().anyMatch(candidate -> candidate.role() == SyntaxRole.UNDERLYING_TYPE)
              && annotation.location() != SyntaxValue.AnnotationLocation.TYPE_USE
              && annotation.location() != SyntaxValue.AnnotationLocation.UNRESOLVED)
          || ((child.role() == SyntaxRole.VALUE || child.role() == SyntaxRole.DEFAULT_VALUE)
              && annotation.location() != SyntaxValue.AnnotationLocation.VALUE)) {
        throw new IllegalArgumentException("Annotation location must match its structural role");
      }
    }
  }

  private static void validateBoundSourceRanges(SyntaxStructuralPath path, SyntaxKind kind, JavaSourceKind sourceKind,
      Optional<SourceSpan> parentOrigin, List<SyntaxChild> children) {
    if (kind == SyntaxKind.ANNOTATION) {
      validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.ANNOTATION_ARGUMENT);
    } else if (kind == SyntaxKind.ANNOTATION_ARGUMENT) {
      validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.ANNOTATION_ELEMENT_NAME);
      validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.VALUE);
      if (children.size() == NAMED_ANNOTATION_CHILD_COUNT) {
        var name = children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION_ELEMENT_NAME).findFirst().orElseThrow();
        var value = children.stream().filter(child -> child.role() == SyntaxRole.VALUE).findFirst().orElseThrow();
        if (name.node().origin().orElseThrow().endOffsetExclusive() > value.node().origin().orElseThrow().startOffset()) {
          throw new IllegalArgumentException("Written annotation name must precede its value without overlap");
        }
      }
    } else if (kind == SyntaxKind.METHOD_DECLARATION) {
      validateWrittenComponentRanges(parentOrigin, children, SyntaxRole.DEFAULT_VALUE);
    } else if (path.steps().stream()
        .anyMatch(step -> step.role() == SyntaxRole.ANNOTATION_ARGUMENT || step.role() == SyntaxRole.DEFAULT_VALUE)) {
      for (var child : children) {
        validateWrittenComponentRanges(parentOrigin, children, child.role());
      }
    } else if (kind == SyntaxKind.TYPE_PARAMETER_DECLARATION) {
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

  private static void validateWrittenLocalType(SyntaxValue.LocalDeclaration local, String sourceFileKey, Optional<SourceSpan> origin,
      SyntaxType type) {
    local.writtenType().ifPresent(written -> {
      var declaration = origin
          .orElseThrow(() -> new IllegalArgumentException("A copied local type requires its written declaration range"));
      var range = written.origin();
      var sameType = (type instanceof SyntaxType.Resolved resolved && written.type() instanceof SyntaxType.Resolved copied)
          ? resolved.identity().category() == copied.identity().category()
              && resolved.identity().erasedName().equals(copied.identity().erasedName()) && resolved.structure().equals(copied.structure())
          : type.equals(written.type());
      if (!range.sourceFileKey().equals(sourceFileKey) || range.startOffset() < declaration.startOffset()
          || range.endOffsetExclusive() > declaration.endOffsetExclusive() || !sameType) {
        throw new IllegalArgumentException("A copied local type must belong to its actual source declaration and attributed type: "
            + local.name() + ", written=" + written + ", declaration=" + declaration + ", type=" + type);
      }
    });
  }

  private static void validateType(SyntaxKind kind, SyntaxType type, SyntaxValue value) {
    if (kind == SyntaxKind.CLASS_LITERAL && type.knownIdentity()
        .filter(identity -> identity.category() != JavaTypeCategory.ERROR && identity.category() != JavaTypeCategory.OTHER
            && (identity.category() != JavaTypeCategory.DECLARED || !"java.lang.Class".equals(identity.erasedName())))
        .isPresent()) {
      throw new IllegalArgumentException("A class literal value requires a java.lang.Class type");
    }
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
    if (kind == SyntaxKind.INSTANCE_OF && type.knownIdentity().filter(identity -> identity.category() != JavaTypeCategory.BOOLEAN
        && identity.category() != JavaTypeCategory.ERROR && identity.category() != JavaTypeCategory.OTHER).isPresent()) {
      throw new IllegalArgumentException("An instance test must have a boolean result type");
    }
    if ((kind == SyntaxKind.LAMBDA_EXPRESSION && type instanceof SyntaxType.Resolved resolved
        && !isFunctionalTargetCategory(resolved.identity().category()))
        || (kind == SyntaxKind.MEMBER_REFERENCE
            && type.knownIdentity().filter(identity -> !isFunctionalTargetCategory(identity.category())).isPresent())) {
      throw new IllegalArgumentException(kind == SyntaxKind.LAMBDA_EXPRESSION
          ? "A lambda target must be a functional interface or intersection type"
          : "A reference target must be a functional interface or intersection type");
    }
  }

  private static boolean isFunctionalTargetCategory(JavaTypeCategory category) {
    return Set.of(JavaTypeCategory.DECLARED, JavaTypeCategory.INTERSECTION, JavaTypeCategory.ERROR, JavaTypeCategory.OTHER)
        .contains(category);
  }

  private static void validateReference(SyntaxKind kind, SyntaxValue syntaxValue, Optional<SemanticReference> reference) {
    if (kind == SyntaxKind.CLASS_LITERAL) {
      if (reference.isPresent()) {
        throw new IllegalArgumentException("A class literal has no field or executable selection");
      }
      return;
    }
    if (kind == SyntaxKind.MEMBER_REFERENCE) {
      var value = (SyntaxValue.MemberReference) syntaxValue;
      if (value.selection() != SyntaxValue.ReferenceSelection.EXECUTABLE) {
        if (reference.isPresent()) {
          throw new IllegalArgumentException("An intrinsic array reference has no named executable declaration");
        }
      } else if (reference.isPresent() && reference.orElseThrow()
          .referenceKind() != (value.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR ? ReferenceKind.CONSTRUCTOR : ReferenceKind.METHOD)) {
        throw new IllegalArgumentException("A reference selection must agree with its written mode");
      }
      return;
    }
    if (kind == SyntaxKind.LOCAL_DECLARATION && syntaxValue instanceof SyntaxValue.LocalDeclaration local && "_".equals(local.name())) {
      if (reference.isPresent()) {
        throw new IllegalArgumentException("An unnamed binding cannot publish a referable identifier");
      }
      return;
    }
    if (kind == SyntaxKind.UNSUPPORTED) {
      return;
    }
    if (kind == SyntaxKind.ANNOTATION_ARGUMENT) {
      if (reference.isEmpty() || reference.orElseThrow().referenceKind() != ReferenceKind.METHOD
          || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || !reference.orElseThrow().symbolKey()
              .equals(Optional.of(((SyntaxValue.AnnotationArgument) syntaxValue).selectedElementKey()))) {
        throw new IllegalArgumentException("An annotation argument requires its selected element reference");
      }
      return;
    }
    if (kind == SyntaxKind.ANNOTATION_NAME_REFERENCE) {
      if (reference.isEmpty()
          || !Set.of(ReferenceKind.FIELD, ReferenceKind.LOCAL, ReferenceKind.ENUM_CONSTANT)
              .contains(reference.orElseThrow().referenceKind())
          || reference.orElseThrow().status() != ResolutionStatus.RESOLVED || reference.orElseThrow().symbolKey().isEmpty()) {
        throw new IllegalArgumentException("An annotation name requires a resolved constant-variable or enum constant reference");
      }
      return;
    }
    if (kind == SyntaxKind.ANNOTATION) {
      if (reference.isPresent() && reference.orElseThrow().referenceKind() != ReferenceKind.TYPE) {
        throw new IllegalArgumentException("ANNOTATION permits only an annotation type reference");
      }
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
      if (reference.isPresent()
          && !Set.of(ReferenceKind.TYPE, ReferenceKind.TYPE_PARAMETER).contains(reference.orElseThrow().referenceKind())) {
        throw new IllegalArgumentException("TYPE_CAST permits only a type or type-parameter declaration reference");
      }
      return;
    }
    var expectedKinds = switch (kind) {
      case LOCAL_DECLARATION -> Set.of(ReferenceKind.LOCAL);
      case VARIABLE_ACCESS -> Set.of(ReferenceKind.LOCAL, ReferenceKind.PARAMETER, ReferenceKind.ENUM_CONSTANT);
      case FIELD_ACCESS -> Set.of(ReferenceKind.FIELD, ReferenceKind.ENUM_CONSTANT);
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

  private static void validateShape(SyntaxKind kind, JavaSourceKind sourceKind, SyntaxValue value, List<SyntaxChild> children,
      Optional<SemanticReference> semanticReference) {
    var expectedValue = switch (kind) {
      case COMPILATION_UNIT -> switch (sourceKind) {
        case COMPILATION_UNIT -> SyntaxValue.CompilationUnit.class;
        case PACKAGE -> SyntaxValue.PackageDeclaration.class;
        case IMPORT -> SyntaxValue.ImportDeclaration.class;
        case MODULE -> SyntaxValue.ModuleDeclaration.class;
        case REQUIRES -> SyntaxValue.Requires.class;
        case EXPORTS -> SyntaxValue.Exports.class;
        case OPENS -> SyntaxValue.Opens.class;
        case USES -> SyntaxValue.Uses.class;
        case PROVIDES -> SyntaxValue.Provides.class;
        default -> throw new IllegalStateException("Unexpected compilation-unit source kind");
      };
      case ANNOTATION -> SyntaxValue.class;
      case ANNOTATION_ARRAY -> SyntaxValue.None.class;
      case ANNOTATION_ARGUMENT -> SyntaxValue.AnnotationArgument.class;
      case ANNOTATION_ELEMENT_NAME, ANNOTATION_NAME_REFERENCE -> SyntaxValue.SourceSymbol.class;
      case ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST -> SyntaxValue.None.class;
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
        case ANNOTATED_TYPE ->
          value instanceof SyntaxValue.SharedTypeAnnotations ? SyntaxValue.SharedTypeAnnotations.class : SyntaxValue.None.class;
        case PARAMETERIZED_TYPE -> value instanceof SyntaxValue.DiamondType ? SyntaxValue.DiamondType.class : SyntaxValue.None.class;
        default -> SyntaxValue.None.class;
      };
      case METHOD_DECLARATION -> SyntaxValue.ExecutableDeclaration.class;
      case LOCAL_DECLARATION -> SyntaxValue.LocalDeclaration.class;
      case LAMBDA_EXPRESSION -> SyntaxValue.Lambda.class;
      case MEMBER_REFERENCE -> SyntaxValue.MemberReference.class;
      case BINDING_PATTERN -> SyntaxValue.PatternBinding.class;
      case LABELED_STATEMENT, BREAK, CONTINUE -> SyntaxValue.StatementLabel.class;
      case VARIABLE_ACCESS, ARRAY_LENGTH -> SyntaxValue.SourceSymbol.class;
      case FIELD_ACCESS -> SyntaxValue.FieldAccess.class;
      case INVOCATION, OBJECT_CREATION -> SyntaxValue.Invocation.class;
      case RECEIVER -> SyntaxValue.Receiver.class;
      case LITERAL -> SyntaxValue.Literal.class;
      case UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR, COMPOUND_ASSIGNMENT, UPDATE -> SyntaxValue.Operator.class;
      case TYPE_CAST -> SyntaxValue.TypeName.class;
      case ARRAY_CREATION -> SyntaxValue.ArrayCreation.class;
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
            if (children.stream().anyMatch(child -> !(child.node().value() instanceof SyntaxValue.Annotation))) {
              throw new IllegalArgumentException("A package annotation child requires an annotation attachment");
            }
          }
          case IMPORT, REQUIRES, EXPORTS, OPENS, USES, PROVIDES -> requireShape(children, Set.of());
          case MODULE -> {
            requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.MODULE_DIRECTIVE));
            for (var child : children) {
              var valid = switch (child.role()) {
                case ANNOTATION -> child.node().value() instanceof SyntaxValue.Annotation;
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
      case ANNOTATION -> {
        requireShape(children, Set.of(SyntaxRole.ANNOTATION_ARGUMENT));
        if (!(value instanceof SyntaxValue.Annotation annotation)) {
          throw new IllegalArgumentException("An annotation requires its captured written form");
        }
        var arguments = children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION_ARGUMENT).toList();
        if (annotation.argumentFailureCode().isPresent()) {
          if (!arguments.isEmpty()) {
            throw new IllegalArgumentException("A diagnosed incomplete annotation cannot publish argument pairs");
          }
        } else if (arguments.size() != annotation.arguments().size()) {
          throw new IllegalArgumentException("Annotation argument children must match its captured source headers");
        }
        var selectedElements = new HashSet<String>();
        for (var child : arguments) {
          if (child.node().syntaxKind() != SyntaxKind.ANNOTATION_ARGUMENT || !child.node().value(SyntaxValue.AnnotationArgument.class)
              .explicitName().equals(annotation.arguments().get(child.position()).explicitName())) {
            throw new IllegalArgumentException("Annotation argument child differs from its captured source header");
          }
          if (!selectedElements.add(child.node().value(SyntaxValue.AnnotationArgument.class).selectedElementKey())) {
            throw new IllegalArgumentException("Annotation argument pairs must select distinct elements");
          }
        }
      }
      case ANNOTATION_ARRAY -> validateAnnotationArray(children);
      case ANNOTATION_ARGUMENT -> {
        requireShape(children, Set.of(SyntaxRole.ANNOTATION_ELEMENT_NAME, SyntaxRole.VALUE));
        requireCount(children, SyntaxRole.VALUE, 1, 1);
        var argument = SyntaxValue.AnnotationArgument.class.cast(value);
        requireCount(children, SyntaxRole.ANNOTATION_ELEMENT_NAME, argument.explicitName().isPresent() ? 1 : 0,
            argument.explicitName().isPresent() ? 1 : 0);
        if (argument.explicitName().isPresent() && !children.stream().filter(child -> child.role() == SyntaxRole.ANNOTATION_ELEMENT_NAME)
            .findFirst().orElseThrow().node().value(SyntaxValue.SourceSymbol.class).name().equals(argument.explicitName().orElseThrow())) {
          throw new IllegalArgumentException("Annotation argument name child differs from its written name");
        }
        var expression = children.stream().filter(child -> child.role() == SyntaxRole.VALUE).findFirst().orElseThrow().node();
        validateAnnotationValueShape(argument.value(), expression);
      }
      case ANNOTATION_NAME_REFERENCE -> {
        requireShape(children, Set.of(SyntaxRole.QUALIFIER));
        requireCount(children, SyntaxRole.QUALIFIER, sourceKind == JavaSourceKind.MEMBER_SELECT ? 1 : 0,
            sourceKind == JavaSourceKind.MEMBER_SELECT ? 1 : 0);
        if (children.stream().anyMatch(child -> child.node().syntaxKind() != SyntaxKind.TYPE_USE)) {
          throw new IllegalArgumentException("A qualified annotation constant requires a structural type qualifier");
        }
      }
      case ANNOTATION_CLASS_LITERAL -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_OPERAND), SyntaxRole.TYPE_OPERAND, 1, 1);
        if (children.getFirst().node().syntaxKind() != SyntaxKind.TYPE_USE) {
          throw new IllegalArgumentException("A class literal requires a structural type operand");
        }
      }
      case CLASS_LITERAL -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_OPERAND), SyntaxRole.TYPE_OPERAND, 0, 1);
        validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
      }
      case ANNOTATION_CAST -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_OPERAND, SyntaxRole.EXPRESSION));
        requireCount(children, SyntaxRole.TYPE_OPERAND, 1, 1);
        requireCount(children, SyntaxRole.EXPRESSION, 1, 1);
        if (children.stream().filter(child -> child.role() == SyntaxRole.TYPE_OPERAND).findFirst().orElseThrow().node()
            .syntaxKind() != SyntaxKind.TYPE_USE) {
          throw new IllegalArgumentException("An annotation cast requires a structural type operand");
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
      case BLOCK, STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK -> requireShape(children, Set.of(SyntaxRole.STATEMENT));
      case LOCAL_DECLARATION -> {
        requireShape(children, Set.of(SyntaxRole.INITIALIZER, SyntaxRole.DECLARED_TYPE, SyntaxRole.ANNOTATION), SyntaxRole.INITIALIZER, 0,
            1);
        validateBodyTypeAttachment(children, SyntaxRole.DECLARED_TYPE);
        var local = (SyntaxValue.LocalDeclaration) value;
        local.bindingEvidence().ifPresent(binding -> {
          requireCount(children, SyntaxRole.INITIALIZER, binding.initialized() ? 1 : 0, binding.initialized() ? 1 : 0);
          requireCount(children, SyntaxRole.ANNOTATION, binding.annotations(), binding.annotations());
          if (binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED) {
            requireCount(children, SyntaxRole.DECLARED_TYPE, 0, 0);
          }
        });
      }
      case MEMBER_REFERENCE -> validateMemberReferenceShape((SyntaxValue.MemberReference) value, children, semanticReference);
      case INSTANCE_OF -> {
        requireShape(children, Set.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND, SyntaxRole.PATTERN), SyntaxRole.EXPRESSION, 1, 1);
        requireCount(children, SyntaxRole.TYPE_OPERAND, 0, 1);
        requireCount(children, SyntaxRole.PATTERN, 0, 1);
        if (children.stream().anyMatch(child -> child.role() == SyntaxRole.TYPE_OPERAND)
            && children.stream().anyMatch(child -> child.role() == SyntaxRole.PATTERN)) {
          throw new IllegalArgumentException("An instance test cannot duplicate its pattern's written type");
        }
        validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
      }
      case BINDING_PATTERN -> {
        requireShape(children, Set.of(SyntaxRole.PARAMETER), SyntaxRole.PARAMETER, 1, 1);
        var binding = children.getFirst().node().value(SyntaxValue.LocalDeclaration.class).bindingEvidence();
        if (binding.filter(evidence -> evidence.context() != LocalBindingEvidence.Context.PATTERN || evidence.initialized()).isPresent()) {
          throw new IllegalArgumentException("A type pattern requires an uninitialized pattern binding");
        }
        ((SyntaxValue.PatternBinding) value).resolvedAccesses().ifPresent(accesses -> {
          if (accesses.stream().anyMatch(access -> !access.sourceFileKey().equals(children.getFirst().node().sourceFileKey()))) {
            throw new IllegalArgumentException("Pattern binding accesses must remain in their captured source file");
          }
          if ("_".equals(children.getFirst().node().value(SyntaxValue.LocalDeclaration.class).name()) && !accesses.isEmpty()) {
            throw new IllegalArgumentException("An unnamed pattern binding cannot have named accesses");
          }
        });
      }
      case DECONSTRUCTION_PATTERN -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_OPERAND, SyntaxRole.PATTERN_COMPONENT));
        validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
      }
      case LAMBDA_EXPRESSION -> {
        requireShape(children, Set.of(SyntaxRole.PARAMETER, SyntaxRole.BODY));
        var lambda = (SyntaxValue.Lambda) value;
        requireCount(children, SyntaxRole.PARAMETER, lambda.parameters().size(), lambda.parameters().size());
        requireCount(children, SyntaxRole.BODY, 1, 1);
        for (var child : children) {
          if (child.role() == SyntaxRole.PARAMETER) {
            var binding = child.node().value(SyntaxValue.LocalDeclaration.class).bindingEvidence().orElseThrow();
            if (binding.context() != LocalBindingEvidence.Context.LAMBDA || binding.initialized()
                || binding.typeForm() != lambda.parameters().get(child.position()).typeForm()) {
              throw new IllegalArgumentException("A lambda parameter must agree with its written form and lexical attachment");
            }
          } else if ((child.node().syntaxKind() == SyntaxKind.BLOCK) != (lambda.bodyKind() == SyntaxValue.LambdaBodyKind.BLOCK)) {
            throw new IllegalArgumentException("A lambda body must agree with its copied written body kind");
          }
        }
      }
      case TYPE_CAST -> {
        requireShape(children, Set.of(SyntaxRole.EXPRESSION, SyntaxRole.TYPE_OPERAND), SyntaxRole.EXPRESSION, 1, 1);
        validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
      }
      case EXPRESSION_STATEMENT, THROW, PARENTHESIZED -> requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 1, 1);
      case RETURN -> requireShape(children, Set.of(SyntaxRole.EXPRESSION), SyntaxRole.EXPRESSION, 0, 1);
      case IF -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.THEN_BRANCH, SyntaxRole.ELSE_BRANCH));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.THEN_BRANCH, 1, 1);
        requireCount(children, SyntaxRole.ELSE_BRANCH, 0, 1);
      }
      case WHILE_LOOP, DO_WHILE_LOOP -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
      }
      case ASSERT -> {
        requireShape(children, Set.of(SyntaxRole.CONDITION, SyntaxRole.DETAIL));
        requireCount(children, SyntaxRole.CONDITION, 1, 1);
        requireCount(children, SyntaxRole.DETAIL, 0, 1);
        if (children.getFirst().role() != SyntaxRole.CONDITION) {
          throw new IllegalArgumentException("An assertion retains its condition before its detail");
        }
      }
      case SYNCHRONIZED -> {
        requireShape(children, Set.of(SyntaxRole.MONITOR, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.MONITOR, 1, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
        if (children.getFirst().role() != SyntaxRole.MONITOR) {
          throw new IllegalArgumentException("A synchronized statement retains its monitor before its block");
        }
      }
      case FOR_LOOP -> {
        requireShape(children, Set.of(SyntaxRole.INITIALIZER, SyntaxRole.CONDITION, SyntaxRole.UPDATE, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.CONDITION, 0, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
      }
      case ENHANCED_FOR_LOOP -> {
        requireShape(children, Set.of(SyntaxRole.PARAMETER, SyntaxRole.ITERABLE, SyntaxRole.BODY));
        requireCount(children, SyntaxRole.PARAMETER, 1, 1);
        requireCount(children, SyntaxRole.ITERABLE, 1, 1);
        requireCount(children, SyntaxRole.BODY, 1, 1);
        var binding = children.stream().filter(child -> child.role() == SyntaxRole.PARAMETER).findFirst().orElseThrow().node();
        if (binding.value() instanceof SyntaxValue.LocalDeclaration local && local.bindingEvidence().isPresent()
            && (local.bindingEvidence().orElseThrow().context() != LocalBindingEvidence.Context.ENHANCED_FOR
                || local.bindingEvidence().orElseThrow().initialized())) {
          throw new IllegalArgumentException("An enhanced-for parameter requires an uninitialized iteration binding");
        }
      }
      case LABELED_STATEMENT -> {
        requireShape(children, Set.of(SyntaxRole.BODY), SyntaxRole.BODY, 1, 1);
        if (((SyntaxValue.StatementLabel) value).name().isEmpty()) {
          throw new IllegalArgumentException("A labeled statement requires its written label");
        }
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
      case SWITCH_EXPRESSION, SWITCH_STATEMENT -> {
        requireShape(children, Set.of(SyntaxRole.SELECTOR, SyntaxRole.CASE));
        requireCount(children, SyntaxRole.SELECTOR, 1, 1);
      }
      case CASE -> validateSwitchCase(SyntaxValue.SwitchCase.class.cast(value), children);
      case YIELD -> requireShape(children, Set.of(SyntaxRole.VALUE), SyntaxRole.VALUE, 1, 1);
      case INVOCATION -> validateInvocation(SyntaxValue.Invocation.class.cast(value), children);
      case OBJECT_CREATION -> validateObjectCreation(SyntaxValue.Invocation.class.cast(value), children);
      case FIELD_ACCESS -> validateFieldAccess(SyntaxValue.FieldAccess.class.cast(value), children);
      case ARRAY_LENGTH -> requireShape(children, Set.of(SyntaxRole.QUALIFIER), SyntaxRole.QUALIFIER, 1, 1);
      case UNARY, UPDATE -> requireShape(children, Set.of(SyntaxRole.OPERAND), SyntaxRole.OPERAND, 1, 1);
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
      case COMPOUND_ASSIGNMENT -> {
        requireShape(children, Set.of(SyntaxRole.LOCATION, SyntaxRole.RIGHT_OPERAND));
        requireCount(children, SyntaxRole.LOCATION, 1, 1);
        requireCount(children, SyntaxRole.RIGHT_OPERAND, 1, 1);
      }
      case ARRAY_CREATION -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_OPERAND, SyntaxRole.DIMENSION, SyntaxRole.ARRAY_ELEMENT, SyntaxRole.ANNOTATION));
        var array = (SyntaxValue.ArrayCreation) value;
        requireCount(children, SyntaxRole.TYPE_OPERAND, 0, array.writtenType() ? 1 : 0);
        validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
        if (array.initializer()) {
          requireCount(children, SyntaxRole.DIMENSION, 0, 0);
          if (array.writtenType() && array.dimensionAnnotationCounts().size() != 1) {
            throw new IllegalArgumentException("An initialized creation retains its first omitted dimension annotation group");
          }
        } else {
          requireCount(children, SyntaxRole.DIMENSION, 1, Integer.MAX_VALUE);
          requireCount(children, SyntaxRole.ARRAY_ELEMENT, 0, 0);
          if (array.dimensionAnnotationCounts().size() != children.stream().filter(child -> child.role() == SyntaxRole.DIMENSION).count()) {
            throw new IllegalArgumentException("An array creation retains one annotation group per sized dimension");
          }
        }
        requireCount(children, SyntaxRole.ANNOTATION, 0, array.dimensionAnnotationCounts().stream().mapToInt(Integer::intValue).sum());
      }
      case ARRAY_ACCESS -> {
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
      case RECEIVER -> {
        requireShape(children, Set.of(SyntaxRole.QUALIFIER));
        var receiver = SyntaxValue.Receiver.class.cast(value).kind();
        requireCount(children, SyntaxRole.QUALIFIER, 0,
            receiver == SyntaxValue.ReceiverKind.QUALIFIED_THIS || receiver == SyntaxValue.ReceiverKind.QUALIFIED_SUPER ? 1 : 0);
      }
      case BREAK, CONTINUE, ANNOTATION_ELEMENT_NAME, VARIABLE_ACCESS, LITERAL, EMPTY, ANY_PATTERN -> requireShape(children, Set.of());
      case RECEIVER_NAME -> {
        requireShape(children, Set.of());
        validateReceiverName(sourceKind, SyntaxValue.SourceSymbol.class.cast(value));
      }
      case TYPE_PARAMETER_DECLARATION -> validateTypeParameterDeclaration(SyntaxValue.TypeParameterDeclaration.class.cast(value), children);
      case UNSUPPORTED -> {
      }
    }
  }

  private static void validateAnnotationArray(List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.VALUE));
    for (var child : children) {
      if (!isAnnotationScalarExpression(child.node().syntaxKind()) && !(child.node().value() instanceof SyntaxValue.Annotation annotation
          && annotation.location() == SyntaxValue.AnnotationLocation.VALUE)) {
        throw new IllegalArgumentException("An annotation array contains an invalid source value");
      }
    }
  }

  private static void validateExecutableChildren(SyntaxKind kind, List<SyntaxChild> children) {
    for (var child : children) {
      var node = child.node();
      if (hasInferredRecordType(node) && !(kind == SyntaxKind.DECONSTRUCTION_PATTERN && child.role() == SyntaxRole.TYPE_OPERAND)
          && !(kind == SyntaxKind.TYPE_USE && child.role() == SyntaxRole.UNDERLYING_TYPE)) {
        throw new IllegalArgumentException("Inferred record type arguments require their deconstruction pattern owner");
      }
      var valid = switch (kind) {
        case BLOCK -> isStatement(node);
        case LOCAL_DECLARATION -> child.role() != SyntaxRole.INITIALIZER || isExpression(node);
        case LAMBDA_EXPRESSION -> child.role() == SyntaxRole.PARAMETER
            ? node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION
            : node.syntaxKind() == SyntaxKind.BLOCK || isExpression(node);
        case MEMBER_REFERENCE -> child.role() == SyntaxRole.TYPE_ARGUMENT
            ? node.syntaxKind() == SyntaxKind.TYPE_USE
            : node.syntaxKind() == SyntaxKind.TYPE_USE || isExpression(node);
        case TYPE_CAST -> child.role() != SyntaxRole.EXPRESSION || isExpression(node);
        case INSTANCE_OF -> switch (child.role()) {
          case EXPRESSION -> isExpression(node);
          case TYPE_OPERAND -> node.syntaxKind() == SyntaxKind.TYPE_USE;
          default -> isPattern(node);
        };
        case BINDING_PATTERN -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION;
        case DECONSTRUCTION_PATTERN -> child.role() == SyntaxRole.TYPE_OPERAND ? node.syntaxKind() == SyntaxKind.TYPE_USE : isPattern(node);
        case ASSERT, EXPRESSION_STATEMENT, RETURN, THROW, PARENTHESIZED, YIELD, ARRAY_LENGTH, UNARY, BINARY, CONDITIONAL_AND,
            CONDITIONAL_OR, ASSIGNMENT, COMPOUND_ASSIGNMENT, UPDATE, ARRAY_ACCESS, CONDITIONAL_EXPRESSION ->
          isExpression(node);
        case IF -> child.role() == SyntaxRole.CONDITION ? isExpression(node) : isStatement(node);
        case SYNCHRONIZED ->
          child.role() == SyntaxRole.MONITOR ? isExpression(node) : isCategory(node, SyntaxKind.BLOCK, JavaSourceKind.BLOCK);
        case WHILE_LOOP, DO_WHILE_LOOP -> child.role() == SyntaxRole.CONDITION ? isExpression(node) : isStatementBody(node);
        case FOR_LOOP -> switch (child.role()) {
          case INITIALIZER -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION || node.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT;
          case UPDATE -> node.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT;
          case CONDITION -> isExpression(node);
          default -> isStatementBody(node);
        };
        case ENHANCED_FOR_LOOP -> switch (child.role()) {
          case PARAMETER -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION;
          case ITERABLE -> isExpression(node);
          default -> isStatementBody(node);
        };
        case LABELED_STATEMENT -> isStatementBody(node);
        case TRY -> switch (child.role()) {
          case RESOURCE -> node.syntaxKind() == SyntaxKind.LOCAL_DECLARATION || node.syntaxKind() == SyntaxKind.VARIABLE_ACCESS
              || node.syntaxKind() == SyntaxKind.FIELD_ACCESS;
          case CATCH_CLAUSE -> isCategory(node, SyntaxKind.CATCH, JavaSourceKind.CATCH);
          default -> isCategory(node, SyntaxKind.BLOCK, JavaSourceKind.BLOCK);
        };
        case CATCH -> child.role() == SyntaxRole.PARAMETER
            ? isCategory(node, SyntaxKind.LOCAL_DECLARATION, JavaSourceKind.VARIABLE)
            : isCategory(node, SyntaxKind.BLOCK, JavaSourceKind.BLOCK);
        case SWITCH_EXPRESSION, SWITCH_STATEMENT ->
          child.role() == SyntaxRole.SELECTOR ? isExpression(node) : isCategory(node, SyntaxKind.CASE, JavaSourceKind.CASE);
        case CASE -> switch (child.role()) {
          case LABEL -> isExpression(node) || isPattern(node);
          case STATEMENT -> isStatement(node);
          case BODY -> isExpression(node) || node.syntaxKind() == SyntaxKind.BLOCK || node.syntaxKind() == SyntaxKind.THROW
              || node.syntaxKind() == SyntaxKind.EXPRESSION_STATEMENT;
          default -> isExpression(node);
        };
        case ARRAY_CREATION -> switch (child.role()) {
          case TYPE_OPERAND -> node.syntaxKind() == SyntaxKind.TYPE_USE;
          case ANNOTATION -> node.syntaxKind() == SyntaxKind.ANNOTATION;
          default -> isExpression(node);
        };
        case INVOCATION, OBJECT_CREATION -> switch (child.role()) {
          case TYPE_ARGUMENT, TYPE_OPERAND -> true;
          case CLASS_BODY -> node.syntaxKind() == SyntaxKind.TYPE_DECLARATION;
          case QUALIFIER -> node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.RECEIVER;
          default -> isExpression(node);
        };
        case FIELD_ACCESS -> child.role() == SyntaxRole.QUALIFIER
            ? node.syntaxKind() == SyntaxKind.TYPE_USE || node.syntaxKind() == SyntaxKind.RECEIVER
            : isExpression(node);
        case CLASS_LITERAL, RECEIVER -> node.syntaxKind() == SyntaxKind.TYPE_USE;
        default -> true;
      };
      if (!valid) {
        throw new IllegalArgumentException("Invalid executable child category for " + kind + '/' + child.role());
      }
    }
  }

  private static boolean isCategory(SyntaxNode node, SyntaxKind category, JavaSourceKind sourceKind) {
    return node.syntaxKind() == category || (node.syntaxKind() == SyntaxKind.UNSUPPORTED && node.sourceKind() == sourceKind);
  }

  private static boolean isExpression(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case INVOCATION, OBJECT_CREATION, ARRAY_CREATION, FIELD_ACCESS, VARIABLE_ACCESS, RECEIVER, LITERAL, CLASS_LITERAL, UNARY, BINARY,
          CONDITIONAL_AND, CONDITIONAL_OR, ASSIGNMENT, COMPOUND_ASSIGNMENT, UPDATE, ARRAY_ACCESS, ARRAY_LENGTH, PARENTHESIZED, TYPE_CAST,
          CONDITIONAL_EXPRESSION, SWITCH_EXPRESSION, LAMBDA_EXPRESSION, MEMBER_REFERENCE, INSTANCE_OF, ANNOTATION_NAME_REFERENCE,
          ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST ->
        true;
      case UNSUPPORTED -> switch (node.sourceKind()) {
        case ARRAY_ACCESS, ASSIGNMENT, CONDITIONAL_EXPRESSION, MEMBER_SELECT, MEMBER_REFERENCE, IDENTIFIER, INSTANCE_OF, METHOD_INVOCATION,
            NEW_ARRAY, NEW_CLASS, LAMBDA_EXPRESSION, PARENTHESIZED, SWITCH_EXPRESSION, TYPE_CAST, POSTFIX_INCREMENT, POSTFIX_DECREMENT,
            PREFIX_INCREMENT, PREFIX_DECREMENT, UNARY_PLUS, UNARY_MINUS, BITWISE_COMPLEMENT, LOGICAL_COMPLEMENT, MULTIPLY, DIVIDE,
            REMAINDER, PLUS, MINUS, LEFT_SHIFT, RIGHT_SHIFT, UNSIGNED_RIGHT_SHIFT, LESS_THAN, GREATER_THAN, LESS_THAN_EQUAL,
            GREATER_THAN_EQUAL, EQUAL_TO, NOT_EQUAL_TO, AND, XOR, OR, CONDITIONAL_AND, CONDITIONAL_OR, MULTIPLY_ASSIGNMENT,
            DIVIDE_ASSIGNMENT, REMAINDER_ASSIGNMENT, PLUS_ASSIGNMENT, MINUS_ASSIGNMENT, LEFT_SHIFT_ASSIGNMENT, RIGHT_SHIFT_ASSIGNMENT,
            UNSIGNED_RIGHT_SHIFT_ASSIGNMENT, AND_ASSIGNMENT, XOR_ASSIGNMENT, OR_ASSIGNMENT, INT_LITERAL, LONG_LITERAL, FLOAT_LITERAL,
            DOUBLE_LITERAL, BOOLEAN_LITERAL, CHAR_LITERAL, STRING_LITERAL, NULL_LITERAL, ERRONEOUS ->
          true;
        default -> false;
      };
      default -> false;
    };
  }

  private static boolean isStatement(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case BLOCK, LOCAL_DECLARATION, EXPRESSION_STATEMENT, RETURN, THROW, IF, WHILE_LOOP, DO_WHILE_LOOP, FOR_LOOP, ENHANCED_FOR_LOOP,
          LABELED_STATEMENT, BREAK, CONTINUE, ASSERT, SYNCHRONIZED, TRY, YIELD, EMPTY, TYPE_DECLARATION, SWITCH_STATEMENT ->
        true;
      case UNSUPPORTED -> switch (node.sourceKind()) {
        case ASSERT, BLOCK, BREAK, CONTINUE, DO_WHILE_LOOP, ENHANCED_FOR_LOOP, EXPRESSION_STATEMENT, FOR_LOOP, IF, LABELED_STATEMENT,
            RETURN, EMPTY_STATEMENT, SWITCH, SYNCHRONIZED, THROW, TRY, VARIABLE, WHILE_LOOP, CLASS, INTERFACE, ENUM, RECORD, YIELD,
            ERRONEOUS ->
          true;
        default -> false;
      };
      default -> false;
    };
  }

  private static boolean isPattern(SyntaxNode node) {
    return switch (node.syntaxKind()) {
      case BINDING_PATTERN, DECONSTRUCTION_PATTERN, ANY_PATTERN -> true;
      default -> false;
    };
  }

  private static boolean hasInferredRecordType(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.TYPE_USE && (node.value() instanceof SyntaxValue.InferredRecordType
        || node.optionalChild(SyntaxRole.UNDERLYING_TYPE).map(SyntaxNode::hasInferredRecordType).orElse(false));
  }

  private static boolean isStatementBody(SyntaxNode node) {
    return isStatement(node) && node.sourceKind() != JavaSourceKind.VARIABLE && node.sourceKind() != JavaSourceKind.CLASS
        && node.sourceKind() != JavaSourceKind.INTERFACE && node.sourceKind() != JavaSourceKind.ENUM
        && node.sourceKind() != JavaSourceKind.RECORD;
  }

  private static boolean isAnnotationArray(SyntaxNode node) {
    return node.syntaxKind() == SyntaxKind.ANNOTATION_ARRAY;
  }

  private static boolean isAnnotationScalarExpression(SyntaxKind kind) {
    return switch (kind) {
      case LITERAL, UNARY, BINARY, CONDITIONAL_AND, CONDITIONAL_OR, PARENTHESIZED, CONDITIONAL_EXPRESSION, ANNOTATION_NAME_REFERENCE,
          ANNOTATION_CLASS_LITERAL, ANNOTATION_CAST ->
        true;
      default -> false;
    };
  }

  private static void validateAnnotationArgumentSource(SyntaxValue.AnnotationArgument argument, JavaSourceKind sourceKind,
      Optional<SourceSpan> origin, List<SyntaxChild> children) {
    var expression = children.stream().filter(child -> child.role() == SyntaxRole.VALUE).findFirst().orElseThrow().node();
    if (argument.explicitName().isPresent()) {
      if (sourceKind != JavaSourceKind.ASSIGNMENT) {
        throw new IllegalArgumentException("A named annotation pair requires an assignment source kind");
      }
    } else if (sourceKind == JavaSourceKind.ASSIGNMENT || sourceKind != expression.sourceKind() || !origin.equals(expression.origin())) {
      throw new IllegalArgumentException("A shorthand annotation pair must match its source value expression");
    }
  }

  private static void validateAnnotationArgumentType(SyntaxValue.AnnotationArgument argument, SyntaxType type, List<SyntaxChild> children) {
    var expression = children.stream().filter(child -> child.role() == SyntaxRole.VALUE).findFirst().orElseThrow().node();
    validateAnnotationValue(argument.value(), type, expression);
  }

  private static void validateAnnotationValueShape(AnnotationElementValue value, SyntaxNode expression) {
    var valid = switch (value) {
      case AnnotationScalar scalar -> isAnnotationScalarExpression(expression.syntaxKind())
          && (!(scalar instanceof AnnotationScalar.EnumConstant) || (expression.syntaxKind() == SyntaxKind.ANNOTATION_NAME_REFERENCE
              && expression.semanticReference().orElseThrow().referenceKind() == ReferenceKind.ENUM_CONSTANT))
          && (!(scalar instanceof AnnotationScalar.ClassLiteral) || expression.syntaxKind() == SyntaxKind.ANNOTATION_CLASS_LITERAL);
      case AnnotationElementValue.Nested nested -> completeNestedAnnotation(nested, expression);
      case AnnotationElementValue.Array array -> isAnnotationArray(expression)
          ? expression.children(SyntaxRole.VALUE).size() == array.elements().size()
          : array.elements().size() == 1 && !isAnnotationArray(expression);
    };
    if (!valid) {
      throw new IllegalArgumentException("Annotation value does not match its written source form");
    }
  }

  private static boolean completeNestedAnnotation(AnnotationElementValue.Nested value, SyntaxNode expression) {
    if (expression.syntaxKind() != SyntaxKind.ANNOTATION || !(expression.value() instanceof SyntaxValue.Annotation annotation)
        || annotation.location() != SyntaxValue.AnnotationLocation.VALUE || annotation.argumentFailureCode().isPresent()) {
      return false;
    }
    return expression
        .semanticReference().filter(reference -> reference.referenceKind() == ReferenceKind.TYPE
            && reference.status() == ResolutionStatus.RESOLVED && reference.symbolKey().equals(Optional.of(value.annotationTypeKey())))
        .isPresent();
  }

  private static void validateAnnotationValue(AnnotationElementValue value, SyntaxType type, SyntaxNode expression) {
    if (!(type instanceof SyntaxType.Resolved resolved) || resolved.structure().isEmpty()) {
      throw new IllegalArgumentException("An annotation value requires a resolved structural element type");
    }
    validateAnnotationValue(value, resolved.structure().orElseThrow(), expression);
  }

  private static void validateAnnotationValue(AnnotationElementValue value, JavaTypeStructure selected, SyntaxNode expression) {
    validateAnnotationValueShape(value, expression);
    switch (value) {
      case AnnotationScalar scalar -> validateAnnotationScalar(scalar, selected, expression);
      case AnnotationElementValue.Nested nested -> {
        if (!(selected instanceof JavaTypeStructure.Declared declared) || !declared.declarationKey().equals(nested.annotationTypeKey())
            || !expression.semanticReference().flatMap(SemanticReference::symbolKey).equals(Optional.of(nested.annotationTypeKey()))) {
          throw new IllegalArgumentException("A nested annotation must match its selected annotation element type");
        }
      }
      case AnnotationElementValue.Array array -> {
        if (!(selected instanceof JavaTypeStructure.Array selectedArray) || selectedArray.component() instanceof JavaTypeStructure.Array) {
          throw new IllegalArgumentException("An annotation array requires a nonarray element type");
        }
        if (isAnnotationArray(expression)) {
          var sourceValues = expression.children(SyntaxRole.VALUE);
          for (var index = 0; index < array.elements().size(); index++) {
            validateAnnotationValue(array.elements().get(index), selectedArray.component(), sourceValues.get(index));
          }
        } else {
          validateAnnotationValue(array.elements().getFirst(), selectedArray.component(), expression);
        }
      }
    }
  }

  private static void validateAnnotationScalar(AnnotationScalar scalar, JavaTypeStructure selected, SyntaxNode expression) {
    var matches = switch (scalar) {
      case AnnotationScalar.BooleanValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.BOOLEAN;
      case AnnotationScalar.ByteValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.BYTE;
      case AnnotationScalar.ShortValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.SHORT;
      case AnnotationScalar.CharValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.CHAR;
      case AnnotationScalar.IntValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.INT;
      case AnnotationScalar.LongValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.LONG;
      case AnnotationScalar.FloatValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.FLOAT;
      case AnnotationScalar.DoubleValue _ ->
        selected instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == JavaTypeCategory.DOUBLE;
      case AnnotationScalar.StringValue _ ->
        selected instanceof JavaTypeStructure.Declared declared && "type:java.lang.String".equals(declared.declarationKey());
      case AnnotationScalar.EnumConstant constant ->
        selected instanceof JavaTypeStructure.Declared declared && declared.declarationKey().equals(constant.enumTypeKey());
      case AnnotationScalar.ClassLiteral _ ->
        selected instanceof JavaTypeStructure.Declared declared && "type:java.lang.Class".equals(declared.declarationKey());
    };
    if (!matches) {
      throw new IllegalArgumentException("Annotation scalar category conflicts with the selected element type");
    }
    if (!(expression.type() instanceof SyntaxType.Resolved sourceType) || sourceType.structure().isEmpty()) {
      throw new IllegalArgumentException("An annotation expression requires an independently resolved structural type");
    }
    if (scalar instanceof AnnotationScalar.EnumConstant constant
        && !expression.semanticReference().flatMap(SemanticReference::symbolKey).equals(Optional.of(constant.declarationKey()))) {
      throw new IllegalArgumentException("An enum annotation value must match its source constant reference");
    }
    if (scalar instanceof AnnotationScalar.ClassLiteral literal) {
      var operand = expression.requiredChild(SyntaxRole.TYPE_OPERAND);
      if (!(operand.type() instanceof SyntaxType.Resolved operandType)
          || !operandType.structure().equals(Optional.of(literal.operandType()))) {
        throw new IllegalArgumentException("A class annotation value must match its written type operand");
      }
    }
    var sourceCategory = sourceType.identity().category();
    var compatible = switch (scalar) {
      case AnnotationScalar.BooleanValue _ -> sourceCategory == JavaTypeCategory.BOOLEAN;
      case AnnotationScalar.ByteValue _,AnnotationScalar.ShortValue _,AnnotationScalar.CharValue _,AnnotationScalar.IntValue _ ->
        Set.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR, JavaTypeCategory.INT).contains(sourceCategory);
      case AnnotationScalar.LongValue _ ->
        Set.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR, JavaTypeCategory.INT, JavaTypeCategory.LONG)
            .contains(sourceCategory);
      case AnnotationScalar.FloatValue _ -> Set.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR,
          JavaTypeCategory.INT, JavaTypeCategory.LONG, JavaTypeCategory.FLOAT).contains(sourceCategory);
      case AnnotationScalar.DoubleValue _ -> Set.of(JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.CHAR,
          JavaTypeCategory.INT, JavaTypeCategory.LONG, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE).contains(sourceCategory);
      case AnnotationScalar.StringValue _ -> "java.lang.String".equals(sourceType.identity().erasedName());
      case AnnotationScalar.EnumConstant constant -> sourceType.structure().orElseThrow() instanceof JavaTypeStructure.Declared declared
          && declared.declarationKey().equals(constant.enumTypeKey());
      case AnnotationScalar.ClassLiteral _ -> sourceType.structure().orElseThrow() instanceof JavaTypeStructure.Declared declared
          && "type:java.lang.Class".equals(declared.declarationKey());
    };
    if (!compatible) {
      throw new IllegalArgumentException("Annotation scalar conflicts with source expression type");
    }
  }

  private static void validateMemberReferenceShape(SyntaxValue.MemberReference reference, List<SyntaxChild> children,
      Optional<SemanticReference> semanticReference) {
    requireShape(children, Set.of(SyntaxRole.QUALIFIER, SyntaxRole.TYPE_ARGUMENT), SyntaxRole.QUALIFIER, 0, 1);
    var selectedType = reference.selectedType();
    if (selectedType instanceof SyntaxType.Resolved resolved
        && resolved.structure().orElse(null) instanceof JavaTypeStructure.Executable executable) {
      if (reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR
          && executable.erasure().returnType().knownIdentity().orElseThrow().category() != JavaTypeCategory.VOID) {
        throw new IllegalArgumentException("A constructor reference requires a void declaration return type");
      }
    }
    var selectedIdentity = selectedType.knownIdentity();
    if (selectedIdentity
        .filter(identity -> identity.category() != (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR
            ? JavaTypeCategory.ARRAY
            : JavaTypeCategory.EXECUTABLE))
        .isPresent()) {
      throw new IllegalArgumentException("A reference requires an executable selection or its exact array type");
    }
    for (var child : children) {
      if (child.role() != SyntaxRole.QUALIFIER)
        continue;
      var qualifier = child.node();
      var qualifierIdentity = qualifier.type().knownIdentity();
      var writtenArray = isWrittenArrayType(qualifier);
      var valid = switch (reference.qualifier()) {
        case TYPE, ARRAY_TYPE -> qualifier.syntaxKind() == SyntaxKind.TYPE_USE;
        case EXPRESSION -> isExpression(qualifier);
        case SUPER -> qualifier.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.SUPER;
        case QUALIFIED_SUPER ->
          qualifier.value() instanceof SyntaxValue.Receiver receiver && receiver.kind() == SyntaxValue.ReceiverKind.QUALIFIED_SUPER;
      };
      if (!valid
          || ((reference.qualifier() == SyntaxValue.ReferenceQualifier.TYPE
              || reference.qualifier() == SyntaxValue.ReferenceQualifier.ARRAY_TYPE)
              && qualifierIdentity.filter(identity -> (identity.category() == JavaTypeCategory.ARRAY) != writtenArray).isPresent())
          || (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR
              && selectedIdentity.filter(identity -> qualifierIdentity.filter(known -> !identity.equals(known)).isPresent()).isPresent())
          || (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR
              && selectedType instanceof SyntaxType.Resolved selected && qualifier.type() instanceof SyntaxType.Resolved written
              && selected.structure().isPresent() && written.structure().isPresent()
              && !selected.structure().equals(written.structure()))) {
        throw new IllegalArgumentException("A reference qualifier must retain its exact written form and constructed array type");
      }
      var knownArray = qualifierIdentity.filter(identity -> identity.category() == JavaTypeCategory.ARRAY).isPresent();
      if ((reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR
          && ((reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR) != writtenArray
              || (knownArray && reference.selection() != SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR)))
          || ((writtenArray || knownArray) && reference.mode() == SyntaxValue.ReferenceMode.METHOD && "clone".equals(reference.name())
              && reference.selection() != SyntaxValue.ReferenceSelection.ARRAY_CLONE)) {
        throw new IllegalArgumentException("A reference selection must agree with its actual array qualifier");
      }
      if (reference.mode() == SyntaxValue.ReferenceMode.CONSTRUCTOR && reference.selection() == SyntaxValue.ReferenceSelection.EXECUTABLE
          && (selectedIdentity.isPresent()
              || semanticReference.filter(selection -> selection.status() == ResolutionStatus.RESOLVED).isPresent())
          && isKnownNonClassConstructorQualifier(qualifier)) {
        throw new IllegalArgumentException("A selected class constructor requires a declared qualifier type");
      }
      if (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CLONE
          && ((reference.qualifier() == SyntaxValue.ReferenceQualifier.TYPE && !writtenArray)
              || qualifierIdentity.filter(identity -> identity.category() != JavaTypeCategory.ARRAY).isPresent())) {
        throw new IllegalArgumentException("An intrinsic array clone reference requires its actual array qualifier");
      }
      if (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CONSTRUCTOR) {
        validateKnownTypeUseType(qualifier, selectedType);
      }
    }
    if (reference.selection() == SyntaxValue.ReferenceSelection.ARRAY_CLONE && selectedType instanceof SyntaxType.Resolved resolved
        && resolved.structure().orElse(null) instanceof JavaTypeStructure.Executable executable
        && (!executable.variables().isEmpty() || !executable.parameters().isEmpty() || !executable.thrownTypes().isEmpty()
            || executable.receiver().isPresent()
            || executable.erasure().returnType().knownIdentity().orElseThrow().category() != JavaTypeCategory.DECLARED
            || !"java.lang.Object".equals(executable.erasure().returnType().knownIdentity().orElseThrow().erasedName())
            || executable.returnType().knownIdentity()
                .filter(identity -> identity.category() != JavaTypeCategory.DECLARED || !"java.lang.Object".equals(identity.erasedName()))
                .isPresent()
            || (executable.returnType() instanceof SyntaxType.Resolved result && result.structure().isPresent() && !result.structure()
                .orElseThrow().equals(new JavaTypeStructure.Declared("type:java.lang.Object", Optional.empty(), List.of()))))) {
      throw new IllegalArgumentException("An intrinsic array clone requires its compiler declaration signature");
    }
  }

  private static boolean isKnownNonClassConstructorQualifier(SyntaxNode qualifier) {
    var knownCategory = qualifier.type().knownIdentity().filter(identity -> identity.category() != JavaTypeCategory.DECLARED).isPresent();
    return knownCategory || qualifier.sourceKind() == JavaSourceKind.PRIMITIVE_TYPE
        || qualifier.semanticReference()
            .filter(
                reference -> reference.status() == ResolutionStatus.RESOLVED && reference.referenceKind() == ReferenceKind.TYPE_PARAMETER)
            .isPresent()
        || (qualifier.sourceKind() == JavaSourceKind.ANNOTATED_TYPE
            && isKnownNonClassConstructorQualifier(qualifier.requiredChild(SyntaxRole.UNDERLYING_TYPE)));
  }

  static void validateKnownTypeUseStructure(SyntaxNode written, JavaTypeStructure selected) {
    if (written.type().knownIdentity().filter(identity -> identity.category() != selected.category()).isPresent()
        || !matchesWrittenNominalCategory(written, selected.category(), false)
        || (written.type() instanceof SyntaxType.Resolved resolved && resolved.structure().isPresent()
            && !resolved.structure().orElseThrow().equals(selected))
        || (written.value() instanceof SyntaxValue.PrimitiveType primitive
            && !new JavaTypeStructure.Primitive(primitive.kind()).equals(selected))
        || written.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED)
            .filter(reference -> switch (reference.referenceKind()) {
              case TYPE -> typeUseDeclaration(selected)
                  .filter(declaration -> reference.symbolKey().equals(Optional.of(declaration.declarationKey()))).isEmpty();
              case TYPE_PARAMETER -> !(selected instanceof JavaTypeStructure.Variable variable)
                  || !reference.symbolKey().equals(Optional.of(variable.parameterKey()));
              default -> false;
            }).isPresent()) {
      throw new IllegalArgumentException("A selected type must agree with its known written components");
    }
    if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateKnownTypeUseStructure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), selected);
    } else if (written.sourceKind() == JavaSourceKind.ARRAY_TYPE) {
      if (!(selected instanceof JavaTypeStructure.Array array)) {
        throw new IllegalArgumentException("A selected type must agree with its known written components");
      }
      validateKnownTypeUseStructure(written.requiredChild(SyntaxRole.COMPONENT_TYPE), array.component());
    } else if (written.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      var declared = typeUseDeclaration(selected)
          .orElseThrow(() -> new IllegalArgumentException("A parameterized written type requires declared selection components"));
      var arguments = written.children(SyntaxRole.TYPE_ARGUMENT);
      if (!(written.value() instanceof SyntaxValue.DiamondType) && arguments.size() != declared.arguments().size()) {
        throw new IllegalArgumentException("Known selected type arguments must match written argument count");
      }
      for (var index = 0; index < arguments.size(); index++) {
        validateKnownTypeUseStructure(arguments.get(index), declared.arguments().get(index));
      }
      var name = written.requiredChild(SyntaxRole.TYPE_NAME);
      if (name.semanticReference().filter(reference -> reference.status() == ResolutionStatus.RESOLVED
          && !reference.symbolKey().equals(Optional.of(declared.declarationKey()))).isPresent()) {
        throw new IllegalArgumentException("Known selected type must match its written type name");
      }
    } else if (Set.of(JavaSourceKind.UNBOUNDED_WILDCARD, JavaSourceKind.EXTENDS_WILDCARD, JavaSourceKind.SUPER_WILDCARD)
        .contains(written.sourceKind())) {
      var kind = switch (written.sourceKind()) {
        case UNBOUNDED_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED;
        case EXTENDS_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.EXTENDS;
        case SUPER_WILDCARD -> JavaTypeStructure.Wildcard.BoundKind.SUPER;
        default -> throw new IllegalStateException("Expected a written wildcard");
      };
      if (!(selected instanceof JavaTypeStructure.Wildcard wildcard) || wildcard.kind() != kind
          || wildcard.bound().isPresent() != (kind != JavaTypeStructure.Wildcard.BoundKind.UNBOUNDED)) {
        throw new IllegalArgumentException("A selected wildcard must agree with its written bound form");
      }
      var role = switch (written.sourceKind()) {
        case EXTENDS_WILDCARD -> SyntaxRole.UPPER_BOUND;
        case SUPER_WILDCARD -> SyntaxRole.LOWER_BOUND;
        default -> null;
      };
      if (role != null) {
        validateKnownTypeUseStructure(written.requiredChild(role), wildcard.bound().orElseThrow());
      }
    }
  }

  static void validateKnownTypeUseType(SyntaxNode written, SyntaxType selected) {
    if (!matchingResolvedType(written.type(), selected)) {
      throw new IllegalArgumentException("A selected signature type must agree with its known written type");
    }
    if (!matchesWrittenArrayDimensions(written.sourceKind(), written.children(), selected)) {
      throw new IllegalArgumentException("A selected type must agree with its known written components");
    }
    if (selected instanceof SyntaxType.Resolved resolved) {
      resolved.structure().ifPresent(structure -> validateKnownTypeUseStructure(written, structure));
    }
  }

  static void validateKnownTypeUseErasure(SyntaxNode written, SyntaxType erased) {
    JavaExecutableErasure.validateKnown(written.type(), erased);
    var category = erased.knownIdentity().orElseThrow().category();
    var matches = switch (written.sourceKind()) {
      case PRIMITIVE_TYPE -> category == written.value(SyntaxValue.PrimitiveType.class).kind();
      case ARRAY_TYPE -> category == JavaTypeCategory.ARRAY;
      case PARAMETERIZED_TYPE, IDENTIFIER, MEMBER_SELECT -> category == JavaTypeCategory.DECLARED;
      default -> true;
    };
    if (!matches || !matchesWrittenArrayDimensions(written.sourceKind(), written.children(), erased)) {
      throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
    }
    if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateKnownTypeUseErasure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), erased);
    } else if (erased instanceof SyntaxType.Resolved resolved) {
      resolved.structure().ifPresent(structure -> validateWrittenErasedStructure(written, structure));
    }
  }

  private static boolean matchesWrittenArrayDimensions(JavaSourceKind sourceKind, List<SyntaxChild> children, SyntaxType selected) {
    return selected.knownIdentity()
        .filter(identity -> identity.category() != JavaTypeCategory.ERROR && identity.category() != JavaTypeCategory.OTHER)
        .map(identity -> writtenArrayDimensions(sourceKind, children)
            .map(dimensions -> dimensions == JavaExecutableErasure.erasedArrayDimensions(identity.erasedName())).orElse(true))
        .orElse(true);
  }

  private static Optional<Integer> writtenArrayDimensions(JavaSourceKind sourceKind, List<SyntaxChild> children) {
    return switch (sourceKind) {
      case ARRAY_TYPE, ANNOTATED_TYPE -> {
        var role = sourceKind == JavaSourceKind.ARRAY_TYPE ? SyntaxRole.COMPONENT_TYPE : SyntaxRole.UNDERLYING_TYPE;
        var component = children.stream().filter(child -> child.role() == role).findFirst().orElseThrow().node();
        var dimensions = writtenArrayDimensions(component.sourceKind(), component.children());
        yield sourceKind == JavaSourceKind.ARRAY_TYPE ? dimensions.map(value -> value + 1) : dimensions;
      }
      case PRIMITIVE_TYPE, PARAMETERIZED_TYPE, IDENTIFIER, MEMBER_SELECT -> Optional.of(0);
      default -> Optional.empty();
    };
  }

  private static void validateWrittenErasedStructure(SyntaxNode written, JavaTypeStructure erased) {
    if (written.sourceKind() == JavaSourceKind.ARRAY_TYPE) {
      if (!(erased instanceof JavaTypeStructure.Array array)) {
        throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
      }
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.COMPONENT_TYPE), array.component());
    } else if (written.sourceKind() == JavaSourceKind.PARAMETERIZED_TYPE) {
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.TYPE_NAME), erased);
    } else if (written.sourceKind() == JavaSourceKind.ANNOTATED_TYPE) {
      validateWrittenErasedStructure(written.requiredChild(SyntaxRole.UNDERLYING_TYPE), erased);
    } else if (!matchesWrittenNominalCategory(written, erased.category(), true)
        || (written.value() instanceof SyntaxValue.PrimitiveType primitive
            && !new JavaTypeStructure.Primitive(primitive.kind()).equals(erased))
        || written.semanticReference()
            .filter(reference -> reference.status() == ResolutionStatus.RESOLVED && reference.referenceKind() == ReferenceKind.TYPE)
            .filter(reference -> typeUseDeclaration(erased)
                .filter(declaration -> reference.symbolKey().equals(Optional.of(declaration.declarationKey()))).isEmpty())
            .isPresent()) {
      throw new IllegalArgumentException("A selected erasure must agree with its known written type form");
    }
  }

  private static boolean matchesWrittenNominalCategory(SyntaxNode written, JavaTypeCategory category, boolean erased) {
    return switch (written.sourceKind()) {
      case IDENTIFIER -> category == JavaTypeCategory.DECLARED || (!erased && category == JavaTypeCategory.TYPE_VARIABLE);
      case MEMBER_SELECT -> category == JavaTypeCategory.DECLARED;
      default -> true;
    };
  }

  private static boolean isWrittenArrayType(SyntaxNode node) {
    return switch (node.sourceKind()) {
      case ARRAY_TYPE -> true;
      case ANNOTATED_TYPE -> isWrittenArrayType(node.requiredChild(SyntaxRole.UNDERLYING_TYPE));
      default -> false;
    };
  }

  private static void validateTypeUse(JavaSourceKind sourceKind, SyntaxValue value, List<SyntaxChild> children) {
    if ((sourceKind == JavaSourceKind.IDENTIFIER || sourceKind == JavaSourceKind.MEMBER_SELECT)
        && !(value instanceof SyntaxValue.SourceSymbol || value instanceof SyntaxValue.PackageQualifier
            || value instanceof SyntaxValue.InferredRecordType)) {
      throw new IllegalArgumentException("A written type name requires a source symbol or package qualifier");
    }
    switch (sourceKind) {
      case PRIMITIVE_TYPE, IDENTIFIER, UNBOUNDED_WILDCARD -> requireShape(children, Set.of());
      case MEMBER_SELECT -> requireShape(children, Set.of(SyntaxRole.TYPE_QUALIFIER), SyntaxRole.TYPE_QUALIFIER, 1, 1);
      case PARAMETERIZED_TYPE -> {
        requireShape(children, Set.of(SyntaxRole.TYPE_NAME, SyntaxRole.TYPE_ARGUMENT));
        requireCount(children, SyntaxRole.TYPE_NAME, 1, 1);
        requireCount(children, SyntaxRole.TYPE_ARGUMENT, value instanceof SyntaxValue.DiamondType ? 0 : 1,
            value instanceof SyntaxValue.DiamondType ? 0 : Integer.MAX_VALUE);
      }
      case ARRAY_TYPE -> requireShape(children, Set.of(SyntaxRole.COMPONENT_TYPE), SyntaxRole.COMPONENT_TYPE, 1, 1);
      case EXTENDS_WILDCARD -> requireShape(children, Set.of(SyntaxRole.UPPER_BOUND), SyntaxRole.UPPER_BOUND, 1, 1);
      case SUPER_WILDCARD -> requireShape(children, Set.of(SyntaxRole.LOWER_BOUND), SyntaxRole.LOWER_BOUND, 1, 1);
      case INTERSECTION_TYPE ->
        requireShape(children, Set.of(SyntaxRole.INTERSECTION_BOUND), SyntaxRole.INTERSECTION_BOUND, 2, Integer.MAX_VALUE);
      case UNION_TYPE -> requireShape(children, Set.of(SyntaxRole.UNION_ALTERNATIVE), SyntaxRole.UNION_ALTERNATIVE, 2, Integer.MAX_VALUE);
      case ANNOTATED_TYPE -> {
        requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.UNDERLYING_TYPE));
        requireCount(children, SyntaxRole.ANNOTATION, value instanceof SyntaxValue.SharedTypeAnnotations ? 0 : 1,
            value instanceof SyntaxValue.SharedTypeAnnotations ? 0 : Integer.MAX_VALUE);
        requireCount(children, SyntaxRole.UNDERLYING_TYPE, 1, 1);
      }
      default -> requireShape(children, Set.of());
    }
    for (var child : children) {
      var valid = switch (child.role()) {
        case TYPE_NAME, TYPE_ARGUMENT, TYPE_QUALIFIER, COMPONENT_TYPE, UNDERLYING_TYPE, UPPER_BOUND, LOWER_BOUND, INTERSECTION_BOUND,
            UNION_ALTERNATIVE ->
          child.node().syntaxKind() == SyntaxKind.TYPE_USE;
        case ANNOTATION -> child.node().syntaxKind() == SyntaxKind.ANNOTATION;
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
    var known = type.knownIdentity();
    if (known.isPresent()) {
      var category = known.orElseThrow().category();
      var matches = switch (sourceKind) {
        case PRIMITIVE_TYPE -> category == ((SyntaxValue.PrimitiveType) value).kind();
        case ARRAY_TYPE -> category == JavaTypeCategory.ARRAY;
        case PARAMETERIZED_TYPE, MEMBER_SELECT -> category == JavaTypeCategory.DECLARED;
        case IDENTIFIER -> category == JavaTypeCategory.DECLARED || category == JavaTypeCategory.TYPE_VARIABLE;
        case UNBOUNDED_WILDCARD, EXTENDS_WILDCARD, SUPER_WILDCARD -> category == JavaTypeCategory.WILDCARD;
        case INTERSECTION_TYPE -> category == JavaTypeCategory.INTERSECTION;
        case UNION_TYPE -> category == JavaTypeCategory.UNION;
        default -> true;
      };
      if (!matches || reference
          .filter(selection -> selection.status() == ResolutionStatus.RESOLVED
              && ((selection.referenceKind() == ReferenceKind.TYPE && category != JavaTypeCategory.DECLARED)
                  || (selection.referenceKind() == ReferenceKind.TYPE_PARAMETER && category != JavaTypeCategory.TYPE_VARIABLE)))
          .isPresent()) {
        throw new IllegalArgumentException("Written type-use kind conflicts with known attributed category");
      }
      if (!matchesWrittenArrayDimensions(sourceKind, children, type)) {
        throw new IllegalArgumentException("Written type-use dimensions conflict with known attributed identity");
      }
      if (sourceKind == JavaSourceKind.ANNOTATED_TYPE) {
        var underlying = children.stream().filter(child -> child.role() == SyntaxRole.UNDERLYING_TYPE).findFirst().orElseThrow().node();
        if (underlying.type().knownIdentity()
            .filter(identity -> identity.category() != category || !identity.erasedName().equals(known.orElseThrow().erasedName()))
            .isPresent()) {
          throw new IllegalArgumentException("Annotated source type conflicts with its known underlying type");
        }
      }
    }
    if (!(type instanceof SyntaxType.Resolved resolved)) {
      return;
    }
    var structure = resolved.structure()
        .orElseThrow(() -> new IllegalArgumentException("A resolved type use requires recursive attributed structure"));
    var declarationType = typeUseDeclaration(structure);
    var matchingSource = switch (sourceKind) {
      case PRIMITIVE_TYPE ->
        structure instanceof JavaTypeStructure.Primitive primitive && primitive.kind() == ((SyntaxValue.PrimitiveType) value).kind();
      case ARRAY_TYPE -> structure instanceof JavaTypeStructure.Array;
      case PARAMETERIZED_TYPE, MEMBER_SELECT -> declarationType.isPresent();
      case IDENTIFIER -> declarationType.isPresent() || structure instanceof JavaTypeStructure.Variable;
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
        && declarationType.filter(declared -> !declared.arguments().isEmpty()).isPresent()
        && path.steps().getLast().role() != SyntaxRole.TYPE_NAME && !(value instanceof SyntaxValue.InferredRecordType)) {
      throw new IllegalArgumentException("A written raw type name cannot carry attributed type arguments");
    }
    if (sourceKind == JavaSourceKind.ARRAY_TYPE
        && !structure.equals(new JavaTypeStructure.Array(requiredTypeUseStructure(children, SyntaxRole.COMPONENT_TYPE, 0)))) {
      throw new IllegalArgumentException("Array source component conflicts with attributed component");
    }
    if (Set.of(JavaSourceKind.UNBOUNDED_WILDCARD, JavaSourceKind.EXTENDS_WILDCARD, JavaSourceKind.SUPER_WILDCARD).contains(sourceKind)
        && structure instanceof JavaTypeStructure.Wildcard wildcard) {
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
    if (sourceKind == JavaSourceKind.MEMBER_SELECT && declarationType.isPresent()) {
      var declared = declarationType.orElseThrow();
      var qualifier = children.getFirst().node();
      if (qualifier.value() instanceof SyntaxValue.PackageQualifier) {
        if (declared.lexicalOwnerKey().isPresent() || declared.enclosingType().isPresent()) {
          throw new IllegalArgumentException("A package-qualified type cannot carry an enclosing type owner");
        }
      } else {
        var written = requiredTypeUseStructure(children, SyntaxRole.TYPE_QUALIFIER, 0);
        var writtenDeclaration = typeUseDeclaration(written);
        if (writtenDeclaration.isEmpty() && !(written instanceof JavaTypeStructure.Variable)) {
          throw new IllegalArgumentException("A written type qualifier requires a declared or type-variable type");
        }
        if (writtenDeclaration.isPresent()
            && declared.lexicalOwnerKey().equals(Optional.of(writtenDeclaration.orElseThrow().declarationKey()))
            && declared.enclosingType().isPresent()
            && (path.steps().getLast().role() == SyntaxRole.TYPE_NAME
                ? !typeUseDeclaration(declared.enclosingType().orElseThrow()).orElseThrow().declarationKey()
                    .equals(writtenDeclaration.orElseThrow().declarationKey())
                : !declared.enclosingType().orElseThrow().equals(written))) {
          throw new IllegalArgumentException("Written type qualifier conflicts with selected enclosing declaration");
        }
      }
    }
    if (sourceKind == JavaSourceKind.PARAMETERIZED_TYPE && declarationType.isPresent()) {
      var declared = declarationType.orElseThrow();
      var name = requiredTypeUseStructure(children, SyntaxRole.TYPE_NAME, 0);
      var selected = typeUseDeclaration(name);
      if (selected.isEmpty() || !selected.orElseThrow().declarationKey().equals(declared.declarationKey())) {
        throw new IllegalArgumentException("Written type name conflicts with selected declaration");
      }
      if (!declared.lexicalOwnerKey().equals(selected.orElseThrow().lexicalOwnerKey())) {
        throw new IllegalArgumentException("Written type name conflicts with selected lexical owner");
      }
      var writtenName = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_NAME).findFirst().orElseThrow().node();
      if (writtenName.sourceKind() == JavaSourceKind.MEMBER_SELECT) {
        var qualifier = writtenName.children().stream().filter(child -> child.role() == SyntaxRole.TYPE_QUALIFIER).findFirst().orElseThrow()
            .node();
        if (!(qualifier.value() instanceof SyntaxValue.PackageQualifier) && declared.enclosingType().isPresent()) {
          var writtenQualifier = requiredTypeUseStructure(writtenName.children(), SyntaxRole.TYPE_QUALIFIER, 0);
          var qualifierType = typeUseDeclaration(writtenQualifier);
          if (qualifierType.isPresent() && declared.lexicalOwnerKey().equals(Optional.of(qualifierType.orElseThrow().declarationKey()))
              && !declared.enclosingType().equals(Optional.of(writtenQualifier))) {
            throw new IllegalArgumentException("Written type qualifier conflicts with attributed enclosing type");
          }
        }
      }
      var arguments = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
          .map(child -> requiredTypeUseStructure(children, SyntaxRole.TYPE_ARGUMENT, child.position())).toList();
      if (!(value instanceof SyntaxValue.DiamondType) && !arguments.equals(declared.arguments())) {
        throw new IllegalArgumentException("Written type arguments conflict with attributed arguments");
      }
    }
    if (declarationType.isPresent()) {
      requireTypeUseTarget(reference, ReferenceKind.TYPE, declarationType.orElseThrow().declarationKey());
    } else if (structure instanceof JavaTypeStructure.Variable variable) {
      requireTypeUseTarget(reference, ReferenceKind.TYPE_PARAMETER, variable.parameterKey());
    }
  }

  private static Optional<TypeUseDeclaration> typeUseDeclaration(JavaTypeStructure structure) {
    return switch (structure) {
      case JavaTypeStructure.Declared declared -> Optional.of(
          new TypeUseDeclaration(declared.declarationKey(), declared.lexicalOwnerKey(), declared.enclosingType(), declared.arguments()));
      case JavaTypeStructure.LocalDeclared local -> Optional.of(
          new TypeUseDeclaration(local.declarationKey(), Optional.of(local.lexicalOwnerKey()), local.enclosingType(), local.arguments()));
      default -> Optional.empty();
    };
  }

  private record TypeUseDeclaration(String declarationKey, Optional<String> lexicalOwnerKey, Optional<JavaTypeStructure> enclosingType,
      List<JavaTypeStructure> arguments) {
  }

  private static JavaTypeStructure requiredTypeUseStructure(List<SyntaxChild> children, SyntaxRole role, int position) {
    return children.stream().filter(child -> child.role() == role && child.position() == position).findFirst().map(SyntaxChild::node)
        .map(SyntaxNode::type).filter(SyntaxType.Resolved.class::isInstance).map(SyntaxType.Resolved.class::cast)
        .flatMap(SyntaxType.Resolved::structure)
        .orElseThrow(() -> new IllegalArgumentException(
            "A written type component requires recursive attributed structure: " + role + '[' + position + "] among "
                + children.stream().map(child -> child.role() + "[" + child.position() + "]=" + child.node().type()).toList()));
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
        case ANNOTATION -> node.value() instanceof SyntaxValue.Annotation;
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
          case TYPE_DECLARATION, STATIC_INITIALIZER_BLOCK, INSTANCE_INITIALIZER_BLOCK, EMPTY -> true;
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
    if (sourceKind == JavaSourceKind.RECORD) {
      validateRecordCounterpartOrigins(children);
    }
    validateEmptyMemberOrder(children);
  }

  private static void validateEmptyMemberOrder(List<SyntaxChild> children) {
    var members = children.stream().filter(child -> child.role() == SyntaxRole.MEMBER).toList();
    for (var empty : members.stream().filter(child -> child.node().syntaxKind() == SyntaxKind.EMPTY).toList()) {
      if (empty.node().origin().isEmpty()) {
        throw new IllegalArgumentException("An empty body declaration requires its exact written source range");
      }
      var range = empty.node().origin().orElseThrow();
      for (var member : members) {
        var origin = member.node().origin();
        if (origin.isEmpty() || (member.position() < empty.position() && origin.orElseThrow().endOffsetExclusive() > range.startOffset())
            || (member.position() > empty.position() && origin.orElseThrow().startOffset() < range.endOffsetExclusive())) {
          throw new IllegalArgumentException("Empty body declaration order conflicts with written member ranges");
        }
      }
    }
  }

  private static void validateRecordCounterpartOrigins(List<SyntaxChild> children) {
    var members = children.stream().filter(child -> child.role() == SyntaxRole.MEMBER).map(SyntaxChild::node).toList();
    var components = children.stream().filter(child -> child.role() == SyntaxRole.RECORD_COMPONENT).map(SyntaxChild::node)
        .map(node -> node.value(SyntaxValue.RecordComponentDeclaration.class)).toList();
    var canonicalSignature = "<init>("
        + components.stream().map(component -> component.declaredType().erasedName()).collect(Collectors.joining(",")) + ")";
    for (var componentChild : children) {
      if (componentChild.role() != SyntaxRole.RECORD_COMPONENT) {
        continue;
      }
      var component = componentChild.node().value(SyntaxValue.RecordComponentDeclaration.class);
      var counterparts = component.counterparts();
      var owner = component.ownerKey();
      if (!counterparts.backingFieldKey().equals(owner + "#field:" + component.name())
          || !counterparts.accessorKey().equals(owner + "#" + component.name() + "()")
          || !counterparts.canonicalParameterKey().equals(owner + "#" + canonicalSignature + ":parameter:" + component.ordinal())) {
        throw new IllegalArgumentException("Record counterpart identities conflict with the record header");
      }
      var writtenAccessor = members.stream().filter(member -> member.syntaxKind() == SyntaxKind.METHOD_DECLARATION)
          .map(member -> member.value(SyntaxValue.ExecutableDeclaration.class))
          .anyMatch(method -> method.declarationKey().equals(counterparts.accessorKey()));
      var writtenCanonicalParameter = members.stream().filter(member -> member.syntaxKind() == SyntaxKind.METHOD_DECLARATION)
          .filter(member -> member.value(SyntaxValue.ExecutableDeclaration.class)
              .kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR)
          .flatMap(member -> member.children(SyntaxRole.PARAMETER).stream()).anyMatch(parameter -> parameter
              .value(SyntaxValue.ParameterDeclaration.class).declarationKey().equals(counterparts.canonicalParameterKey()));
      if (counterparts.accessorImplicit() == writtenAccessor || counterparts.canonicalParameterDerived() == writtenCanonicalParameter) {
        throw new IllegalArgumentException("Record counterpart provenance conflicts with written members");
      }
    }
  }

  private static void validateVariableDeclaration(SyntaxKind kind, SyntaxValue.VariableDeclaration declaration,
      List<SyntaxChild> children) {
    if ((kind == SyntaxKind.FIELD_DECLARATION) != (declaration.kind() == SyntaxValue.VariableDeclaration.Kind.FIELD)) {
      throw new IllegalArgumentException("Variable declaration form does not match its syntax kind");
    }
    requireShape(children,
        kind == SyntaxKind.FIELD_DECLARATION
            ? Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE, SyntaxRole.INITIALIZER)
            : Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE, SyntaxRole.ARGUMENT, SyntaxRole.CLASS_BODY));
    requireCount(children, SyntaxRole.INITIALIZER, 0, declaration.initializer() && kind == SyntaxKind.FIELD_DECLARATION ? 1 : 0);
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, declaration.type() ? 1 : 0, declaration.type() ? 1 : 0);
    if (kind == SyntaxKind.ENUM_CONSTANT_DECLARATION) {
      var represented = children.stream().anyMatch(child -> child.role() == SyntaxRole.ARGUMENT || child.role() == SyntaxRole.CLASS_BODY);
      if (represented) {
        requireCount(children, SyntaxRole.ARGUMENT, declaration.arguments(), declaration.arguments());
        requireCount(children, SyntaxRole.CLASS_BODY, declaration.constantBody() ? 1 : 0, declaration.constantBody() ? 1 : 0);
      }
      children.stream().filter(child -> child.role() == SyntaxRole.CLASS_BODY).forEach(child -> {
        if (child.node().syntaxKind() != SyntaxKind.TYPE_DECLARATION
            || !child.node().value(SyntaxValue.TypeDeclaration.class).name().isEmpty()) {
          throw new IllegalArgumentException("An enum constant body requires its nameless type declaration");
        }
      });
    }
    for (var child : children) {
      if (child.role() == SyntaxRole.ANNOTATION && !(child.node().value() instanceof SyntaxValue.Annotation)) {
        throw new IllegalArgumentException("A variable annotation requires a written annotation attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && (child.node().syntaxKind() != SyntaxKind.TYPE_USE || !child.node().sourceKind().identifiesTypeUseSyntax())) {
        throw new IllegalArgumentException("A field type requires a written type-use attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && child.node().type().knownIdentity().filter(identity -> !identity.equals(declaration.declaredType())).isPresent()) {
        throw new IllegalArgumentException("A field type attachment conflicts with its declaration type: "
            + child.node().type().knownIdentity().orElseThrow() + " versus " + declaration.declaredType());
      }
    }
  }

  private static void validateRecordComponentDeclaration(SyntaxValue.RecordComponentDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, 1, 1);
    for (var child : children) {
      if (child.role() == SyntaxRole.ANNOTATION && !(child.node().value() instanceof SyntaxValue.Annotation)) {
        throw new IllegalArgumentException("A record component annotation requires a written annotation attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && (child.node().syntaxKind() != SyntaxKind.TYPE_USE || !child.node().sourceKind().identifiesTypeUseSyntax())) {
        throw new IllegalArgumentException("A record component requires a written type-use attachment");
      }
      if (child.role() == SyntaxRole.DECLARED_TYPE
          && child.node().type().knownIdentity().filter(identity -> !identity.equals(declaration.declaredType())).isPresent()) {
        throw new IllegalArgumentException("A record component type attachment conflicts with its declaration type");
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
        case ANNOTATION -> node.value() instanceof SyntaxValue.Annotation;
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
    var defaultCount = declaration.defaultValue().isPresent() ? 1 : 0;
    requireCount(children, SyntaxRole.DEFAULT_VALUE, defaultCount, defaultCount);
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.value() instanceof SyntaxValue.Annotation;
        case TYPE_PARAMETER -> node.syntaxKind() == SyntaxKind.TYPE_PARAMETER_DECLARATION
            && node.value(SyntaxValue.TypeParameterDeclaration.class).ownerKey().equals(declaration.declarationKey())
            && node.value(SyntaxValue.TypeParameterDeclaration.class).ordinal() == child.position();
        case RECEIVER_PARAMETER -> node.sourceKind() == JavaSourceKind.VARIABLE && node.syntaxKind() == SyntaxKind.RECEIVER_PARAMETER
            && node.value() instanceof SyntaxValue.ReceiverParameter receiver && receiver.ownerKey().equals(declaration.declarationKey())
            && receiverMatchesExecutable(declaration, node.requiredChild(SyntaxRole.QUALIFIER))
            && matchingResolvedType(node.requiredChild(SyntaxRole.DECLARED_TYPE).type(), declaration.receiverType());
        case PARAMETER -> node.sourceKind() == JavaSourceKind.VARIABLE && node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION
            && node.value() instanceof SyntaxValue.ParameterDeclaration parameter
            && parameter.declarationKey().equals(declaration.declarationKey() + ":parameter:" + child.position())
            && parameter.varArgs() == (shape.varArgs() && child.position() == shape.parameters() - 1);
        case RETURN_TYPE -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax();
        case THROWN_TYPE -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.sourceKind().identifiesTypeUseSyntax()
            && matchingResolvedType(node.type(), declaration.thrownTypes().get(child.position()));
        case BODY -> node.syntaxKind() == SyntaxKind.BLOCK;
        case DEFAULT_VALUE -> declaration.kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.ANNOTATION_ELEMENT;
        default -> false;
      };
      if (!valid) {
        throw new IllegalArgumentException("Executable declaration child kind does not match its role");
      }
      if (child.role() == SyntaxRole.DEFAULT_VALUE) {
        var returnType = children.stream().filter(other -> other.role() == SyntaxRole.RETURN_TYPE).findFirst().orElseThrow().node();
        validateAnnotationValue(declaration.defaultValue().orElseThrow(), returnType.type(), child.node());
      }
    }
  }

  private static boolean receiverMatchesExecutable(SyntaxValue.ExecutableDeclaration declaration, SyntaxNode nameExpression) {
    return switch (declaration.kind()) {
      case METHOD -> nameExpression.sourceKind() == JavaSourceKind.IDENTIFIER;
      case CONSTRUCTOR -> nameExpression.sourceKind() == JavaSourceKind.MEMBER_SELECT
          && nameExpression.value(SyntaxValue.SourceSymbol.class).name().equals(declaration.receiverSimpleName().orElseThrow() + ".this");
      case COMPACT_CONSTRUCTOR, ANNOTATION_ELEMENT -> false;
    };
  }

  private static boolean matchingResolvedType(SyntaxType written, SyntaxType attributed) {
    var actual = written.knownIdentity();
    var expected = attributed.knownIdentity();
    return (actual.isEmpty() || expected.isEmpty()
        || (actual.orElseThrow().category() == expected.orElseThrow().category()
            && actual.orElseThrow().erasedName().equals(expected.orElseThrow().erasedName())))
        && (!(written instanceof SyntaxType.Resolved resolved) || !(attributed instanceof SyntaxType.Resolved resolvedExpected)
            || resolved.structure().isEmpty() || resolvedExpected.structure().isEmpty()
            || resolved.structure().equals(resolvedExpected.structure()));
  }

  private static void validateParameterDeclaration(SyntaxValue.ParameterDeclaration declaration, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ANNOTATION, SyntaxRole.DECLARED_TYPE));
    requireCount(children, SyntaxRole.ANNOTATION, declaration.annotations(), declaration.annotations());
    requireCount(children, SyntaxRole.DECLARED_TYPE, declaration.type() ? 1 : 0, declaration.type() ? 1 : 0);
    for (var child : children) {
      var node = child.node();
      var valid = switch (child.role()) {
        case ANNOTATION -> node.value() instanceof SyntaxValue.Annotation;
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
        case ANNOTATION -> node.value() instanceof SyntaxValue.Annotation;
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
    requireShape(children, Set.of(SyntaxRole.RECEIVER, SyntaxRole.QUALIFIER, SyntaxRole.ARGUMENT, SyntaxRole.TYPE_ARGUMENT));
    requireCount(children, SyntaxRole.RECEIVER, invocation.form().hasReceiverExpression() ? 1 : 0,
        invocation.form().hasReceiverExpression() ? 1 : 0);
    requireCount(children, SyntaxRole.QUALIFIER, 0, invocation.form().hasWrittenQualifier() ? 1 : 0);
    switch (invocation.form()) {
      case STATIC_TYPE -> validateBodyTypeAttachment(children, SyntaxRole.QUALIFIER);
      case INSTANCE_THIS -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.THIS);
      case INSTANCE_QUALIFIED_THIS -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.QUALIFIED_THIS);
      case SUPER -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.SUPER);
      case QUALIFIED_SUPER -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.QUALIFIED_SUPER);
      default -> {
      }
    }
    validateBodyTypeArguments(children);
  }

  private static void validateObjectCreation(SyntaxValue.Invocation invocation, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.ENCLOSING_INSTANCE, SyntaxRole.ARGUMENT, SyntaxRole.TYPE_OPERAND, SyntaxRole.TYPE_ARGUMENT,
        SyntaxRole.CLASS_BODY));
    requireCount(children, SyntaxRole.CLASS_BODY, 0, 1);
    if (children.stream().filter(child -> child.role() == SyntaxRole.CLASS_BODY)
        .anyMatch(child -> child.node().syntaxKind() != SyntaxKind.TYPE_DECLARATION
            || !child.node().value(SyntaxValue.TypeDeclaration.class).name().isEmpty())) {
      throw new IllegalArgumentException("An anonymous creation requires a nameless type body");
    }
    var expectedEnclosingInstances = invocation.form() == InvocationForm.QUALIFIED_OBJECT_CREATION ? 1 : 0;
    if (invocation.form() != InvocationForm.OBJECT_CREATION && invocation.form() != InvocationForm.QUALIFIED_OBJECT_CREATION) {
      throw new IllegalArgumentException("OBJECT_CREATION requires a construction invocation form");
    }
    requireCount(children, SyntaxRole.ENCLOSING_INSTANCE, expectedEnclosingInstances, expectedEnclosingInstances);
    validateBodyTypeAttachment(children, SyntaxRole.TYPE_OPERAND);
    validateBodyTypeArguments(children);
  }

  private static void validateBodyTypeAttachment(List<SyntaxChild> children, SyntaxRole role) {
    requireCount(children, role, 0, 1);
    if (children.stream().filter(child -> child.role() == role).anyMatch(child -> child.node().syntaxKind() != SyntaxKind.TYPE_USE)) {
      throw new IllegalArgumentException("A written body type attachment requires a type-use occurrence");
    }
  }

  private static void validateKnownWrittenBodyType(SyntaxKind kind, SyntaxType type, List<SyntaxChild> children) {
    if (kind == SyntaxKind.CLASS_LITERAL) {
      validateClassLiteralType(type, children);
      return;
    }
    var role = switch (kind) {
      case LOCAL_DECLARATION -> SyntaxRole.DECLARED_TYPE;
      case TYPE_CAST -> SyntaxRole.TYPE_OPERAND;
      default -> null;
    };
    var expectedIdentity = type.knownIdentity();
    if (role == null || expectedIdentity.isEmpty()) {
      return;
    }
    for (var child : children) {
      if (child.role() == role && child.node().type().knownIdentity().isPresent()) {
        var writtenIdentity = child.node().type().knownIdentity().orElseThrow();
        var identityMatches = writtenIdentity.category() == expectedIdentity.orElseThrow().category()
            && writtenIdentity.erasedName().equals(expectedIdentity.orElseThrow().erasedName());
        var structureMatches = kind != SyntaxKind.TYPE_CAST || !(type instanceof SyntaxType.Resolved expected)
            || !(child.node().type() instanceof SyntaxType.Resolved written) || expected.structure().isEmpty()
            || written.structure().isEmpty()
            || castTypeStructuresMatch(expected.structure().orElseThrow(), written.structure().orElseThrow());
        if (!identityMatches || !structureMatches) {
          throw new IllegalArgumentException("A known written body type must match its containing occurrence's type");
        }
      }
    }
  }

  private static void validateClassLiteralType(SyntaxType type, List<SyntaxChild> children) {
    if (!(type instanceof SyntaxType.Resolved resolved)
        || !(resolved.structure().orElse(null) instanceof JavaTypeStructure.Declared declared)) {
      return;
    }
    if (!"type:java.lang.Class".equals(declared.declarationKey()) || declared.arguments().size() != 1) {
      throw new IllegalArgumentException("A class literal value requires one java.lang.Class type argument");
    }
    var written = children.stream().filter(child -> child.role() == SyntaxRole.TYPE_OPERAND).findFirst().map(SyntaxChild::node);
    if (written.isEmpty() || !(written.orElseThrow().type() instanceof SyntaxType.Resolved operand) || operand.structure().isEmpty()) {
      return;
    }
    var structure = operand.structure().orElseThrow();
    if (structure instanceof JavaTypeStructure.Primitive primitive) {
      var boxedName = switch (primitive.kind()) {
        case BOOLEAN -> "Boolean";
        case BYTE -> "Byte";
        case SHORT -> "Short";
        case INT -> "Integer";
        case LONG -> "Long";
        case CHAR -> "Character";
        case FLOAT -> "Float";
        case DOUBLE -> "Double";
        case VOID -> "Void";
        default -> throw new IllegalArgumentException("A class literal has an invalid primitive operand");
      };
      structure = new JavaTypeStructure.Declared("type:java.lang." + boxedName, Optional.empty(), List.of());
    }
    if (!declared.arguments().getFirst().equals(structure)) {
      throw new IllegalArgumentException("A known class literal type argument must match its written operand");
    }
  }

  private static boolean castTypeStructuresMatch(JavaTypeStructure result, JavaTypeStructure target) {
    if (result.equals(target)) {
      return true;
    }
    var resultDeclaration = typeUseDeclaration(result);
    var targetDeclaration = typeUseDeclaration(target);
    if (result.getClass() != target.getClass() || resultDeclaration.isEmpty() || targetDeclaration.isEmpty()) {
      return false;
    }
    if (result instanceof JavaTypeStructure.LocalDeclared actualLocal && target instanceof JavaTypeStructure.LocalDeclared writtenLocal
        && actualLocal.kind() != writtenLocal.kind()) {
      return false;
    }
    var actual = resultDeclaration.orElseThrow();
    var written = targetDeclaration.orElseThrow();
    return actual.declarationKey().equals(written.declarationKey()) && actual.lexicalOwnerKey().equals(written.lexicalOwnerKey())
        && actual.enclosingType().equals(written.enclosingType()) && castArgumentsMatch(actual.arguments(), written.arguments());
  }

  private static boolean castArgumentsMatch(List<JavaTypeStructure> result, List<JavaTypeStructure> target) {
    if (result.size() != target.size()) {
      return false;
    }
    for (var index = 0; index < result.size(); index++) {
      var actual = result.get(index);
      var written = target.get(index);
      if (actual.equals(written)) {
        continue;
      }
      if (!(written instanceof JavaTypeStructure.Wildcard wildcard) || !(actual instanceof JavaTypeStructure.Captured captured)) {
        return false;
      }
      // Upper capture bounds depend on declaration parameters outside this occurrence.
      var expectedLower = wildcard.kind() == JavaTypeStructure.Wildcard.BoundKind.SUPER ? wildcard.bound() : Optional.empty();
      if (!captured.lowerBound().equals(expectedLower)) {
        return false;
      }
    }
    return true;
  }

  private static void validateBodyTypeArguments(List<SyntaxChild> children) {
    if (children.stream().filter(child -> child.role() == SyntaxRole.TYPE_ARGUMENT)
        .anyMatch(child -> child.node().syntaxKind() != SyntaxKind.TYPE_USE)) {
      throw new IllegalArgumentException("Written invocation type arguments require type-use occurrences");
    }
  }

  private static void validateFieldAccess(SyntaxValue.FieldAccess access, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.RECEIVER, SyntaxRole.QUALIFIER));
    requireCount(children, SyntaxRole.RECEIVER, access.receiver().hasReceiverExpression() ? 1 : 0,
        access.receiver().hasReceiverExpression() ? 1 : 0);
    requireCount(children, SyntaxRole.QUALIFIER, 0, switch (access.receiver()) {
      case STATIC_TYPE, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS, SUPER, QUALIFIED_SUPER -> 1;
      default -> 0;
    });
    switch (access.receiver()) {
      case STATIC_TYPE -> validateBodyTypeAttachment(children, SyntaxRole.QUALIFIER);
      case INSTANCE_THIS -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.THIS);
      case INSTANCE_QUALIFIED_THIS -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.QUALIFIED_THIS);
      case SUPER -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.SUPER);
      case QUALIFIED_SUPER -> validateReceiverQualifier(children, SyntaxValue.ReceiverKind.QUALIFIED_SUPER);
      default -> {
      }
    }
  }

  private static void validateReceiverQualifier(List<SyntaxChild> children, SyntaxValue.ReceiverKind expected) {
    if (children.stream().filter(child -> child.role() == SyntaxRole.QUALIFIER)
        .anyMatch(child -> !(child.node().value() instanceof SyntaxValue.Receiver receiver) || receiver.kind() != expected)) {
      throw new IllegalArgumentException("A written receiver qualifier contradicts its selected source form");
    }
  }

  private static void requireShape(List<SyntaxChild> children, Set<SyntaxRole> roles) {
    if (children.stream().map(SyntaxChild::role).anyMatch(role -> !roles.contains(role))) {
      throw new IllegalArgumentException("Syntax node contains a child role that its kind does not permit");
    }
  }

  private static void validateSwitchCase(SyntaxValue.SwitchCase switchCase, List<SyntaxChild> children) {
    requireShape(children, Set.of(SyntaxRole.LABEL, SyntaxRole.BODY, SyntaxRole.STATEMENT, SyntaxRole.GUARD));
    var expressionLabelCount = switchCase.labels().stream().filter(label -> !(label instanceof SyntaxValue.SwitchLabel.Default)).count();
    requireCount(children, SyntaxRole.LABEL, (int) expressionLabelCount, (int) expressionLabelCount);
    var labels = children.stream().filter(child -> child.role() == SyntaxRole.LABEL).map(SyntaxChild::node).toList();
    for (var label : switchCase.labels()) {
      if (label instanceof SyntaxValue.SwitchLabel.Pattern pattern && !isPattern(labels.get(pattern.childPosition()))) {
        throw new IllegalArgumentException("A pattern switch label requires a pattern child");
      }
      if (label instanceof SyntaxValue.SwitchLabel.Expression expression && !isExpression(labels.get(expression.childPosition()))) {
        throw new IllegalArgumentException("A constant switch label requires an expression child");
      }
    }
    if (switchCase.labels().size() > 1 && switchCase.labels().getLast() instanceof SyntaxValue.SwitchLabel.Default
        && labels.getFirst().sourceKind() != JavaSourceKind.NULL_LITERAL) {
      throw new IllegalArgumentException("A combined default switch label requires exactly one preceding null literal");
    }
    var patternLabels = switchCase.labels().stream().allMatch(SyntaxValue.SwitchLabel.Pattern.class::isInstance);
    requireCount(children, SyntaxRole.GUARD, 0, patternLabels ? 1 : 0);
    switch (switchCase.form()) {
      case RULE -> {
        requireCount(children, SyntaxRole.BODY, 1, 1);
        requireCount(children, SyntaxRole.STATEMENT, 0, 0);
      }
      case STATEMENT_GROUP -> requireCount(children, SyntaxRole.BODY, 0, 0);
    }
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
