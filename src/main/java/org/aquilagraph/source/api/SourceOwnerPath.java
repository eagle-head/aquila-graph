package org.aquilagraph.source.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable input-local source parent roles and their role-local ordinals. WRITTEN_EXPRESSION distinguishes explicitly grammar-derived
 * addresses from parsed compiler owners.
 *
 * @param steps
 *          original parsed relationships in root-to-owner order
 */
public record SourceOwnerPath(List<Step> steps) {
  /**
   * Copies the structural steps.
   *
   * @param steps
   *          original parsed relationships
   */
  public SourceOwnerPath {
    steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
  }

  /**
   * Returns the compilation-unit owner path.
   *
   * @return empty input-local root path
   */
  public static SourceOwnerPath root() {
    return new SourceOwnerPath(List.of());
  }

  /**
   * Appends a direct parsed child role and its original role-local ordinal.
   *
   * @param role
   *          exact parsed parent role
   * @param ordinal
   *          original zero-based position within that role
   * @return extended immutable path
   */
  public SourceOwnerPath child(Role role, int ordinal) {
    var children = new ArrayList<>(steps);
    children.add(new Step(role, ordinal));
    return new SourceOwnerPath(children);
  }

  /**
   * Returns the direct structural parent, absent at the compilation-unit boundary.
   *
   * @return parent path or empty at the root
   */
  public Optional<SourceOwnerPath> parent() {
    return steps.isEmpty() ? Optional.empty() : Optional.of(new SourceOwnerPath(steps.subList(0, steps.size() - 1)));
  }

  /**
   * Returns the direct structural parent role and ordinal, absent at the root.
   *
   * @return final structural step or empty at the root
   */
  public Optional<Step> lastStep() {
    return steps.isEmpty() ? Optional.empty() : Optional.of(steps.getLast());
  }

  /**
   * Returns the number of parsed parent relationships.
   *
   * @return structural step count
   */
  public int size() {
    return steps.size();
  }

  /**
   * Checks whether this path is the compilation-unit owner.
   *
   * @return whether the path has no parent relationship
   */
  public boolean isEmpty() {
    return steps.isEmpty();
  }

  /**
   * Checks a typed prefix, including this path itself.
   *
   * @param descendant
   *          path to compare with this prefix
   * @return whether every step of this path begins the supplied path
   */
  public boolean isAncestorOf(SourceOwnerPath descendant) {
    Objects.requireNonNull(descendant, "descendant");
    return size() <= descendant.size() && steps.equals(descendant.steps().subList(0, size()));
  }

  /**
   * One typed parsed parent relationship.
   *
   * @param role
   *          exact structural parent role
   * @param ordinal
   *          original zero-based position within that role
   */
  public record Step(Role role, int ordinal) {
    /**
     * Validates its closed role and nonnegative original list position.
     *
     * @param role
     *          exact structural parent role
     * @param ordinal
     *          original zero-based position within that role
     */
    public Step {
      Objects.requireNonNull(role, "role");
      if (ordinal < 0) {
        throw new IllegalArgumentException("A parsed owner role requires a nonnegative ordinal");
      }
    }
  }

