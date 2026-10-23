package org.aquilagraph.frontend.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.AggregateFixture;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxKind;

import org.aquilagraph.source.api.SyntaxNodeKey;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class JavaDeclarationTypeAttachmentAstTest {
  private static final CompilerConfiguration JAVA_25 = CompilerConfiguration.forRelease(JavaRelease.JAVA_25);
  private static final String SOURCE = """
      package sample;
      import java.util.List;
      import java.io.IOException;
      import java.sql.SQLException;
      sealed class Base<T> permits Child {}
      non-sealed abstract class Child<T extends Number & java.io.Serializable> extends Base<String> implements Runnable {
        int number;
        List<String> names;
        List<Integer> counts;
        abstract <U extends Number> List<U> convert(Child<T> this, int count, U value) throws IOException, SQLException;
        abstract void absent();
        Child() {}
      }
      record Pair(int left, List<String> right) {}
      """;

  @TempDir Path sourceRoot;

  @Test
  void givenEveryDeclarationHeaderRole_whenAnalyzed_thenTypeAttachmentsAndSelectedDeclarationsRemainExact() throws IOException {
    // Arrange
    var source = SOURCE;
    Files.writeString(sourceRoot.resolve("Declarations.java"), source);

    // Act
    var result = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var aggregate = result.aggregate().orElseThrow();
    var owner = aggregate.roots().getFirst().sourceFileKey();
    var root = SyntaxStructuralPath.sourceFileRoot();
    var base = root.child(SyntaxRole.TYPE_DECLARATION, 0);
    var child = root.child(SyntaxRole.TYPE_DECLARATION, 1);
    var pair = root.child(SyntaxRole.TYPE_DECLARATION, 2);
    var method = child.child(SyntaxRole.MEMBER, 3);
    var methodKey = aggregate.node(SyntaxNodeKey.from(owner, method, JavaSourceKind.METHOD).value())
        .orElseThrow(() -> new AssertionError(result.diagnostics().toString()))
        .value(SyntaxValue.ExecutableDeclaration.class).declarationKey();
    var childParameter = "type:sample.Child#type-parameter:0";
    var methodParameter = methodKey + "#type-parameter:0";
    var list = "type:java.util.List";
    var string = declared("type:java.lang.String");
    var integer = declared("type:java.lang.Integer");
    var expected = List.of(
        attachment(source, base, SyntaxRole.PERMITTED_SUBCLASS, 0, JavaSourceKind.IDENTIFIER, "permits Child", 8,
            declared("type:sample.Child"), "type:sample.Child"),
        attachment(source, child.child(SyntaxRole.TYPE_PARAMETER, 0), SyntaxRole.TYPE_BOUND, 0, JavaSourceKind.IDENTIFIER,
            "extends Number &", 8, declared("type:java.lang.Number"), "type:java.lang.Number"),
        attachment(source, child.child(SyntaxRole.TYPE_PARAMETER, 0), SyntaxRole.TYPE_BOUND, 1, JavaSourceKind.MEMBER_SELECT,
            "& java.io.Serializable", 2, declared("type:java.io.Serializable"), "type:java.io.Serializable"),
        attachment(source, child, SyntaxRole.SUPERCLASS, 0, JavaSourceKind.PARAMETERIZED_TYPE, "extends Base<String>", 8,
            parameterized("type:sample.Base", string), "type:sample.Base"),
        attachment(source, child, SyntaxRole.INTERFACE, 0, JavaSourceKind.IDENTIFIER, "implements Runnable", 11,
            declared("type:java.lang.Runnable"), "type:java.lang.Runnable"),
        attachment(source, child.child(SyntaxRole.MEMBER, 0), SyntaxRole.DECLARED_TYPE, 0, JavaSourceKind.PRIMITIVE_TYPE,
            "int number", 0, primitive(JavaTypeCategory.INT), null),
        attachment(source, child.child(SyntaxRole.MEMBER, 1), SyntaxRole.DECLARED_TYPE, 0, JavaSourceKind.PARAMETERIZED_TYPE,
            "List<String> names", 0, parameterized(list, string), list),
        attachment(source, child.child(SyntaxRole.MEMBER, 2), SyntaxRole.DECLARED_TYPE, 0, JavaSourceKind.PARAMETERIZED_TYPE,
            "List<Integer> counts", 0, parameterized(list, integer), list),
        attachment(source, method.child(SyntaxRole.TYPE_PARAMETER, 0), SyntaxRole.TYPE_BOUND, 0, JavaSourceKind.IDENTIFIER,
            "extends Number> List", 8, declared("type:java.lang.Number"), "type:java.lang.Number"),
        attachment(source, method, SyntaxRole.RETURN_TYPE, 0, JavaSourceKind.PARAMETERIZED_TYPE, "List<U> convert", 0,
            parameterized(list, new JavaTypeStructure.Variable(methodParameter)), list),
        attachment(source, method.child(SyntaxRole.RECEIVER_PARAMETER, 0), SyntaxRole.DECLARED_TYPE, 0,
            JavaSourceKind.PARAMETERIZED_TYPE, "Child<T> this", 0,
            parameterized("type:sample.Child", new JavaTypeStructure.Variable(childParameter)), "type:sample.Child"),
        attachment(source, method.child(SyntaxRole.PARAMETER, 0), SyntaxRole.DECLARED_TYPE, 0, JavaSourceKind.PRIMITIVE_TYPE,
            "int count", 0, primitive(JavaTypeCategory.INT), null),
        attachment(source, method.child(SyntaxRole.PARAMETER, 1), SyntaxRole.DECLARED_TYPE, 0, JavaSourceKind.IDENTIFIER,
            "U value", 0, new JavaTypeStructure.Variable(methodParameter), methodParameter),
        attachment(source, method, SyntaxRole.THROWN_TYPE, 0, JavaSourceKind.IDENTIFIER, "throws IOException", 7,
            declared("type:java.io.IOException"), "type:java.io.IOException"),
        attachment(source, method, SyntaxRole.THROWN_TYPE, 1, JavaSourceKind.IDENTIFIER, "IOException, SQLException", 13,
            declared("type:java.sql.SQLException"), "type:java.sql.SQLException"),
        attachment(source, child.child(SyntaxRole.MEMBER, 4), SyntaxRole.RETURN_TYPE, 0, JavaSourceKind.PRIMITIVE_TYPE,
            "void absent", 0, primitive(JavaTypeCategory.VOID), null),
        attachment(source, pair.child(SyntaxRole.RECORD_COMPONENT, 0), SyntaxRole.DECLARED_TYPE, 0,
            JavaSourceKind.PRIMITIVE_TYPE, "int left", 0, primitive(JavaTypeCategory.INT), null),
        attachment(source, pair.child(SyntaxRole.RECORD_COMPONENT, 1), SyntaxRole.DECLARED_TYPE, 0,
            JavaSourceKind.PARAMETERIZED_TYPE, "List<String> right", 0, parameterized(list, string), list));

    // Assert
    assertTrue(result.diagnostics().stream().noneMatch(diagnostic -> diagnostic.code().startsWith("compiler.err.")),
        () -> result.diagnostics().toString());
    assertEquals(Set.copyOf(expected.stream().map(item -> edge(owner, item)).toList()),
        Set.copyOf(aggregate.structuralEdges().stream().filter(edge -> isDeclarationTypeEdge(aggregate, edge)).toList()));
    for (var item : expected) {
      var node = aggregate.node(SyntaxNodeKey.from(owner, item.path(), item.kind()).value()).orElseThrow();
      assertEquals(SyntaxKind.TYPE_USE, node.syntaxKind());
      assertEquals(new SourceSpan(owner, item.start(), item.start() + item.length()), node.origin().orElseThrow());
      assertEquals(item.structure(), ((SyntaxType.Resolved) node.type()).structure().orElseThrow());
      assertEquals(item.targetKey().map(key -> reference(item.structure(), key)), node.semanticReference());
    }
    assertEquals(Set.copyOf(expected.stream().filter(item -> item.targetKey().isPresent())
        .map(item -> semanticEdge(owner, item)).toList()), Set.copyOf(aggregate.semanticEdges().stream()
            .filter(edge -> expected.stream().anyMatch(item -> edge.sourceKey()
                .equals(SyntaxNodeKey.from(owner, item.path(), item.kind()).value()))).toList()));
    assertNotEquals(expected.get(6).structure(), expected.get(7).structure());
    assertEquals(List.of(), aggregate.node(SyntaxNodeKey.from(owner, method, JavaSourceKind.METHOD).value()).orElseThrow()
        .children(SyntaxRole.BODY));
    assertEquals(List.of(), aggregate.node(SyntaxNodeKey.from(owner, child.child(SyntaxRole.MEMBER, 4), JavaSourceKind.METHOD).value())
        .orElseThrow().children(SyntaxRole.RECEIVER_PARAMETER));
    assertEquals(List.of(), aggregate.node(SyntaxNodeKey.from(owner, child.child(SyntaxRole.MEMBER, 5), JavaSourceKind.METHOD).value())
        .orElseThrow().children(SyntaxRole.RETURN_TYPE));
    var declarationLinks = Set.of(
        declarationEdge(owner, child.child(SyntaxRole.TYPE_PARAMETER, 0), JavaSourceKind.TYPE_PARAMETER,
            ReferenceKind.TYPE_PARAMETER, childParameter),
        declarationEdge(owner, method.child(SyntaxRole.TYPE_PARAMETER, 0), JavaSourceKind.TYPE_PARAMETER,
            ReferenceKind.TYPE_PARAMETER, methodParameter),
        declarationEdge(owner, method.child(SyntaxRole.PARAMETER, 0), JavaSourceKind.VARIABLE,
            ReferenceKind.PARAMETER, methodKey + ":parameter:0"),
        declarationEdge(owner, method.child(SyntaxRole.PARAMETER, 1), JavaSourceKind.VARIABLE,
            ReferenceKind.PARAMETER, methodKey + ":parameter:1"));
    assertEquals(declarationLinks, Set.copyOf(aggregate.semanticEdges().stream()
        .filter(link -> declarationLinks.stream().anyMatch(expectedLink -> expectedLink.sourceKey().equals(link.sourceKey())))
        .toList()));
    var repeated = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis());
    var repeatedAggregate = repeated.aggregate().orElseThrow();
    assertEquals(typeTuples(aggregate), typeTuples(repeatedAggregate));
    assertEquals(aggregate.structuralEdges(), repeatedAggregate.structuralEdges());
    assertEquals(aggregate.semanticEdges(), repeatedAggregate.semanticEdges());
    assertEquals(result.diagnostics(), repeated.diagnostics());
  }

  @Test
  void givenTypeParameterOrFormalWithInvalidDeclarationEdges_whenAggregateIsSealed_thenLinksAreRejected() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Declarations.java"), SOURCE);
    var aggregate = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis()).aggregate().orElseThrow();
    var declarations = List.of(
        aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_PARAMETER_DECLARATION).findFirst().orElseThrow(),
        aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.PARAMETER_DECLARATION).findFirst().orElseThrow());

    for (var declaration : declarations) {
      var original = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(declaration.nodeId()))
          .findFirst().orElseThrow();
      var referenceKind = ((SyntaxSemanticTarget.Declaration) original.target()).reference().referenceKind();
      var unrelated = new SyntaxSemanticEdge(declaration.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
          new SyntaxSemanticTarget.Declaration(new SemanticReference(referenceKind, ResolutionStatus.RESOLVED,
              Optional.of("type:unrelated"), Optional.empty(), List.of(), List.of())));
      var missing = new ArrayList<>(aggregate.semanticEdges());
      missing.remove(original);
      var wrong = new ArrayList<>(missing);
      wrong.add(unrelated);
      var extra = new ArrayList<>(aggregate.semanticEdges());
      extra.add(unrelated);

      // Act
      var absentError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, missing));
      var wrongError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, wrong));
      var extraError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, extra));

      // Assert
      assertTrue(absentError.getMessage().contains("declaration reference"));
      assertTrue(wrongError.getMessage().contains("declaration reference"));
      assertTrue(extraError.getMessage().contains("declaration reference"));
    }
  }

  @Test
  void givenResolvedTypeUseWithInvalidDeclarationEdges_whenAggregateIsSealed_thenTargetsAreRejected() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Declarations.java"), SOURCE);
    var aggregate = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis()).aggregate().orElseThrow();
    var typeUse = aggregate.nodes().stream()
        .filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE && node.semanticReference().isPresent())
        .findFirst().orElseThrow();
    var original = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(typeUse.nodeId()))
        .findFirst().orElseThrow();
    var referenceKind = ((SyntaxSemanticTarget.Declaration) original.target()).reference().referenceKind();
    var unrelated = new SyntaxSemanticEdge(typeUse.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(new SemanticReference(referenceKind, ResolutionStatus.RESOLVED,
            Optional.of("type:unrelated"), Optional.empty(), List.of(), List.of())));
    var missing = new ArrayList<>(aggregate.semanticEdges());
    missing.remove(original);
    var wrong = new ArrayList<>(missing);
    wrong.add(unrelated);
    var extra = new ArrayList<>(aggregate.semanticEdges());
    extra.add(unrelated);

    // Act
    var absentError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, missing));
    var wrongError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, wrong));
    var extraError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, extra));

    // Assert
    assertTrue(absentError.getMessage().contains("omit an owned node reference"));
    assertTrue(wrongError.getMessage().contains("omit an owned node reference"));
    assertTrue(extraError.getMessage().contains("type-use declaration reference"));
  }

  @Test
  void givenSourceDeclarationsWithInvalidDeclarationEdges_whenAggregateIsSealed_thenTargetsAreRejected() throws IOException {
    // Arrange
    Files.writeString(sourceRoot.resolve("Declarations.java"), SOURCE + "enum Choice { YES }\n");
    var aggregate = new JavaAnalysisPipeline(new AnalysisEnvironmentCapture(new CompilerDependencyCapture())).analyze(new JavaSourceLoader().discover(sourceRoot), JAVA_25, JavaTranslationPolicy.legacyAnalysis()).aggregate().orElseThrow();
    var declarations = new ArrayList<>(List.of(SyntaxKind.TYPE_DECLARATION, SyntaxKind.METHOD_DECLARATION, SyntaxKind.FIELD_DECLARATION,
        SyntaxKind.ENUM_CONSTANT_DECLARATION, SyntaxKind.RECORD_COMPONENT_DECLARATION).stream()
        .map(kind -> aggregate.nodes().stream().filter(node -> node.syntaxKind() == kind).findFirst().orElseThrow())
        .toList());
    declarations.add(aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.METHOD_DECLARATION
        && node.value(SyntaxValue.ExecutableDeclaration.class).kind() == SyntaxValue.ExecutableDeclaration.ExecutableKind.CONSTRUCTOR)
        .findFirst().orElseThrow());
    for (var declaration : declarations) {
      var original = aggregate.semanticEdges().stream().filter(edge -> edge.sourceKey().equals(declaration.nodeId()))
          .findFirst().orElseThrow();
      var selectedReference = ((SyntaxSemanticTarget.Declaration) original.target()).reference();
      var referenceKind = selectedReference.referenceKind();
      var unrelated = new SyntaxSemanticEdge(declaration.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
          new SyntaxSemanticTarget.Declaration(new SemanticReference(referenceKind, ResolutionStatus.RESOLVED,
              Optional.of("type:unrelated"), Optional.empty(), List.of(), List.of())));
      var incorrectKind = new SyntaxSemanticEdge(declaration.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
          new SyntaxSemanticTarget.Declaration(new SemanticReference(referenceKind == ReferenceKind.TYPE ? ReferenceKind.FIELD
              : ReferenceKind.TYPE, ResolutionStatus.RESOLVED, selectedReference.symbolKey(), Optional.empty(), List.of(), List.of())));
      var missing = new ArrayList<>(aggregate.semanticEdges());
      missing.remove(original);
      var wrong = new ArrayList<>(missing);
      wrong.add(unrelated);
      var wrongKind = new ArrayList<>(missing);
      wrongKind.add(incorrectKind);
      var extra = new ArrayList<>(aggregate.semanticEdges());
      extra.add(unrelated);

      // Act
      var absentError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, missing));
      var wrongError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, wrong));
      var kindError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, wrongKind));
      var extraError = assertThrows(IllegalArgumentException.class, () -> sealWithEdges(aggregate, extra));

      // Assert
      assertTrue(absentError.getMessage().contains("declaration reference"));
      assertTrue(wrongError.getMessage().contains("declaration reference"));
      assertTrue(kindError.getMessage().contains("declaration reference"));
      assertTrue(extraError.getMessage().contains("declaration reference"));
    }
  }

  private static Attachment attachment(String source, SyntaxStructuralPath parent, SyntaxRole role, int ordinal, JavaSourceKind kind,
      String marker, int offset, JavaTypeStructure structure, String targetKey) {
    var start = source.indexOf(marker);
    if (start < 0) {
      throw new IllegalArgumentException("Missing source marker: " + marker);
    }
    var suffix = marker.substring(offset);
    var separator = suffix.indexOf(' ');
    var token = separator < 0 ? suffix : suffix.substring(0, separator);
    if (kind == JavaSourceKind.IDENTIFIER && (token.endsWith(">") || token.endsWith(","))) {
      token = token.substring(0, token.length() - 1);
    }
    var length = token.length();
    return new Attachment(parent, role, ordinal, kind, start + offset, length, structure, Optional.ofNullable(targetKey));
  }

  private static JavaTypeStructure declared(String key) {
    return new JavaTypeStructure.Declared(key, Optional.empty(), List.of());
  }

  private static JavaTypeStructure parameterized(String key, JavaTypeStructure argument) {
    return new JavaTypeStructure.Declared(key, Optional.empty(), List.of(argument));
  }

  private static JavaTypeStructure primitive(JavaTypeCategory kind) {
    return new JavaTypeStructure.Primitive(kind);
  }

  private static boolean isDeclarationTypeEdge(JavaAstAggregate aggregate, SyntaxStructuralEdge edge) {
    var parent = aggregate.node(edge.parentKey()).orElseThrow();
    var child = aggregate.node(edge.childKey()).orElseThrow();
    return child.syntaxKind() == SyntaxKind.TYPE_USE && parent.syntaxKind() != SyntaxKind.TYPE_USE;
  }

  private static SyntaxStructuralEdge edge(String owner, Attachment attachment) {
    var parent = attachment.parent();
    var parentKind = switch (parent.steps().getLast().role()) {
      case TYPE_DECLARATION -> JavaSourceKind.CLASS;
      case TYPE_PARAMETER -> JavaSourceKind.TYPE_PARAMETER;
      case RECORD_COMPONENT, PARAMETER, RECEIVER_PARAMETER -> JavaSourceKind.VARIABLE;
      case MEMBER -> parent.steps().getLast().position() < 3 ? JavaSourceKind.VARIABLE : JavaSourceKind.METHOD;
      default -> throw new IllegalArgumentException("Unexpected declaration type parent: " + parent);
    };
    return new SyntaxStructuralEdge(SyntaxNodeKey.from(owner, parent, parentKind).value(), attachment.role(), attachment.ordinal(),
        SyntaxNodeKey.from(owner, attachment.path(), attachment.kind()).value());
  }

  private static SemanticReference reference(JavaTypeStructure structure, String key) {
    var kind = structure instanceof JavaTypeStructure.Variable ? ReferenceKind.TYPE_PARAMETER : ReferenceKind.TYPE;
    return new SemanticReference(kind, ResolutionStatus.RESOLVED, Optional.of(key), Optional.empty(), List.of(), List.of());
  }

  private static SyntaxSemanticEdge semanticEdge(String owner, Attachment attachment) {
    return new SyntaxSemanticEdge(SyntaxNodeKey.from(owner, attachment.path(), attachment.kind()).value(),
        SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference(attachment.structure(), attachment.targetKey().orElseThrow())));
  }

  private static SyntaxSemanticEdge declarationEdge(String owner, SyntaxStructuralPath path, JavaSourceKind sourceKind,
      ReferenceKind referenceKind, String key) {
    return new SyntaxSemanticEdge(SyntaxNodeKey.from(owner, path, sourceKind).value(),
        SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(new SemanticReference(referenceKind, ResolutionStatus.RESOLVED,
            Optional.of(key), Optional.empty(), List.of(), List.of())));
  }

  private static List<TypeTuple> typeTuples(JavaAstAggregate aggregate) {
    return aggregate.nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.TYPE_USE)
        .map(node -> new TypeTuple(node.nodeId(), node.sourceKind(), node.origin().orElseThrow(), node.type(), node.value(),
            node.semanticReference())).toList();
  }

  private static JavaAstAggregate sealWithEdges(JavaAstAggregate aggregate, List<SyntaxSemanticEdge> edges) {
    var root = aggregate.roots().getFirst();
    return AggregateFixture.builder(aggregate.snapshot()).add(new SyntaxSourceContribution(root.sourceFileKey(), root,
        aggregate.nodes(), aggregate.structuralEdges(), edges)).seal();
  }

  private record TypeTuple(String key, JavaSourceKind sourceKind, SourceSpan origin, SyntaxType type, SyntaxValue value,
      Optional<SemanticReference> reference) {}

  private record Attachment(SyntaxStructuralPath parent, SyntaxRole role, int ordinal, JavaSourceKind kind, int start, int length,
      JavaTypeStructure structure, Optional<String> targetKey) {
    private SyntaxStructuralPath path() {
      return parent.child(role, ordinal);
    }
  }
}
