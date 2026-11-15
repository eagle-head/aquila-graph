package org.aquilagraph.identity.api;

import static org.aquilagraph.testsupport.TestLocations.capturedAbsolute;
import static org.aquilagraph.testsupport.TestLocations.capturedPath;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.junit.jupiter.api.Test;

class QualifiedIdentityTest {
  @Test
  void givenBlankOrUnrelatedSuppliedKeys_whenQualifiedIdentitiesAreConstructed_thenTheDerivedKeysRemainMandatory() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var graph = GraphIdentity.fromContent(ast, "cfg/1", "graph/1", "method", "graph-content");
    var invalidKeys = List.of("", " \t\n", "unrelated-key");

    // Act
    var astFailures = invalidKeys.stream().map(key -> assertThrows(IllegalArgumentException.class,
        () -> new AstIdentity(key, ast.softwareSignature(), ast.sourceModelVersion(), ast.astProfileVersion(),
            ast.semanticProductionRevision(), ast.canonicalContentDigest()))).toList();
    var graphFailures = invalidKeys.stream().map(key -> assertThrows(IllegalArgumentException.class,
        () -> new GraphIdentity(key, ast, graph.cfgProfileVersion(), graph.graphModelVersion(), graph.methodKey(),
            graph.canonicalContentDigest()))).toList();

