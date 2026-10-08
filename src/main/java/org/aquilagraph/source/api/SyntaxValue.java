package org.aquilagraph.source.api;

import java.lang.annotation.ElementType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Copied written syntax and attributed facts queried after compiler closure. */
public sealed interface SyntaxValue {
  /** Written reference selection mode. */
  enum ReferenceMode {
    /** A reference to a named method. */
    METHOD,
    /** A reference to class or array construction. */
    CONSTRUCTOR
  }

  /** Written qualifier category, independently of the functional target type. */
  enum ReferenceQualifier {
    /** A bound expression qualifier. */
    EXPRESSION,
    /** A written reference type qualifier. */
    TYPE,
    /** An unqualified lexical superclass receiver. */
    SUPER,
    /** A qualified lexical superclass receiver. */
    QUALIFIED_SUPER,
    /** A written array type for construction. */
    ARRAY_TYPE
  }

  /** Applicable selection identity category for a functional reference. */
  enum ReferenceSelection {
    /** A selected named executable declaration. */
    EXECUTABLE,
    /** Array construction established by its written array type. */
    ARRAY_CONSTRUCTOR,
    /** The intrinsic clone member of an array, without a named class declaration. */
    ARRAY_CLONE
  }

  /**
   * Written reference and copied selection facts, without invocation behavior.
   *
   * @param name
   *          referenced method name or {@code new}
   * @param mode
   *          method or constructor mode
   * @param qualifier
   *          written qualifier form
   * @param selectedType
   *          selected executable declaration type or constructed array type outcome
   * @param selection
   *          named executable or intrinsic array operation selection
   * @param staticMember
   *          whether the selected method is static
   */
  record MemberReference(String name, ReferenceMode mode, ReferenceQualifier qualifier, SyntaxType selectedType,
      ReferenceSelection selection, boolean staticMember) implements SyntaxValue {
    /** Validates scalar source distinctions and retains missing selection evidence truthfully. */
    public MemberReference {
      SyntaxValueSupport.requireName(name);
      Objects.requireNonNull(mode, "mode");
      Objects.requireNonNull(qualifier, "qualifier");
      Objects.requireNonNull(selectedType, "selectedType");
      Objects.requireNonNull(selection, "selection");
      if ((mode == ReferenceMode.CONSTRUCTOR) != "new".equals(name)
          || (mode == ReferenceMode.CONSTRUCTOR && qualifier != ReferenceQualifier.TYPE && qualifier != ReferenceQualifier.ARRAY_TYPE)
          || (qualifier == ReferenceQualifier.ARRAY_TYPE) != (selection == ReferenceSelection.ARRAY_CONSTRUCTOR)
          || (qualifier == ReferenceQualifier.ARRAY_TYPE && mode != ReferenceMode.CONSTRUCTOR)
          || (selection == ReferenceSelection.ARRAY_CONSTRUCTOR
              && (mode != ReferenceMode.CONSTRUCTOR || qualifier != ReferenceQualifier.ARRAY_TYPE))
          || (selection == ReferenceSelection.ARRAY_CLONE && (mode != ReferenceMode.METHOD || !"clone".equals(name)
              || (qualifier != ReferenceQualifier.TYPE && qualifier != ReferenceQualifier.EXPRESSION)))
          || (staticMember
              && (mode != ReferenceMode.METHOD || qualifier != ReferenceQualifier.TYPE || selection != ReferenceSelection.EXECUTABLE))) {
        throw new IllegalArgumentException("Reference mode, name and qualifier must agree");
      }
    }
  }

  /** Written lambda body forms. */
  enum LambdaBodyKind {
    /** A single written expression. */
    EXPRESSION,
    /** A written statement block, including an empty block. */
    BLOCK
  }

  /**
   * Written functional parameter form, separate from its attributed binding type.
   *
   * @param typeForm
   *          written, inferred, or implicit type form
   * @param variableArity
   *          whether an ellipsis was written
   */
  record LambdaParameter(LocalBindingEvidence.TypeForm typeForm, boolean variableArity) {
    /** Requires a copied type-form classification, including erroneous written input. */
    public LambdaParameter {
      Objects.requireNonNull(typeForm, "typeForm");
    }
  }

  /**
   * Copied lambda syntax; its node carries the functional target type and owns its parameters and body.
   *
   * @param bodyKind
   *          written expression or block body
   * @param parenthesized
   *          whether the parameter list has written parentheses
   * @param parameters
   *          written parameter forms in source order
   * @param returnType
   *          copied functional descriptor return type, unresolved when attribution is unavailable
   */
  record Lambda(LambdaBodyKind bodyKind, boolean parenthesized, List<LambdaParameter> parameters,
      SyntaxType returnType) implements SyntaxValue {
    /** Copies the ordered written forms independently of successful compiler attribution. */
    public Lambda {
      Objects.requireNonNull(bodyKind, "bodyKind");
      Objects.requireNonNull(returnType, "returnType");
      parameters = List.copyOf(parameters);
    }
  }

  /** Written diamond syntax with compiler-inferred arguments and no written argument children. */
  enum DiamondType implements SyntaxValue {
    /** Diamond source form. */
    INSTANCE
  }

  /**
   * Previously owned physical annotations reused by another compiler tree for the same field header.
   *
   */
  final class SharedTypeAnnotations extends Symbol {
    private final List<String> sourceAnnotationKeys;

    /**
     * Validates the immutable source identities.
     *
     * @param annotationNodeKeys
     *          source annotation identities in written order
     */
    public SharedTypeAnnotations(List<String> annotationNodeKeys) {
      super("", Form.SHARED_TYPE_ANNOTATIONS, List.copyOf(Objects.requireNonNull(annotationNodeKeys, "annotationNodeKeys")));
      this.sourceAnnotationKeys = List.copyOf(annotationNodeKeys);
      this.sourceAnnotationKeys.forEach(key -> SyntaxValueSupport.requireText(key, "shared annotation node key"));
      if (this.sourceAnnotationKeys.isEmpty() || Set.copyOf(this.sourceAnnotationKeys).size() != this.sourceAnnotationKeys.size()) {
        throw new IllegalArgumentException("Shared type annotations require distinct source annotation identities");
      }
    }

    /**
     * Returns source annotation identities in written order.
     *
     * @return owned annotation identities
     */
    public List<String> annotationNodeKeys() {
      return sourceAnnotationKeys;
    }
  }
  /** Written annotation surface form. */
  enum AnnotationForm {
    /** Annotation with no argument delimiters. */
    MARKER,
    /** Annotation with written empty argument delimiters. */
    EMPTY_NORMAL,
    /** Annotation with one unnamed value. */
    SINGLE_ELEMENT,
    /** Annotation with explicitly named values. */
    NAMED_NORMAL
  }

  /** Resolved applicability of a written annotation occurrence. */
  enum AnnotationLocation {
    /** The annotation applies to a declaration. */
    DECLARATION,
    /** The annotation applies to an exact written or constructed type location. */
    TYPE_USE,
    /** One written annotation applies to both a declaration and its closest type. */
    DUAL,
    /** A nested annotation is the value of an annotation element. */
    VALUE,
    /** The precise annotation location is not yet represented. */
    UNRESOLVED
  }

  /**
   * An argument header retained before attribution.
   *
   * @param explicitName
   *          written element name, when present
   */
  record AnnotationArgumentHeader(Optional<String> explicitName) {
    /** Validates the optional written name. */
    public AnnotationArgumentHeader {
      Objects.requireNonNull(explicitName, "explicitName");
      explicitName.ifPresent(SyntaxValueSupport::requireName);
    }
  }

  /** Indicates that a syntax kind requires no additional scalar value. */
  enum None implements SyntaxValue {
    /** Singleton absence value. */
    INSTANCE
  }

  /** Primitive kind written in a source type use, including the distinct {@code void} pseudo-type. */
  final class PrimitiveType extends Symbol {
    private final JavaTypeCategory writtenKind;

