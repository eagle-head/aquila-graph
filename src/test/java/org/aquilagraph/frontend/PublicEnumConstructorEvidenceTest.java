package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ExecutableType;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaRelease;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.syntax.InvocationArityMode;
import org.aquilagraph.syntax.EnumConstructorContextDecision.Target;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxReadiness;
import org.aquilagraph.syntax.SyntaxRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicEnumConstructorEvidenceTest {
  private static final String SOURCE = """
      enum Empty { FIRST, SECOND }
      enum Valued {
        ONE(1);
        final int value;
        Valued(int value) { this.value = value; }
      }
      """;

  @TempDir
  Path output;

  @Test
  void givenImplicitAndWrittenEnumConstructors_whenPublicCompilerAttributesAndGenerates_thenDeclarationAndSyntheticSuperFormalsRemainDistinct() throws IOException {
    // Arrange
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(output.resolve("Enums.java").toUri(), SOURCE);
    var observations = new LinkedHashMap<String, ConstructorEvidence>();

    // Act
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none", "-d", output.toString()), null, List.of(file));
      var units = task.parse();
      task.analyze();
      var trees = Trees.instance(task);
      var types = task.getTypes();
      var semantics = new SemanticResolver(trees, types, task.getElements(), new DeclarationIdentityRegistry(trees, types));
      var applicability = new JavaInvocationApplicability(semantics);
      for (var unit : units) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethod(MethodTree method, Void unused) {
            var path = getCurrentPath();
            if (trees.getElement(path) instanceof ExecutableElement declaration && declaration.getKind() == ElementKind.CONSTRUCTOR
                && declaration.getEnclosingElement() instanceof TypeElement owner && owner.getKind() == ElementKind.ENUM) {
              var statement = method.getBody().getStatements().getFirst();
              assertTrue(statement instanceof ExpressionStatementTree);
              var expression = ((ExpressionStatementTree) statement).getExpression();
              assertTrue(expression instanceof MethodInvocationTree);
              var call = (MethodInvocationTree) expression;
              assertTrue(call.getMethodSelect() instanceof IdentifierTree identifier && identifier.getName().contentEquals("super"));
              var bodyPath = new TreePath(path, method.getBody());
              var callPath = new TreePath(new TreePath(bodyPath, statement), call);
              var selected = (ExecutableElement) trees.getElement(callPath);
              var selectedMirror = trees.getTypeMirror(new TreePath(callPath, call.getMethodSelect()));
              var selectedParameters = selectedMirror instanceof ExecutableType selectedType
                  ? selectedType.getParameterTypes().stream().map(Object::toString).toList() : List.<String>of();
              var arguments = call.getArguments().stream().map(argument -> trees.getTypeMirror(new TreePath(callPath, argument))).toList();
              observations.put(owner.getQualifiedName().toString(), new ConstructorEvidence(
                  method.getParameters().stream().map(parameter -> parameter.getType().toString()).toList(),
                  declaration.getParameters().stream().map(parameter -> parameter.asType().toString()).toList(),
                  task.getElements().getOrigin(declaration).name(),
                  ((TypeElement) selected.getEnclosingElement()).getQualifiedName().toString(),
                  selected.getParameters().stream().map(parameter -> parameter.asType().toString()).toList(),
                  selectedMirror == null ? "ABSENT" : selectedMirror.getKind().name(), selectedParameters,
                  call.getArguments().size(), selectedMirror instanceof ExecutableType selectedType
                      && applicability.select(selectedType, selected.isVarArgs(), arguments).isPresent()));
            }
            return super.visitMethod(method, unused);
          }
        }.scan(unit, null);
      }
      task.generate();
    }
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    var generated = generatedConstructors();
    System.out.println("ENUM_PUBLIC_EVIDENCE=" + Map.copyOf(observations));
    System.out.println("ENUM_GENERATED_EVIDENCE=" + generated);

    // Assert
    assertEquals(2, observations.size());
    assertEquals(List.of(), observations.get("Empty").treeParameters());
    assertEquals(List.of(), observations.get("Empty").declarationParameters());
    assertEquals(List.of("int"), observations.get("Valued").treeParameters());
    assertEquals(List.of("int"), observations.get("Valued").declarationParameters());
    for (var observation : observations.values()) {
      assertEquals("java.lang.Enum", observation.selectedOwner());
      assertEquals(List.of("java.lang.String", "int"), observation.selectedDeclarationParameters());
      assertEquals("EXECUTABLE", observation.selectedInvocationKind());
      assertEquals(List.of("java.lang.String", "int"), observation.selectedInvocationParameters());
      assertEquals(0, observation.writtenArguments());
      assertTrue(!observation.ordinaryApplicability());
    }
    assertEquals(new GeneratedConstructor("(Ljava/lang/String;I)V", "(Ljava/lang/String;I)V", List.of(0, 1, 2)), generated.get("Empty"));
    assertEquals(new GeneratedConstructor("(Ljava/lang/String;II)V", "(Ljava/lang/String;I)V", List.of(0, 1, 2)), generated.get("Valued"));
  }

  @Test
  void givenSyntheticEnumSuperArgumentsAbsentFromPublicTrees_whenFrontendProducesDecisions_thenSeparateContextSuppliesExactSelectedEnumFormals() throws IOException {
    // Arrange
    var source = SourceFile.fromContent(Path.of("Enums.java"), SOURCE);
    var snapshot = InputSnapshot.fromSources(output, List.of(source));

    // Act
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));

    // Assert
    for (var owner : List.of("Empty", "Valued")) {
      var constructor = input.program().types().stream().filter(type -> type.qualifiedName().equals(owner)).findFirst().orElseThrow()
          .methods().stream().filter(method -> method.constructor()).findFirst().orElseThrow();
      var body = input.methodSyntax().stream().filter(method -> method.methodKey().equals(constructor.methodKey())).findFirst().orElseThrow();
      var call = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
      assertEquals(InvocationArityMode.FIXED, body.invocationArity(call.nodeId()).orElseThrow().mode());
      assertEquals(2, body.invocationArity(call.nodeId()).orElseThrow().formalCount());
      var context = body.enumConstructorContext(call.nodeId()).orElseThrow();
      assertEquals(Target.ENUM_SUPERCLASS, context.target());
      assertEquals("type:java.lang.Enum#<init>(java.lang.String,int)", context.selectedConstructorKey());
      assertEquals(List.of("java.lang.String", "int"), List.of(context.nameType().erasedName(), context.ordinalType().erasedName()));
      assertTrue(call.children(SyntaxRole.ARGUMENT).isEmpty());
      assertEquals(owner.equals("Empty") ? 0 : 1, constructor.parameters().size());
      assertEquals(SyntaxReadiness.LOWERABLE, body.readiness());
    }
  }

  @Test
  void givenExplicitEnumThisDelegation_whenPublicCompilerAndFrontendRun_thenSourceFormalsRemainApplicableDespiteGeneratedHiddenPrefix() throws IOException {
    // Arrange
    var source = """
        enum Delegated {
          FIRST;
          Delegated() { this(7); }
          Delegated(int value) {}
        }
        """;
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(output.resolve("Delegated.java").toUri(), source);
    var observations = new ArrayList<ConstructorEvidence>();

    // Act
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
          List.of("--release", "25", "-proc:none", "-d", output.toString()), null, List.of(file));
      var units = task.parse();
      task.analyze();
      var trees = Trees.instance(task);
      var types = task.getTypes();
      var semantics = new SemanticResolver(trees, types, task.getElements(), new DeclarationIdentityRegistry(trees, types));
      var applicability = new JavaInvocationApplicability(semantics);
      for (var unit : units) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitMethod(MethodTree method, Void unused) {
            var path = getCurrentPath();
            if (trees.getElement(path) instanceof ExecutableElement declaration && declaration.getKind() == ElementKind.CONSTRUCTOR
                && declaration.getParameters().isEmpty()) {
              var statement = method.getBody().getStatements().getFirst();
              assertTrue(statement instanceof ExpressionStatementTree);
              var expression = ((ExpressionStatementTree) statement).getExpression();
              assertTrue(expression instanceof MethodInvocationTree);
              var call = (MethodInvocationTree) expression;
              assertTrue(call.getMethodSelect() instanceof IdentifierTree identifier && identifier.getName().contentEquals("this"));
              var bodyPath = new TreePath(path, method.getBody());
              var callPath = new TreePath(new TreePath(bodyPath, statement), call);
              var selected = (ExecutableElement) trees.getElement(callPath);
              var selectedMirror = trees.getTypeMirror(new TreePath(callPath, call.getMethodSelect()));
              var selectedParameters = selectedMirror instanceof ExecutableType selectedType
                  ? selectedType.getParameterTypes().stream().map(Object::toString).toList() : List.<String>of();
              var arguments = call.getArguments().stream().map(argument -> trees.getTypeMirror(new TreePath(callPath, argument))).toList();
              observations.add(new ConstructorEvidence(
                  method.getParameters().stream().map(parameter -> parameter.getType().toString()).toList(),
                  declaration.getParameters().stream().map(parameter -> parameter.asType().toString()).toList(),
                  task.getElements().getOrigin(declaration).name(),
                  ((TypeElement) selected.getEnclosingElement()).getQualifiedName().toString(),
                  selected.getParameters().stream().map(parameter -> parameter.asType().toString()).toList(),
                  selectedMirror == null ? "ABSENT" : selectedMirror.getKind().name(), selectedParameters,
                  call.getArguments().size(), selectedMirror instanceof ExecutableType selectedType
                      && applicability.select(selectedType, selected.isVarArgs(), arguments).isPresent()));
            }
            return super.visitMethod(method, unused);
          }
        }.scan(unit, null);
      }
      task.generate();
    }
    var generated = ClassFile.of().parse(output.resolve("Delegated.class"));
    var constructor = generated.methods().stream()
        .filter(method -> method.methodName().equalsString("<init>") && method.methodType().equalsString("(Ljava/lang/String;I)V"))
        .findFirst().orElseThrow();
    var instructions = constructor.code().orElseThrow().elementList().stream().filter(Instruction.class::isInstance).map(Instruction.class::cast).toList();
    var selectedCall = instructions.stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
        .filter(call -> call.owner().asInternalName().equals("Delegated") && call.name().equalsString("<init>"))
        .findFirst().orElseThrow();
    var loadedSlots = instructions.subList(0, instructions.indexOf(selectedCall)).stream().filter(LoadInstruction.class::isInstance)
        .map(LoadInstruction.class::cast).map(LoadInstruction::slot).toList();
    var snapshot = InputSnapshot.fromSources(output, List.of(SourceFile.fromContent(Path.of("Delegated.java"), source)));
    var input = new JavaFrontend(new JavaSourceLoader()).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25));
    var declaration = input.program().types().stream().filter(type -> type.qualifiedName().equals("Delegated")).findFirst().orElseThrow()
        .methods().stream().filter(method -> method.constructor() && method.parameters().isEmpty()).findFirst().orElseThrow();
    var body = input.methodSyntax().stream().filter(method -> method.methodKey().equals(declaration.methodKey())).findFirst().orElseThrow();
    var call = body.root().descendantsAndSelf().stream().filter(node -> node.syntaxKind() == SyntaxKind.INVOCATION).findFirst().orElseThrow();
    System.out.println("ENUM_THIS_PUBLIC_EVIDENCE=" + List.copyOf(observations));
    System.out.println("ENUM_THIS_GENERATED_EVIDENCE=" + selectedCall.type().stringValue() + ":" + loadedSlots);

    // Assert
    assertTrue(diagnostics.getDiagnostics().stream().noneMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
    assertEquals(1, observations.size());
    var observation = observations.getFirst();
    assertEquals(List.of(), observation.treeParameters());
    assertEquals(List.of(), observation.declarationParameters());
    assertEquals("Delegated", observation.selectedOwner());
    assertEquals(List.of("int"), observation.selectedDeclarationParameters());
    assertEquals("EXECUTABLE", observation.selectedInvocationKind());
    assertEquals(List.of("int"), observation.selectedInvocationParameters());
    assertEquals(1, observation.writtenArguments());
    assertTrue(observation.ordinaryApplicability());
    assertEquals("(Ljava/lang/String;II)V", selectedCall.type().stringValue());
    assertEquals(List.of(0, 1, 2), loadedSlots);
    assertEquals(InvocationArityMode.FIXED, body.invocationArity(call.nodeId()).orElseThrow().mode());
    assertEquals(1, body.invocationArity(call.nodeId()).orElseThrow().formalCount());
    assertEquals(Target.THIS_CONSTRUCTOR, body.enumConstructorContext(call.nodeId()).orElseThrow().target());
    assertEquals(SyntaxReadiness.LOWERABLE, body.readiness());
  }

  private Map<String, GeneratedConstructor> generatedConstructors() throws IOException {
    var result = new LinkedHashMap<String, GeneratedConstructor>();
    for (var owner : List.of("Empty", "Valued")) {
      var model = ClassFile.of().parse(output.resolve(owner + ".class"));
      var constructor = model.methods().stream().filter(method -> method.methodName().equalsString("<init>")).findFirst().orElseThrow();
      var instructions = constructor.code().orElseThrow().elementList().stream().filter(Instruction.class::isInstance).map(Instruction.class::cast).toList();
      for (var index = 0; index < instructions.size(); index++) {
        if (instructions.get(index) instanceof InvokeInstruction call && call.owner().asInternalName().equals("java/lang/Enum")
            && call.name().equalsString("<init>")) {
          var loads = instructions.subList(0, index).stream().filter(LoadInstruction.class::isInstance).map(LoadInstruction.class::cast)
              .map(LoadInstruction::slot).toList();
          result.put(owner, new GeneratedConstructor(constructor.methodType().stringValue(), call.type().stringValue(), loads));
        }
      }
    }
    return Map.copyOf(result);
  }

  private record ConstructorEvidence(List<String> treeParameters, List<String> declarationParameters, String declarationOrigin,
      String selectedOwner, List<String> selectedDeclarationParameters, String selectedInvocationKind, List<String> selectedInvocationParameters, int writtenArguments,
      boolean ordinaryApplicability) {}

  private record GeneratedConstructor(String descriptor, String selectedSuperDescriptor, List<Integer> loadedSlots) {}
}
