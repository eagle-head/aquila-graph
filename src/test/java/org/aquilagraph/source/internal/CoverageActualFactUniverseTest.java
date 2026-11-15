package org.aquilagraph.source.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactReference.Kind;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.CoverageSyntaxFactValue.Child;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.Test;

class CoverageActualFactUniverseTest {
  private static final SyntaxStructuralPath ROOT = SyntaxStructuralPath.sourceFileRoot();
  private static final SyntaxRole MEMBER = SyntaxRole.MEMBER;
  private static final String PARENT = "parent";
  private static final String FIRST = "first";
  private static final String SECOND = "second";

  @Test
  void givenEqualWrittenChildren_whenQueryingSelectedRolesAndPositions_thenOccurrenceOrderAndTextAreExact() {
    // Arrange
    var source = source("Example.java", "; ;");
    var parent = syntax(ROOT, List.of(new Child(MEMBER, 1, SECOND), new Child(MEMBER, 0, FIRST)));
    var first = syntax(ROOT.child(MEMBER, 0), List.of());
    var second = syntax(ROOT.child(MEMBER, 1), List.of());
    var owner = reference(PARENT, source, parent, Optional.empty());
    var firstChild = reference(FIRST, source, first, Optional.of(span(source, 0, 1)));
    var secondChild = reference(SECOND, source, second, Optional.of(span(source, 2, 3)));
    var facts = new CoverageActualFactUniverse(List.of(firstChild, owner, secondChild),
        Map.of(PARENT, parent, FIRST, first, SECOND, second), List.of(source));

    // Act
    var members = facts.children(owner, List.of(MEMBER));
    var position = facts.child(owner, MEMBER, 0);
    var missingPosition = facts.child(owner, MEMBER, 2);
    var emptyRole = facts.children(owner, List.of(SyntaxRole.ANNOTATION));
    var written = members.stream().map(facts::capturedText).toList();

    // Assert
    assertEquals(List.of(secondChild, firstChild), members);
    assertEquals(Optional.of(firstChild), position);
    assertTrue(missingPosition.isEmpty());
    assertTrue(emptyRole.isEmpty());
    assertEquals(List.of(Optional.of(";"), Optional.of(";")), written);
  }

  @Test
  void givenDanglingAndForeignAttachments_whenReadingActualChildren_thenPresentMalformedSlotsAreRefused() {
    // Arrange
    var source = source("Owner.java", ";");
    var foreignSource = source("Other.java", ";");
    var parent = syntax(ROOT, List.of(new Child(MEMBER, 0, FIRST), new Child(MEMBER, 1, SECOND)));
    var sameSourceSibling = syntax(ROOT.child(MEMBER, 3), List.of());
    var foreign = syntax(ROOT.child(MEMBER, 1), List.of());
    var owner = reference(PARENT, source, parent, Optional.empty());
    var wrongPath = reference(FIRST, source, sameSourceSibling, Optional.of(span(source, 0, 1)));
    var wrongSource = reference(SECOND, foreignSource, foreign, Optional.of(span(foreignSource, 0, 1)));
    var dangling = new CoverageActualFactUniverse(List.of(owner), Map.of(PARENT, parent), List.of(source));
    var inconsistent = new CoverageActualFactUniverse(List.of(owner, wrongPath, wrongSource),
        Map.of(PARENT, parent, FIRST, sameSourceSibling, SECOND, foreign), List.of(source, foreignSource));

    // Act
    var missing = assertThrows(IllegalArgumentException.class, () -> dangling.children(owner, List.of(MEMBER)));
    var sibling = assertThrows(IllegalArgumentException.class, () -> inconsistent.child(owner, MEMBER, 0));
    var crossSource = assertThrows(IllegalArgumentException.class, () -> inconsistent.child(owner, MEMBER, 1));

    // Assert
    assertTrue(missing.getMessage().contains("exact source occurrence"));
    assertTrue(sibling.getMessage().contains("exact source occurrence"));
    assertTrue(crossSource.getMessage().contains("exact source occurrence"));
  }