    /**
     * Validates and retains the written kind.
     *
     * @param kind
     *          written primitive or void kind
     */
    public PrimitiveType(JavaTypeCategory kind) {
      super(Objects.requireNonNull(kind, "writtenKind").name(), Form.PRIMITIVE_TYPE, kind);
      if (!Set.of(JavaTypeCategory.BOOLEAN, JavaTypeCategory.BYTE, JavaTypeCategory.SHORT, JavaTypeCategory.INT, JavaTypeCategory.LONG,
          JavaTypeCategory.CHAR, JavaTypeCategory.FLOAT, JavaTypeCategory.DOUBLE, JavaTypeCategory.VOID).contains(kind)) {
        throw new IllegalArgumentException("A primitive source type requires a primitive or void kind");
      }
      writtenKind = kind;
    }

    /**
     * Returns the written primitive or void kind.
     *
     * @return exact written category
     */
    public JavaTypeCategory kind() {
      return writtenKind;
    }
  }

  /** A written package-name segment qualifying a type use. */
  final class PackageQualifier extends Symbol {
    /**
     * Validates and retains the package-name segment.
     *
     * @param name
     *          written package-name segment
     */
    public PackageQualifier(String name) {
      super(name, Form.PACKAGE_QUALIFIER, None.INSTANCE);
      SyntaxValueSupport.requireName(name);
    }
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
   * Ordered identifier components supplied by a source-name tree.
   *
   * @param components
   *          ordered source identifiers
   */
  record QualifiedName(List<String> components) {
    /** Validates and retains the ordered components. */
    public QualifiedName {
      components = List.copyOf(Objects.requireNonNull(components, "components"));
      if (components.isEmpty()) {
        throw new IllegalArgumentException("A qualified name requires an identifier");
      }
      for (var component : components) {
        SyntaxValueSupport.requireName(component);
        if (component.indexOf('.') >= 0) {
          throw new IllegalArgumentException("A qualified-name component cannot contain a dot");
        }
      }
    }

    /**
     * Returns the source name assembled from its components.
     *
     * @return qualified source name
     */
    public String dottedName() {
      return String.join(".", components);
    }
  }

  /** Written module header. */
  final class ModuleDeclaration extends Symbol {
    private record Header(QualifiedName name, boolean open) {
    }

    private final Header header;

    /**
     * Creates a written module header.
     *
     * @param name
     *          ordered module-name components
     * @param open
     *          whether the declaration uses {@code open module}
     */
    public ModuleDeclaration(QualifiedName name, boolean open) {
      this(new Header(Objects.requireNonNull(name, "name"), open));
    }

    private ModuleDeclaration(Header header) {
      super(header.name().dottedName(), Form.MODULE_DECLARATION, header);
      this.header = header;
    }

    /**
     * Returns ordered module-name components.
     *
     * @return qualified module name
     */
    public QualifiedName qualifiedName() {
      return header.name();
    }

    /**
     * Returns whether the source uses {@code open module}.
     *
     * @return {@code true} for an open module
     */
    public boolean open() {
      return header.open();
    }
  }

  /** Closed family of source module directives. */
  abstract sealed class ModuleDirective extends Symbol permits Requires, Exports, Opens, Uses, Provides {
    ModuleDirective(String name, Form form, Object details) {
      super(name, form, details);
    }
  }

  /** Required module and written modifiers. */
  final class Requires extends ModuleDirective {
    private record RequiresDetails(QualifiedName module, boolean staticPhase, boolean transitive) {
    }

    private final RequiresDetails details;

    /**
     * Creates a written requires directive.
     *
     * @param module
     *          required module name
     * @param staticPhase
     *          whether {@code static} was written
     * @param transitive
     *          whether {@code transitive} was written
     */
    public Requires(QualifiedName module, boolean staticPhase, boolean transitive) {
      this(new RequiresDetails(Objects.requireNonNull(module, "module"), staticPhase, transitive));
    }

    private Requires(RequiresDetails details) {
      super(details.module().dottedName(), Form.REQUIRES, details);
      this.details = details;
    }

    /**
     * Returns the required module name.
     *
     * @return qualified module name
     */
    public QualifiedName module() {
      return details.module();
    }

    /**
     * Returns whether {@code static} was written.
     *
     * @return {@code true} when static was written
     */
    public boolean staticPhase() {
      return details.staticPhase();
    }

    /**
     * Returns whether {@code transitive} was written.
     *
     * @return {@code true} when transitive was written
     */
    public boolean transitive() {
      return details.transitive();
    }
  }

  /** Exported package and optional ordered target modules. */
  final class Exports extends ModuleDirective {
    private record ExportDetails(QualifiedName packageName, Optional<List<QualifiedName>> targets) {
    }

    private final ExportDetails details;

    /**
     * Creates a written exports directive.
     *
     * @param packageName
     *          exported package name
     * @param targets
     *          absent for an unqualified export, otherwise a nonempty ordered list
     */
    public Exports(QualifiedName packageName, Optional<List<QualifiedName>> targets) {
      this(new ExportDetails(Objects.requireNonNull(packageName, "packageName"), SyntaxValueSupport.copyTargets(targets)));
    }

    private Exports(ExportDetails details) {
      super(details.packageName().dottedName(), Form.EXPORTS, details);
      this.details = details;
    }

    /**
     * Returns the exported package name.
     *
     * @return qualified package name
     */
    public QualifiedName packageName() {
      return details.packageName();
    }

    /**
     * Returns ordered targets, or absence for an unqualified export.
     *
     * @return optional ordered target modules
     */
    public Optional<List<QualifiedName>> targets() {
      return details.targets();
    }
  }

  /** Opened package and optional ordered target modules. */
  final class Opens extends ModuleDirective {
    private record OpenDetails(QualifiedName packageName, Optional<List<QualifiedName>> targets) {
    }

    private final OpenDetails details;

    /**
     * Creates a written opens directive.
     *
     * @param packageName
     *          opened package name
     * @param targets
     *          absent for an unqualified open, otherwise a nonempty ordered list
     */
    public Opens(QualifiedName packageName, Optional<List<QualifiedName>> targets) {
      this(new OpenDetails(Objects.requireNonNull(packageName, "packageName"), SyntaxValueSupport.copyTargets(targets)));
    }

    private Opens(OpenDetails details) {
      super(details.packageName().dottedName(), Form.OPENS, details);
      this.details = details;
    }

    /**
     * Returns the opened package name.
     *
     * @return qualified package name
     */
    public QualifiedName packageName() {
      return details.packageName();
    }

    /**
     * Returns ordered targets, or absence for an unqualified open.
     *
     * @return optional ordered target modules
     */
    public Optional<List<QualifiedName>> targets() {
      return details.targets();
    }
  }

  /** Consumed service type. */
  final class Uses extends ModuleDirective {
    private final QualifiedName serviceName;

    /**
     * Creates a written uses directive.
     *
     * @param service
     *          source service name
     */
    public Uses(QualifiedName service) {
      super(Objects.requireNonNull(service, "service").dottedName(), Form.USES, service);
      serviceName = service;
    }

    /**
     * Returns the consumed service name.
     *
     * @return qualified service name
     */
    public QualifiedName service() {
      return serviceName;
    }
  }

  /** Provided service type and ordered implementation types. */
  final class Provides extends ModuleDirective {
    private record ProvidesDetails(QualifiedName service, List<QualifiedName> providers) {
    }

    private final ProvidesDetails details;

    /**
     * Creates a written provides directive.
     *
     * @param service
     *          source service name
     * @param providers
     *          source provider names in declaration order
     */
    public Provides(QualifiedName service, List<QualifiedName> providers) {
      this(new ProvidesDetails(Objects.requireNonNull(service, "service"), SyntaxValueSupport.copyProviders(providers)));
    }

    private Provides(ProvidesDetails details) {
      super(details.service().dottedName(), Form.PROVIDES, details);
      this.details = details;
    }

    /**
     * Returns the provided service name.
     *
     * @return qualified service name
     */
    public QualifiedName service() {
      return details.service();
    }

    /**
     * Returns provider names in source order.
     *
     * @return ordered provider names
     */
    public List<QualifiedName> providers() {
      return details.providers();
    }
  }

