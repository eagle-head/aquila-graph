package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.model.InvocationForm;
import org.aquilagraph.model.InvocationSemantics;

/** Typed source facts required while syntax is lowered directly to semantic graphs. */
public sealed interface SyntaxValue {
  /** Indicates that a syntax kind requires no additional scalar value. */
  enum None implements SyntaxValue {
    /** Singleton absence value. */
    INSTANCE
  }

  /** Source form of a parsed compilation unit. */
  enum CompilationUnitForm {
    /** A compilation unit that may contain package, import, and type declarations. */
    ORDINARY,
    /** A compilation unit whose written class members belong to an implicit class. */
    COMPACT,
    /** A compilation unit containing a module declaration. */
    MODULAR
  }

  /**
   * Declared local name; declaration identity is carried by the node's semantic reference.
   *
   * @param name
   *          source declaration name
   */
  record LocalDeclaration(String name) implements SyntaxValue {
    /** Validates the declaration name. */
    public LocalDeclaration {
      SyntaxValueSupport.requireName(name);
    }
  }

  /** Shared source-name value boundary for typed syntax facts. */
  abstract sealed class Symbol implements SyntaxValue permits SourceSymbol, CompilationUnit, PackageDeclaration, ImportDeclaration {
    enum Form {
      SYMBOL, COMPILATION_UNIT, PACKAGE_DECLARATION, IMPORT_DECLARATION
    }

    private final String sourceName;
    private final Form valueForm;
    private final Object valueDetails;

    Symbol(String name, Form valueForm, Object valueDetails) {
      this.sourceName = name;
      this.valueForm = valueForm;
      this.valueDetails = valueDetails;
    }

    /**
     * Returns the retained source name.
     *
     * @return the source name
     */
    public String name() {
      return sourceName;
    }

    @Override
    public final boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      return other instanceof Symbol that && valueForm == that.valueForm && sourceName.equals(that.sourceName)
          && valueDetails.equals(that.valueDetails);
    }

    @Override
    public final int hashCode() {
      return Objects.hash(valueForm, sourceName, valueDetails);
    }

