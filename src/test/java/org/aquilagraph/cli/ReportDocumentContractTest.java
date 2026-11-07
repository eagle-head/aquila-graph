package org.aquilagraph.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.aquilagraph.cli.ReportDocument.Absence;
import org.aquilagraph.cli.ReportDocument.Decimal;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Group;
import org.aquilagraph.cli.ReportDocument.Literal;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Part;
import org.aquilagraph.cli.ReportDocument.QuotedComposite;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.cli.ReportDocument.Sequence;
import org.aquilagraph.cli.ReportDocument.Text;
import org.aquilagraph.cli.ReportDocument.Value;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

@Isolated("Temporarily changes the JVM format locale")
class ReportDocumentContractTest {
  @Test
  void givenNestedTypedDocumentAndArabicLocale_whenRenderedTwice_thenOneTimeEscapingAsciiNumbersAndCapturedTerminatorsAreDeterministic() {
    // Arrange
    var document = new ReportDocument(new Section(List.of(
        new Group(new Field("Header", new Text("quoted\" slash\\ CR\r LF\n TAB\t \u0000\u001f\u007f caf\u00e9 \ud83d\ude80")),
            List.of(new Section(List.of(new Field("Missing", Absence.UNAVAILABLE), new Field("Literal missing", new Text("unavailable")))),
                new Group(new Field("Nested", new Literal("COMPLETE")), List.of(
                    new Field("Number", new Decimal(-123)),
                    new Field("Compound", new Sequence(List.of(new Text("method\""), new Literal(" count="), new Decimal(2)))),
                    new Field("Context", new QuotedComposite(List.of(new Text("source\"\\"), new Literal("/"), new Decimal(3)))))))),
        new Section(List.of(new Field("Unused", Absence.NOT_REQUESTED))))),
        new Section(List.of(new Field("", new Literal("Usage line")), new Field("Detail", Absence.NOT_APPLICABLE))));
    var renderer = new CommandTextRenderer("\r\n");
    var locale = Locale.getDefault(Locale.Category.FORMAT);
    final PreparedCommandOutput first;
    final PreparedCommandOutput second;
    try {
      Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("ar-EG-u-nu-arab"));
      // Act
      first = renderer.render(document);
      second = renderer.render(document);
    } finally {
      Locale.setDefault(Locale.Category.FORMAT, locale);
    }

    // Assert
    assertEquals(first, second);
    assertEquals("Header: \"quoted\\\" slash\\\\ CR\\r LF\\n TAB\\t \\u0000\\u001f\\u007f caf\u00e9 \ud83d\ude80\"\r\n"
        + "  Missing: unavailable\r\n  Literal missing: \"unavailable\"\r\n  Nested: COMPLETE\r\n"
        + "    Number: -123\r\n    Compound: \"method\\\"\" count=2\r\n    Context: \"source\\\"\\\\/3\"\r\nUnused: not-requested\r\n", first.output());
    assertEquals("Usage line\r\nDetail: not-applicable\r\n", first.error());
  }

  @Test
  void givenCallerOwnedDocumentCollections_whenAliasesChange_thenRenderedDocumentAndExposedCollectionsRemainImmutable() {
    // Arrange
    var parts = new ArrayList<Value>(List.of(new Text("original"), new Decimal(1)));
    var quotedParts = new ArrayList<Part>(List.of(new Text("origin"), new Literal("/"), new Decimal(2)));
    var children = new ArrayList<Node>(List.of(new Field("Value", new Sequence(parts)), new Field("Quoted", new QuotedComposite(quotedParts))));
    var group = new Group(new Field("Group", new Literal("TOKEN")), children);
    var roots = new ArrayList<Node>(List.of(group));
    var section = new Section(roots);
    var document = new ReportDocument(section, new Section(List.of()));

    // Act
    parts.clear(); quotedParts.clear(); children.clear(); roots.clear();
    var rendered = new CommandTextRenderer("\n").render(document);

    // Assert
    assertEquals("Group: TOKEN\n  Value: \"original\"1\n  Quoted: \"origin/2\"\n", rendered.output());
    assertEquals("", rendered.error());
    assertThrows(UnsupportedOperationException.class, () -> section.children().clear());
    assertThrows(UnsupportedOperationException.class, () -> group.children().clear());
  }
}