  /**
   * Declared local name; declaration identity is carried by the node's semantic reference.
   *
   * @param name
   *          source declaration name
   * @param writtenType
   *          independently copied written type source and compiler semantics, when available
   * @param writtenModifiers
   *          copied written flags in sorted public modifier-name order
   * @param bindingEvidence
   *          copied binding identity and source shape, absent in compatible legacy values
   */
  record LocalDeclaration(String name, Optional<WrittenTypeEvidence> writtenType, List<String> writtenModifiers,
      Optional<LocalBindingEvidence> bindingEvidence) implements SyntaxValue {
    /** Validates the declaration name and copied written type. */
    public LocalDeclaration {
      if (!"_".equals(name)) {
        SyntaxValueSupport.requireName(name);
      }
      Objects.requireNonNull(writtenType, "writtenType");
      writtenModifiers = List.copyOf(Objects.requireNonNull(writtenModifiers, "writtenModifiers"));
      Objects.requireNonNull(bindingEvidence, "bindingEvidence");
      if (bindingEvidence.filter(binding -> binding.typeForm() != LocalBindingEvidence.TypeForm.DECLARED).isPresent()
          && writtenType.isPresent()) {
        throw new IllegalArgumentException("Inferred and implicit bindings cannot contain a copied written type");
      }
    }

    /**
     * Creates a compatible declaration with copied written type and modifier evidence.
     *
     * @param name
     *          source declaration name
     * @param writtenType
     *          copied written type
     * @param writtenModifiers
     *          copied source flags
     */
    public LocalDeclaration(String name, Optional<WrittenTypeEvidence> writtenType, List<String> writtenModifiers) {
      this(name, writtenType, writtenModifiers, Optional.empty());
    }

    /**
     * Creates a declaration without separately retained written type evidence.
     *
     * @param name
     *          declaration name
     */
    public LocalDeclaration(String name) {
      this(name, Optional.empty(), List.of());
    }

    /**
     * Creates a declaration with copied written type evidence and no written flags.
     *
     * @param name
     *          declaration name
     * @param writtenType
     *          copied written type
     */
    public LocalDeclaration(String name, Optional<WrittenTypeEvidence> writtenType) {
      this(name, writtenType, List.of());
    }
  }

  /**
   * Written label name of a statement or transfer, absent for an unlabeled transfer.
   *
   * @param name
   *          optional written identifier
   */
  record StatementLabel(Optional<String> name) implements SyntaxValue {
    /** Validates the optional written identifier. */
    public StatementLabel {
      Objects.requireNonNull(name, "name");
      name.ifPresent(SyntaxValueSupport::requireName);
    }
  }

