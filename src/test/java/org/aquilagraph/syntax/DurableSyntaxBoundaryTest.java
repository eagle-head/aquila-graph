package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DurableSyntaxBoundaryTest {
  private static final Pattern FORBIDDEN = Pattern.compile(
      "com\\.sun\\.source|javax\\.lang\\.model|javax\\.tools|org\\.neo4j|org\\.aquilagraph\\.core");

  @Test
  void givenDurableSyntaxTypes_whenSignaturesAreInspected_thenCompilerAndStorageTypesDoNotEscape() {
    // Arrange
    var types = List.of(JavaAstAggregate.class, JavaAstAggregateBuilder.class, JavaSourceKind.class,
        MethodBodyOrigin.class, MethodSyntax.class, SyntaxAnalysisInput.class, SyntaxChild.class, SyntaxKind.class,
        SyntaxNode.class, SyntaxNodeKey.class, SyntaxNodeTuple.class, SyntaxOperator.class, SyntaxRangeTuple.class,
        SyntaxReadiness.class, SyntaxRole.class, SyntaxSemanticEdge.class, SyntaxSemanticRelation.class,
        SyntaxSemanticTarget.class, SyntaxSourceContribution.class, SyntaxStructuralEdge.class,
        SyntaxStructuralPath.class, SyntaxType.class, SyntaxValue.class, SyntaxValueSupport.class);

    // Act and assert
    types.forEach(DurableSyntaxBoundaryTest::verifyType);
    assertTrue(Arrays.stream(MethodSyntax.class.getDeclaredConstructors())
        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
  }

  private static void verifyType(Class<?> type) {
    verify(type.getName(), type.toGenericString());
    Arrays.stream(type.getDeclaredAnnotations()).forEach(annotation -> verify(type.getName(), annotation.toString()));
    Arrays.stream(type.getDeclaredFields()).forEach(field -> {
      verify(type.getName(), field.toGenericString());
      Arrays.stream(field.getDeclaredAnnotations()).forEach(annotation -> verify(type.getName(), annotation.toString()));
    });
    Arrays.stream(type.getDeclaredConstructors()).forEach(constructor -> {
      verify(type.getName(), constructor.toGenericString());
      Arrays.stream(constructor.getDeclaredAnnotations())
          .forEach(annotation -> verify(type.getName(), annotation.toString()));
      Arrays.stream(constructor.getParameterAnnotations()).flatMap(Arrays::stream)
          .forEach(annotation -> verify(type.getName(), annotation.toString()));
    });
    Arrays.stream(type.getDeclaredMethods()).forEach(method -> {
      verify(type.getName(), method.toGenericString());
      Arrays.stream(method.getDeclaredAnnotations()).forEach(annotation -> verify(type.getName(), annotation.toString()));
      Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream)
          .forEach(annotation -> verify(type.getName(), annotation.toString()));
    });
    if (type.isRecord()) {
      Arrays.stream(type.getRecordComponents()).forEach(component -> {
        verify(type.getName(), component.getGenericType().getTypeName());
        Arrays.stream(component.getDeclaredAnnotations())
            .forEach(annotation -> verify(type.getName(), annotation.toString()));
      });
    }
    Arrays.stream(type.getDeclaredClasses()).forEach(DurableSyntaxBoundaryTest::verifyType);
  }

  private static void verify(String owner, String signature) {
    assertFalse(FORBIDDEN.matcher(signature).find(), owner + " exposes " + signature);
  }
}