  @Test
  void givenRepeatedAttachmentAtOnePosition_whenReadingEitherShape_thenAmbiguousPositionIsRefused() {
    // Arrange
    var source = source("Example.java", ";");
    var attachment = new Child(MEMBER, 0, FIRST);
    var parent = syntax(ROOT, List.of(attachment, attachment));
    var child = syntax(ROOT.child(MEMBER, 0), List.of());
    var owner = reference(PARENT, source, parent, Optional.empty());
    var member = reference(FIRST, source, child, Optional.of(span(source, 0, 1)));
    var facts = new CoverageActualFactUniverse(List.of(owner, member), Map.of(PARENT, parent, FIRST, child), List.of(source));

    // Act
    var single = assertThrows(IllegalArgumentException.class, () -> facts.child(owner, MEMBER, 0));
    var group = assertThrows(IllegalArgumentException.class, () -> facts.children(owner, List.of(MEMBER)));

    // Assert
    assertTrue(single.getMessage().contains("one exact attachment"));
    assertTrue(group.getMessage().contains("one exact attachment"));
  }

  @Test
  void givenSameIdentityWithDifferentValue_whenLookingUpTheClaim_thenNeitherLookupNorChildNavigationMatches() {
    // Arrange
    var source = source("Example.java", ";");
    var parent = syntax(ROOT, List.of());
    var forged = syntax(ROOT, List.of(new Child(MEMBER, 0, FIRST)));
    var owner = reference(PARENT, source, parent, Optional.empty());
    var claimed = reference(PARENT, source, forged, Optional.empty());
    var facts = new CoverageActualFactUniverse(List.of(owner), Map.of(PARENT, parent), List.of(source));

    // Act
    var actual = facts.reference(owner);
    var exact = facts.reference(claimed);
    var typed = facts.sourceValue(claimed);
    var failure = assertThrows(IllegalArgumentException.class, () -> facts.child(claimed, MEMBER, 0));

    // Assert
    assertEquals(Optional.of(owner), actual);
    assertTrue(exact.isEmpty());
    assertTrue(typed.isEmpty());
    assertTrue(failure.getMessage().contains("exact independently copied actual reference"));
  }

  @Test
  void givenAbsentEmptyAndInvalidOrigins_whenReadingCapturedText_thenOnlyValidSpansYieldText() {
    // Arrange
    var source = source("Owner.java", ";");
    var foreign = source("Other.java", ";");
    var parent = syntax(ROOT, List.of());
    var empty = reference(PARENT, source, parent, Optional.of(span(source, 1, 1)));
    var absent = reference(FIRST, source, parent, Optional.empty());
    var invalid = reference(SECOND, source, parent, Optional.of(span(source, 0, 2)));
    var facts = new CoverageActualFactUniverse(List.of(empty, absent, invalid), Map.of(PARENT, parent, FIRST, parent, SECOND, parent),
        List.of(source, foreign));
    var uncaptured = new CoverageActualFactUniverse(List.of(empty), Map.of(PARENT, parent), List.of(foreign));

    // Act
    var boundaryText = facts.capturedText(empty);
    var absentText = facts.capturedText(absent);
    var invalidOrigin = assertThrows(IllegalArgumentException.class, () -> facts.capturedText(invalid));
    var otherOrigin = assertThrows(IllegalArgumentException.class, () -> facts.capturedText(empty, span(foreign, 0, 1)));
    var uncapturedOrigin = assertThrows(IllegalArgumentException.class, () -> uncaptured.capturedText(empty));

    // Assert
    assertEquals(Optional.of(""), boundaryText);
    assertTrue(absentText.isEmpty());
    assertTrue(invalidOrigin.getMessage().contains("captured source"));
    assertTrue(otherOrigin.getMessage().contains("captured source"));
    assertTrue(uncapturedOrigin.getMessage().contains("captured source"));
  }

  private static SourceFile source(String path, String content) {
    return SourceFile.fromContent(CapturedLocation.parse(CapturedLocation.Syntax.POSIX, path), content);
  }

  private static SourceSpan span(SourceFile file, int start, int end) {
    return new SourceSpan(file.sourceFileKey(), start, end);
  }

  private static CoverageSyntaxFactValue syntax(SyntaxStructuralPath path, List<Child> children) {
    return new CoverageSyntaxFactValue(path, JavaSourceKind.EMPTY_STATEMENT, SyntaxKind.EMPTY, SyntaxType.NotApplicable.INSTANCE,
        SyntaxValue.None.INSTANCE, children, Optional.empty(), Optional.empty());
  }

  private static CoverageFactReference reference(String key, SourceFile source, CoverageSyntaxFactValue syntax, Optional<SourceSpan> origin) {
    return new CoverageFactReference(Kind.SYNTAX, key, source.sourceFileKey(), new CoverageFactValue.Source(CoverageSourceProjection.copy(syntax)), origin);
  }
}
