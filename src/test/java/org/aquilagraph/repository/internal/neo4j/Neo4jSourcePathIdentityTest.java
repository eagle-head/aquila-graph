package org.aquilagraph.repository.internal.neo4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.StableId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

class Neo4jSourcePathIdentityTest {
  private static final String CONTENT = "class Example {}";
  private static final String CONTENT_DIGEST = "f651cac89b8678333791dc9663cb935efae80171ab89e41bb662d1c72b40c362";
  private static final String CHANGED_CONTENT = "class Example { int value; }";
  private static final String CHANGED_CONTENT_DIGEST = "c2ba3c4c1f42b3471d6df30d290c606f132a3896ec56b97952c948e49679f729";
  private static final String NESTED_LINK = "relationship:51c0c983769cd964a5c15d34d0e41aa5b47119dbc1100171422e453e7696d200";

  @TestFactory
  Stream<DynamicTest> givenExplicitRelativeLocations_whenMapped_thenTheirCanonicalSourceLinksAreExact() {
    return Stream.of(
        sourcePathCase("NativeSegments", CapturedLocation.parse(Syntax.WINDOWS, "src/Example.java"), CONTENT, "src/Example.java", CONTENT_DIGEST, NESTED_LINK),
        sourcePathCase("NormalizedAlias", CapturedLocation.parse(Syntax.WINDOWS, "src/unused/.././Example.java"), CONTENT, "src/Example.java",
            CONTENT_DIGEST, NESTED_LINK),
        sourcePathCase("BackslashSpelling", CapturedLocation.parse(Syntax.WINDOWS, "src\\Example.java"), CONTENT, "src/Example.java", CONTENT_DIGEST, NESTED_LINK),
        sourcePathCase("AlreadyCanonicalFlatPath", CapturedLocation.parse(Syntax.WINDOWS, "Example.java"), CONTENT, "Example.java", CONTENT_DIGEST,
            "relationship:c17d777d93b30e7c2360e09d8b940e9fe819f351ae2cb4a60e29d78b364c7a69"),
        sourcePathCase("CaseSensitivePath", CapturedLocation.parse(Syntax.WINDOWS, "src/example.java"), CONTENT, "src/example.java", CONTENT_DIGEST,
            "relationship:b6523b9259d244688b01b6979740bbe1671f153980727b38a607000cbb3c5436"),
        sourcePathCase("ChangedContent", CapturedLocation.parse(Syntax.WINDOWS, "src/Example.java"), CHANGED_CONTENT, "src/Example.java", CHANGED_CONTENT_DIGEST,
            "relationship:a9a4ff1d9881ce1cee488394f5ca18ddbd1a32ed09da903177aae96edf0a1fd0"));
  }

  private DynamicTest sourcePathCase(String context, CapturedLocation relativePath, String content, String canonicalPath, String contentDigest,
      String expectedLink) {
    return DynamicTest.dynamicTest("given" + context + "_whenMapped_thenSourceAndRelationshipUseTheExpectedCanonicalIdentity",
        () -> assertSourcePathIdentity(relativePath, content, canonicalPath, contentDigest, expectedLink));
  }

  private void assertSourcePathIdentity(CapturedLocation relativePath, String content, String canonicalPath, String contentDigest, String expectedLink) {
    // Arrange
    var source = SourceFile.fromContent(relativePath, content);
    var snapshot = InputSnapshot.fromSources(CapturedLocation.parse(Syntax.WINDOWS, "C:/source-root"), List.of(source));
    var expectedSourceKey = StableId.sha256("source", canonicalPath, contentDigest);
    var expectedSnapshotDigest = StableId.sha256("snapshot", canonicalPath, contentDigest);
    var expectedSnapshotKey = StableId.sha256("input-snapshot", expectedSnapshotDigest);
    var builder = new Neo4jProjectionBuilder("run");
    builder.node("SoftwareVersion", "software");

    // Act
    new Neo4jInputMapper().map("software", snapshot, builder);
    var projection = builder.build();

    // Assert
    assertEquals(contentDigest, source.contentDigest());
    assertEquals(expectedSourceKey, source.sourceFileKey());
    assertEquals(expectedSnapshotDigest, snapshot.snapshotDigest());
    assertEquals(List.of(new Neo4jNode(expectedSourceKey, "SourceFile",
        Map.of("runId", "run", "key", expectedSourceKey, "relativePath", canonicalPath,
            "contentDigest", contentDigest, "content", content))),
        projection.nodes().stream().filter(node -> node.label().equals("SourceFile")).toList());
    assertEquals(List.of(new Neo4jRelationship(expectedLink, "HAS_SOURCE", expectedSnapshotKey, expectedSourceKey,
        Map.of("runId", "run", "key", expectedLink))),
        projection.relationships().stream().filter(link -> link.type().equals("HAS_SOURCE")).toList());
  }

  @TestFactory
  Stream<DynamicTest> givenEqualCanonicalPaths_whenCaptured_thenSnapshotRejectsDuplicates() {
    return Stream.of(
        DynamicTest.dynamicTest("givenEqualCanonicalPathsAndContent_whenSnapshotIsCaptured_thenDuplicatePathsAreRejected",
            () -> assertDuplicatePathRejected(CONTENT, true)),
        DynamicTest.dynamicTest("givenEqualCanonicalPathsWithDistinctContent_whenSnapshotIsCaptured_thenDuplicatePathsAreRejected",
            () -> assertDuplicatePathRejected(CHANGED_CONTENT, false)));
  }

  private void assertDuplicatePathRejected(String secondContent, boolean sameSourceKey) {
    // Arrange
    var first = SourceFile.fromContent(CapturedLocation.parse(Syntax.WINDOWS, "src/Example.java"), CONTENT);
    var second = SourceFile.fromContent(CapturedLocation.parse(Syntax.WINDOWS, "src\\Example.java"), secondContent);

    // Act
    Executable capture = () -> InputSnapshot.fromSources(CapturedLocation.parse(Syntax.WINDOWS, "C:/source-root"), List.of(first, second));

    // Assert
    assertEquals(first.canonicalRelativePath(), second.canonicalRelativePath());
    assertEquals(sameSourceKey, first.sourceFileKey().equals(second.sourceFileKey()));
    assertThrowsExactly(IllegalArgumentException.class, capture);
  }
}
