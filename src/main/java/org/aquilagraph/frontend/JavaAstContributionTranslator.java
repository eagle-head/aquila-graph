package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import org.aquilagraph.model.AnalysisDiagnostic;
import org.aquilagraph.model.ReferenceKind;
import org.aquilagraph.model.ResolutionStatus;
import org.aquilagraph.model.SemanticReference;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceSpan;
import org.aquilagraph.syntax.JavaSourceKind;
import org.aquilagraph.syntax.MethodBodyOrigin;
import org.aquilagraph.syntax.SyntaxChild;
import org.aquilagraph.syntax.SyntaxKind;
import org.aquilagraph.syntax.SyntaxNode;
import org.aquilagraph.syntax.SyntaxRole;
import org.aquilagraph.syntax.SyntaxSemanticEdge;
import org.aquilagraph.syntax.SyntaxSemanticRelation;
import org.aquilagraph.syntax.SyntaxSemanticTarget;
import org.aquilagraph.syntax.SyntaxSourceContribution;
import org.aquilagraph.syntax.SyntaxStructuralPath;
import org.aquilagraph.syntax.SyntaxType;
import org.aquilagraph.syntax.SyntaxValue;

/** Assembles the source-only declaration/body tree for one attributed compilation unit. */
final class JavaAstContributionTranslator {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;

  JavaAstContributionTranslator(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
  }

  UnitTranslation translate(CompilationUnitTree unit, SourceFile sourceFile, List<TranslatedMethodBody> bodies) {
    Objects.requireNonNull(unit, "unit");
    Objects.requireNonNull(sourceFile, "sourceFile");
    Objects.requireNonNull(bodies, "bodies");
    var diagnostics = new ArrayList<AnalysisDiagnostic>();
    var bodyByPath = new HashMap<SyntaxStructuralPath, TranslatedMethodBody>();
    for (var body : bodies) {
      if (body.root().sourceFileKey().equals(sourceFile.sourceFileKey())
          && bodyByPath.putIfAbsent(body.root().structuralPath(), body) != null) {
        throw new IllegalArgumentException("Duplicate translated body path: " + body.root().structuralPath());
      }
    }
    var publishedBodies = new HashMap<String, SyntaxNode>();
    var unavailableBodies = new ArrayList<String>();
    var declarationEdges = new ArrayList<SyntaxSemanticEdge>();
    var rootPath = SyntaxStructuralPath.sourceFileRoot();
    var rootRange = requiredRange(unit, sourceFile, unit, rootPath, diagnostics);
    if (rootRange.isEmpty()) {
      return new UnitTranslation(Optional.empty(), List.of(), diagnostics);
    }

    var unitPath = new TreePath(unit);
    var typeChildren = new ArrayList<SyntaxChild>();
    for (var sourceOrdinal = 0; sourceOrdinal < unit.getTypeDecls().size(); sourceOrdinal++) {
      var declaration = unit.getTypeDecls().get(sourceOrdinal);
      if (declaration instanceof ClassTree type) {
        appendTopLevelType(unit, sourceFile, unitPath, type, rootPath, sourceOrdinal, typeChildren, bodyByPath, publishedBodies,
            unavailableBodies, declarationEdges, diagnostics);
      }
    }
    var root = SyntaxNode.source(sourceFile.sourceFileKey(), rootPath, JavaSourceKind.COMPILATION_UNIT, SyntaxKind.COMPILATION_UNIT,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, typeChildren, Optional.empty(), rootRange.orElseThrow());
    var nodes = root.descendantsAndSelf();
    var structural = nodes.stream().flatMap(node -> node.structuralEdges().stream()).toList();
    var semantic = new ArrayList<>(declarationEdges);
    nodes.forEach(node -> semantic.addAll(node.semanticEdges()));
    var contribution = new SyntaxSourceContribution(sourceFile.sourceFileKey(), root, nodes, structural, semantic);
    var registrations = new ArrayList<BodyRegistration>();
    for (var body : bodies) {
      if (!body.root().sourceFileKey().equals(sourceFile.sourceFileKey())) {
        continue;
      }
      var published = publishedBodies.get(body.methodKey());
      var origin = published != null && sameInstance(published, body.root())
          ? MethodBodyOrigin.SOURCE_AST
          : unavailableBodies.contains(body.methodKey())
              ? MethodBodyOrigin.UNAVAILABLE_SOURCE_POSITION
              : MethodBodyOrigin.EXECUTION_PROJECTION;
      registrations.add(new BodyRegistration(body.methodKey(), body.root(), origin));
    }
    return new UnitTranslation(Optional.of(contribution), registrations, diagnostics);
  }

