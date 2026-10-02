package org.aquilagraph.syntax;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.aquilagraph.model.AttributedCoverageExpectations;
import org.aquilagraph.model.CoverageAnnotationValue;
import org.aquilagraph.model.CoverageEvidenceValue;
import org.aquilagraph.model.CoverageExecutableType;
import org.aquilagraph.model.CoverageFactReference;
import org.aquilagraph.model.CoverageFactValue;
import org.aquilagraph.model.CoveragePropertyValue;
import org.aquilagraph.model.CoverageSourceFact;
import org.aquilagraph.model.CoverageTypeStructure;
import org.aquilagraph.model.CoverageValidationEvidence;
import org.aquilagraph.model.DeclarationMemberOrder;
import org.aquilagraph.model.DeclarationTypeEvidence;
import org.aquilagraph.model.DiagnosticEmission;
import org.aquilagraph.model.FeatureContextOutcome;
import org.aquilagraph.model.LanguageFeatureCoverageResult;
import org.aquilagraph.model.SourceContext;
import org.aquilagraph.model.SourceCoverageInventory;
import org.aquilagraph.model.SourceOwnerPath;
import org.aquilagraph.model.UnmatchedSourceFact;
import org.junit.jupiter.api.Test;

class DurableSyntaxBoundaryTest {
  private static final Pattern FORBIDDEN = Pattern.compile(
      "com\\.sun\\.source|javax\\.lang\\.model|javax\\.tools|org\\.neo4j|org\\.aquilagraph\\.core");

  @Test
  void givenDurableSyntaxTypes_whenSignaturesAreInspected_thenCompilerAndStorageTypesDoNotEscape() throws ClassNotFoundException {
    // Arrange
    var types = List.of(JavaAstAggregate.class, JavaAstAggregateBuilder.class, JavaSourceKind.class,
        MethodBodyOrigin.class, MethodSyntax.class, SyntaxAnalysisInput.class, SyntaxChild.class, SyntaxKind.class,
        SyntaxNode.class, SyntaxNodeKey.class, SyntaxNodeTuple.class, SyntaxOperator.class, SyntaxRangeTuple.class,
        SyntaxReadiness.class, SyntaxRole.class, SyntaxSemanticEdge.class, SyntaxSemanticRelation.class,
        SyntaxSemanticTarget.class, SyntaxSourceContribution.class, SyntaxStructuralEdge.class,
        SyntaxStructuralPath.class, SyntaxType.class, SyntaxValue.class, SyntaxValueSupport.class,
        CoverageActualFacts.class, CoverageActualFactUniverse.class, CoverageActualFactView.class, CoverageAnnotationFacts.class, CoverageWrittenTypeUseTopology.class,
        CoverageSyntaxFactValue.class, CoverageSourceProjection.class,
        WrittenSourceAssociation.class, WrittenTypeEvidence.class, PackageQualifierEvidence.class, TypedUseDecision.class,
        InvocationArityDecision.class, InvocationArityMode.class, StaticAccessDecision.class,
        StaticAccessKind.class, StringConcatenationDecision.class, MethodDecisionIndex.class,
        MethodDecisionRegistration.class, MethodDecisionValidation.class, DecisionValueValidation.class,
        AttributedCoverageExpectations.class, CoverageAnnotationValue.class,
        Class.forName("org.aquilagraph.model.CoverageAnnotationValueSupport", false, CoverageAnnotationValue.class.getClassLoader()),
        Class.forName("org.aquilagraph.model.CoverageValueValidation", false, CoverageFactValue.class.getClassLoader()),
        CoverageExecutableType.class,
        CoverageEvidenceValue.class, CoverageFactReference.class, CoverageFactValue.class,
        CoveragePropertyValue.class, CoverageSourceFact.class,
        CoverageTypeStructure.class, CoverageValidationEvidence.class, DeclarationMemberOrder.class,
        DeclarationTypeEvidence.class, DiagnosticEmission.class, FeatureContextOutcome.class,
        LanguageFeatureCoverageResult.class, SourceContext.class, SourceCoverageInventory.class, SourceOwnerPath.class,
        UnmatchedSourceFact.class);

    // Act
    var signatures = types.stream().flatMap(type -> typeSignatures(type).stream()).toList();

    // Assert
    signatures.forEach(signature -> assertFalse(FORBIDDEN.matcher(signature).find(), signature));
    assertTrue(Arrays.stream(MethodSyntax.class.getDeclaredConstructors())
        .noneMatch(constructor -> Modifier.isPublic(constructor.getModifiers())));
  }

  private static List<String> typeSignatures(Class<?> type) {
    var signatures = new ArrayList<String>();
    signatures.add(type.toGenericString());
    Arrays.stream(type.getDeclaredAnnotations()).forEach(annotation -> signatures.add(annotation.toString()));
    Arrays.stream(type.getDeclaredFields()).forEach(field -> {
      signatures.add(field.toGenericString());
      Arrays.stream(field.getDeclaredAnnotations()).forEach(annotation -> signatures.add(annotation.toString()));
    });
    Arrays.stream(type.getDeclaredConstructors()).forEach(constructor -> {
      signatures.add(constructor.toGenericString());
      Arrays.stream(constructor.getDeclaredAnnotations())
          .forEach(annotation -> signatures.add(annotation.toString()));
      Arrays.stream(constructor.getParameterAnnotations()).flatMap(Arrays::stream)
          .forEach(annotation -> signatures.add(annotation.toString()));
    });
    Arrays.stream(type.getDeclaredMethods()).forEach(method -> {
      signatures.add(method.toGenericString());
      Arrays.stream(method.getDeclaredAnnotations()).forEach(annotation -> signatures.add(annotation.toString()));
      Arrays.stream(method.getParameterAnnotations()).flatMap(Arrays::stream)
          .forEach(annotation -> signatures.add(annotation.toString()));
    });
    if (type.isRecord()) {
      Arrays.stream(type.getRecordComponents()).forEach(component -> {
        signatures.add(component.getGenericType().getTypeName());
        Arrays.stream(component.getDeclaredAnnotations())
            .forEach(annotation -> signatures.add(annotation.toString()));
      });
    }
    Arrays.stream(type.getDeclaredClasses()).forEach(nested -> signatures.addAll(typeSignatures(nested)));
    return signatures.stream().map(signature -> type.getName() + " exposes " + signature).toList();
  }
}
