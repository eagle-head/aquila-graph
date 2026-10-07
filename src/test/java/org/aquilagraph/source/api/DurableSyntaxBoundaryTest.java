package org.aquilagraph.source.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import org.aquilagraph.architecture.ClassfileDependencies;
import org.aquilagraph.cfg.internal.CfgAdmission;
import org.aquilagraph.cfg.internal.CfgReadiness;
import org.junit.jupiter.api.Test;

class DurableSyntaxBoundaryTest {
  private static final Pattern FORBIDDEN = Pattern.compile(
      "com\\.sun\\.source|javax\\.lang\\.model|javax\\.tools|org\\.neo4j|org\\.aquilagraph\\.cfg");

  @Test
  void givenImmutableAggregateReader_whenCompiledReferencesAreInspected_thenAdmissionAndBuildersAreAbsent() throws IOException {
    // Arrange
    var classFiles = List.of("JavaAstAggregate", "JavaAstAggregate$NodeFacts", "SourceBodyFacts", "MethodSyntax", "MethodDecisionIndex");

    // Act
    for (var name : classFiles) {
      var bytes = Files.readAllBytes(Path.of("target/classes/org/aquilagraph/source/api", name + ".class"));
      var references = ClassfileDependencies.read(bytes);

      // Assert
      references.types().forEach(type -> {
        assertFalse(type.contains("JavaAstAggregateBuilder"), name + " depends on " + type);
        assertFalse(type.contains("AstAggregateAdmission"), name + " depends on " + type);
        assertFalse(type.startsWith("org.aquilagraph.source.admission."), name + " depends on " + type);
      });
    }
  }

  @Test
  void givenDurableSyntaxTypes_whenSignaturesAreInspected_thenCompilerAndStorageTypesDoNotEscape() throws ClassNotFoundException {
    // Arrange
    var types = List.of(JavaAstAggregate.class, JavaAstAggregateBuilder.class, JavaSourceKind.class,
        MethodBodyOrigin.class, MethodSyntax.class, SyntaxAnalysisInput.class, SyntaxChild.class, SyntaxKind.class,
        SyntaxNode.class, SyntaxNodeKey.class, SyntaxNodeTuple.class, SyntaxOperator.class, SyntaxRangeTuple.class,
        SyntaxRole.class, SyntaxSemanticEdge.class, SyntaxSemanticRelation.class,
        SyntaxSemanticTarget.class, SyntaxSourceContribution.class, SyntaxStructuralEdge.class,
        SyntaxStructuralPath.class, SyntaxType.class, SyntaxValue.class, SyntaxValueSupport.class,
        CoverageActualFacts.class, CoverageActualFactUniverse.class, CoverageActualFactView.class, CoverageAnnotationFacts.class, CoverageWrittenTypeUseTopology.class,
        CoverageSyntaxFactValue.class, CoverageSourceProjection.class,
        WrittenSourceAssociation.class, WrittenTypeEvidence.class, PackageQualifierEvidence.class, ConversionEvidence.class,
        InvocationApplicability.class, InvocationApplicabilityMode.class, StaticMemberEvidence.class,
        StaticMemberKind.class, StringExpressionEvidence.class, EvidenceValue.class, EvidenceOrigin.class, EvidenceState.class,
        AstContractException.class, MethodDecisionIndex.class,
        MethodDecisionRegistration.class, MethodDecisionValidation.class, DecisionValueValidation.class,
        AttributedCoverageExpectations.class, CoverageAnnotationValue.class,
        Class.forName("org.aquilagraph.source.api.CoverageAnnotationValueSupport", false, CoverageAnnotationValue.class.getClassLoader()),
        Class.forName("org.aquilagraph.source.api.CoverageValueValidation", false, CoverageFactValue.class.getClassLoader()),
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

  @Test
  void givenSourceMethodQueries_whenPublicBoundaryIsInspected_thenCfgPolicyRemainsWithTheConsumer() {
    // Arrange
    var sourceMethods = Arrays.stream(MethodSyntax.class.getDeclaredMethods()).filter(method -> Modifier.isPublic(method.getModifiers()));

    // Act
    var sourceNames = sourceMethods.map(method -> method.getName()).toList();

    // Assert
    assertFalse(sourceNames.contains("readiness"));
    assertTrue(CfgAdmission.class.getPackageName().equals("org.aquilagraph.cfg.internal"));
    assertTrue(CfgReadiness.class.getPackageName().equals("org.aquilagraph.cfg.internal"));
    assertTrue(Arrays.stream(SyntaxKind.class.getDeclaredMethods()).noneMatch(method -> method.getName().equals("isMethodBodySyntax")));
    assertTrue(Arrays.stream(StaticMemberEvidence.class.getDeclaredMethods())
        .noneMatch(method -> method.getName().equals("triggersInitialization")));
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
