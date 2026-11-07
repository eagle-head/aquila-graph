package org.aquilagraph.cli;

import java.util.List;
import java.util.Objects;

/** Closed compositional document vocabulary with raw typed field values. */
record ReportDocument(Section output, Section error) {
  ReportDocument {
    Objects.requireNonNull(output, "output");
    Objects.requireNonNull(error, "error");
  }

  sealed interface Node permits Field, Group, Section {
  }

  record Field(String label, Value value) implements Node {
    Field {
      Objects.requireNonNull(label, "label");
      Objects.requireNonNull(value, "value");
    }
  }

  record Group(Field header, List<Node> children) implements Node {
    Group {
      Objects.requireNonNull(header, "header");
      children = List.copyOf(children);
    }
  }

  record Section(List<Node> children) implements Node {
    Section {
      children = List.copyOf(children);
    }
  }

  sealed interface Value permits Part, Absence, Sequence, QuotedComposite {
  }
  sealed interface Part extends Value permits Text, Decimal, Literal {
  }

  record Text(String value) implements Part {
    Text {
      Objects.requireNonNull(value, "value");
    }
  }
  record Decimal(long value) implements Part {
  }
  record Literal(String value) implements Part {
    Literal {
      Objects.requireNonNull(value, "value");
    }
  }

  enum Absence implements Value {
    UNAVAILABLE("unavailable"), NOT_APPLICABLE("not-applicable"), NOT_REQUESTED("not-requested");
    private final String spelling;
    Absence(String token) {
      spelling = token;
    }
    String token() {
      return spelling;
    }
  }

  record Sequence(List<Value> parts) implements Value {
    Sequence {
      parts = List.copyOf(parts);
    }
  }
  record QuotedComposite(List<Part> parts) implements Value {
    QuotedComposite {
      parts = List.copyOf(parts);
    }
  }

  static Value text(String value) {
    return new Text(value);
  }
  static Value number(long value) {
    return new Decimal(value);
  }
  static Value token(String value) {
    return new Literal(value);
  }
  static Value sequence(Value... values) {
    return new Sequence(List.of(values));
  }
}
