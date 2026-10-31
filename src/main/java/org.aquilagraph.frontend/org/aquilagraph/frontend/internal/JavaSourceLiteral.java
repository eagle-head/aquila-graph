package org.aquilagraph.frontend.internal;

import java.math.BigInteger;
import java.util.Optional;
import javax.lang.model.type.TypeKind;
import org.aquilagraph.source.api.DecimalMinimumOperand;
import org.aquilagraph.source.api.JavaTypeCategory;

/** Interprets translated literal tokens without assigning a positive value to restricted magnitudes. */
final class JavaSourceLiteral {
  private static final int CHARACTER_UTF16_LENGTH = 1;
  private JavaSourceLiteral() {
  }

  static Optional<Value> parse(JavaWrittenExpression.Literal literal, boolean immediateMinus) {
    var text = literal.token().meaning();
    try {
      return Optional.of(switch (literal.sourceKind()) {
        case STRING_LITERAL -> new Value(TypeKind.DECLARED, Optional.of(string(literal.token())), Optional.empty());
        case CHAR_LITERAL -> {
          var characters = text.substring(1, text.length() - 1).translateEscapes();
          if (characters.length() != CHARACTER_UTF16_LENGTH) {
            throw new IllegalArgumentException("A character token requires one translated UTF-16 value");
          }
          yield new Value(TypeKind.CHAR, Optional.of(characters.charAt(0)), Optional.empty());
        }
        case BOOLEAN_LITERAL -> new Value(TypeKind.BOOLEAN, Optional.of(Boolean.valueOf(text)), Optional.empty());
        case NULL_LITERAL -> new Value(TypeKind.NULL, Optional.empty(), Optional.empty());
        case FLOAT_LITERAL -> new Value(TypeKind.FLOAT, Optional.of(Float.valueOf(text.replace("_", ""))), Optional.empty());
        case DOUBLE_LITERAL -> new Value(TypeKind.DOUBLE, Optional.of(Double.valueOf(text.replace("_", ""))), Optional.empty());
        case INT_LITERAL, LONG_LITERAL -> integer(text, literal.sourceKind().name().startsWith("LONG"), immediateMinus);
        default -> throw new IllegalArgumentException("The token is not a supported literal");
      });
    } catch (IllegalArgumentException invalidLiteral) {
      return Optional.empty();
    }
  }

  private static String string(JavaSourceTokens.Token token) {
    var text = token.value();
    if (token.kind() != JavaSourceTokens.Kind.TEXT_BLOCK) {
      return text.substring(1, text.length() - 1).translateEscapes();
    }
    var body = text.substring(3, text.length() - 3).replace("\r\n", "\n").replace('\r', '\n');
    var firstLine = body.indexOf('\n');
    if (firstLine < 0) {
      throw new IllegalArgumentException("A text block requires its opening line terminator");
    }
    return body.substring(firstLine + 1).stripIndent().translateEscapes();
  }

  private static Value integer(String spelling, boolean wide, boolean immediateMinus) {
    var text = spelling.replace("_", "");
    if (wide) {
      text = text.substring(0, text.length() - 1);
    }
    var radix = text.startsWith("0x") || text.startsWith("0X")
        ? 16
        : text.startsWith("0b") || text.startsWith("0B") ? 2 : text.length() > 1 && text.charAt(0) == '0' ? 8 : 10;
    var digits = radix == 16 || radix == 2 ? text.substring(2) : text;
    var magnitude = new BigInteger(digits, radix);
    var bits = wide ? Long.SIZE : Integer.SIZE;
    var kind = wide ? TypeKind.LONG : TypeKind.INT;
    var maximum = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
    if (radix == 10 && magnitude.compareTo(maximum) > 0) {
      if (!immediateMinus || !magnitude.equals(maximum.add(BigInteger.ONE))) {
        throw new IllegalArgumentException("The decimal magnitude is outside its permitted unary-minus context");
      }
      return new Value(kind, Optional.empty(),
          Optional.of(new DecimalMinimumOperand(wide ? JavaTypeCategory.LONG : JavaTypeCategory.INT, magnitude.toString())));
    }
    if (magnitude.signum() < 0 || magnitude.bitLength() > bits) {
      throw new IllegalArgumentException("The integral magnitude exceeds its literal width");
    }
    Object value;
    if (wide) {
      value = Long.valueOf(magnitude.longValue());
    } else {
      value = Integer.valueOf(magnitude.intValue());
    }
    return new Value(kind, Optional.of(value), Optional.empty());
  }

  record Value(TypeKind kind, Optional<Object> constant, Optional<DecimalMinimumOperand> restrictedDecimal) {
  }
}