    @Override
    public String toString() {
      return getClass().getSimpleName() + "[name=" + sourceName + ']';
    }
  }

  /** Local, parameter, or assignable-variable source name. */
  final class SourceSymbol extends Symbol {
    /**
     * Validates the source name.
     *
     * @param name
     *          the source name
     */
    public SourceSymbol(String name) {
      super(name, Form.SYMBOL, None.INSTANCE);
      SyntaxValueSupport.requireName(name);
    }

    @Override
    public String toString() {
      return "Symbol[name=" + name() + ']';
    }
  }

  /** Parsed compilation-unit form, independent of its filename and children. */
  final class CompilationUnit extends Symbol {
    private final CompilationUnitForm sourceForm;

    /**
     * Creates a typed compilation-unit value.
     *
     * @param form
     *          the parsed unit form
     */
    public CompilationUnit(CompilationUnitForm form) {
      super("compilation-unit", Form.COMPILATION_UNIT, Objects.requireNonNull(form, "compilation unit form"));
      this.sourceForm = form;
    }

    /**
     * Returns the parsed unit form.
     *
     * @return the parsed unit form
     */
    public CompilationUnitForm form() {
      return sourceForm;
    }

    @Override
    public String toString() {
      return "CompilationUnit[form=" + sourceForm + ']';
    }
  }

  /** Qualified name written in a package declaration. */
  final class PackageDeclaration extends Symbol {
    /**
     * Creates a qualified package name value.
     *
     * @param qualifiedName
     *          the written package name
     */
    public PackageDeclaration(String qualifiedName) {
      super(qualifiedName, Form.PACKAGE_DECLARATION, None.INSTANCE);
      SyntaxValueSupport.requireName(qualifiedName);
    }

    /**
     * Returns the written qualified package name.
     *
     * @return the qualified package name
     */
    public String qualifiedName() {
      return name();
    }

  }

  /** Written import target and source form. */
  final class ImportDeclaration extends Symbol {
    private record ImportForm(boolean staticImport, boolean moduleImport, boolean onDemand) {
    }

    private final boolean isStaticImport;
    private final boolean isModuleImport;
    private final boolean isOnDemand;

    /**
     * Creates an import with its qualified target and exact source form.
     *
     * @param qualifiedName
     *          the imported target
     * @param staticImport
     *          whether the declaration is static
     * @param moduleImport
     *          whether the declaration imports a module
     * @param onDemand
     *          whether the declaration ends in a star
     */
    public ImportDeclaration(String qualifiedName, boolean staticImport, boolean moduleImport, boolean onDemand) {
      super(qualifiedName, Form.IMPORT_DECLARATION, new ImportForm(staticImport, moduleImport, onDemand));
      SyntaxValueSupport.requireName(qualifiedName);
      if (moduleImport && (staticImport || onDemand)) {
        throw new IllegalArgumentException("A module import cannot be static or on demand");
      }
      this.isStaticImport = staticImport;
      this.isModuleImport = moduleImport;
      this.isOnDemand = onDemand;
    }

    /**
     * Returns the imported target without an on-demand star.
     *
     * @return the qualified imported target
     */
    public String qualifiedName() {
      return name();
    }

    /**
     * Returns whether the declaration uses static import.
     *
     * @return true for a static import
     */
    public boolean staticImport() {
      return isStaticImport;
    }

    /**
     * Returns whether the declaration imports a module.
     *
     * @return true for a module import
     */
    public boolean moduleImport() {
      return isModuleImport;
    }

    /**
     * Returns whether the declaration ends in an on-demand star.
     *
     * @return true for an on-demand import
     */
    public boolean onDemand() {
      return isOnDemand;
    }

    @Override
    public String toString() {
      return "ImportDeclaration[qualifiedName=" + qualifiedName() + ", staticImport=" + isStaticImport + ", moduleImport=" + isModuleImport
          + ", onDemand=" + isOnDemand + ']';
    }
  }

  /**
   * Field name and compile-time receiver form.
   *
   * @param name
   *          selected field name
   * @param receiver
   *          compile-time receiver form
   */
  record FieldAccess(String name, FieldReceiver receiver) implements SyntaxValue {
    /** Validates the field access facts. */
    public FieldAccess {
      SyntaxValueSupport.requireName(name);
      Objects.requireNonNull(receiver, "receiver");
    }
  }

  /**
   * Selected method or constructor and its compile-time invocation form.
   *
   * @param name
   *          selected executable name
   * @param form
   *          compile-time invocation form
   * @param semantics
   *          resolved invocation semantics when compiler qualification succeeded
   */
  record Invocation(String name, InvocationForm form, Optional<InvocationSemantics> semantics) implements SyntaxValue {
    /** Validates the invocation facts. */
    public Invocation {
      SyntaxValueSupport.requireName(name);
      Objects.requireNonNull(form, "form");
      Objects.requireNonNull(semantics, "semantics");
      semantics.filter(value -> value.form() != form).ifPresent(value -> {
        throw new IllegalArgumentException("Resolved invocation semantics must retain the source form");
      });
    }
  }

  /**
   * Explicit receiver keyword retained as a value expression.
   *
   * @param kind
   *          receiver keyword form
   */
  record Receiver(ReceiverKind kind) implements SyntaxValue {
    /** Validates the receiver kind. */
    public Receiver {
      Objects.requireNonNull(kind, "kind");
    }
  }

  /**
   * Stable textual representation of a literal value.
   *
   * @param text
   *          literal representation
   */
  record Literal(String text) implements SyntaxValue {
    /** Validates the literal representation. */
    public Literal {
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * Typed operator retained from the compiler tree.
   *
   * @param operator
   *          retained operator
   */
  record Operator(SyntaxOperator operator) implements SyntaxValue {
    /** Validates the operator. */
    public Operator {
      Objects.requireNonNull(operator, "operator");
    }
  }

  /**
   * Compiler-reported display name of a type use; identity is carried by the semantic reference.
   *
   * @param name
   *          compiler-reported display name
   */
  record TypeName(String name) implements SyntaxValue {
    /** Validates the type display name. */
    public TypeName {
      SyntaxValueSupport.requireName(name);
    }
  }

  /** Receiver form of a field read or field assignment location. */
  public enum FieldReceiver {
    /** Unqualified access to an instance field through the implicit receiver. */
    INSTANCE_IMPLICIT,
    /** Instance field selected through an evaluated expression child. */
    INSTANCE_EXPRESSION,
    /** Instance field selected explicitly through {@code this}. */
    INSTANCE_THIS,
    /** Instance field selected through qualified {@code this}. */
    INSTANCE_QUALIFIED_THIS,
    /** Superclass field selected through {@code super}. */
    SUPER,
    /** Superclass field selected through qualified {@code super}. */
    QUALIFIED_SUPER,
    /** Unqualified access to a static field. */
    STATIC_UNQUALIFIED,
    /** Static field selected through a type qualifier that has no runtime evaluation. */
    STATIC_TYPE,
    /** Static field selected through an evaluated expression whose result is discarded. */
    STATIC_EXPRESSION;

    /**
     * Returns whether this form owns one evaluated receiver child.
     *
     * @return whether an expression receiver is evaluated
     */
    public boolean evaluatesExpression() {
      return this == INSTANCE_EXPRESSION || this == STATIC_EXPRESSION;
    }

    /**
     * Returns whether a receiver value is consumed by the field operation.
     *
     * @return whether the field operation consumes a receiver value
     */
    public boolean consumesReceiver() {
      return switch (this) {
        case INSTANCE_IMPLICIT, INSTANCE_EXPRESSION, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS, SUPER, QUALIFIED_SUPER -> true;
        case STATIC_UNQUALIFIED, STATIC_TYPE, STATIC_EXPRESSION -> false;
      };
    }
  }

  /** Explicit receiver value forms. */
  public enum ReceiverKind {
    /** Current instance. */
    THIS,
    /** Lexically enclosing qualified instance. */
    QUALIFIED_THIS,
    /** Current instance viewed for superclass selection. */
    SUPER,
    /** Qualified enclosing instance viewed for superclass selection. */
    QUALIFIED_SUPER
  }

  /**
   * Structural form and ordered labels belonging to one switch case.
   *
   * @param form
   *          switch-case body form
   * @param labels
   *          ordered retained labels
   */
  record SwitchCase(SwitchCaseForm form, List<SwitchLabel> labels) implements SyntaxValue {
    /** Validates the case form and copies its labels. */
    public SwitchCase {
      Objects.requireNonNull(form, "form");
      labels = List.copyOf(Objects.requireNonNull(labels, "labels"));
      if (labels.isEmpty()) {
        throw new IllegalArgumentException("A switch case must retain at least one label");
      }
      var defaultCount = labels.stream().filter(SwitchLabel.Default.class::isInstance).count();
      if (defaultCount > 0 && labels.size() != 1) {
        throw new IllegalArgumentException("A supported default switch label must be the case's only label");
      }
      var nextExpressionPosition = 0;
      for (var label : labels) {
        Objects.requireNonNull(label, "label");
        if (label instanceof SwitchLabel.Expression expression) {
          if (expression.childPosition() != nextExpressionPosition) {
            throw new IllegalArgumentException("Switch label expression positions must be contiguous");
          }
          nextExpressionPosition++;
        }
      }
    }
  }

  /** Structural form of a switch case. */
  enum SwitchCaseForm {
    /** Arrow rule with exactly one body. */
    RULE,
    /** Colon-delimited statement group, including an empty fall-through group. */
    STATEMENT_GROUP
  }

  /** Typed label belonging to a switch case. */
  sealed interface SwitchLabel {
    /** Default label. */
    enum Default implements SwitchLabel {
      /** Singleton default label. */
      INSTANCE
    }

    /**
     * Constant-expression label represented by a typed syntax child.
     *
     * @param childPosition
     *          position among the case's expression-label children
     */
    record Expression(int childPosition) implements SwitchLabel {
      /** Validates the label-child position. */
      public Expression {
        if (childPosition < 0) {
          throw new IllegalArgumentException("A switch label child position cannot be negative");
        }
      }
    }
  }

  /**
   * Compiler syntax category outside the accepted profile.
   *
   * @param compilerKind
   *          compiler tree kind outside the accepted profile
   */
  record Unsupported(String compilerKind) implements SyntaxValue {
    /** Validates the compiler syntax category. */
    public Unsupported {
      SyntaxValueSupport.requireText(compilerKind, "compilerKind");
    }
  }
}
