package org.aquilagraph.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Annotation;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.classfile.attribute.PermittedSubclassesAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.cfg.internal.JavaCfgDeriver;
import javax.tools.ToolProvider;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class ModuleDependencyBoundaryTest {
  private static final String PREFIX = "org.aquilagraph.";
  private static final Set<String> NULLNESS_METADATA = Set.of("org.jspecify.annotations.NullMarked", "org.jspecify.annotations.Nullable");
  private static final Set<String> PUBLIC_PACKAGES = Set.of(PREFIX + "identity.api", PREFIX + "source.api", PREFIX + "cfg.api",
      PREFIX + "cfg", PREFIX + "frontend", PREFIX + "application", PREFIX + "application.port", PREFIX + "repository.neo4j");
  private static final Map<String, Set<String>> READABLE_PACKAGES = Map.of(
      "identity", Set.of(),
      "source", Set.of(PREFIX + "identity.api"),
      "cfg", Set.of(PREFIX + "identity.api", PREFIX + "source.api"),
      "frontend", Set.of(PREFIX + "identity.api", PREFIX + "source.api", PREFIX + "source.admission"),
      "application", Set.of(PREFIX + "identity.api", PREFIX + "source.api", PREFIX + "cfg.api"),
      "repository", Set.of(PREFIX + "identity.api", PREFIX + "source.api", PREFIX + "source.retention", PREFIX + "cfg.api",
          PREFIX + "application", PREFIX + "application.port", PREFIX + "application.internal.repository"),
      "cli", PUBLIC_PACKAGES);
  private static final Set<String> BASE_TYPES = platformTypes("java.base");
  private static final Set<String> COMPILER_TYPES = publicCompilerTypes();
  private static final Set<String> LOGGING_TYPES = platformTypes("java.logging");
  private static final Map<String, String> TYPE_OWNERS = typeOwners();

  @TestFactory
  List<DynamicTest> givenCanonicalModuleOutput_whenEveryClassfileAndMetadataChannelIsInspected_thenOnlyItsSelectedDependenciesRemain() {
    return List.of("identity", "source", "cfg", "frontend", "application", "repository", "cli").stream()
        .map(module -> DynamicTest.dynamicTest(module, () -> inspectModule(module))).toList();
  }

  private static void inspectModule(String module) throws IOException {
    var root = Path.of("target/classes", PREFIX + module);
    try (var paths = Files.walk(root)) {
      var files = paths.filter(path -> path.toString().endsWith(".class")).toList();
      assertFalse(files.isEmpty(), "Module has no compiled classes: " + module);
      for (var file : files) {
        var bytes = Files.readAllBytes(file);
        var model = ClassFile.of().parse(bytes);
        var owner = model.isModuleInfo() ? PREFIX + module : model.thisClass().asInternalName().replace('/', '.');
        var permits = model.attributes().stream().filter(PermittedSubclassesAttribute.class::isInstance)
            .map(PermittedSubclassesAttribute.class::cast).flatMap(attribute -> attribute.permittedSubclasses().stream())
            .map(type -> type.asInternalName().replace('/', '.')).collect(Collectors.toUnmodifiableSet());
        var violations = violations(module, owner, permits, ClassfileDependencies.read(bytes));
        assertTrue(violations.isEmpty(), () -> file + ": " + String.join(", ", violations));
        if (model.flags().has(AccessFlag.PUBLIC) && !module.equals("cli")) {
          var ownerPackage = packageName(owner);
          if (PUBLIC_PACKAGES.contains(ownerPackage) || ownerPackage.equals(PREFIX + "source.admission")
              || ownerPackage.equals(PREFIX + "source.retention")) {
            var surface = ClassfileDependencies.publicSignatures(bytes);
            for (var type : surface.types()) {
              assertTrue(BASE_TYPES.contains(type) || PUBLIC_PACKAGES.contains(packageName(type))
                  || packageName(type).equals(ownerPackage), file + " exposes " + type);
            }
          }
        }
      }
    }
  }

  @Test
  void givenAForbiddenGenericBound_whenTheClassfileGateRuns_thenTheGenericDependencyIsRejected() throws IOException {
    var references = references(GenericLeak.class);
    assertTrue(references.types().contains(AstValueAdmission.class.getName()));
    assertTrue(violations("cfg", PREFIX + "cfg.api.Probe", Set.of(), references)
        .contains("forbidden type " + AstValueAdmission.class.getName()));
    var signatures = ClassfileDependencies.publicSignatures(classBytes(GenericLeak.class));
    assertTrue(signatures.types().contains(AstValueAdmission.class.getName()));
  }

  @Test
  void givenAnIntrinsicValidatorCallingPrivilegedConstruction_whenTheGateRuns_thenTheReaderClosureRejectsTheBackEdge() throws IOException {
    assertTrue(violations("source", PREFIX + "source.internal.values.Probe", Set.of(), references(GenericLeak.class))
        .contains("forbidden type " + AstValueAdmission.class.getName()));
    var graph = references(CfgImplementationLeak.class);
    assertTrue(graph.types().contains(JavaCfgDeriver.class.getName()));
    assertTrue(violations("cfg", PREFIX + "cfg.internal.values.Probe", Set.of(), graph)
        .contains("forbidden type " + JavaCfgDeriver.class.getName()));
  }

  @TestFactory
  List<DynamicTest> givenForbiddenInvisibleOrTypeUseMetadata_whenTheClassfileGateRuns_thenItsAnnotationIsRejected() {
    return List.of(InvisibleMetadataLeak.class, TypeUseMetadataLeak.class).stream().map(fixture -> DynamicTest.dynamicTest(
        fixture.getSimpleName(), () -> {
          var references = references(fixture);
          assertTrue(references.annotations().contains(UnapprovedMetadata.class.getName()));
          assertTrue(violations("source", PREFIX + "source.api.Probe", Set.of(), references)
              .contains("forbidden annotation " + UnapprovedMetadata.class.getName()));
        })).toList();
  }

  @Test
  void givenNullnessMetadataAndAnOrdinaryAnnotationValueType_whenTheGateRuns_thenOnlyMetadataIsAllowed() throws IOException {
    var metadata = references(NullnessMetadata.class);
    assertTrue(metadata.annotations().contains(Nullable.class.getName()));
    assertFalse(metadata.types().contains(Nullable.class.getName()));
    assertFalse(violations("source", PREFIX + "source.api.Probe", Set.of(), metadata)
        .contains("forbidden annotation " + Nullable.class.getName()));
    var operational = references(NullnessValueLeak.class);
    assertTrue(operational.types().contains(Nullable.class.getName()));
    assertTrue(violations("source", PREFIX + "source.api.Probe", Set.of(), operational)
        .contains("forbidden type " + Nullable.class.getName()));
  }

  @TestFactory
  List<DynamicTest> givenForbiddenModuleOrPackageMetadata_whenTheGateRuns_thenTheDescriptorAnnotationIsRejected() {
    var annotation = RuntimeInvisibleAnnotationsAttribute.of(Annotation.of(ClassDesc.of(UnapprovedMetadata.class.getName())));
    var descriptor = ModuleAttribute.of(ModuleDesc.of(PREFIX + "probe"), builder -> {});
    var module = ClassFile.of().buildModule(descriptor, builder -> builder.with(annotation));
    var packageInfo = ClassFile.of().build(ClassDesc.of(PREFIX + "source.api.package-info"), builder -> builder.with(annotation));
    return Map.of("module-info", module, "package-info", packageInfo).entrySet().stream().map(entry -> DynamicTest.dynamicTest(
        entry.getKey(), () -> {
          var references = ClassfileDependencies.read(entry.getValue());
          assertFalse(references.types().contains("module-info"));
          assertTrue(references.annotations().contains(UnapprovedMetadata.class.getName()));
          assertTrue(violations("source", PREFIX + "source.api.Probe", Set.of(), references)
              .contains("forbidden annotation " + UnapprovedMetadata.class.getName()));
        })).toList();
  }

  @Test
  void givenNamedConsumerAndCompiledApplicationModule_whenCompilingClients_thenPublicPortsAreAvailableAndSupportIsRestricted(
      @TempDir Path directory) throws Exception {
    // Arrange
    var modules = Path.of("target/classes").toAbsolutePath();
    var annotations = Path.of(NullMarked.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var descriptor = ModuleFinder.of(modules).find(PREFIX + "application").orElseThrow().descriptor();
    var exports = descriptor.exports().stream().collect(Collectors.toMap(ModuleDescriptor.Exports::source, value -> value));
    var supportPackage = PREFIX + "application.internal.repository";
    var module = Files.writeString(directory.resolve("module-info.java"), "module publication.consumer { requires org.aquilagraph.application; }");
    var source = directory.resolve("Client.java");
    var classes = directory.resolve("classes");

    // Act
    var publicAccess = compileClient(module, source, classes, modules + File.pathSeparator + annotations,
        "org.aquilagraph.application.port.RepositoryOperationException");
    var deniedAccess = compileClient(module, source, classes, modules + File.pathSeparator + annotations,
        supportPackage + ".RepositoryOperationBoundary");

    // Assert
    assertEquals(Set.of(PREFIX + "repository"), exports.get(supportPackage).targets());
    assertEquals(0, publicAccess.status(), publicAccess.diagnostics());
    assertEquals(1, deniedAccess.status(), deniedAccess.diagnostics());
    assertTrue(deniedAccess.diagnostics().contains("compiler.err.package.not.visible"), deniedAccess.diagnostics());
    assertTrue(deniedAccess.diagnostics().contains(supportPackage), deniedAccess.diagnostics());
  }

  private static CompilerResult compileClient(Path module, Path source, Path classes, String modulePath, String type) throws IOException {
    var simpleName = type.substring(type.lastIndexOf('.') + 1);
    Files.writeString(source, "import " + type + "; final class Client { " + simpleName + " evidence; }");
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new StringWriter();
    try (var manager = compiler.getStandardFileManager(null, null, null)) {
      var accepted = compiler.getTask(diagnostics, manager, null,
          List.of("-proc:none", "-XDrawDiagnostics", "--module-path", modulePath, "-d", classes.toString()), null,
          manager.getJavaFileObjects(module, source)).call();
      return new CompilerResult(Boolean.TRUE.equals(accepted) ? 0 : 1, diagnostics.toString());
    }
  }

  private record CompilerResult(int status, String diagnostics) {}

  private static List<String> violations(String module, String owner, Set<String> permits, ClassfileDependencies.References references) {
    var result = new ArrayList<String>();
    var ownerPackage = packageName(owner);
    var reader = (ownerPackage.equals(PREFIX + module + ".api") || ownerPackage.equals(PREFIX + module + ".internal.values")
        || ownerPackage.startsWith(PREFIX + module + ".internal.values.")) && (module.equals("source") || module.equals("cfg"));
    for (var type : references.types()) {
      var own = module.equals(TYPE_OWNERS.get(type));
      var intrinsic = type.startsWith(PREFIX + module + ".internal.values.");
      var allowedOwn = own && (!reader || packageName(type).equals(PREFIX + module + ".api") || intrinsic || permits.contains(type));
      var allowed = BASE_TYPES.contains(type) || allowedOwn || READABLE_PACKAGES.get(module).contains(packageName(type))
          || (module.equals("frontend") && COMPILER_TYPES.contains(type))
          || (module.equals("repository") && LOGGING_TYPES.contains(type))
          || (module.equals("repository") && type.startsWith("org.neo4j.driver."));
      if (!allowed || type.startsWith("com.sun.tools.javac.") || type.startsWith("jdk.internal.") || type.startsWith("sun.")) {
        result.add("forbidden type " + type);
      }
    }
    for (var annotation : references.annotations()) {
      if (!BASE_TYPES.contains(annotation) && !NULLNESS_METADATA.contains(annotation)) {
        result.add("forbidden annotation " + annotation);
      }
    }
    return result;
  }

  private static ClassfileDependencies.References references(Class<?> fixture) throws IOException {
    return ClassfileDependencies.read(classBytes(fixture));
  }

  private static byte[] classBytes(Class<?> fixture) throws IOException {
    try (var input = fixture.getResourceAsStream('/' + fixture.getName().replace('.', '/') + ".class")) {
      if (input == null) {
        throw new IOException("Missing fixture classfile: " + fixture.getName());
      }
      return input.readAllBytes();
    }
  }

  private static String packageName(String type) {
    return type.substring(0, type.lastIndexOf('.'));
  }

  private static Map<String, String> typeOwners() {
    var owners = new HashMap<String, String>();
    for (var module : READABLE_PACKAGES.keySet()) {
      var root = Path.of("target/classes", PREFIX + module);
      try (var paths = Files.walk(root)) {
        for (var file : paths.filter(path -> path.toString().endsWith(".class")
            && !path.getFileName().toString().equals("module-info.class")).toList()) {
          var type = ClassFile.of().parse(Files.readAllBytes(file)).thisClass().asInternalName().replace('/', '.');
          var previous = owners.putIfAbsent(type, module);
          if (previous != null) {
            throw new IllegalStateException("Duplicate product type " + type + " in " + previous + " and " + module);
          }
        }
      } catch (IOException exception) {
        throw new IllegalStateException("Could not inspect canonical module " + module, exception);
      }
    }
    return Map.copyOf(owners);
  }

  private static Set<String> platformTypes(String... modules) {
    var result = new HashSet<String>();
    for (var name : modules) {
      var module = ModuleFinder.ofSystem().find(name).orElseThrow();
      try (var reader = module.open(); var resources = reader.list()) {
        resources.filter(value -> value.endsWith(".class") && !value.equals("module-info.class"))
            .map(value -> value.substring(0, value.length() - 6).replace('/', '.')).forEach(result::add);
      } catch (IOException exception) {
        throw new IllegalStateException("Could not inspect platform module " + name, exception);
      }
    }
    return Set.copyOf(result);
  }

  private static Set<String> publicCompilerTypes() {
    var result = new HashSet<String>();
    for (var name : List.of("java.compiler", "jdk.compiler")) {
      var module = ModuleFinder.ofSystem().find(name).orElseThrow();
      var packages = module.descriptor().exports().stream().filter(value -> !value.isQualified()).map(value -> value.source())
          .collect(Collectors.toUnmodifiableSet());
      platformTypes(name).stream().filter(type -> packages.contains(packageName(type))).forEach(result::add);
    }
    return Set.copyOf(result);
  }

  static final class GenericLeak {
    public List<AstValueAdmission> value;
  }

  static final class CfgImplementationLeak {
    public List<JavaCfgDeriver> value;
  }

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE, ElementType.TYPE_USE})
  @interface UnapprovedMetadata {}

  @UnapprovedMetadata
  static final class InvisibleMetadataLeak {}

  static final class TypeUseMetadataLeak {
    List<@UnapprovedMetadata String> value;
  }

  static final class NullnessMetadata {
    @Nullable Object value;
  }

  static final class NullnessValueLeak {
    Nullable value;
  }
}