  /** Shared source-name value boundary for typed syntax facts. */
  abstract sealed class Symbol implements SyntaxValue
      permits SourceSymbol, CompilationUnit, PackageDeclaration, ImportDeclaration, ModuleDeclaration, ModuleDirective, TypeDeclaration,
      TypeParameterDeclaration, ExecutableDeclaration, ParameterHeader, VariableDeclaration, RecordComponentDeclaration, PrimitiveType,
      PackageQualifier, Annotation, AnnotationArgument, SharedTypeAnnotations {
    enum Form {
      SYMBOL, COMPILATION_UNIT, PACKAGE_DECLARATION, IMPORT_DECLARATION, MODULE_DECLARATION, REQUIRES, EXPORTS, OPENS, USES, PROVIDES,
      TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, EXECUTABLE_DECLARATION, PARAMETER_DECLARATION, RECEIVER_PARAMETER, VARIABLE_DECLARATION,
      RECORD_COMPONENT_DECLARATION, PRIMITIVE_TYPE, PACKAGE_QUALIFIER, ANNOTATION, ANNOTATION_ARGUMENT, SHARED_TYPE_ANNOTATIONS
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
     * Returns the retained source name, or an empty string for an unnamed annotation argument.
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

  /** One written annotation pair with a compiler-selected element and typed value. */
  final class AnnotationArgument extends Symbol {
    private record AnnotationArgumentDetails(Optional<String> explicitName, String selectedElementKey, AnnotationElementValue value) {
      private AnnotationArgumentDetails {
        Objects.requireNonNull(explicitName, "explicitName").ifPresent(SyntaxValueSupport::requireName);
        SyntaxValueSupport.requireText(selectedElementKey, "selectedElementKey");
        Objects.requireNonNull(value, "value");
      }
    }

    private final AnnotationArgumentDetails details;

    /**
     * Creates an explicit annotation element-value pair.
     *
     * @param explicitName
     *          written name, if present
     * @param selectedElementKey
     *          selected element declaration identity
     * @param value
     *          attributed typed value
     */
    public AnnotationArgument(Optional<String> explicitName, String selectedElementKey, AnnotationElementValue value) {
      this(new AnnotationArgumentDetails(explicitName, selectedElementKey, value));
    }

    private AnnotationArgument(AnnotationArgumentDetails details) {
      super(details.explicitName().orElse(""), Form.ANNOTATION_ARGUMENT, details);
      this.details = details;
    }

    /**
     * Returns the written name, absent for shorthand syntax.
     *
     * @return written name, when present
     */
    public Optional<String> explicitName() {
      return details.explicitName();
    }

    /**
     * Returns the selected annotation element declaration key.
     *
     * @return selected element key
     */
    public String selectedElementKey() {
      return details.selectedElementKey();
    }

    /**
     * Returns the attributed typed value.
     *
     * @return attributed value
     */
    public AnnotationElementValue value() {
      return details.value();
    }
  }

  /** Exact written form and ordered argument headers of one annotation occurrence. */
  final class Annotation extends Symbol {
    private record AnnotationDetails(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location,
        Set<ElementType> applicableTargets, boolean closestTypeAdmissible, Optional<String> argumentFailureCode,
        Optional<String> repeatableContainerKey) {
      private AnnotationDetails {
        Objects.requireNonNull(form, "form");
        Objects.requireNonNull(location, "location");
        applicableTargets = Set.copyOf(Objects.requireNonNull(applicableTargets, "applicableTargets"));
        Objects.requireNonNull(argumentFailureCode, "argumentFailureCode")
            .ifPresent(code -> SyntaxValueSupport.requireText(code, "argumentFailureCode"));
        Objects.requireNonNull(repeatableContainerKey, "repeatableContainerKey")
            .ifPresent(key -> SyntaxValueSupport.requireText(key, "repeatableContainerKey"));
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (!closestTypeAdmissible && (location == AnnotationLocation.TYPE_USE || location == AnnotationLocation.DUAL)) {
          throw new IllegalArgumentException("An inadmissible closest type cannot receive an annotation");
        }
        if ((location == AnnotationLocation.VALUE || location == AnnotationLocation.UNRESOLVED) != applicableTargets.isEmpty()) {
          throw new IllegalArgumentException("Resolved annotation applicability must match its target set");
        }
        if (argumentFailureCode.isPresent() && arguments.isEmpty()) {
          throw new IllegalArgumentException("An argument failure requires written arguments");
        }
        if ((form == AnnotationForm.MARKER || form == AnnotationForm.EMPTY_NORMAL) != arguments.isEmpty()) {
          throw new IllegalArgumentException("Annotation argument count does not match its written form");
        }
        if (form == AnnotationForm.SINGLE_ELEMENT && (arguments.size() != 1 || arguments.getFirst().explicitName().isPresent())) {
          throw new IllegalArgumentException("Single-element annotation requires one unnamed argument");
        }
        if (form == AnnotationForm.NAMED_NORMAL && arguments.stream().anyMatch(argument -> argument.explicitName().isEmpty())) {
          throw new IllegalArgumentException("Normal annotation requires named arguments");
        }
      }
    }

    private final AnnotationDetails details;

    /**
     * Creates one written annotation form with its current location outcome.
     *
     * @param form
     *          written annotation form
     * @param arguments
     *          ordered written argument headers
     * @param location
     *          current location outcome
     * @param applicableTargets
     *          compiler-derived annotation target kinds
     */
    public Annotation(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location,
        Set<ElementType> applicableTargets) {
      this(form, arguments, location, applicableTargets, true);
    }

    /**
     * Creates one attributed annotation with its closest-type admissibility.
     *
     * @param form
     *          written annotation form
     * @param arguments
     *          ordered written argument headers
     * @param location
     *          current location outcome
     * @param applicableTargets
     *          compiler-derived annotation target kinds
     * @param closestTypeAdmissible
     *          whether the closest written type admits an annotation
     */
    public Annotation(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location,
        Set<ElementType> applicableTargets, boolean closestTypeAdmissible) {
      this(new AnnotationDetails(form, arguments, location, applicableTargets, closestTypeAdmissible, Optional.empty(), Optional.empty()));
    }

    /**
     * Creates a captured or nested value occurrence before application targets are resolved.
     *
     * @param form
     *          written annotation form
     * @param arguments
     *          written annotation arguments
     * @param location
     *          current location outcome
     */
    public Annotation(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location) {
      this(form, arguments, location, Set.of());
    }

    /**
     * Creates an occurrence whose written arguments have a blocking diagnostic and no projected pairs.
     *
     * @param form
     *          written annotation form
     * @param arguments
     *          ordered written argument headers
     * @param location
     *          current location outcome
     * @param applicableTargets
     *          compiler-derived annotation target kinds
     * @param diagnosticCode
     *          blocking source diagnostic code
     * @return diagnosed incomplete annotation occurrence
     */
    public static Annotation withArgumentFailure(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location,
        Set<ElementType> applicableTargets, String diagnosticCode) {
      return withArgumentFailure(form, arguments, location, applicableTargets, true, diagnosticCode);
    }

    /**
     * Creates an attributed occurrence with unavailable arguments and a known closest-type outcome.
     *
     * @param form
     *          written annotation form
     * @param arguments
     *          ordered written argument headers
     * @param location
     *          current location outcome
     * @param applicableTargets
     *          compiler-derived annotation target kinds
     * @param closestTypeAdmissible
     *          whether the closest written type admits an annotation
     * @param diagnosticCode
     *          blocking source diagnostic code
     * @return diagnosed incomplete annotation occurrence
     */
    public static Annotation withArgumentFailure(AnnotationForm form, List<AnnotationArgumentHeader> arguments, AnnotationLocation location,
        Set<ElementType> applicableTargets, boolean closestTypeAdmissible, String diagnosticCode) {
      return new Annotation(new AnnotationDetails(form, arguments, location, applicableTargets, closestTypeAdmissible,
          Optional.of(diagnosticCode), Optional.empty()));
    }

    private Annotation(AnnotationDetails details) {
      super("annotation", Form.ANNOTATION, details);
      this.details = details;
    }

    /**
     * Adds independently copied compiler metadata for the annotation's repeatable container.
     *
     * @param containerKey
     *          canonical container annotation type, when declared
     * @return an immutable annotation with the same source and value facts
     */
    public Annotation withRepeatableContainer(Optional<String> containerKey) {
      return new Annotation(new AnnotationDetails(details.form(), details.arguments(), details.location(), details.applicableTargets(),
          details.closestTypeAdmissible(), details.argumentFailureCode(), containerKey));
    }

    /**
     * Returns the compiler-declared repeatable container type.
     *
     * @return canonical container type identity, when declared
     */
    public Optional<String> repeatableContainerKey() {
      return details.repeatableContainerKey();
    }

    /**
     * Returns the source form captured before attribution.
     *
     * @return written annotation form
     */
    public AnnotationForm form() {
      return details.form();
    }

    /**
     * Returns ordered written argument headers.
     *
     * @return immutable argument headers
     */
    public List<AnnotationArgumentHeader> arguments() {
      return details.arguments();
    }

    /**
     * Returns the blocking diagnostic code for incomplete written arguments.
     *
     * @return diagnostic code, when present
     */
    public Optional<String> argumentFailureCode() {
      return details.argumentFailureCode();
    }

    /**
     * Returns whether a declaration target has been established.
     *
     * @return current location outcome
     */
    public AnnotationLocation location() {
      return details.location();
    }

    /**
     * Returns the compiler-derived annotation interface target set, empty for unresolved or nested values.
     *
     * @return applicable annotation target kinds
     */
    public Set<ElementType> applicableTargets() {
      return details.applicableTargets();
    }

    /**
     * Returns the attributed admissibility of the closest written type.
     *
     * @return whether that type admits an annotation
     */
    public boolean closestTypeAdmissible() {
      return details.closestTypeAdmissible();
    }
  }

  /** Written type declaration facts and the required shape of its direct source children. */
  final class TypeDeclaration extends Symbol {
    /**
     * Expected direct attachments and semantic members.
     *
     * @param annotations
     *          number of written declaration annotations
     * @param typeParameters
     *          number of written type parameters
     * @param superclass
     *          whether a written superclass is present
     * @param interfaces
     *          number of written interface attachments
     * @param permittedSubclasses
     *          number of written permitted subclass attachments
     * @param recordComponents
     *          number of written record-header components
     * @param members
     *          number of written body members
     */
    public record Shape(int annotations, int typeParameters, boolean superclass, int interfaces, int permittedSubclasses,
        int recordComponents, int members) {
      /** Rejects negative cardinalities. */
      public Shape {
        if (annotations < 0 || typeParameters < 0 || interfaces < 0 || permittedSubclasses < 0 || recordComponents < 0 || members < 0) {
          throw new IllegalArgumentException("Type declaration child counts cannot be negative");
        }
      }
    }

    private record Details(String declarationKey, Set<JavaTypeModifier> writtenModifiers, Set<JavaTypeModifier> effectiveModifiers,
        Shape shape) {
      private Details {
        SyntaxValueSupport.requireText(declarationKey, SyntaxValueSupport.DECLARATION_KEY_ARGUMENT);
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        Objects.requireNonNull(shape, "shape");
      }
    }

    private final Details details;

    /**
     * Creates a source type declaration without conflating written and attributed modifiers.
     *
     * @param name
     *          written simple name
     * @param declarationKey
     *          canonical declaration identity
     * @param writtenModifiers
     *          modifiers on the source tree
     * @param effectiveModifiers
     *          modifiers reported by the declaration element
     * @param shape
     *          required direct child shape
     */
    public TypeDeclaration(String name, String declarationKey, Set<JavaTypeModifier> writtenModifiers,
        Set<JavaTypeModifier> effectiveModifiers, Shape shape) {
      this(name, new Details(declarationKey, writtenModifiers, effectiveModifiers, shape));
    }

    /**
     * Returns the canonical declaration identity.
     *
     * @return canonical declaration key
     */
    public String declarationKey() {
      return details.declarationKey();
    }

    private TypeDeclaration(String name, Details details) {
      super(name, Form.TYPE_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns modifiers present on the source tree.
     *
     * @return source-written modifiers
     */
    public Set<JavaTypeModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns modifiers reported by the attributed declaration.
     *
     * @return attributed modifiers
     */
    public Set<JavaTypeModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns the required direct child shape.
     *
     * @return required direct child shape
     */
    public Shape shape() {
      return details.shape();
    }
  }

  /** Written field or enum constant with its declaration header and source-only initialization shape. */
  final class VariableDeclaration extends Symbol {
    /** Source declaration form. */
    public enum Kind {
      /** Ordinary field. */
      FIELD,
      /** Enum constant. */
      ENUM_CONSTANT
    }

    private record VariableDeclarationFacts(String ownerKey, String declarationKey, Kind kind,
        Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers, JavaTypeIdentity declaredType,
        int annotations, boolean type, boolean initializer, boolean argumentList, int arguments, boolean constantBody,
        List<String> sharedDeclaratorKeys) {
      private VariableDeclarationFacts {
        SyntaxValueSupport.requireText(ownerKey, SyntaxValueSupport.OWNER_KEY_ARGUMENT);
        SyntaxValueSupport.requireText(declarationKey, SyntaxValueSupport.DECLARATION_KEY_ARGUMENT);
        Objects.requireNonNull(kind, "kind");
        sharedDeclaratorKeys = List.copyOf(Objects.requireNonNull(sharedDeclaratorKeys, "sharedDeclaratorKeys"));
        if (!sharedDeclaratorKeys.isEmpty() && (kind != Kind.FIELD || sharedDeclaratorKeys.size() < 2
            || Set.copyOf(sharedDeclaratorKeys).size() != sharedDeclaratorKeys.size() || !sharedDeclaratorKeys.contains(declarationKey))) {
          throw new IllegalArgumentException("A shared field header requires all distinct source declarator identities");
        }
        sharedDeclaratorKeys.forEach(key -> SyntaxValueSupport.requireText(key, "shared field declarator key"));
        var prefix = ownerKey + (kind == Kind.FIELD ? "#field:" : "#enum:");
        if (!declarationKey.startsWith(prefix) || declarationKey.length() == prefix.length()) {
          throw new IllegalArgumentException("A variable declaration key must belong to its type owner and kind");
        }
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        Objects.requireNonNull(declaredType, "declaredType");
        if (annotations < 0 || arguments < 0 || (kind == Kind.FIELD && (!type || argumentList || arguments != 0 || constantBody))
            || (kind == Kind.ENUM_CONSTANT && (type || !initializer || (arguments > 0 && !argumentList)))) {
          throw new IllegalArgumentException("A variable declaration has an invalid source shape");
        }
      }
    }

    private final VariableDeclarationFacts details;

    /**
     * Creates a written field or enum constant.
     *
     * @param name
     *          written name
     * @param ownerKey
     *          canonical type owner
     * @param declarationKey
     *          canonical declaration identity
     * @param kind
     *          source declaration form
     * @param writtenModifiers
     *          source-written modifiers
     * @param effectiveModifiers
     *          attributed modifiers
     * @param declaredType
     *          attributed variable type
     * @param annotations
     *          written annotation count
     * @param type
     *          written type attachment presence
     * @param initializer
     *          written initializer presence
     * @param argumentList
     *          whether an enum constant has a written argument list, including an empty list
     * @param arguments
     *          enum constructor argument count
     * @param constantBody
     *          enum constant-specific body presence
     * @param sharedDeclaratorKeys
     *          ordered declarations sharing this source header, empty for a single declaration
     */
    public VariableDeclaration(String name, String ownerKey, String declarationKey, Kind kind,
        Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers, JavaTypeIdentity declaredType,
        int annotations, boolean type, boolean initializer, boolean argumentList, int arguments, boolean constantBody,
        List<String> sharedDeclaratorKeys) {
      this(name, new VariableDeclarationFacts(ownerKey, declarationKey, kind, writtenModifiers, effectiveModifiers, declaredType,
          annotations, type, initializer, argumentList, arguments, constantBody, sharedDeclaratorKeys));
    }

    private VariableDeclaration(String name, VariableDeclarationFacts details) {
      super(name, Form.VARIABLE_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns canonical declaration identity.
     *
     * @return canonical declaration identity
     */
    public String declarationKey() {
      return details.declarationKey();
    }

    /**
     * Returns canonical type owner.
     *
     * @return canonical type owner
     */
    public String ownerKey() {
      return details.ownerKey();
    }

    /**
     * Returns the source-ordered declarations sharing this header.
     *
     * @return shared declarator identities, empty for a single declaration
     */
    public List<String> sharedDeclaratorKeys() {
      return details.sharedDeclaratorKeys();
    }

    /**
     * Returns source declaration form.
     *
     * @return source declaration form
     */
    public Kind kind() {
      return details.kind();
    }

    /**
     * Returns source-written modifiers.
     *
     * @return source-written modifiers
     */
    public Set<JavaDeclarationModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns compiler-attributed modifiers.
     *
     * @return compiler-attributed modifiers
     */
    public Set<JavaDeclarationModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns compiler-attributed declared type.
     *
     * @return compiler-attributed declared type
     */
    public JavaTypeIdentity declaredType() {
      return details.declaredType();
    }

    /**
     * Returns number of written annotation attachments.
     *
     * @return number of written annotation attachments
     */
    public int annotations() {
      return details.annotations();
    }

    /**
     * Returns whether a written type attachment exists.
     *
     * @return whether a written type attachment exists
     */
    public boolean type() {
      return details.type();
    }

    /**
     * Returns whether a written initializer exists.
     *
     * @return whether a written initializer exists
     */
    public boolean initializer() {
      return details.initializer();
    }

    /**
     * Returns number of written enum constructor arguments.
     *
     * @return number of written enum constructor arguments
     */
    public int arguments() {
      return details.arguments();
    }

    /**
     * Returns whether the enum constant writes an argument list, including an empty list.
     *
     * @return written argument-list presence
     */
    public boolean argumentList() {
      return details.argumentList();
    }

    /**
     * Returns whether a constant-specific class body exists.
     *
     * @return whether a constant-specific class body exists
     */
    public boolean constantBody() {
      return details.constantBody();
    }
  }

  /** Written record-header component with an identity independent of generated members. */
  final class RecordComponentDeclaration extends Symbol {
    /**
     * Compiler-correlated declarations that can receive applicable record annotations.
     *
     * @param backingFieldKey
     *          backing field declaration key
     * @param accessorKey
     *          accessor method declaration key
     * @param canonicalParameterKey
     *          canonical constructor parameter declaration key
     * @param accessorImplicit
     *          whether the accessor is implicit in source
     * @param canonicalParameterDerived
     *          whether the canonical parameter is derived from the record header
     */
    public record Counterparts(String backingFieldKey, String accessorKey, String canonicalParameterKey, boolean accessorImplicit,
        boolean canonicalParameterDerived) {
      /** Validates the three independently identified counterpart declarations. */
      public Counterparts {
        SyntaxValueSupport.requireText(backingFieldKey, "backingFieldKey");
        SyntaxValueSupport.requireText(accessorKey, "accessorKey");
        SyntaxValueSupport.requireText(canonicalParameterKey, "canonicalParameterKey");
      }
    }

    private record RecordComponentFacts(String ownerKey, int ordinal, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, JavaTypeIdentity declaredType, int annotations, boolean varArgs,
        Counterparts counterparts) {
      private RecordComponentFacts {
        SyntaxValueSupport.requireText(ownerKey, SyntaxValueSupport.OWNER_KEY_ARGUMENT);
        if (ordinal < 0 || annotations < 0) {
          throw new IllegalArgumentException("A record component requires non-negative ordinal and annotation count");
        }
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        Objects.requireNonNull(declaredType, "declaredType");
        Objects.requireNonNull(counterparts, "counterparts");
      }
    }

    private final RecordComponentFacts details;

    /**
     * Creates a written record-header component.
     *
     * @param name
     *          written component name
     * @param ownerKey
     *          canonical record identity
     * @param ordinal
     *          header position
     * @param writtenModifiers
     *          source-written modifiers
     * @param effectiveModifiers
     *          attributed modifiers
     * @param declaredType
     *          attributed component type
     * @param annotations
     *          written annotation count
     * @param varArgs
     *          variable-arity final component state
     * @param counterparts
     *          correlated compiler declarations eligible for annotation propagation
     */
    public RecordComponentDeclaration(String name, String ownerKey, int ordinal, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, JavaTypeIdentity declaredType, int annotations, boolean varArgs,
        Counterparts counterparts) {
      this(name, new RecordComponentFacts(ownerKey, ordinal, writtenModifiers, effectiveModifiers, declaredType, annotations, varArgs,
          counterparts));
    }

    private RecordComponentDeclaration(String name, RecordComponentFacts details) {
      super(name, Form.RECORD_COMPONENT_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns canonical component identity.
     *
     * @return canonical component identity
     */
    public String declarationKey() {
      return details.ownerKey() + "#record-component:" + details.ordinal();
    }

    /**
     * Returns canonical record owner.
     *
     * @return canonical record owner
     */
    public String ownerKey() {
      return details.ownerKey();
    }

    /**
     * Returns header position.
     *
     * @return header position
     */
    public int ordinal() {
      return details.ordinal();
    }

    /**
     * Returns source-written modifiers.
     *
     * @return source-written modifiers
     */
    public Set<JavaDeclarationModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns compiler-attributed modifiers.
     *
     * @return compiler-attributed modifiers
     */
    public Set<JavaDeclarationModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns compiler-attributed component type.
     *
     * @return compiler-attributed component type
     */
    public JavaTypeIdentity declaredType() {
      return details.declaredType();
    }

    /**
     * Returns number of written annotation attachments.
     *
     * @return number of written annotation attachments
     */
    public int annotations() {
      return details.annotations();
    }

    /**
     * Returns variable-arity final component state.
     *
     * @return variable-arity final component state
     */
    public boolean varArgs() {
      return details.varArgs();
    }

    /**
     * Returns compiler-correlated counterpart identities for record annotation propagation.
     *
     * @return counterpart declaration identities
     */
    public Counterparts counterparts() {
      return details.counterparts();
    }
  }

  /** Written type parameter, its canonical identity, and its direct source attachments. */
  final class TypeParameterDeclaration extends Symbol {
    private record TypeParameterDetails(String ownerKey, int ordinal, int annotations, int bounds, JavaTypeStructure upperBound) {
      private TypeParameterDetails {
        SyntaxValueSupport.requireText(ownerKey, SyntaxValueSupport.OWNER_KEY_ARGUMENT);
        Objects.requireNonNull(upperBound, "upperBound");
        if (ordinal < 0 || annotations < 0 || bounds < 0) {
          throw new IllegalArgumentException("Type parameter child counts cannot be negative");
        }
      }
    }

    private final TypeParameterDetails details;

    /**
     * Creates a source type parameter with its owner-and-ordinal declaration key.
     *
     * @param name
     *          written simple name
     * @param ownerKey
     *          canonical owner identity
     * @param ordinal
     *          declaration position within that owner
     * @param annotations
     *          written annotation attachment count
     * @param bounds
     *          written bound attachment count
     * @param upperBound
     *          compiler-derived upper bound, including implicit or intersection components
     */
    public TypeParameterDeclaration(String name, String ownerKey, int ordinal, int annotations, int bounds, JavaTypeStructure upperBound) {
      this(name, new TypeParameterDetails(ownerKey, ordinal, annotations, bounds, upperBound));
    }

    private TypeParameterDeclaration(String name, TypeParameterDetails details) {
      super(name, Form.TYPE_PARAMETER_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns the canonical declaration key.
     *
     * @return canonical declaration key
     */
    public String declarationKey() {
      return details.ownerKey() + "#type-parameter:" + details.ordinal();
    }

    /**
     * Returns the canonical owner key.
     *
     * @return canonical owner key
     */
    public String ownerKey() {
      return details.ownerKey();
    }

    /**
     * Returns the owner-local declaration position.
     *
     * @return owner-local declaration position
     */
    public int ordinal() {
      return details.ordinal();
    }

    /**
     * Returns the written annotation count.
     *
     * @return number of written annotation attachments
     */
    public int annotations() {
      return details.annotations();
    }

    /**
     * Returns the written bound count.
     *
     * @return number of written bound attachments
     */
    public int bounds() {
      return details.bounds();
    }

    /**
     * Returns the compiler-derived upper bound, which may contain an unwritten implicit component.
     *
     * @return recursive attributed upper bound
     */
    public JavaTypeStructure upperBound() {
      return details.upperBound();
    }
  }

  /** Written method, constructor, or annotation-interface element and its direct header shape. */
  final class ExecutableDeclaration extends Symbol {
    /** Source executable form. */
    public enum ExecutableKind {
      /** Ordinary method. */
      METHOD,
      /** Constructor written in the source type. */
      CONSTRUCTOR,
      /** Compact canonical constructor written in a record. */
      COMPACT_CONSTRUCTOR,
      /** Annotation-interface element. */
      ANNOTATION_ELEMENT
    }

    /**
     * Required direct source attachments.
     *
     * @param annotations
     *          written declaration annotations
     * @param typeParameters
     *          written type parameters
     * @param receiver
     *          explicit receiver presence
     * @param parameters
     *          written formal parameters
     * @param returnType
     *          written return type presence
     * @param thrownTypes
     *          written throws attachments
     * @param body
     *          written body presence
     * @param varArgs
     *          attributed variable-arity state
     */
    public record HeaderShape(int annotations, int typeParameters, boolean receiver, int parameters, boolean returnType, int thrownTypes,
        boolean body, boolean varArgs) {
      /** Rejects impossible cardinalities. */
      public HeaderShape {
        if (annotations < 0 || typeParameters < 0 || parameters < 0 || thrownTypes < 0) {
          throw new IllegalArgumentException("Executable declaration child counts cannot be negative");
        }
      }
    }

    /**
     * Compiler-attributed header facts kept separate from written source attachments.
     *
     * @param receiverType
     *          attributed receiver type or no receiver type
     * @param receiverOwnerKey
     *          canonical type owner of a resolved receiver
     * @param receiverSimpleName
     *          compiler-selected simple name of the receiver type declaration
     * @param enclosingTypeKey
     *          immediate enclosing type of a constructor's declaring type, when present
     * @param thrownTypes
     *          attributed thrown types in declaration order
     * @param defaultValue
     *          attributed annotation element default, absent when no default is written
     */
    public record AttributedHeader(SyntaxType receiverType, Optional<String> receiverOwnerKey, Optional<String> receiverSimpleName,
        Optional<String> enclosingTypeKey, List<SyntaxType> thrownTypes, Optional<AnnotationElementValue> defaultValue) {
      /** Validates receiver identity, ordered thrown types, and optional default presence. */
      public AttributedHeader {
        Objects.requireNonNull(receiverType, "receiverType");
        Objects.requireNonNull(receiverOwnerKey, "receiverOwnerKey");
        Objects.requireNonNull(receiverSimpleName, "receiverSimpleName");
        Objects.requireNonNull(enclosingTypeKey, "enclosingTypeKey");
        Objects.requireNonNull(defaultValue, "defaultValue");
        thrownTypes = List.copyOf(Objects.requireNonNull(thrownTypes, "thrownTypes"));
        receiverOwnerKey.ifPresent(key -> SyntaxValueSupport.requireText(key, "receiverOwnerKey"));
        receiverSimpleName.ifPresent(SyntaxValueSupport::requireName);
        enclosingTypeKey.ifPresent(key -> SyntaxValueSupport.requireText(key, "enclosingTypeKey"));
        if ((receiverType != SyntaxType.NotApplicable.INSTANCE && !(receiverType instanceof SyntaxType.Resolved))
            || (receiverType instanceof SyntaxType.Resolved) != receiverOwnerKey.isPresent()
            || receiverOwnerKey.isPresent() != receiverSimpleName.isPresent()) {
          throw new IllegalArgumentException("An executable requires a correlated attributed receiver type and owner");
        }
        if (receiverType instanceof SyntaxType.Resolved resolved) {
          if (resolved.identity().category() != JavaTypeCategory.DECLARED) {
            throw new IllegalArgumentException("An executable receiver requires a declared type");
          }
          var key = receiverOwnerKey.orElseThrow();
          var structure = resolved.structure()
              .orElseThrow(() -> new IllegalArgumentException("An executable receiver requires a structural declared type"));
          var selectedKey = switch (structure) {
            case JavaTypeStructure.Declared declared -> declared.declarationKey();
            case JavaTypeStructure.LocalDeclared local -> local.declarationKey();
            default -> throw new IllegalArgumentException("An executable receiver requires a declared type");
          };
          if (!key.equals(selectedKey)) {
            throw new IllegalArgumentException("An executable receiver type must identify its attributed owner");
          }
        }
        for (var thrownType : thrownTypes) {
          if (!(thrownType instanceof SyntaxType.Resolved resolved) || (resolved.identity().category() != JavaTypeCategory.DECLARED
              && resolved.identity().category() != JavaTypeCategory.TYPE_VARIABLE)) {
            throw new IllegalArgumentException("An executable thrown type requires a resolved declared or type-variable outcome");
          }
        }
      }

      /**
       * Creates an attributed header without a receiver type.
       *
       * @param thrownTypes
       *          attributed thrown types in declaration order
       * @param defaultValue
       *          attributed annotation element default, when present
       * @return attributed header
       */
      public static AttributedHeader withoutReceiver(List<SyntaxType> thrownTypes, Optional<AnnotationElementValue> defaultValue) {
        return new AttributedHeader(SyntaxType.NotApplicable.INSTANCE, Optional.empty(), Optional.empty(), Optional.empty(), thrownTypes,
            defaultValue);
      }

      /**
       * Creates an attributed header with a resolved receiver and its canonical type owner.
       *
       * @param receiverType
       *          resolved receiver type
       * @param receiverOwnerKey
       *          canonical receiver type owner
       * @param receiverSimpleName
       *          compiler-selected simple name of the receiver type declaration
       * @param enclosingTypeKey
       *          immediate enclosing type of a constructor's declaring type, when present
       * @param thrownTypes
       *          attributed thrown types in declaration order
       * @return attributed header
       */
      public static AttributedHeader withReceiver(SyntaxType receiverType, String receiverOwnerKey, String receiverSimpleName,
          Optional<String> enclosingTypeKey, List<SyntaxType> thrownTypes) {
        return new AttributedHeader(receiverType, Optional.of(receiverOwnerKey), Optional.of(receiverSimpleName), enclosingTypeKey,
            thrownTypes, Optional.empty());
      }
    }

    private record ExecutableDetails(String ownerKey, String declarationKey, ExecutableKind kind,
        Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers, HeaderShape shape,
        AttributedHeader attributed) {
      private ExecutableDetails {
        SyntaxValueSupport.requireText(ownerKey, SyntaxValueSupport.OWNER_KEY_ARGUMENT);
        SyntaxValueSupport.requireText(declarationKey, SyntaxValueSupport.DECLARATION_KEY_ARGUMENT);
        if (!declarationKey.startsWith(ownerKey + "#") || declarationKey.length() <= ownerKey.length() + 1) {
          throw new IllegalArgumentException("An executable declaration key must belong to its type owner");
        }
        Objects.requireNonNull(kind, "kind");
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(attributed, "attributed");
        if (shape.receiver() && !(attributed.receiverType() instanceof SyntaxType.Resolved)) {
          throw new IllegalArgumentException("An executable requires an available attributed receiver-type outcome");
        }
        if (shape.thrownTypes() != attributed.thrownTypes().size()) {
          throw new IllegalArgumentException("Written and attributed thrown-type counts must agree");
        }
        if ((writtenModifiers.contains(JavaDeclarationModifier.STATIC) || effectiveModifiers.contains(JavaDeclarationModifier.STATIC))
            && (shape.receiver() || attributed.receiverType() != SyntaxType.NotApplicable.INSTANCE)) {
          throw new IllegalArgumentException("A static executable cannot have a receiver parameter or receiver type");
        }
        if (attributed.receiverType() instanceof SyntaxType.Resolved) {
          var receiverOwnerKey = attributed.receiverOwnerKey().orElseThrow();
          if (kind == ExecutableKind.METHOD && !ownerKey.equals(receiverOwnerKey)) {
            throw new IllegalArgumentException("A method receiver must identify its declaring type");
          }
          if (kind == ExecutableKind.CONSTRUCTOR && !attributed.enclosingTypeKey().equals(Optional.of(receiverOwnerKey))) {
            throw new IllegalArgumentException("A constructor receiver must identify its immediately enclosing type");
          }
          if (kind == ExecutableKind.COMPACT_CONSTRUCTOR || kind == ExecutableKind.ANNOTATION_ELEMENT) {
            throw new IllegalArgumentException("This executable kind cannot have a receiver type");
          }
        }
        if ((kind == ExecutableKind.CONSTRUCTOR || kind == ExecutableKind.COMPACT_CONSTRUCTOR) && shape.returnType()) {
          throw new IllegalArgumentException("A constructor cannot own a return-type occurrence");
        }
        if (kind == ExecutableKind.METHOD && !shape.returnType()) {
          throw new IllegalArgumentException("A method requires a return-type occurrence");
        }
        if (kind == ExecutableKind.CONSTRUCTOR && !shape.body()) {
          throw new IllegalArgumentException("A constructor requires a body");
        }
        if (kind != ExecutableKind.ANNOTATION_ELEMENT && attributed.defaultValue().isPresent()) {
          throw new IllegalArgumentException("Only an annotation element can own a default value");
        }
        if (kind == ExecutableKind.COMPACT_CONSTRUCTOR
            && (shape.parameters() != 0 || !shape.body() || shape.receiver() || shape.typeParameters() != 0 || shape.thrownTypes() != 0)) {
          throw new IllegalArgumentException("A compact constructor has an invalid source header");
        }
        if (kind != ExecutableKind.COMPACT_CONSTRUCTOR && shape.varArgs() && shape.parameters() == 0) {
          throw new IllegalArgumentException("A variable-arity executable requires a written formal parameter");
        }
        if (kind == ExecutableKind.ANNOTATION_ELEMENT && (shape.receiver() || shape.parameters() != 0 || shape.typeParameters() != 0
            || shape.thrownTypes() != 0 || shape.body() || shape.varArgs() || !shape.returnType())) {
          throw new IllegalArgumentException("An annotation element has an invalid executable shape");
        }
      }

    }

    private final ExecutableDetails details;

    /**
     * Creates an executable declaration with separate written and attributed modifiers.
     *
     * @param name
     *          written source name
     * @param ownerKey
     *          canonical type owner identity
     * @param declarationKey
     *          canonical declaration identity
     * @param kind
     *          source executable form
     * @param writtenModifiers
     *          modifiers written on the source tree
     * @param effectiveModifiers
     *          compiler-attributed modifiers
     * @param shape
     *          required child shape
     * @param attributed
     *          compiler-attributed receiver, thrown-type, and annotation default outcomes
     */
    public ExecutableDeclaration(String name, String ownerKey, String declarationKey, ExecutableKind kind,
        Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers, HeaderShape shape,
        AttributedHeader attributed) {
      this(name, new ExecutableDetails(ownerKey, declarationKey, kind, writtenModifiers, effectiveModifiers, shape, attributed));
    }

    private ExecutableDeclaration(String name, ExecutableDetails details) {
      super(name, Form.EXECUTABLE_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns the canonical declaration key.
     *
     * @return canonical declaration key
     */
    public String declarationKey() {
      return details.declarationKey();
    }

    /**
     * Returns the canonical type owner key.
     *
     * @return canonical type owner key
     */
    public String ownerKey() {
      return details.ownerKey();
    }

    /**
     * Returns the source executable form.
     *
     * @return source executable form
     */
    public ExecutableKind kind() {
      return details.kind();
    }

    /**
     * Returns this annotation element's attributed default, if it declares one.
     *
     * @return the default value, absent for a required element or another executable kind
     */
    public Optional<AnnotationElementValue> defaultValue() {
      return details.attributed().defaultValue();
    }

    /**
     * Returns source-written modifiers.
     *
     * @return source-written modifiers
     */
    public Set<JavaDeclarationModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns compiler-attributed modifiers.
     *
     * @return compiler-attributed modifiers
     */
    public Set<JavaDeclarationModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns the required direct child shape.
     *
     * @return required direct child shape
     */
    public HeaderShape shape() {
      return details.shape();
    }

    /**
     * Returns the compiler-attributed receiver-type outcome independently of written receiver syntax.
     *
     * @return resolved receiver type or no receiver type
     */
    public SyntaxType receiverType() {
      return details.attributed().receiverType();
    }

    /**
     * Returns the canonical owner of a resolved receiver type when one is available.
     *
     * @return canonical receiver type owner, if present
     */
    public Optional<String> receiverOwnerKey() {
      return details.attributed().receiverOwnerKey();
    }

    /**
     * Returns the compiler-selected simple name of a resolved receiver type.
     *
     * @return receiver declaration name, if present
     */
    public Optional<String> receiverSimpleName() {
      return details.attributed().receiverSimpleName();
    }

    /**
     * Returns all independently retained attributed header components.
     *
     * @return immutable attributed header, including enclosing owner and defaults
     */
    public AttributedHeader attributedHeader() {
      return details.attributed();
    }

    /**
     * Returns the compiler-attributed thrown types in source declaration order.
     *
     * @return ordered attributed thrown types
     */
    public List<SyntaxType> thrownTypes() {
      return details.attributed().thrownTypes();
    }
  }

  /** Closed boundary for formal declarations and explicit receiver syntax. */
  abstract sealed class ParameterHeader extends Symbol permits ParameterDeclaration, ReceiverParameter {
    ParameterHeader(String name, Form form, Object details) {
      super(name, form, details);
    }
  }

  /** Source formal parameter declaration with a compiler-correlated identity. */
  final class ParameterDeclaration extends ParameterHeader {
    private record ParameterDetails(String declarationKey, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, int annotations, boolean type, boolean varArgs) {
      private ParameterDetails {
        SyntaxValueSupport.requireText(declarationKey, SyntaxValueSupport.DECLARATION_KEY_ARGUMENT);
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        if (annotations < 0 || !type) {
          throw new IllegalArgumentException("Invalid formal parameter declaration shape");
        }
      }
    }

    private final ParameterDetails details;

    /**
     * Creates a source parameter with its declared header facts.
     *
     * @param declarationKey
     *          canonical declaration identity
     * @param name
     *          written source name
     * @param writtenModifiers
     *          source-written modifiers
     * @param effectiveModifiers
     *          compiler-attributed modifiers
     * @param annotations
     *          written annotation count
     * @param type
     *          declared type attachment state
     * @param varArgs
     *          attributed variable-arity state
     */
    public ParameterDeclaration(String declarationKey, String name, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, int annotations, boolean type, boolean varArgs) {
      this(name, new ParameterDetails(declarationKey, writtenModifiers, effectiveModifiers, annotations, type, varArgs));
    }

    private ParameterDeclaration(String name, ParameterDetails details) {
      super(name, Form.PARAMETER_DECLARATION, details);
      SyntaxValueSupport.requireName(name);
      this.details = details;
    }

    /**
     * Returns the canonical declaration key.
     *
     * @return canonical declaration key
     */
    public String declarationKey() {
      return details.declarationKey();
    }

    /**
     * Returns source-written modifiers.
     *
     * @return source-written modifiers
     */
    public Set<JavaDeclarationModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns compiler-attributed modifiers.
     *
     * @return compiler-attributed modifiers
     */
    public Set<JavaDeclarationModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns the written annotation count.
     *
     * @return written annotation count
     */
    public int annotations() {
      return details.annotations();
    }

    /**
     * Returns the type-attachment state.
     *
     * @return true when a type is attached
     */
    public boolean type() {
      return details.type();
    }

    /**
     * Returns the variable-arity state.
     *
     * @return true for a variable-arity formal
     */
    public boolean varArgs() {
      return details.varArgs();
    }
  }

  /** Explicit receiver syntax owned by an executable; it does not declare a variable. */
  final class ReceiverParameter extends ParameterHeader {
    private record ReceiverDetails(String ownerKey, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, int annotations) {
      private ReceiverDetails {
        SyntaxValueSupport.requireText(ownerKey, SyntaxValueSupport.OWNER_KEY_ARGUMENT);
        writtenModifiers = Set.copyOf(Objects.requireNonNull(writtenModifiers, SyntaxValueSupport.WRITTEN_MODIFIERS_ARGUMENT));
        effectiveModifiers = Set.copyOf(Objects.requireNonNull(effectiveModifiers, SyntaxValueSupport.EFFECTIVE_MODIFIERS_ARGUMENT));
        if (annotations < 0) {
          throw new IllegalArgumentException("Receiver annotation count cannot be negative");
        }
      }
    }

    private final ReceiverDetails details;

    /**
     * Creates explicit receiver syntax with its owning executable and written attachments.
     *
     * @param ownerKey
     *          canonical owner executable identity
     * @param writtenModifiers
     *          source-written modifiers
     * @param effectiveModifiers
     *          compiler-attributed modifiers
     * @param annotations
     *          written annotation count
     */
    public ReceiverParameter(String ownerKey, Set<JavaDeclarationModifier> writtenModifiers,
        Set<JavaDeclarationModifier> effectiveModifiers, int annotations) {
      this(new ReceiverDetails(ownerKey, writtenModifiers, effectiveModifiers, annotations));
    }

    private ReceiverParameter(ReceiverDetails details) {
      super("this", Form.RECEIVER_PARAMETER, details);
      this.details = details;
    }

    /**
     * Returns the canonical owner executable identity.
     *
     * @return owner executable key
     */
    public String ownerKey() {
      return details.ownerKey();
    }

    /**
     * Returns source-written modifiers.
     *
     * @return source-written modifiers
     */
    public Set<JavaDeclarationModifier> writtenModifiers() {
      return details.writtenModifiers();
    }

    /**
     * Returns compiler-attributed modifiers.
     *
     * @return compiler-attributed modifiers
     */
    public Set<JavaDeclarationModifier> effectiveModifiers() {
      return details.effectiveModifiers();
    }

    /**
     * Returns the written annotation count.
     *
     * @return number of written annotation attachments
     */
    public int annotations() {
      return details.annotations();
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
    private final Optional<PackageQualifierEvidence> sourceQualifier;

    /**
     * Creates a qualified package name value.
     *
     * @param qualifiedName
     *          the written package name
     */
    public PackageDeclaration(String qualifiedName) {
      this(qualifiedName, Optional.empty());
    }

    /**
     * Creates a package name with its independently copied written qualifier.
     *
     * @param qualifiedName
     *          canonical qualified package name
     * @param qualifier
     *          exact source-tree kind and range when supplied by a source producer
     */
    public PackageDeclaration(String qualifiedName, Optional<PackageQualifierEvidence> qualifier) {
      super(qualifiedName, Form.PACKAGE_DECLARATION, Objects.requireNonNull(qualifier, "qualifier"));
      SyntaxValueSupport.requireName(qualifiedName);
      sourceQualifier = qualifier;
    }

    /**
     * Returns the copied written name attachment when present.
     *
     * @return exact package qualifier source evidence
     */
    public Optional<PackageQualifierEvidence> qualifier() {
      return sourceQualifier;
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

  /** Source qualifier form of a selected field. */
  public enum FieldReceiver {
    /** Unqualified access to an instance field through the implicit receiver. */
    INSTANCE_IMPLICIT,
    /** Instance field selected through a written expression qualifier. */
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
    /** Static field selected through a written type qualifier. */
    STATIC_TYPE,
    /** Static field selected through a written expression qualifier. */
    STATIC_EXPRESSION;

    /**
     * Returns whether the source form owns a receiver-expression child.
     *
     * @return whether a written receiver expression is represented
     */
    public boolean hasReceiverExpression() {
      return this == INSTANCE_EXPRESSION || this == STATIC_EXPRESSION;
    }

    /**
     * Returns whether the selected field is static.
     *
     * @return whether this is a static field selection
     */
    public boolean isStatic() {
      return this == STATIC_UNQUALIFIED || this == STATIC_TYPE || this == STATIC_EXPRESSION;
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
   * Written array form and annotation grouping; expressions and annotations remain canonical children.
   *
   * @param writtenType
   *          whether the expression writes {@code new} and a component type
   * @param initializer
   *          whether braces are present, including empty braces
   * @param dimensionAnnotationCounts
   *          ordered annotation group sizes for sized dimensions, or the first omitted dimension of an initialized creation
   */
  record ArrayCreation(boolean writtenType, boolean initializer, List<Integer> dimensionAnnotationCounts) implements SyntaxValue {
    /** Copies the grouping and rejects impossible initializer-only forms. */
    public ArrayCreation {
      dimensionAnnotationCounts = List.copyOf(dimensionAnnotationCounts);
      if (dimensionAnnotationCounts.stream().anyMatch(count -> count < 0)
          || (!writtenType && (!initializer || !dimensionAnnotationCounts.isEmpty()))) {
        throw new IllegalArgumentException("An array initializer without a written type requires braces and no dimension annotations");
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
