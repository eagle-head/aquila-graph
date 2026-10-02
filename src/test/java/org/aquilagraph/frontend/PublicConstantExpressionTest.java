package org.aquilagraph.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class PublicConstantExpressionTest {
  @Test
  void givenNumericOverflowShiftsAndFloatingPromotion_whenPublicCompilerAttributes_thenCopiedValuesMatchActualConstantFields() throws IOException {
    // Arrange
    var source = """
        class Constants {
          static final int intOverflow = 2147483647 + 1;
          static final long longOverflow = 9223372036854775807L + 1L;
          static final int maskedIntShift = 1 << 33;
          static final long maskedLongShift = 1L << 65;
          static final int unsignedShift = -1 >>> 1;
          static final float roundedFloat = 16777216f + 1f;
          static final boolean floatPromotion = 16777217L == 16777216f;
          static final boolean doublePromotion = 9007199254740993L == 9007199254740992d;
          static final boolean nanEquality = (0.0 / 0.0) == (0.0 / 0.0);
          static final boolean nanInequality = (0.0 / 0.0) != (0.0 / 0.0);
          static final boolean infinityComparison = 1.0 / 0.0 > 0;
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(11, observations.size());
    assertTrue(observations.values().stream().allMatch(observation -> observation.compilerConstant().isPresent()));
    assertEquals(Optional.of(Integer.MIN_VALUE), observations.get("intOverflow").compilerConstant());
    assertEquals(Optional.of(Long.MIN_VALUE), observations.get("longOverflow").compilerConstant());
    assertEquals(Optional.of(true), observations.get("floatPromotion").compilerConstant());
    assertEquals(Optional.of(true), observations.get("doublePromotion").compilerConstant());
    assertConstantAgreement(observations);
  }

  @Test
  void givenSignedZerosNaNAndInfinities_whenPublicCompilerAttributes_thenCopiedEqualityMatchesActualConstantFields() throws IOException {
    // Arrange
    var source = """
        class Constants {
          static final boolean floatZeroEqual = -0.0f == 0.0f;
          static final boolean floatZeroNotEqual = -0.0f != 0.0f;
          static final boolean floatNanEqual = (0.0f / 0.0f) == (0.0f / 0.0f);
          static final boolean floatNanNotEqual = (0.0f / 0.0f) != (0.0f / 0.0f);
          static final boolean floatNanToFinite = (0.0f / 0.0f) == 1.0f;
          static final boolean floatInfinityEqual = (1.0f / 0.0f) == (1.0f / 0.0f);
          static final boolean floatOppositeInfinityEqual = (1.0f / 0.0f) == (-1.0f / 0.0f);
          static final boolean floatAdjacentEqual = 0x1.0p0f == 0x1.000002p0f;
          static final boolean doubleZeroEqual = -0.0 == 0.0;
          static final boolean doubleZeroNotEqual = -0.0 != 0.0;
          static final boolean doubleNanEqual = (0.0 / 0.0) == (0.0 / 0.0);
          static final boolean doubleNanNotEqual = (0.0 / 0.0) != (0.0 / 0.0);
          static final boolean doubleNanToFinite = (0.0 / 0.0) == 1.0;
          static final boolean doubleInfinityEqual = (1.0 / 0.0) == (1.0 / 0.0);
          static final boolean doubleOppositeInfinityEqual = (1.0 / 0.0) == (-1.0 / 0.0);
          static final boolean doubleAdjacentEqual = 0x1.0p0 == 0x1.0000000000001p0;
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(16, observations.size());
    assertTrue(observations.values().stream().allMatch(observation -> observation.compilerConstant().isPresent()));
    assertEquals(Optional.of(true), observations.get("floatZeroEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("floatZeroNotEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("floatNanEqual").compilerConstant());
    assertEquals(Optional.of(true), observations.get("floatNanNotEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("floatNanToFinite").compilerConstant());
    assertEquals(Optional.of(true), observations.get("floatInfinityEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("floatOppositeInfinityEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("floatAdjacentEqual").compilerConstant());
    assertEquals(Optional.of(true), observations.get("doubleZeroEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("doubleZeroNotEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("doubleNanEqual").compilerConstant());
    assertEquals(Optional.of(true), observations.get("doubleNanNotEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("doubleNanToFinite").compilerConstant());
    assertEquals(Optional.of(true), observations.get("doubleInfinityEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("doubleOppositeInfinityEqual").compilerConstant());
    assertEquals(Optional.of(false), observations.get("doubleAdjacentEqual").compilerConstant());
    assertConstantAgreement(observations);
  }

  @Test
  void givenCastsAndConditionalNumericTypes_whenPublicCompilerAttributes_thenCopiedValuesPreserveSelectedTypeAndPrecision() throws IOException {
    // Arrange
    var source = """
        class Constants {
          static final int saturatedInt = (int) (1.0 / 0.0);
          static final long saturatedLong = (long) (1.0 / 0.0);
          static final byte narrowedByte = (byte) 130;
          static final short narrowedShort = (short) 32768;
          static final char narrowedChar = (char) -1;
          static final short conditionalShort = true ? (short) 4 : 5;
          static final float negativeZero = -0.0f;
          static final String text = "x" + (char) 65 + 16777217f;
          static final String castString = (String) "cast";
          static final boolean stringEquality = "x" + 1 == "x1";
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(10, observations.size());
    assertTrue(observations.values().stream().allMatch(observation -> observation.compilerConstant().isPresent()));
    assertEquals(Optional.of(Integer.MAX_VALUE), observations.get("saturatedInt").compilerConstant());
    assertEquals(Optional.of(Long.MAX_VALUE), observations.get("saturatedLong").compilerConstant());
    assertEquals(Optional.of((short) 4), observations.get("conditionalShort").compilerConstant());
    assertConstantAgreement(observations);
  }

  @Test
  void givenUnselectedAbruptBranchesAndNonconstantOperands_whenPublicCompilerAttributes_thenCopiedStatusMatchesActualConstantFields() throws IOException {
    // Arrange
    var source = """
        class Constants<T extends String> {
          static final String conditionalAbrupt = "x" + (true ? 1 : 1 / 0);
          static final String shortCircuitAbrupt = "x" + (false && 1 / 0 == 0);
          static final String selectedCall = "x" + (true ? 1 : System.nanoTime());
          static final String dynamic = "x" + System.nanoTime();
          static final String nullOperand = "x" + null;
          static final int VALUE = 1;
          static final String qualifiedName = "x" + Constants.VALUE;
          static final String primaryQualifier = "x" + new Constants<String>().VALUE;
          final String thisQualifier = "x" + this.VALUE;
          final String boundCast = "x" + (T) "a";
        }
        """;

    // Act
    var observations = observe(source);

    // Assert
    assertEquals(10, observations.size());
    assertTrue(observations.get("selectedCall").compilerConstant().isEmpty());
    assertTrue(observations.get("dynamic").compilerConstant().isEmpty());
    assertTrue(observations.get("nullOperand").compilerConstant().isEmpty());
    assertTrue(observations.get("primaryQualifier").compilerConstant().isEmpty());
    assertTrue(observations.get("thisQualifier").compilerConstant().isEmpty());
    assertTrue(observations.get("boundCast").compilerConstant().isEmpty());
    assertEquals(Optional.of("x1"), observations.get("qualifiedName").compilerConstant());
    assertConstantAgreement(observations);
  }

  @Test
  void givenAnUnavailableAttributedOperand_whenPublicCompilerReportsItsErrorType_thenConstantStatusStaysUnknown() throws IOException {
    // Arrange
    var source = """
        class Constants {
          static final String unavailable = "x" + missing;
          static final String dynamic = "x" + System.nanoTime();
          static final String constant = "x" + 1;
        }
        """;

    // Act
    var observations = observe(source, true);

    // Assert
    assertEquals(Optional.of(TypeKind.ERROR), observations.get("unavailable").rightOperandKind());
    assertTrue(observations.get("unavailable").constantStatus().isEmpty());
    assertTrue(observations.get("unavailable").copiedConstant().isEmpty());
    assertEquals(Optional.of(false), observations.get("dynamic").constantStatus());
    assertEquals(Optional.of(true), observations.get("constant").constantStatus());
  }

  @Test
  void givenTypeAndVariableQualifiedConstantFields_whenPublicCompilerAttributes_thenOnlyTheTypeNameSuppliesAConstantExpression() throws IOException {
    // Arrange
    var source = """
        class Constants {
          static final int VALUE = 7;
          static final Constants holder = new Constants();
          static final String typeQualifier = "x" + Constants.VALUE;
          static final String variableQualifier = "x" + holder.VALUE;
          static final String parenthesizedVariableQualifier = "x" + (holder).VALUE;
        }
        """;

    // Act
    var observations = observe(source);
    System.out.println("QUALIFIED_CONSTANT_PUBLIC_EVIDENCE=" + observations);

    // Assert
    assertEquals(5, observations.size());
    assertEquals(Optional.of(ElementKind.CLASS), observations.get("typeQualifier").qualifierElementKind());
    assertEquals(Optional.of(ElementKind.FIELD), observations.get("variableQualifier").qualifierElementKind());
    assertEquals(Optional.of("x7"), observations.get("typeQualifier").compilerConstant());
    assertTrue(observations.get("variableQualifier").compilerConstant().isEmpty());
    assertTrue(observations.get("parenthesizedVariableQualifier").compilerConstant().isEmpty());
    assertConstantAgreement(observations);
  }

  private static void assertConstantAgreement(Map<String, Observation> observations) {
    observations.forEach((name, observation) -> {
      assertEquals(observation.compilerConstant(), observation.copiedConstant(), name);
      assertEquals(Optional.of(observation.compilerConstant().isPresent()), observation.constantStatus(), name);
    });
  }

  private static Map<String, Observation> observe(String source) throws IOException {
    return observe(source, false);
  }

  private static Map<String, Observation> observe(String source, boolean erroneous) throws IOException {
    var compiler = ToolProvider.getSystemJavaCompiler();
    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    var file = SimpleJavaFileObject.forSource(Path.of("D:/Projects/aquila-graph/aquila-graph/target/compiler-input/Constants.java").toUri(), source);
    try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      var task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("--release", "25", "-proc:none"), null, List.of(file));
      var units = task.parse();
      task.analyze();
      assertEquals(erroneous, diagnostics.getDiagnostics().stream().anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR));
      var trees = Trees.instance(task);
      var types = task.getTypes();
      var semantics = new SemanticResolver(trees, types, task.getElements(), new DeclarationIdentityRegistry(trees, types));
      var constants = new JavaConstantExpression(semantics);
      var observations = new LinkedHashMap<String, Observation>();
      for (var unit : units) {
        new TreePathScanner<Void, Void>() {
          @Override
          public Void visitVariable(VariableTree variable, Void unused) {
            var path = getCurrentPath();
            if (path.getParentPath().getLeaf() instanceof ClassTree && variable.getInitializer() != null
                && trees.getElement(path) instanceof VariableElement field) {
              var initializerPath = new TreePath(path, variable.getInitializer());
              var rightKind = variable.getInitializer() instanceof BinaryTree binary
                  ? Optional.of(trees.getTypeMirror(new TreePath(initializerPath, binary.getRightOperand())).getKind())
                  : Optional.<TypeKind>empty();
              var qualifierKind = variable.getInitializer() instanceof BinaryTree binary && binary.getRightOperand() instanceof MemberSelectTree selection
                  ? Optional.ofNullable(trees.getElement(new TreePath(new TreePath(initializerPath, selection), selection.getExpression())))
                      .map(element -> element.getKind())
                  : Optional.<ElementKind>empty();
              observations.put(field.getSimpleName().toString(), new Observation(Optional.ofNullable(field.getConstantValue()),
                  constants.value(initializerPath), constants.constantStatus(initializerPath), rightKind, qualifierKind));
            }
            return super.visitVariable(variable, unused);
          }
        }.scan(unit, null);
      }
      return Map.copyOf(observations);
    }
  }

  private record Observation(Optional<Object> compilerConstant, Optional<Object> copiedConstant, Optional<Boolean> constantStatus,
      Optional<TypeKind> rightOperandKind, Optional<ElementKind> qualifierElementKind) {}
}
