package org.aquilagraph.frontend.internal;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

/** Renders source declarations through explicit lexical collaborators. */
final class JavaDeclarationProjector {
  private final JavaTranslationPolicy policy;
  private static final String DIAGNOSTIC_PRODUCER = "JavaAstContributionTranslator";
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory treeInventory;
  private final JavaDeclarationHeaderProjector headers;
  private final JavaWrittenSourceAssociations sourceAssociations;
  private final JavaDeclarationScope declarations;
  private final JavaBodySourceCorrelation bodyCorrelation;
  private final JavaInitializationProjector initializations;

  private record ProjectionContext(CompilationUnitTree unit, SourceFile sourceFile, Map<String, JavaType> typesByKey,
      DiagnosticEmissionCollector diagnosticBatches) {
  }

  JavaDeclarationProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory treeInventory,
      JavaDeclarationHeaderProjector headers, JavaTranslationPolicy policy, JavaWrittenSourceAssociations sourceAssociations,
      JavaDeclarationScope declarations, JavaBodySourceCorrelation bodyCorrelation) {
    this.policy = Objects.requireNonNull(policy, "policy");
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.treeInventory = Objects.requireNonNull(treeInventory, "treeInventory");
    this.headers = Objects.requireNonNull(headers, "headers");
    this.sourceAssociations = sourceAssociations;
    this.declarations = declarations;
    this.bodyCorrelation = bodyCorrelation;
    initializations = new JavaInitializationProjector(semantics, sourceEvidence, treeInventory, policy, sourceAssociations,
        bodyCorrelation);
  }

  private JavaDeclarationProjector child() {
    var associations = sourceAssociations.child();
    var scope = declarations.child();
    var correlation = bodyCorrelation.child(associations, scope);
    return new JavaDeclarationProjector(semantics, sourceEvidence, treeInventory, headers.child(associations, scope::fieldAnnotations),
        policy, associations, scope, correlation);
  }

  private void accept() {
    sourceAssociations.accept();
    declarations.accept();
    bodyCorrelation.accept();
  }

  void appendTopLevelType(CompilationUnitTree unit, SourceFile sourceFile, TreePath unitPath, ClassTree declaration,
      SyntaxStructuralPath rootPath, int sourceOrdinal, List<SyntaxChild> children, DiagnosticEmissionCollector diagnostics,
      Map<String, JavaType> typesByKey) {
    var position = children.size();
    var path = rootPath.child(SyntaxRole.TYPE_DECLARATION, position);
    var occurrencePath = rootPath.child(SyntaxRole.TYPE_DECLARATION, sourceOrdinal);
    var declarationPath = new TreePath(unitPath, declaration);
    var translated = type(unit, sourceFile, declarationPath, declaration, path, occurrencePath, diagnostics, typesByKey);
    translated.ifPresent(node -> children.add(new SyntaxChild(SyntaxRole.TYPE_DECLARATION, position, node)));
  }

  private Optional<SyntaxNode> type(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, DiagnosticEmissionCollector diagnostics,
      Map<String, JavaType> typesByKey) {
    var operation = child();
    var result = operation.renderType(unit, sourceFile, classPath, declaration, path, occurrencePath, operation.declarations, diagnostics,
        typesByKey);
    if (result.isPresent()) {
      operation.accept();
    } else {
      operation.declarations.refuse();
      operation.bodyCorrelation.refuse();
    }
    return result;
  }

  private Optional<SyntaxNode> renderType(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, ClassTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, JavaDeclarationScope semanticEdges,
      DiagnosticEmissionCollector diagnostics, Map<String, JavaType> typesByKey) {
    var identity = headers.identifyType(unit, sourceFile, classPath, declaration, occurrencePath, diagnostics);
    if (identity.isEmpty()) {
      return Optional.empty();
    }
    var header = headers.typeHeader(unit, sourceFile, classPath, declaration, path, occurrencePath, identity.orElseThrow(), diagnostics,
        typesByKey);
    var typeElement = identity.orElseThrow().element();
    var children = new ArrayList<>(header.contributions().children());
    retainReferences(header.contributions(), semanticEdges);
    var complete = header.contributions().complete();
    var members = new ArrayList<SyntaxChild>();
    var emptyDeclarations = policy.supportsDeclarationFidelity() ? treeInventory.emptyBodyDeclarations(declaration) : List.<SourceSpan>of();
    var emptyOrdinal = 0;
    for (var sourceOrdinal = 0; sourceOrdinal < declaration.getMembers().size(); sourceOrdinal++) {
      var member = declaration.getMembers().get(sourceOrdinal);
      if (!treeInventory.isWritten(member) || member instanceof EmptyStatementTree) {
        continue;
      }
      if (declaration.getKind() == Tree.Kind.RECORD && member instanceof VariableTree field
          && typeElement.getRecordComponents().stream().anyMatch(component -> component.getSimpleName().contentEquals(field.getName()))) {
        continue;
      }
      var memberStart = sourceEvidence.rawStart(unit, member);
      while (emptyOrdinal < emptyDeclarations.size() && emptyDeclarations.get(emptyOrdinal).startOffset() < memberStart) {
        appendEmptyDeclaration(sourceFile, classPath, path, emptyOrdinal, emptyDeclarations.get(emptyOrdinal), members);
        emptyOrdinal++;
      }
      var before = members.size();
      appendMember(unit, sourceFile, classPath, member, path, occurrencePath, sourceOrdinal, SyntaxRole.MEMBER, members, diagnostics,
          typesByKey);
      complete &= members.size() == before + 1;
    }
    while (emptyOrdinal < emptyDeclarations.size()) {
      appendEmptyDeclaration(sourceFile, classPath, path, emptyOrdinal, emptyDeclarations.get(emptyOrdinal), members);
      emptyOrdinal++;
    }
    if (!complete) {
      return Optional.empty();
    }
    children.addAll(members);
    var value = headers.completeType(declaration, header, members.size());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.valueOf(declaration.getKind().name()),
        SyntaxKind.TYPE_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value, children, Optional.empty(), identity.orElseThrow().range());
    sourceAssociations.created(node, classPath);
    declarationEdge(classPath, node, semanticEdges);
    return Optional.of(node);
  }

  private void appendEmptyDeclaration(SourceFile sourceFile, TreePath classPath, SyntaxStructuralPath typePath, int ordinal,
      SourceSpan range, List<SyntaxChild> members) {
    var position = members.size();
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), typePath.child(SyntaxRole.MEMBER, position), JavaSourceKind.EMPTY_STATEMENT,
        SyntaxKind.EMPTY, SyntaxType.NotApplicable.INSTANCE, SyntaxValue.None.INSTANCE, List.of(), Optional.empty(), range);
    sourceAssociations.retain(node, treeInventory.emptyDeclarationAssociation(node, classPath, ordinal));
    members.add(new SyntaxChild(SyntaxRole.MEMBER, position, node));
  }

  void appendMember(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, Tree member, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, int sourceOrdinal, SyntaxRole role, List<SyntaxChild> members,
      DiagnosticEmissionCollector diagnostics, Map<String, JavaType> typesByKey) {
    var operation = child();
    var size = members.size();
    operation.renderMember(unit, sourceFile, classPath, member, typePath, typeOccurrencePath, sourceOrdinal, role, members,
        operation.bodyCorrelation.publication(), operation.declarations, diagnostics, typesByKey);
    if (members.size() > size) {
      operation.accept();
    } else {
      operation.declarations.refuse();
      operation.bodyCorrelation.refuse();
    }
  }

  private void renderMember(CompilationUnitTree unit, SourceFile sourceFile, TreePath classPath, Tree member, SyntaxStructuralPath typePath,
      SyntaxStructuralPath typeOccurrencePath, int sourceOrdinal, SyntaxRole role, List<SyntaxChild> members,
      JavaBodySourceCorrelation.Publication publication, JavaDeclarationScope semanticEdges, DiagnosticEmissionCollector diagnostics,
      Map<String, JavaType> typesByKey) {
    var memberTreePath = new TreePath(classPath, member);
    if (!treeInventory.isWritten(member)) {
      return;
    }
    if (semantics.isImplicitDeclaration(memberTreePath)) {
      diagnostics.emit(DIAGNOSTIC_PRODUCER,
          FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.inconsistent-member-origin",
              "A written member has a compiler-generated declaration origin", sourceEvidence.origin(unit, sourceFile, member)));
      return;
    }
    var position = members.size();
    var memberPath = typePath.child(role, position);
    var memberOccurrencePath = typeOccurrencePath.child(role, sourceOrdinal);
    Optional<SyntaxNode> translated = switch (member) {
      case MethodTree method -> method(unit, sourceFile, memberTreePath, method, memberPath, memberOccurrencePath, publication,
          semanticEdges, diagnostics, typesByKey);
      case VariableTree field -> field(unit, sourceFile, memberTreePath, field, memberPath, memberOccurrencePath, semanticEdges,
          diagnostics, new ProjectionContext(unit, sourceFile, typesByKey, diagnostics));
      case ClassTree nested -> type(unit, sourceFile, memberTreePath, nested, memberPath, memberOccurrencePath, diagnostics, typesByKey);
      case BlockTree initializer -> initializations.initializer(unit, sourceFile, memberTreePath, initializer, memberPath,
          memberOccurrencePath, diagnostics, (nested, nestedPath, nestedSourcePath) -> projectNestedDeclaration(
              new ProjectionContext(unit, sourceFile, typesByKey, diagnostics), nested, nestedPath, nestedSourcePath));
      case EmptyStatementTree _ -> Optional.empty();
      default -> {
        diagnostics.emit(DIAGNOSTIC_PRODUCER,
            FrontendDiagnosticFactory.otherError(sourceFile.sourceFileKey(), "frontend.unclassified-type-member",
                "A written type member has no source AST declaration form", sourceEvidence.origin(unit, sourceFile, member)));
        yield Optional.empty();
      }
    };
    translated.ifPresent(node -> members.add(new SyntaxChild(role, position, node)));
  }

  private Optional<SyntaxNode> method(CompilationUnitTree unit, SourceFile sourceFile, TreePath methodPath, MethodTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, JavaBodySourceCorrelation.Publication publication,
      JavaDeclarationScope semanticEdges, DiagnosticEmissionCollector diagnostics, Map<String, JavaType> typesByKey) {
    var range = requiredRange(unit, sourceFile, declaration, occurrencePath, diagnostics);
    var body = declaration.getBody() == null
        ? null
        : bodyCorrelation.body(JavaSyntaxStructuralPath.executionRoot(new TreePath(methodPath, declaration.getBody()))).orElse(null);
    if (range.isEmpty()) {
      if (body != null) {
        publication.unavailable(body.methodKey());
      }
      return Optional.empty();
    }
    var selected = headers.identifyExecutable(unit, sourceFile, methodPath, declaration, range, diagnostics);
    if (selected.isEmpty()) {
      if (body != null) {
        publication.unavailable(body.methodKey());
      }
      return Optional.empty();
    }
    var identity = selected.orElseThrow();
    var executable = identity.element();
    var methodKey = identity.key();
    var constructor = identity.constructor();
    var stagedPublication = publication;
    var stagedEdges = semanticEdges;
    if (policy.supportsNestedDeclarations() && declaration.getBody() != null && body == null) {
      var context = JavaExecutableProjectionContext.canonicalMethod(policy,
          (nested, nestedPath, nestedSourcePath) -> projectNestedDeclaration(
              new ProjectionContext(unit, sourceFile, typesByKey, diagnostics), nested, nestedPath, nestedSourcePath));
      var translator = new JavaMethodBodyTranslator(semantics, sourceEvidence, treeInventory, context);
      var translated = translator.translate(unit, sourceFile, methodKey, declaration.getBody(),
          new TreePath(methodPath, declaration.getBody()), constructor, false);
      body = translated.value();
      bodyCorrelation.addBody(body);
      diagnostics.include(translated.emissions());
    }
    var projectedHeader = headers.signature(unit, sourceFile, methodPath, declaration, path, occurrencePath, identity, diagnostics);
    if (projectedHeader.isEmpty()) {
      if (body != null) {
        publication.unavailable(body.methodKey());
      }
      return Optional.empty();
    }
    var header = projectedHeader.orElseThrow();
    var children = new ArrayList<>(header.contributions().children());
    retainReferences(header.contributions(), stagedEdges);
    var complete = header.contributions().complete();
    if (declaration.getBody() != null) {
      if (body == null) {
        throw new IllegalStateException("Translated body is unavailable for source method: " + methodPath);
      }
      var projected = bodyCorrelation.sourceBody(body, path.child(SyntaxRole.BODY, 0), methodPath, declaration, unit, sourceFile,
          diagnostics);
      if (projected.isPresent()) {
        var node = projected.orElseThrow();
        children.add(new SyntaxChild(SyntaxRole.BODY, 0, node));
        stagedPublication.publish(body.methodKey(), node);
      } else {
        complete = false;
      }
    }
    var projectedDefault = headers.completeDefault(unit, sourceFile, declaration, executable, path, diagnostics);
    projectedDefault.value().ifPresent(value -> children.add(new SyntaxChild(SyntaxRole.DEFAULT_VALUE, 0, value.node())));
    var defaultValue = projectedDefault.value().map(JavaDeclarationHeaderProjector.DefaultValue::value);
    complete &= projectedDefault.complete();
    if (!complete) {
      if (body != null) {
        publication.unavailable(body.methodKey());
      }
      return Optional.empty();
    }
    var value = headers.completeExecutable(sourceFile, header, defaultValue, diagnostics);
    if (value.isEmpty()) {
      if (body != null) {
        publication.unavailable(body.methodKey());
      }
      return Optional.empty();
    }
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.METHOD, SyntaxKind.METHOD_DECLARATION,
        SyntaxType.NotApplicable.INSTANCE, value.orElseThrow(), children, Optional.empty(), range.orElseThrow());
    sourceAssociations.created(node, methodPath);
    declarationEdge(methodPath, node, stagedEdges);
    return Optional.of(node);
  }

  private Optional<SyntaxNode> field(CompilationUnitTree unit, SourceFile sourceFile, TreePath fieldPath, VariableTree declaration,
      SyntaxStructuralPath path, SyntaxStructuralPath occurrencePath, JavaDeclarationScope semanticEdges,
      DiagnosticEmissionCollector diagnostics, ProjectionContext context) {
    var projected = headers.field(unit, sourceFile, fieldPath, declaration, path, occurrencePath, diagnostics);
    if (projected.isEmpty()) {
      return Optional.empty();
    }
    var header = projected.orElseThrow();
    var value = header.value();
    var children = new ArrayList<>(header.children());
    var enumConstant = value.kind() == SyntaxValue.VariableDeclaration.Kind.ENUM_CONSTANT;
    var initialization = initializations.fieldChildren(unit, sourceFile, fieldPath, declaration, value, path, diagnostics,
        (nested, nestedPath, nestedSourcePath) -> projectNestedDeclaration(context, nested, nestedPath, nestedSourcePath));
    if (initialization.isEmpty()) {
      return Optional.empty();
    }
    children.addAll(initialization.orElseThrow().children());
    var node = SyntaxNode.source(sourceFile.sourceFileKey(), path, JavaSourceKind.VARIABLE,
        enumConstant ? SyntaxKind.ENUM_CONSTANT_DECLARATION : SyntaxKind.FIELD_DECLARATION, SyntaxType.NotApplicable.INSTANCE, value,
        children, Optional.empty(), header.range());
    sourceAssociations.created(node, fieldPath);
    declarationEdge(fieldPath, node, semanticEdges);
    initializations.retain(initialization.orElseThrow(), node);
    return Optional.of(node);
  }

  private TranslationResult<SyntaxNode> projectNestedDeclaration(ProjectionContext context, ClassTree declaration, TreePath treePath,
      SyntaxStructuralPath path) {
    var diagnostics = context.diagnosticBatches().newBatch();
    final Optional<SyntaxNode> node;
    try {
      node = type(context.unit(), context.sourceFile(), treePath, declaration, path, path, diagnostics, context.typesByKey());
    } catch (JavaSourceProjectionFailure failure) {
      throw new JavaSourceProjectionFailure(diagnostics.snapshot(), failure);
    }
    if (node.isEmpty()) {
      throw new JavaSourceProjectionFailure(diagnostics.snapshot());
    }
    return new TranslationResult<>(node.orElseThrow(), diagnostics.snapshot());
  }

  private static void retainReferences(JavaDeclarationHeaderProjector.Contributions contributions, JavaDeclarationScope scope) {
    contributions.references().forEach(reference -> scope.add(reference.source(), reference.reference()));
  }

  private Optional<SourceSpan> requiredRange(CompilationUnitTree unit, SourceFile sourceFile, Tree tree, SyntaxStructuralPath path,
      DiagnosticEmissionCollector diagnostics) {
    return sourceEvidence.requiredRange(unit, sourceFile, tree, path, diagnostics).value();
  }

  private void declarationEdge(TreePath path, SyntaxNode node, JavaDeclarationScope edges) {
    JavaDeclarationReference.attributed(semantics, path, node).ifPresent(reference -> edges.add(reference.source(), reference.reference()));
  }
}
