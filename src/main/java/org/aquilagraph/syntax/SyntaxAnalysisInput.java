package org.aquilagraph.syntax;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.AnalysisEnvironmentSnapshot;
import org.aquilagraph.model.CompilerConfiguration;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.JavaMethod;
import org.aquilagraph.model.JavaProgram;

/**
 * Transient input passed from the Java frontend to semantic analysis.
 *
 * @param snapshot
 *          analyzed source snapshot
 * @param environmentSnapshot
 *          captured compiler environment
 * @param program
 *          declaration-only Java program
 * @param methodSyntax
 *          syntax evidence for methods that declare bodies
 * @param diagnostics
 *          frontend diagnostics
 */
public record SyntaxAnalysisInput(InputSnapshot snapshot, AnalysisEnvironmentSnapshot environmentSnapshot, JavaProgram program,
    List<MethodSyntax> methodSyntax, List<AnalysisDiagnostic> diagnostics) {
  /** Validates and defensively copies boundary data. */
  public SyntaxAnalysisInput {
    Objects.requireNonNull(snapshot, "snapshot");
    Objects.requireNonNull(environmentSnapshot, "environmentSnapshot");
    Objects.requireNonNull(program, "program");
    methodSyntax = List.copyOf(Objects.requireNonNull(methodSyntax, "methodSyntax"));
    diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    validateMethodSyntax(program, methodSyntax);
  }

  /**
   * Returns the compiler configuration used by the frontend.
   *
   * @return compiler configuration
   */
  public CompilerConfiguration compilerConfiguration() {
    return environmentSnapshot.compilerConfiguration();
  }

  /**
   * Finds transient syntax evidence by stable method identity.
   *
   * @param methodKey
   *          method identity
   * @return matching syntax evidence when the method declares a body
   */
  public Optional<MethodSyntax> methodSyntax(String methodKey) {
    Objects.requireNonNull(methodKey, "methodKey");
    return methodSyntax.stream().filter(syntax -> syntax.methodKey().equals(methodKey)).findFirst();
  }

  private static void validateMethodSyntax(JavaProgram program, List<MethodSyntax> methodSyntax) {
    Map<String, JavaMethod> methods = program.methods().stream()
        .collect(Collectors.toUnmodifiableMap(JavaMethod::methodKey, Function.identity()));
    var syntaxMethodKeys = new HashSet<String>();
    for (var syntax : methodSyntax) {
      var method = methods.get(Objects.requireNonNull(syntax, "methodSyntax entry").methodKey());
      if (method == null || !method.hasBody() || !syntaxMethodKeys.add(syntax.methodKey())) {
        throw new IllegalArgumentException("Method syntax must uniquely describe a program method body");
      }
    }
    var declaredBodyKeys = methods.values().stream().filter(JavaMethod::hasBody).map(JavaMethod::methodKey).collect(Collectors.toSet());
    if (!syntaxMethodKeys.equals(declaredBodyKeys)) {
      throw new IllegalArgumentException("Every declared method body requires syntax evidence");
    }
  }
}
