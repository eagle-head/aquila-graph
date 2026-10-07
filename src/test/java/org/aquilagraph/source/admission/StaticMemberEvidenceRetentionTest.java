package org.aquilagraph.source.admission;

import org.aquilagraph.cfg.CfgDerivers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import org.aquilagraph.cfg.api.CfgProfile;
import org.aquilagraph.cfg.api.CfgRequest;
import org.aquilagraph.cfg.api.MethodCfgState;
import org.aquilagraph.cfg.api.OperationKind;
import org.aquilagraph.frontend.JavaFrontend;
import org.aquilagraph.frontend.JavaSourceLoader;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AstCompletion;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.AstResult;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.application.AstRetentionService;
import org.aquilagraph.application.PublicationStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticMemberEvidenceRetentionTest {
  @TempDir Path directory;

  @Test
  void givenSourceOnlyStaticConstantInTypeAnnotation_whenDeriving_thenSourceReferenceIsRetainedWithoutRuntimeMemberAccess()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        import java.lang.annotation.ElementType;
        import java.lang.annotation.Target;
        @Target(ElementType.TYPE_USE) @interface Limit { int value(); }
        class Stored { String read(Object value) { return (@Limit(Integer.MAX_VALUE) String) value; } }
        """)));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("read")).findFirst().orElseThrow();

    // Act
    var decoded = AstCodec.decode(AstCodec.encode(original), original.identity());
    var outcome = CfgDerivers.standard().derive(decoded,
        new CfgRequest(decoded.identity(), List.of(method.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

    // Assert
    assertEquals(AstCompletion.COMPLETE, decoded.completion(), decoded.diagnostics().toString());
    assertEquals(original.identity(), decoded.identity());
    assertEquals(original.aggregate(), decoded.aggregate());
    assertEquals(MethodCfgState.AVAILABLE, outcome.state(), outcome.diagnostics().toString());
    assertTrue(decoded.body(method.methodKey()).orElseThrow().evidence().staticMembers().isEmpty());
    assertTrue(decoded.body(method.methodKey()).orElseThrow().root().descendantsAndSelf().stream()
        .anyMatch(node -> node.syntaxKind() == SyntaxKind.ANNOTATION_NAME_REFERENCE));
    assertTrue(outcome.graph().orElseThrow().operationNodes().stream()
        .noneMatch(operation -> operation.kind() == OperationKind.MEMBER_ACCESS || operation.kind() == OperationKind.CLASS_INITIALIZATION));
  }

  @Test
  void givenInheritedAndExternalStaticMembers_whenRequiredOwnerIsOmittedMissingOrErroneous_thenDecodedSourceIsIncompleteAndCannotPublish()
      throws IOException {
    // Arrange
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"), """
        class Parent { static int mutable; }
        class Stored extends Parent { int read() { return mutable + Integer.MAX_VALUE; } }
        """)));
    var original = new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
    var method = original.program().methods().stream().filter(value -> value.name().equals("read")).findFirst().orElseThrow();
    var members = original.body(method.methodKey()).orElseThrow().evidence().staticMembers();
    var retention = new AstRetentionService(ignored -> {
      throw new AssertionError("A missing required owner cannot acquire storage");
    });
    var incomplete = new ArrayList<AstResult>();

    // Act
    for (var selected : members) {
      var owner = selected.declaringOwner();
      var variants = List.of(EvidenceValue.<JavaTypeReference>missing(owner.origin(), "Owner unavailable"),
          EvidenceValue.<JavaTypeReference>erroneous(owner.origin(), "Owner erroneous"));
      incomplete.add(replaceMembers(original, members.stream().filter(value -> !value.equals(selected)).toList()));
      for (var variant : variants) {
        var replacement = new StaticMemberEvidence(selected.methodKey(), selected.accessNodeId(), selected.selectedMemberKey(),
            selected.kind(), variant, selected.constantVariable());
        incomplete.add(replaceMembers(original, members.stream().map(value -> value.equals(selected) ? replacement : value).toList()));
      }
    }
    var decoded = incomplete.stream().map(value -> AstCodec.decode(AstCodec.encode(value), value.identity())).toList();
    var publications = decoded.stream().map(value -> retention.retainAst(AstOutcome.available(value))).toList();

    // Assert
    assertEquals(AstCompletion.COMPLETE, original.completion(), original.diagnostics().toString());
    assertEquals(2, members.size());
    assertEquals(6, decoded.size());
    assertTrue(decoded.stream().allMatch(value -> value.completion() == AstCompletion.INCOMPLETE));
    assertTrue(decoded.stream().allMatch(value -> value.program().equals(original.program())));
    assertTrue(decoded.stream().allMatch(value -> value.body(method.methodKey()).orElseThrow().root()
        .equals(original.body(method.methodKey()).orElseThrow().root())));
    assertTrue(publications.stream().allMatch(value -> value.status() == PublicationStatus.NOT_ELIGIBLE));
    for (var value : decoded) {
      for (var member : value.body(method.methodKey()).orElseThrow().evidence().staticMembers()) {
        var selected = members.stream().filter(candidate -> candidate.accessNodeId().equals(member.accessNodeId())).findFirst().orElseThrow();
        assertEquals(selected.selectedMemberKey(), member.selectedMemberKey());
        assertEquals(selected.constantVariable(), member.constantVariable());
      }
    }
  }

  @Test
  void givenKnownStaticMemberAndOwner_whenOptionalClassificationIsMissingOrErroneous_thenSourceFactsRemainAndCfgReportsTheExactSlot()
      throws IOException {
    // Arrange
    var original = produce();
    var member = member(original);
    var origin = member.constantVariable().origin();
    var classifications = List.of(EvidenceValue.<Boolean>missing(origin, "Classification unavailable"),
        EvidenceValue.<Boolean>erroneous(origin, "Classification erroneous"));

    for (var classification : classifications) {
      var replacement = new StaticMemberEvidence(member.methodKey(), member.accessNodeId(), member.selectedMemberKey(), member.kind(),
          member.declaringType(), classification);
      // Act
      var retained = replace(original, replacement);
      var decoded = AstCodec.decode(AstCodec.encode(retained), retained.identity());
      var outcome = CfgDerivers.standard().derive(decoded,
          new CfgRequest(decoded.identity(), List.of(member.methodKey()), CfgProfile.JAVA_BOUNDED)).methods().getFirst();

      // Assert
      assertEquals(AstCompletion.COMPLETE, decoded.completion());
      assertNotEquals(original.identity(), decoded.identity());
      assertNotEquals(original.aggregate(), decoded.aggregate());
      assertEquals(replacement, member(decoded));
      assertEquals(original.aggregate().node(member.accessNodeId()), decoded.aggregate().node(member.accessNodeId()));
      var state = classification.state() == EvidenceState.ERRONEOUS ? MethodCfgState.ERRONEOUS_EVIDENCE : MethodCfgState.INSUFFICIENT_EVIDENCE;
      assertEquals(state, outcome.state());
      assertEquals(List.of(state), outcome.blockers().stream().map(value -> value.kind()).toList());
      assertEquals(List.of(StaticMemberEvidence.CONSTANT_SLOT), outcome.blockers().stream().map(value -> value.slot()).toList());
      assertTrue(outcome.graph().isEmpty());
    }
  }

  private AstResult produce() throws IOException {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Stored.java"),
        "class Stored { int read() { return Integer.MAX_VALUE; } }")));
    return new JavaFrontend(new JavaSourceLoader()).produce(snapshot,
        new CompilerConfiguration(JavaRelease.JAVA_25, List.of(), List.of(), List.of()), AstProfile.JAVA_SOURCE_BOUNDED).result().orElseThrow();
  }

  private static StaticMemberEvidence member(AstResult ast) {
    var method = ast.program().types().stream().flatMap(type -> type.methods().stream()).filter(value -> value.name().equals("read"))
        .findFirst().orElseThrow();
    return ast.body(method.methodKey()).orElseThrow().evidence().staticMembers().getFirst();
  }

  private static AstResult replace(AstResult original, StaticMemberEvidence replacement) {
    var members = original.body(replacement.methodKey()).orElseThrow().evidence().staticMembers().stream()
        .map(value -> value.accessNodeId().equals(replacement.accessNodeId()) ? replacement : value).toList();
    return replaceMembers(original, members);
  }

  private static AstResult replaceMembers(AstResult original, List<StaticMemberEvidence> members) {
    var copied = CopiedAstValues.capture(original.aggregate());
    var bodies = copied.bodies().stream().map(body -> new CopiedAstValues.Body(body.methodKey(), body.root(), body.origin(), body.uses(),
        body.invocations(), body.staticAccesses().isEmpty() ? body.staticAccesses() : members,
        body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    var aggregate = AstValueAdmission.admit(new CopiedAstValues(copied.snapshot(), copied.sources(), bodies, copied.memberOrders(),
        copied.associations(), copied.declarationTypes()));
    return AstResultAdmission.admit(new SyntaxAnalysisInput(original.snapshot(), original.environment(), original.program(),
        Optional.of(aggregate), original.diagnostics(), original.coverage()), original.profile(), original.identity().semanticProductionRevision());
  }
}
