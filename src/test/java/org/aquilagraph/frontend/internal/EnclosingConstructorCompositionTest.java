package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.util.JavacTask;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.retention.AstCodec;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class EnclosingConstructorCompositionTest {
  private static final String DECLARATIONS = "class Outer<A> { class Box { <T> Box(T value) {} } } "
      + "class Holder<A> { Outer<A> outer; } ";

  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenEnclosingMemberContexts_whenProducedAndDecoded_thenAttributedConstructorFactsSurvive() {
    // Arrange
    var cases = List.of(
        new Context("SubstitutedField", "class Probe { Object make(Holder<Integer> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Integer", false),
        new Context("InheritedField", "class Probe extends Holder<Integer> { Object make() { return outer.new Box(1); } }",
            "java.lang.Integer", false),
        new Context("InheritedSuperField", "class Probe extends Holder<Integer> { Object make() { return super.outer.new Box(1); } }",
            "java.lang.Integer", false),
        new Context("LocalSubclass", "class Probe { Object make() { class Local extends Holder<Integer> { Object value() { return outer.new Box(1); } } return new Local().value(); } }",
            "java.lang.Integer", false),
        new Context("LambdaField", "class Probe { java.util.function.Supplier<Object> make(Holder<Integer> holder) { return () -> holder.outer.new Box(1); } }",
            "java.lang.Integer", false),
        new Context("TypeVariable", "class Probe { <O extends Outer<Integer>> Object make(O outer) { return outer.new Box(1); } }",
            "java.lang.Object", true),
        new Context("InheritedVariableBound", "class Child<A> extends Outer<A> {} class Probe { <O extends Child<Integer>> Object make(O outer) { return outer.new Box(1); } }",
            "java.lang.Object", true),
        new Context("ChainedTypeVariable", "class Probe { <P extends Outer<Integer>, O extends P> Object make(O outer) { return outer.new Box(1); } }",
            "java.lang.Object", true),
        new Context("IntersectionBound", "class Probe { <O extends Outer<Integer> & Runnable> Object make(O outer) { return outer.new Box(1); } }",
            "java.lang.Object", true),
        new Context("AnonymousTypeVariable", "class Probe { <O extends Outer<Integer>> Object make(O outer) { return outer.new Box(1) {}; } }",
            "java.lang.Object", false),
        new Context("NestedCapturedField", "class Probe { Object make(Holder<?> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Integer", false, "class Outer<A> { class Box { <T> Box(T value) {} } } class Holder<A> { Outer<java.util.List<A>> outer; } "),
        new Context("NestedBoundedField", "class Probe { Object make(Holder<? extends Number> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Integer", false, "class Outer<A> { class Box { <T> Box(T value) {} } } class Holder<A> { Outer<java.util.List<A>> outer; } "),
        new Context("ArrayCapturedField", "class Probe { Object make(Holder<?> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Integer", false, "class Outer<A> { class Box { <T> Box(T value) {} } } class Holder<A> { Outer<A[]> outer; } "),
        new Context("CapturedEnclosingBound", "class Probe { Object make(Holder<?, ?> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Object", false, "class Outer<A> { class Box { <T> Box(T value) {} } } class Holder<A, O extends Outer<A>> { O outer; } "),
        new Context("RepeatedCapturedBound", "class Probe { Object make(Holder<?, ?> holder) { return holder.outer.new Box(1); } }",
            "java.lang.Object", false, "class Pair<A, B> {} class Outer<A> { class Box { <T> Box(T value) {} } } class Child<A> extends Outer<Pair<A, A>> {} class Holder<A, O extends Child<A>> { O outer; } "),
        new Context("AnonymousVoidLambda", "class Probe { Runnable make(Holder<Integer> holder) { return () -> holder.outer.new Box(1) {}; } }",
            "java.lang.Integer", false, "class Outer<A> { class Box { <T> Box(T value) {} } } class Holder<A> { Outer<java.util.List<A>> outer; } "),
        new Context("FinalQualifierAssignment", "class Probe { Object make(Holder<Integer> holder) { final Outer<Integer> outer; return (outer = holder.outer).new Box(1) {}; } }",
            "java.lang.Integer", false),
        new Context("FinalArgumentAssignment", "class Probe { Object make(Holder<Integer> holder) { final int value; return holder.outer.new Box(value = 1) {}; } }",
            "java.lang.Integer", false),
        new Context("AnonymousDiamond", "class Probe { Outer<Integer>.Box<String> make(Outer<Integer> outer) { return outer.new Box<>(1) {}; } }",
            "java.lang.Integer", false, "class Outer<A> { class Box<B> { <T> Box(T value) {} } } "),
        new Context("AnnotatedMemberArgument", "class Probe { Object make(Outer<Integer> outer) { return outer.new Box<@Mark String>(1); } }",
            "java.lang.Integer", false, "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} class Outer<A> { class Box<B> { <T> Box(T value) {} } } "),
        new Context("AnnotatedMemberName", "class Probe { Object make(Outer<Integer> outer) { return outer.new @Mark Box<String>(1); } }",
            "java.lang.Integer", false, "import java.lang.annotation.*; @Target(ElementType.TYPE_USE) @interface Mark {} class Outer<A> { class Box<B> { <T> Box(T value) {} } } "),
        new Context("LocalMethodBinder", "class Probe { <M> Object make() { class Outer<A extends M> { class Box { <T> Box(T value) {} } } Outer<M> outer = new Outer<>(); return outer.new Box(1); } }",
            "java.lang.Integer", false, ""),
        new Context("LocalInheritedVariableBound", "class Probe { void make() { class Outer<A> { class Box { <T> Box(T value) {} } } class Child<A> extends Outer<A> {} class Uses { <O extends Child<Integer>> Object read(O outer) { return outer.new Box(1); } } } }",
            "java.lang.Object", false, ""),
        new Context("InlineEnclosingDeclaration", "class Probe { Object make() { return new Object() { class Box { <T> Box(T value) {} } }.new Box(1); } }",
            "java.lang.Integer", false, ""),
        new Context("AnonymousInlineEnclosingDeclaration", "class Probe { Object make() { return new Object() { class Box { <T> Box(T value) {} } }.new Box(1) {}; } }",
            "java.lang.Integer", false, ""),
        new Context("AnonymousNumberBound", "class Probe { <O extends Outer<Integer>> Object make(O outer) { return outer.new Box(1) {}; } }",
            "java.lang.Number", false, "class Outer<A extends Number> { class Box { <T extends A> Box(T value) {} } } "));

    // Act
    var observations = cases.stream().map(context -> DynamicTest.dynamicTest("given" + context.name()
        + "_whenProducedAndDecoded_thenOriginalSelectionAndEnclosingTypeSurvive", () -> {
          // Arrange
          var source = context.declarations() + context.source();
          assertCompilerAccepts(source);
          var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Probe.java"), source)));

          // Act
          var outcome = JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
              CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_MODULE_SERVICES));

          // Assert
          assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
          assertTrue(outcome.diagnostics().isEmpty(), outcome.diagnostics().toString());
          var original = outcome.result().orElseThrow();
          assertEquals("java-source/22", original.identity().sourceModelVersion());
          assertEquals("javac-public/14", original.identity().semanticProductionRevision());
          var encoded = AstCodec.encode(original);
          var decoded = AstCodec.decode(encoded, original.identity());
          for (var ast : List.of(original, decoded)) {
            assertEquals(source, ast.snapshot().sourceFiles().getFirst().content());
            var creations = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.OBJECT_CREATION)
                .filter(node -> {
                  var range = node.origin().orElseThrow();
                  var written = source.substring(Math.toIntExact(range.startOffset()), Math.toIntExact(range.endOffsetExclusive()));
                  return written.contains(".new Box") || written.contains(".new @Mark Box");
                }).toList();
            assertEquals(1, creations.size());
            var creation = creations.getFirst();
            assertTrue(creation.semanticReference().orElseThrow().symbolKey().isPresent());
            var owners = ast.aggregate().executionScopes().stream().map(scope -> ast.aggregate().executionSyntax(scope.key()).orElseThrow())
                .filter(scope -> scope.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).isPresent()).toList();
            assertEquals(1, owners.size());
            var owner = owners.getFirst();
            var conversion = owner.useDecision(creation.nodeId(), SyntaxRole.ARGUMENT, 0).orElseThrow();
            assertEquals(owner.scopeKey(), conversion.scopeKey());
            assertEquals("int", conversion.sourceType().displayName());
            assertEquals(context.target(), conversion.selectedTarget().displayName());
            assertEquals("java.lang.Integer", conversion.boxedType().orElseThrow().displayName());
            assertEquals(InvocationApplicabilityMode.FIXED, owner.invocationArity(creation.nodeId()).orElseThrow().mode());
            assertEquals(1, owner.invocationArity(creation.nodeId()).orElseThrow().formalCount());
            if (context.variableEnclosure()) {
              var type = assertInstanceOf(SyntaxType.Resolved.class, creation.type());
              var declared = assertInstanceOf(JavaTypeStructure.Declared.class, type.structure().orElseThrow());
              assertInstanceOf(JavaTypeStructure.Variable.class, declared.enclosingType().orElseThrow());
              var ownerView = assertInstanceOf(JavaTypeStructure.Declared.class, declared.enclosingOwnerType().orElseThrow());
              assertEquals(declared.lexicalOwnerKey().orElseThrow(), ownerView.declarationKey());
              assertEquals(1, ownerView.arguments().size());
              var argumentType = assertInstanceOf(JavaTypeStructure.Declared.class, ownerView.arguments().getFirst());
              var writtenInteger = ast.aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE)
                  .filter(node -> node.type().knownIdentity().filter(identity -> identity.displayName().equals("java.lang.Integer")).isPresent())
                  .findFirst().orElseThrow();
              assertEquals(writtenInteger.semanticReference().orElseThrow().symbolKey().orElseThrow(), argumentType.declarationKey());
            }
          }
          assertEquals(original.aggregate(), decoded.aggregate());
          assertEquals(original.identity(), decoded.identity());
          assertEquals(original.coverage(), decoded.coverage());
          assertArrayEquals(encoded, AstCodec.encode(decoded));
        }));

    // Assert
    return observations;
  }

  private static void assertCompilerAccepts(String source) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null,
          List.of(SimpleJavaFileObject.forSource(URI.create("memory:///Probe.java"), source)));
      task.parse();
      task.analyze();
      assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
    }
  }

  private record Context(String name, String source, String target, boolean variableEnclosure, String declarations) {
    Context(String name, String source, String target, boolean variableEnclosure) {
      this(name, source, target, variableEnclosure, DECLARATIONS);
    }
  }
}