    // Assert
    assertEquals(3, astFailures.size());
    assertEquals(3, graphFailures.size());
  }

  @Test
  void givenNonwrittenOriginsWithEqualAndDifferentOwners_whenUsedAsSetMembers_thenAllProvenanceComponentsDefineValueEquality() {
    // Arrange
    var original = new NonwrittenBodyProvenance("implicit-constructor", "file", "owner");
    var candidates = List.of(new NonwrittenBodyProvenance("implicit-constructor", "file", "owner"),
        new NonwrittenBodyProvenance("enum-constructor", "file", "owner"),
        new NonwrittenBodyProvenance("implicit-constructor", "other-file", "owner"),
        new NonwrittenBodyProvenance("implicit-constructor", "file", "other-owner"));
    var origins = Set.of(original);

    // Act
    var membership = candidates.stream().map(origins::contains).toList();

    // Assert
    assertEquals(List.of(true, false, false, false), membership);
  }

  @Test
  void givenAnEmptyAstQualificationComponent_whenItsMatchingKeyIsDerived_thenTheIncompleteIdentityIsRejected() {
    // Arrange
    var complete = List.of("software", "source/1", "profile/1", "production/1", "content");

    // Act and Assert
    for (var blank : List.of("", " \t\n")) {
      for (var index = 0; index < complete.size(); index++) {
        var fields = new ArrayList<>(complete);
        fields.set(index, blank);
        assertThrows(IllegalArgumentException.class,
            () -> AstIdentity.fromContent(fields.get(0), fields.get(1), fields.get(2), fields.get(3), fields.get(4)));
      }
    }
  }

  @Test
  void givenAnEmptyGraphQualificationComponent_whenItsMatchingKeyIsDerived_thenTheIncompleteIdentityIsRejected() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var complete = List.of("cfg/1", "graph/1", "method", "graph-content");

    // Act and Assert
    for (var blank : List.of("", " \t\n")) {
      for (var index = 0; index < complete.size(); index++) {
        var fields = new ArrayList<>(complete);
        fields.set(index, blank);
        assertThrows(IllegalArgumentException.class,
            () -> GraphIdentity.fromContent(ast, fields.get(0), fields.get(1), fields.get(2), fields.get(3)));
      }
    }
  }

  @Test
  void givenEmptyBodyOrOriginFields_whenAnExclusiveBodyAssociationIsSupplied_thenMissingProvenanceIsRejected() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var complete = List.of("implicit-constructor", "file", "owner");
    var provenance = new NonwrittenBodyProvenance(complete.get(0), complete.get(1), complete.get(2));

    // Act and Assert
    for (var blank : List.of("", " \t\n")) {
      assertThrows(IllegalArgumentException.class, () -> new BodyRef(ast, blank, Optional.of("body"), Optional.empty()));
      assertThrows(IllegalArgumentException.class, () -> new BodyRef(ast, blank, Optional.empty(), Optional.of(provenance)));
      assertThrows(IllegalArgumentException.class, () -> new BodyRef(ast, "method", Optional.of(blank), Optional.empty()));
      for (var index = 0; index < complete.size(); index++) {
        var fields = new ArrayList<>(complete);
        fields.set(index, blank);
        assertThrows(IllegalArgumentException.class, () -> new NonwrittenBodyProvenance(fields.get(0), fields.get(1), fields.get(2)));
      }
    }
  }

  @Test
  void givenAnEmptyLocalKeyWithAValidOwner_whenQualifiedQueriesAreNamed_thenTheMissingOccurrenceIsRejected() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var graph = GraphIdentity.fromContent(ast, "cfg/1", "graph/1", "method", "graph-content");

    // Act and Assert
    for (var blank : List.of("", " \t\n")) {
      assertThrows(IllegalArgumentException.class, () -> new SourceOccurrence(ast, blank));
      assertThrows(IllegalArgumentException.class, () -> new GraphNodeKey(graph, blank));
      assertThrows(IllegalArgumentException.class, () -> new GraphValueKey(graph, blank));
      assertThrows(IllegalArgumentException.class, () -> new GraphEdgeKey(graph, blank));
    }
  }

  @Test
  void givenNegativeAndValidSequenceCounts_whenCanonicalFramesAreWritten_thenRefusalPreservesTheNextValidFrame() {
    // Arrange
    var writer = new CanonicalValueWriter("v");
    var original = writer.toByteArray();

    // Act
    assertThrows(IllegalArgumentException.class, () -> writer.count(-1));
    assertThrows(IllegalArgumentException.class, () -> writer.count(Integer.MIN_VALUE));

    // Assert
    assertArrayEquals(original, writer.toByteArray());
    writer.count(0);
    writer.count(2);
    assertArrayEquals(HexFormat.of().parseHex("0000000100760000000000000002"), writer.toByteArray());
  }

  @Test
  void givenEachAstQualificationComponent_whenDerivingKeys_thenIndependentFixedKeysAndSuppliedKeyRejectionsMatch() {
    // Arrange
    var tuples = List.of(
        List.of("software", "source/1", "profile/1", "production/1", "content-a"),
        List.of("software-changed", "source/1", "profile/1", "production/1", "content-a"),
        List.of("software", "source/1-changed", "profile/1", "production/1", "content-a"),
        List.of("software", "source/1", "profile/1-changed", "production/1", "content-a"),
        List.of("software", "source/1", "profile/1", "production/1-changed", "content-a"),
        List.of("software", "source/1", "profile/1", "production/1", "content-a-changed"));
    var keys = List.of(
        "ast-result:85e61eaf32421e6306a28289f889e0ab5a7214dc440dbe062ac66f8966df2096",
        "ast-result:04860298b075094077d16422446e4aeb762fb7053b784664a05f5174493616f6",
        "ast-result:55ee3d64ec4d1b1ca992a8aac73e2650ed2b81818fa62e6d5a43232221c24f9d",
        "ast-result:97535c691186f83b4215ff3c728d107108f4f9101dd83ed81b0b5468ce30d964",
        "ast-result:3327fe26817dc9bbcefba252545543d3ad76837c1b0a43000b4587777671a7fd",
        "ast-result:da6fcbc36110de430e7108a57225ab4784f2247d8dc89052acbbe4204e1692be");

    // Act
    var identities = tuples.stream().map(value -> AstIdentity.fromContent(value.get(0), value.get(1), value.get(2), value.get(3), value.get(4))).toList();

    // Assert
    assertEquals(keys, identities.stream().map(AstIdentity::astResultKey).toList());
    for (var tuple : tuples.subList(1, tuples.size())) {
      assertThrows(IllegalArgumentException.class,
          () -> new AstIdentity(keys.getFirst(), tuple.get(0), tuple.get(1), tuple.get(2), tuple.get(3), tuple.get(4)));
    }
  }

  @Test
  void givenEachGraphQualificationComponent_whenDerivingKeys_thenIndependentFixedKeysAndSuppliedKeyRejectionsMatch() {
    // Arrange
    var ast = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content-a");
    var other = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content-a-changed");
    var tuples = List.of(
        new GraphTuple(ast, "cfg/1", "graph/1", "method", "graph-content"),
        new GraphTuple(other, "cfg/1", "graph/1", "method", "graph-content"),
        new GraphTuple(ast, "cfg/2", "graph/1", "method", "graph-content"),
        new GraphTuple(ast, "cfg/1", "graph/2", "method", "graph-content"),
        new GraphTuple(ast, "cfg/1", "graph/1", "other", "graph-content"),
        new GraphTuple(ast, "cfg/1", "graph/1", "method", "other-content"));
    var keys = List.of(
        "cfg-derivation:348692a7a24bf05c36efd93036b7a5723362550b8ffcd97e8e0b74e51e4c911f",
        "cfg-derivation:60144e17421e6ece73c948ae8169c60ff5cfae045cbb26bd574c64d81e70e90a",
        "cfg-derivation:b1c234f8890b7215272e9ce2dd5e1ef36e6f6f92fe13912f86b6e6df8802f6ca",
        "cfg-derivation:7ac3ed5613074dbb32ac7c19489903385fb6751c70f2fc88d00a00dcd5a3c4e5",
        "cfg-derivation:bf5508e49e832a6afd7c76d5cf8ae20fcffa2e75a74c80db15071d9917de46ff",
        "cfg-derivation:a531f3fd5a1097ab70cac0c0918a0db5a1222465026ccac0b033059696468820");

    // Act
    var identities = tuples.stream().map(value -> GraphIdentity.fromContent(value.ast(), value.profile(), value.format(), value.method(), value.content())).toList();

    // Assert
    assertEquals(keys, identities.stream().map(GraphIdentity::derivationKey).toList());
    for (var tuple : tuples.subList(1, tuples.size())) {
      assertThrows(IllegalArgumentException.class,
          () -> new GraphIdentity(keys.getFirst(), tuple.ast(), tuple.profile(), tuple.format(), tuple.method(), tuple.content()));
    }
  }

  private record GraphTuple(AstIdentity ast, String profile, String format, String method, String content) {
  }

  @Test
  void givenQualifiedResult_whenAComponentOrSuppliedKeyDiffers_thenDistinctContentCannotReuseItsIdentity() {
    // Arrange
    var original = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content-a");

    // Act
    var changed = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content-b");

    // Assert
    assertNotEquals(original, changed);
    assertThrows(IllegalArgumentException.class, () -> new AstIdentity(original.astResultKey(), "software", "source/1", "profile/1",
        "production/1", "content-b"));
  }

  @Test
  void givenWrittenOrNonwrittenBodyOrigins_whenBodyReferenceIsConstructed_thenExactlyOneOriginIsRequired() {
    // Arrange
    var identity = AstIdentity.fromContent("software", "source/1", "profile/1", "production/1", "content");
    var provenance = new NonwrittenBodyProvenance("implicit-constructor", "file", "owner");

    // Act
    var written = new BodyRef(identity, "method", Optional.of("body"), Optional.empty());
    var inserted = new BodyRef(identity, "method", Optional.empty(), Optional.of(provenance));

    // Assert
    assertEquals(Optional.of("body"), written.sourceOccurrenceKey());
    assertEquals(Optional.of(provenance), inserted.nonwrittenProvenance());
    assertThrows(IllegalArgumentException.class, () -> new BodyRef(identity, "method", Optional.empty(), Optional.empty()));
    assertThrows(IllegalArgumentException.class, () -> new BodyRef(identity, "method", Optional.of("body"), Optional.of(provenance)));
  }

  @Test
  void givenUnpairedSurrogateAndReplacementText_whenCanonicalStringsAreWritten_thenExactUtf16UnitsRemainDistinct() {
    // Arrange
    var surrogate = new CanonicalValueWriter("");
    var replacement = new CanonicalValueWriter("");

    // Act
    surrogate.text("\ud800");
    replacement.text("?");

    // Assert
    assertArrayEquals(HexFormat.of().parseHex("0000000000000001d800"), surrogate.toByteArray());
    assertArrayEquals(HexFormat.of().parseHex("0000000000000001003f"), replacement.toByteArray());
    assertNotEquals(surrogate.digest("content"), replacement.digest("content"));
  }

  @Test
  void givenSignedZerosAndDistinctNanPayloads_whenCanonicalScalarsAreWritten_thenAllRawBitsSurvive() {
    // Arrange
    var writer = new CanonicalValueWriter("");

    // Act
    writer.floating(0.0f);
    writer.floating(-0.0f);
    writer.floating(Float.intBitsToFloat(0x7fc00001));
    writer.floating(Float.intBitsToFloat(0x7fc00002));
    writer.floating(0.0d);
    writer.floating(-0.0d);

    // Assert
    assertArrayEquals(HexFormat.of().parseHex("0000000000000000800000007fc000017fc0000200000000000000008000000000000000"),
        writer.toByteArray());
  }

  @Test
  void givenSuppliedContentWithEqualLabels_whenCaptureIsEncoded_thenActualContentAndExplicitAbsenceRemainDistinct() {
    // Arrange
    var sourceName = CapturedLocation.parse(Syntax.POSIX, "A.java");
    var first = new InputSnapshot(CapturedLocation.parse(Syntax.POSIX, "/first-root"), "SHA-256", "supplied",
        List.of(new SourceFile("key", sourceName, "digest", "a")));
    var second = new InputSnapshot(CapturedLocation.parse(Syntax.POSIX, "/second-root"), "SHA-256", "supplied",
        List.of(new SourceFile("key", sourceName, "digest", "b")));
    var relocated = new InputSnapshot(CapturedLocation.parse(Syntax.POSIX, "/other-root"), "SHA-256", "supplied", first.sourceFiles());

    // Act
    var firstBytes = CapturedValueEncoding.encode(Optional.of(first), Optional.empty());
    var secondBytes = CapturedValueEncoding.encode(Optional.of(second), Optional.empty());

    // Assert
    assertArrayEquals(HexFormat.of().parseHex(
        "0000001100630061007000740075007200650064002d00760061006c007500650073002f0033000000050069006e00700075007401"
            + "00000007005300480041002d003200350036000000080073007500700070006c0069006500640000000100000003006b0065007900"
            + "0000060041002e006a006100760061000000050050004f0053004900580000000800520045004c0041005400490056004500000000"
            + "00000001000000060041002e006a006100760061000000060041002e006a0061007600610000000600640069006700650073007400"
            + "00000100610000000b0065006e007600690072006f006e006d0065006e007400000000080073006f00660074007700610072006500"), firstBytes);
    assertArrayEquals(HexFormat.of().parseHex(
        "0000001100630061007000740075007200650064002d00760061006c007500650073002f0033000000050069006e00700075007400"
            + "0000000b0065006e007600690072006f006e006d0065006e007400000000080073006f00660074007700610072006500"),
        CapturedValueEncoding.encode(Optional.empty(), Optional.empty()));
    assertArrayEquals(firstBytes, CapturedValueEncoding.encode(Optional.of(relocated), Optional.empty()));
    assertNotEquals(HexFormat.of().formatHex(firstBytes), HexFormat.of().formatHex(secondBytes));
    assertNotEquals(HexFormat.of().formatHex(firstBytes),
        HexFormat.of().formatHex(CapturedValueEncoding.encode(Optional.empty(), Optional.empty())));
  }

  @Test
  void givenDependencyCountsAndRepeatedPositions_whenEnvironmentIsEncoded_thenRetainedFactsChangeDespiteEqualLegacySignature() {
    // Arrange
    var path = Path.of("library.jar").toAbsolutePath();
    var configuration = new CompilerConfiguration(JavaRelease.JAVA_25, List.of(capturedAbsolute(path), capturedAbsolute(path)), List.of(), List.of());
    var a = new DependencySnapshot(capturedAbsolute(path), DependencyKind.REGULAR_FILE, 1, 1, "same-fingerprint",
        new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of()));
    var b = new DependencySnapshot(capturedAbsolute(path), DependencyKind.REGULAR_FILE, 2, 1, "same-fingerprint",
        new DependencySemantics(DependencySemantics.State.CLASS_PATH, List.of()));
    var first = new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", List.of(), List.of(a, b), List.of());
    var second = new AnalysisEnvironmentSnapshot(configuration, "compiler", "vendor", "version", List.of(), List.of(b, a), List.of());

    // Act
    var firstBytes = CapturedValueEncoding.encode(Optional.empty(), Optional.of(first));
    var secondBytes = CapturedValueEncoding.encode(Optional.empty(), Optional.of(second));

    // Assert
    assertEquals(first.environmentDigest(), second.environmentDigest());
    assertNotEquals(HexFormat.of().formatHex(firstBytes), HexFormat.of().formatHex(secondBytes));
  }
}
