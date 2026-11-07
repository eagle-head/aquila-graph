package org.aquilagraph.cli;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Deque;
import java.util.Objects;
import java.util.stream.Collectors;
import org.aquilagraph.cli.ReportDocument.Decimal;
import org.aquilagraph.cli.ReportDocument.Field;
import org.aquilagraph.cli.ReportDocument.Group;
import org.aquilagraph.cli.ReportDocument.Literal;
import org.aquilagraph.cli.ReportDocument.Node;
import org.aquilagraph.cli.ReportDocument.Part;
import org.aquilagraph.cli.ReportDocument.Section;
import org.aquilagraph.cli.ReportDocument.Text;
import org.aquilagraph.cli.ReportDocument.Value;

/** Sole owner of field layout, ASCII decimals, escaping and captured line endings. */
class CommandTextRenderer {
  private static final int HEX_DIGITS = 4;
  private final String lineSeparator;

  CommandTextRenderer(String lineSeparator) {
    Objects.requireNonNull(lineSeparator, "lineSeparator");
    super();
    this.lineSeparator = lineSeparator;
  }

  PreparedCommandOutput render(ReportDocument document) {
    Objects.requireNonNull(document, "document");
    return new PreparedCommandOutput(render(document.output()), render(document.error()));
  }

  private String render(Section section) {
    var result = new StringBuilder();
    var frames = new ArrayDeque<Frame>();
    frames.push(new Frame(section, 0));
    while (!frames.isEmpty()) {
      var frame = frames.pop();
      switch (frame.node()) {
        case Field field -> field(field, frame.indentation(), result);
        case Group group -> {
          field(group.header(), frame.indentation(), result);
          push(frames, group.children(), frame.indentation() + 1);
        }
        case Section nested -> push(frames, nested.children(), frame.indentation());
      }
    }
    return result.toString();
  }

  private static void push(Deque<Frame> frames, List<Node> children, int indentation) {
    for (var index = children.size() - 1; index >= 0; index--) {
      frames.push(new Frame(children.get(index), indentation));
    }
  }

  private void field(Field field, int indentation, StringBuilder result) {
    result.append("  ".repeat(indentation));
    if (!field.label().isEmpty()) {
      result.append(field.label()).append(": ");
    }
    result.append(value(field.value())).append(lineSeparator);
  }

  private static String value(Value value) {
    return switch (value) {
      case Text text -> quote(text.value());
      case Decimal number -> Long.toString(number.value());
      case Literal literal -> literal.value();
      case ReportDocument.Absence absence -> absence.token();
      case ReportDocument.Sequence sequence -> sequence.parts().stream().map(CommandTextRenderer::value).collect(Collectors.joining());
      case ReportDocument.QuotedComposite composite ->
        quote(composite.parts().stream().map(CommandTextRenderer::plain).collect(Collectors.joining()));
    };
  }

  private static String plain(Part part) {
    return switch (part) {
      case Text text -> text.value();
      case Decimal number -> Long.toString(number.value());
      case Literal literal -> literal.value();
    };
  }

  private static String quote(String value) {
    var result = new StringBuilder("\"");
    for (var index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      switch (character) {
        case '\\' -> result.append("\\\\");
        case '"' -> result.append("\\\"");
        case '\r' -> result.append("\\r");
        case '\n' -> result.append("\\n");
        case '\t' -> result.append("\\t");
        default -> {
          if (character < ' ' || character == '\u007f') {
            var hex = Integer.toHexString(character);
            result.append("\\u").append("0".repeat(HEX_DIGITS - hex.length())).append(hex);
          } else {
            result.append(character);
          }
        }
      }
    }
    return result.append('"').toString();
  }

  private record Frame(Node node, int indentation) {
  }
}
