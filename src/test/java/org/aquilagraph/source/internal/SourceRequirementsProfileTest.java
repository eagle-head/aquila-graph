package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.AstProductionAdmission;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.retention.AstCodec;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.aquilagraph.frontend.internal.JavaFrontend;
import org.aquilagraph.frontend.internal.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.cfg.CfgDerivers;
import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceRequirementsProfileTest {
  @TempDir Path directory;

  @Test
  void givenParenthesizedLocalFieldAndArrayTargets_whenProducingSource_thenActualTargetChildrenSurviveIndependentCfgRefusal()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Targets.java"), """
        class Targets {
          int field;
          int local(int input) { (input) = 1; return input; }
          int member() { (field) = 2; return field; }
          int array(int[] values) { (values[0]) = 3; return values[0]; }
        }
        """)));

    // Act
    var outcome = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED);
    var ast = outcome.result().orElseThrow();
    var methods = ast.program().methods().stream().filter(method -> List.of("local", "member", "array").contains(method.name())).toList();
    var targets = methods.stream().map(method -> ast.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.ASSIGNMENT).findFirst().orElseThrow().requiredChild(SyntaxRole.LOCATION)).toList();
    var cfg = CfgDerivers.standard().derive(ast,
        new CfgRequest(ast.identity(), methods.stream().map(method -> method.methodKey()).toList(), CfgProfile.JAVA_BOUNDED));

    // Assert
    assertEquals(AstCompletion.COMPLETE, outcome.completion(), outcome.diagnostics().toString());
    assertEquals(List.of(SyntaxKind.PARENTHESIZED, SyntaxKind.PARENTHESIZED, SyntaxKind.PARENTHESIZED),
        targets.stream().map(node -> node.syntaxKind()).toList());
    assertEquals(List.of(SyntaxKind.VARIABLE_ACCESS, SyntaxKind.FIELD_ACCESS, SyntaxKind.ARRAY_ACCESS),
        targets.stream().map(node -> node.requiredChild(SyntaxRole.EXPRESSION).syntaxKind()).toList());
    var array = targets.getLast().requiredChild(SyntaxRole.EXPRESSION);
    assertTrue(ast.aggregate().node(array.requiredChild(SyntaxRole.ARRAY).nodeId()).isPresent());
    assertTrue(ast.aggregate().node(array.requiredChild(SyntaxRole.INDEX).nodeId()).isPresent());
    assertEquals(List.of(MethodCfgState.UNSUPPORTED, MethodCfgState.UNSUPPORTED, MethodCfgState.UNSUPPORTED),
        cfg.methods().stream().map(method -> method.state()).toList());
    assertTrue(cfg.methods().stream().allMatch(method -> method.graph().isEmpty()));
    assertEquals(AstCompletion.COMPLETE, AstCodec.decode(AstCodec.encode(ast), ast.identity()).completion());
  }

  @Test
  void givenValidGenericHeaders_whenSourceIsQualified_thenPackageQualifiersAndAbsentOptionalSlotsDoNotCreateGaps() throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Headers.java"), """
        import java.util.List;
        import java.io.IOException;
        abstract class Headers<T extends Number & Comparable<T>> implements java.io.Serializable {
          List<String>[] values;
          abstract int map(Headers<T> this, int input, List<? extends Number> upper,
              List<? super T> lower, List<?> any) throws IOException;
          abstract void absent();
        }
        record Pair(int left, List<String> right) {}
        """)));
    var input = new JavaFrontend(new JavaSourceLoader()).produce(new AstProductionRequest(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED)).result().orElseThrow();
    var gaps = input.aggregate().nodes().stream()
        .filter(node -> (node.syntaxKind().requiresType() && !(node.type() instanceof SyntaxType.Resolved))
            || node.semanticReference().map(reference -> reference.status() != ResolutionStatus.RESOLVED).orElse(false))
        .map(node -> node.syntaxKind() + "/" + node.sourceKind() + "/" + node.type() + "/" + node.semanticReference()).toList();

    // Act and Assert
    assertEquals(AstCompletion.COMPLETE, AstProductionAdmission.outcome(SourceValueCapture.capture(input), AstProfile.JAVA_SOURCE_BOUNDED, "javac-public/1").completion(),
        gaps.toString());
  }
}