  /** Closed structural roles of parsed Java child slots; no durable syntax identity is assigned. */
  public enum Role {
    /** Grammar-derived expression occurrence with explicit normalized-region correspondence. */
    WRITTEN_EXPRESSION,
    /** Compilation unit's package declaration. */
    PACKAGE_DECLARATION,
    /** Compilation unit's ordered import. */
    IMPORT,
    /** Compilation unit's ordered top-level declaration. */
    TYPE_DECLARATION,
    /** Compilation unit's module declaration. */
    MODULE_DECLARATION,
    /** Written annotation attached to its parsed owner. */
    ANNOTATION,
    /** Package name within a declaration or module directive. */
    PACKAGE_NAME,
    /** Import's qualified name. */
    IMPORT_NAME,
    /** Declaration's modifier carrier. */
    MODIFIERS,
    /** Declaration's ordered type parameter. */
    TYPE_PARAMETER,
    /** Declaration's explicit superclass type. */
    SUPERCLASS,
    /** Declaration's ordered interface type. */
    INTERFACE,
    /** Declaration's ordered permitted subclass. */
    PERMITTED_SUBCLASS,
    /** Type declaration's ordered parsed member. */
    MEMBER,
    /** Standalone body semicolon captured directly from written source. */
    EMPTY_DECLARATION,
    /** Method's written return type. */
    RETURN_TYPE,
    /** Declaration's formal or handler parameter. */
    PARAMETER,
    /** Method's explicit receiver parameter. */
    RECEIVER_PARAMETER,
    /** Method's ordered thrown type. */
    THROWN_TYPE,
    /** Executable, handler or protected construct's body. */
    BODY,
    /** Annotation element's written default value. */
    DEFAULT_VALUE,
    /** Variable or binding declaration's written type slot. */
    DECLARED_TYPE,
    /** Receiver declaration's written name expression. */
    NAME_EXPRESSION,
    /** Variable or loop's ordered initializer. */
    INITIALIZER,
    /** Block or control construct's written statement. */
    STATEMENT,
    /** Control construct's predicate expression. */
    CONDITION,
    /** Loop's ordered update expression. */
    UPDATE,
    /** Enhanced loop's iterated expression. */
    ITERABLE,
    /** Switch's ordered case. */
    CASE,
    /** Case's ordered source label. */
    LABEL,
    /** Case's guard expression. */
    GUARD,
    /** Synchronized statement's monitor expression. */
    MONITOR,
    /** Try statement's ordered resource. */
    RESOURCE,
    /** Try statement's ordered handler. */
    CATCH,
    /** Try statement's finalization block. */
    FINALLY,
    /** Conditional expression's true alternative. */
    TRUE_EXPRESSION,
    /** Conditional expression's false alternative. */
    FALSE_EXPRESSION,
    /** Conditional statement's true branch. */
    THEN_STATEMENT,
    /** Conditional statement's false branch. */
    ELSE_STATEMENT,
    /** Parsed owner's expression operand. */
    EXPRESSION,
    /** Assertion's detail expression. */
    DETAIL,
    /** Type or invocation's ordered explicit type argument. */
    TYPE_ARGUMENT,
    /** Invocation's selected method expression. */
    METHOD_SELECT,
    /** Invocation, construction or annotation's ordered argument. */
    ARGUMENT,
    /** Construction's enclosing instance expression. */
    ENCLOSING_EXPRESSION,
    /** Construction's written type name. */
    CONSTRUCTED_TYPE,
    /** Anonymous construction's class declaration. */
    CLASS_BODY,
    /** Array construction's written element type. */
    ARRAY_ELEMENT_TYPE,
    /** Array construction's ordered dimension expression. */
    DIMENSION,
    /** Array construction's ordered element initializer. */
    ARRAY_INITIALIZER,
    /** Array construction's original ordered dimension annotation group, including empty groups. */
    DIMENSION_ANNOTATION_GROUP,
    /** Assignment's storage expression. */
    ASSIGNED_VARIABLE,
    /** Binary expression's left operand. */
    LEFT_OPERAND,
    /** Binary expression's right operand. */
    RIGHT_OPERAND,
    /** Cast's explicit type operand. */
    CAST_TYPE,
    /** Instance test's explicit nonpattern type. */
    TESTED_TYPE,
    /** Instance test or label's pattern. */
    PATTERN,
    /** Constant case label's expression. */
    CONSTANT_EXPRESSION,
    /** Deconstruction pattern's written type. */
    DECONSTRUCTOR,
    /** Deconstruction pattern's ordered nested pattern. */
    NESTED_PATTERN,
    /** Array access's index expression. */
    INDEX,
    /** Member selection's written qualifier. */
    TYPE_QUALIFIER,
    /** Parameterized type's nominal name. */
    TYPE_NAME,
    /** Array type's written component. */
    COMPONENT_TYPE,
    /** Union type's ordered alternative. */
    UNION_ALTERNATIVE,
    /** Intersection type's ordered bound. */
    INTERSECTION_BOUND,
    /** Type parameter's ordered bound. */
    TYPE_BOUND,
    /** Wildcard's explicit upper bound. */
    UPPER_BOUND,
    /** Wildcard's explicit lower bound. */
    LOWER_BOUND,
    /** Annotation's written type. */
    ANNOTATION_TYPE,
    /** Annotated type's underlying type. */
    UNDERLYING_TYPE,
    /** Module declaration or directive's module name. */
    MODULE_NAME,
    /** Module declaration's ordered directive. */
    DIRECTIVE,
    /** Module directive's service type name. */
    SERVICE_NAME,
    /** Provides directive's ordered implementation name. */
    IMPLEMENTATION_NAME,
    /** Yield statement's value expression. */
    YIELDED_VALUE
  }
}