  private void appendTopLevelType(CompilationUnitTree unit, SourceFile sourceFile, TreePath unitPath, ClassTree declaration,
      SyntaxStructuralPath rootPath, int sourceOrdinal, List<SyntaxChild> children, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var position = children.size();
    var path = rootPath.child(SyntaxRole.TYPE_DECLARATION, position);
    var occurrencePath = rootPath.child(SyntaxRole.TYPE_DECLARATION, sourceOrdinal);
    var declarationPath = new TreePath(unitPath, declaration);
    var translated = type(unit, sourceFile, declarationPath, declaration, path, occurrencePath, bodies, publishedBodies, unavailableBodies,
        semanticEdges, diagnostics);
    translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, position, node)));
  }

  private Optional<SyntaxNode> type(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    if (semantics.isImplicitDeclaration(classPath)) {
      return Optional.empty();
    }
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var members = new ArrayList<SyntaxChild>();
    for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
      var member = declaration.getMembers().get(sourceOrdinal);
      appendMember(unit, sourceFile, classPath, member, path, occurrencePath, sourceOrdinal, members, bodies, publishedBodies,
          unavailableBodies, semanticEdges, diagnostics);
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.valueOf(declaration.getKind().name()),
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, members, Optional.empty(),
        range.orElseThrow());
    declarationEdge(classPath, node, semanticEdges);
    return Optional.of(node);
  }

  private void appendMember(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, Tree member, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, int sourceOrdinal, List<SyntaxChild> members,
      Map<SyntaxStructuralPath, TranslatedMethodBody> bodies, Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies,
      List<SyntaxSemanticEdge> semanticEdges, List<AnalysisDiagnostic> diagnostics) {
    var memberTreePath = new TreePath(classPath, member);
    if (member instanceof MethodTree && semantics.isImplicitDeclaration(memberTreePath)) {
      return;
    }
    var position = members.size();
    var memberPath = typePath.child(SyntaxRole.MEMBER, position);
    var memberOccurrencePath = typeOccurrencePath.child(SyntaxRole.MEMBER, sourceOrdinal);
    Optional<SyntaxNode> translated = switch (member) {
      case MethodTree method -> method(unit, sourceFile, memberTreePath, method, memberPath, memberOccurrencePath, bodies, publishedBodies,
          unavailableBodies, semanticEdges, diagnostics);
      case VariableTree field ->
        field(unit, sourceFile, memberTreePath, field, memberPath, memberOccurrencePath, semanticEdges, diagnostics);
      case ClassTree nested -> type(unit, sourceFile, memberTreePath, nested, memberPath, memberOccurrencePath, bodies, publishedBodies,
          unavailableBodies, semanticEdges, diagnostics);
      default -> Optional.empty();
    };
    translated.ifPresent(node -> members.add(new SyntaxChild(SyntaxRole.MEMBER, position, node)));
  }

  private Optional<SyntaxNode> method(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, MethodTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, Map<SyntaxStructuralPath, TranslatedMethodBody> bodies,
      Map<String, SyntaxNode> publishedBodies, List<String> unavailableBodies, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      if (declaration.getBody() != null) {
        var bodyPath = JavaSyntaxStructuralPath.methodBody(new TreePath(methodPath, declaration.getBody()));
        var body = bodies.get(bodyPath);
        if (body != null) {
          unavailableBodies.add(body.methodKey());
        }
      }
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    if (declaration.getBody() != null) {
      var originalPath = JavaSyntaxStructuralPath.methodBody(new TreePath(methodPath, declaration.getBody()));
      var body = bodies.get(originalPath);
      if (body == null) {
        throw new IllegalStateException("Translated body is unavailable for source method: " + originalPath);
      }
      var projected = sourceBody(body.root(), path.child(SyntaxRole.BODY, 0), methodPath, declaration);
      if (projected.isPresent()) {
        var node = projected.orElseThrow();
        children.add(new SyntaxChild(SyntaxRole.BODY, 0, node));
        publishedBodies.put(body.methodKey(), node);
      } else {
        unavailableBodies.add(body.methodKey());
      }
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, children, Optional.empty(), range.orElseThrow());
    declarationEdge(methodPath, node, semanticEdges);
    return Optional.of(node);
  }

  private Optional<SyntaxNode> field(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, List<SyntaxSemanticEdge> semanticEdges,
      List<AnalysisDiagnostic> diagnostics) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE, SyntaxKind.FIELD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range.orElseThrow());
    declarationEdge(fieldPath, node, semanticEdges);
    return Optional.of(node);
  }

  private Optional<SyntaxNode> sourceBody(SyntaxNode body, SyntaxStructuralPath path, TreePath methodPath, MethodTree declaration) {
    var statements = declaration.getBody().getStatements();
    var bodyPath = new TreePath(methodPath, declaration.getBody());
    var omitInsertedCall = !statements.isEmpty()
        && treeInventory.isCompilerInsertedSuperCall(new TreePath(bodyPath, statements.getFirst()));
    return project(body, path, omitInsertedCall, true);
  }

  private Optional<SyntaxNode> project(SyntaxNode original, SyntaxStructuralPath path, boolean omitInsertedCall, boolean bodyRoot) {
    var range = original.origin();
    if (range.isEmpty()) {
      return Optional.empty();
    }
    var children = new ArrayList<SyntaxChild>();
    var positions = new EnumMap<SyntaxRole, Integer>(SyntaxRole.class);
    var unchanged = original.structuralPath().equals(path);
    for (var child : original.children()) {
      if (bodyRoot && omitInsertedCall && child.role() == SyntaxRole.STATEMENT && child.position() == 0) {
        unchanged = false;
        continue;
      }
      var position = positions.getOrDefault(child.role(), 0);
      var projected = project(child.node(), path.child(child.role(), position), omitInsertedCall, false);
      if (projected.isEmpty()) {
        return Optional.empty();
      }
      var node = projected.orElseThrow();
      children.add(new SyntaxChild(child.role(), position, node));
      positions.put(child.role(), position + 1);
      unchanged &= child.position() == position && sameInstance(node, child.node());
    }
    if (unchanged) {
      return Optional.of(original);
    }
    return Optional.of(SyntaxNode.source(original.sourceFileKey(), path, original.sourceKind(), original.syntaxKind(), original.type(),
        original.value(), children, original.semanticReference(), range.orElseThrow()));
  }

  private static boolean sameInstance(SyntaxNode first, SyntaxNode second) {
    var identity = new IdentityHashMap<SyntaxNode, Boolean>();
    identity.put(first, Boolean.TRUE);
    return identity.containsKey(second);
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      List<AnalysisDiagnostic> diagnostics) {
    return switch (sourceEvidence.position(unit, sourceFile, tree)) {
      case JavaSourceEvidence.Position.Available available -> Optional.of(available.span());
      case JavaSourceEvidence.Position.Missing ignored -> {
        diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile, path, "frontend.missing-source-position",
            "A required source occurrence has no compiler position"));
        yield Optional.empty();
      }
      case JavaSourceEvidence.Position.Invalid ignored -> {
        diagnostics.add(FrontendDiagnosticFactory.sourcePositionError(sourceFile, path, "frontend.invalid-source-range",
            "A required source occurrence has an invalid compiler range"));
        yield Optional.empty();
      }
    };
  }

  private void declarationEdge(TreePath path, SyntaxNode node, List<SyntaxSemanticEdge> edges) {
    var declaration = semantics.element(path);
    if (declaration == null) {
      return;
    }
    var key = semantics.declarationKey(declaration);
    var kind = referenceKind(declaration.getKind());
    if (key.isEmpty() || kind.isEmpty()) {
      return;
    }
    var reference = new SemanticReference(kind.orElseThrow(), ResolutionStatus.RESOLVED, key, Optional.empty(), List.of(), List.of());
    edges.add(new SyntaxSemanticEdge(node.nodeId(), SyntaxSemanticRelation.DECLARATION_REFERENCE,
        new SyntaxSemanticTarget.Declaration(reference)));
  }

  private static Optional<ReferenceKind> referenceKind(ElementKind kind) {
    return switch (kind) {
      case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> Optional.of(ReferenceKind.TYPE);
      case CONSTRUCTOR -> Optional.of(ReferenceKind.CONSTRUCTOR);
      case METHOD -> Optional.of(ReferenceKind.METHOD);
      case FIELD -> Optional.of(ReferenceKind.FIELD);
      case ENUM_CONSTANT -> Optional.of(ReferenceKind.ENUM_CONSTANT);
      default -> Optional.empty();
    };
  }

  record UnitTranslation(Optional<SyntaxSourceContribution> contribution, List<BodyRegistration> bodies,
      List<AnalysisDiagnostic> diagnostics) {
    UnitTranslation {
      Objects.requireNonNull(contribution, "contribution");
      bodies = List.copyOf(bodies);
      diagnostics = List.copyOf(diagnostics);
    }
  }

  record BodyRegistration(String methodKey, SyntaxNode root, MethodBodyOrigin origin) {
  }
}
