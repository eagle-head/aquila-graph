package org.aquilagraph.syntax;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.model.JavaTypeCategory;
import org.aquilagraph.model.JavaTypeModifier;
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
   */
  record LocalDeclaration(String name) implements SyntaxValue {
    /** Validates the declaration name. */
    public LocalDeclaration {
      SyntaxValueSupport.requireName(name);
    }
  }

  /** Shared source-name value boundary for typed syntax facts. */
  abstract sealed class Symbol implements SyntaxValue permits SourceSymbol, CompilationUnit, PackageDeclaration, ImportDeclaration,
      ModuleDeclaration, ModuleDirective, TypeDeclaration, TypeParameterDeclaration, ExecutableDeclaration, ParameterHeader {
    enum Form {
      SYMBOL, COMPILATION_UNIT, PACKAGE_DECLARATION, IMPORT_DECLARATION, MODULE_DECLARATION, REQUIRES, EXPORTS, OPENS, USES, PROVIDES,
      TYPE_DECLARATION, TYPE_PARAMETER_DECLARATION, EXECUTABLE_DECLARATION, PARAMETER_DECLARATION, RECEIVER_PARAMETER
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
     * @param members
     *          number of written body members
     */
    public record Shape(int annotations, int typeParameters, boolean superclass, int interfaces, int permittedSubclasses, int members) {
      /** Rejects negative cardinalities. */
      public Shape {
        if (annotations < 0 || typeParameters < 0 || interfaces < 0 || permittedSubclasses < 0 || members < 0) {
          throw new IllegalArgumentException("Type declaration child counts cannot be negative");
        }
      }
    }

    private record Details(String declarationKey, Set<JavaTypeModifier> writtenModifiers, Set<JavaTypeModifier> effectiveModifiers,
        Shape shape) {
      private Details {
        SyntaxValueSupport.requireText(declarationKey, "declarationKey");
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

  /** Written type parameter, its canonical identity, and its direct source attachments. */
  final class TypeParameterDeclaration extends Symbol {
    private record TypeParameterDetails(String ownerKey, int ordinal, int annotations, int bounds) {
      private TypeParameterDetails {
        SyntaxValueSupport.requireText(ownerKey, "ownerKey");
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
     */
    public TypeParameterDeclaration(String name, String ownerKey, int ordinal, int annotations, int bounds) {
      this(name, new TypeParameterDetails(ownerKey, ordinal, annotations, bounds));
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
     * @param defaultValue
     *          written annotation default presence
     * @param varArgs
     *          attributed variable-arity state
     */
    public record HeaderShape(int annotations, int typeParameters, boolean receiver, int parameters, boolean returnType, int thrownTypes,
        boolean body, boolean defaultValue, boolean varArgs) {
      /** Rejects impossible cardinalities. */
      public HeaderShape {
        if (annotations < 0 || typeParameters < 0 || parameters < 0 || thrownTypes < 0) {
          throw new IllegalArgumentException("Executable declaration child counts cannot be negative");
        }
        if (body && defaultValue) {
          throw new IllegalArgumentException("An executable cannot have both a body and an annotation default");
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
     * @param thrownTypes
     *          attributed thrown types in declaration order
     */
    public record AttributedHeader(SyntaxType receiverType, Optional<String> receiverOwnerKey, List<SyntaxType> thrownTypes) {
      /** Validates the attributed receiver identity and ordered thrown types. */
      public AttributedHeader {
        Objects.requireNonNull(receiverType, "receiverType");
        Objects.requireNonNull(receiverOwnerKey, "receiverOwnerKey");
        thrownTypes = List.copyOf(Objects.requireNonNull(thrownTypes, "thrownTypes"));
        receiverOwnerKey.ifPresent(key -> SyntaxValueSupport.requireText(key, "receiverOwnerKey"));
        if ((receiverType != SyntaxType.NotApplicable.INSTANCE && !(receiverType instanceof SyntaxType.Resolved))
            || (receiverType instanceof SyntaxType.Resolved) != receiverOwnerKey.isPresent()) {
          throw new IllegalArgumentException("An executable requires a correlated attributed receiver type and owner");
        }
        if (receiverType instanceof SyntaxType.Resolved resolved) {
          if (resolved.identity().category() != JavaTypeCategory.DECLARED) {
            throw new IllegalArgumentException("An executable receiver requires a declared type");
          }
          var key = receiverOwnerKey.orElseThrow();
          if (key.startsWith("type:") && !key.contains("#") && !key.substring("type:".length()).equals(resolved.identity().erasedName())) {
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
       * @return attributed header
       */
      public static AttributedHeader withoutReceiver(List<SyntaxType> thrownTypes) {
        return new AttributedHeader(SyntaxType.NotApplicable.INSTANCE, Optional.empty(), thrownTypes);
      }

      /**
       * Creates an attributed header with a resolved receiver and its canonical type owner.
       *
       * @param receiverType
       *          resolved receiver type
       * @param receiverOwnerKey
       *          canonical receiver type owner
       * @param thrownTypes
       *          attributed thrown types in declaration order
       * @return attributed header
       */
      public static AttributedHeader withReceiver(SyntaxType receiverType, String receiverOwnerKey, List<SyntaxType> thrownTypes) {
        return new AttributedHeader(receiverType, Optional.of(receiverOwnerKey), thrownTypes);
      }
    }

    private record ExecutableDetails(String ownerKey, String declarationKey, ExecutableKind kind,
        Set<JavaDeclarationModifier> writtenModifiers, Set<JavaDeclarationModifier> effectiveModifiers, HeaderShape shape,
        AttributedHeader attributed) {
      private ExecutableDetails {
        SyntaxValueSupport.requireText(ownerKey, "ownerKey");
        SyntaxValueSupport.requireText(declarationKey, "declarationKey");
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
          if (kind == ExecutableKind.CONSTRUCTOR && !receiverMatchesConstructorOwner(ownerKey, receiverOwnerKey)) {
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
        if (kind != ExecutableKind.ANNOTATION_ELEMENT && shape.defaultValue()) {
          throw new IllegalArgumentException("Only an annotation element can own a default value");
        }
        if (kind == ExecutableKind.COMPACT_CONSTRUCTOR && (shape.parameters() != 0 || !shape.body() || shape.receiver()
            || shape.typeParameters() != 0 || shape.thrownTypes() != 0 || shape.defaultValue())) {
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

      private static boolean receiverMatchesConstructorOwner(String ownerKey, String receiverOwnerKey) {
        var memberPrefix = receiverOwnerKey + ".";
        var memberOwner = ownerKey.startsWith(memberPrefix) && !ownerKey.substring(memberPrefix.length()).contains(".");
        var localOwner = ownerKey.startsWith(receiverOwnerKey + "#") && ownerKey.contains(":type:");
        return memberOwner || localOwner;
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
     *          compiler-attributed receiver and thrown-type outcomes
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
        SyntaxValueSupport.requireText(declarationKey, "declarationKey");
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
        SyntaxValueSupport.requireText(ownerKey, "ownerKey");
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
