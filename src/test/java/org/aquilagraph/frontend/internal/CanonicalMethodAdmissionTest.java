package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.internal.AstResultAdmission;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaType;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class CanonicalMethodAdmissionTest {
  @TempDir Path directory;

  @TestFactory
  Stream<DynamicTest> givenOriginalCompilerExpectations_whenSameKeyDeclarationChanges_thenPublicAdmissionRejectsContradiction() {
    return Stream.of(AstProfile.JAVA_SOURCE_BOUNDED, AstProfile.JAVA_SOURCE_EXPANDED)
        .flatMap(profile -> Stream.of(false, true).map(signature -> DynamicTest.dynamicTest(
            profile.version() + (signature ? "/signature" : "/return-type"), () -> assertChangedDeclaration(profile, signature))));
  }

  private void assertChangedDeclaration(AstProfile profile, boolean signature) throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory,
        List.of(SourceFile.fromContent(Path.of("Example.java"), "class Example { int subject(int input) { return input; } }")));
    var original = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(snapshot, CompilerConfiguration.forRelease(JavaRelease.JAVA_25), JavaTranslationPolicy.legacyAnalysis());
    var baseline = AstResultAdmission.admit(original, profile, "javac-public/1");
    var method = original.program().methods().stream().filter(value -> value.name().equals("subject")).findFirst().orElseThrow();
    var changed = new JavaMethod(method.methodKey(), method.ownerTypeKey(), method.name(), signature ? "subject(long)" : method.signature(),
        signature ? method.returnType() : "long", method.parameters(), method.modifiers(), method.constructor(), method.sourceDeclared(),
        method.hasBody(), method.origins());
    var program = new JavaProgram(original.program().types().stream().map(type -> new JavaType(type.typeKey(), type.qualifiedName(),
        type.kind(), type.modifiers(), type.enclosingType(), type.directSuperclass(), type.directInterfaces(), type.permittedSubclasses(),
        type.typeParameters(), type.sourceDeclared(), type.fields(), type.recordComponents(),
        type.methods().stream().map(value -> value.equals(method) ? changed : value).toList(), type.synthesizedDeclarations(), type.origins()))
        .toList());

    // Act
    assertThrows(IllegalArgumentException.class, () -> {
      var supplied = JavaCoverageTestSupport.withFacts(original, program, Optional.of(original.aggregate().orElseThrow()),
          JavaCoverageTestSupport.emissions(original));
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().writtenInventory(),
          supplied.languageFeatureCoverageResult().validationEvidence().writtenInventory());
      assertEquals(original.languageFeatureCoverageResult().validationEvidence().attributedExpectations(),
          supplied.languageFeatureCoverageResult().validationEvidence().attributedExpectations());
      AstResultAdmission.admit(supplied, profile, "javac-public/1");
    });

    // Assert
    assertEquals(AstCompletion.COMPLETE, baseline.completion());
    assertEquals(method.methodKey(), changed.methodKey());
    assertEquals(method.parameters(), changed.parameters());
  }
}
