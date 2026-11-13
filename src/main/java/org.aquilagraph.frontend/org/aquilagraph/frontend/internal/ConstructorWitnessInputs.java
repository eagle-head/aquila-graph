package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import org.aquilagraph.frontend.internal.ConstructorWitnessFileManager.Replacement;

/** Builds private source and binary inputs using the attributed original context. */
final class ConstructorWitnessInputs {
  private final SnapshotSourceCatalog sources;
  private final StandardJavaFileManager borrowedManager;
  private final Trees originalTrees;
  private final Types originalTypes;
  private final Elements originalElements;
  private final Function<CompilationUnitTree, JavaSourceTokens> lexicalView;

  ConstructorWitnessInputs(SnapshotSourceCatalog sources, StandardJavaFileManager borrowedManager, Trees trees, Types types,
      Elements elements, Function<CompilationUnitTree, JavaSourceTokens> lexicalView) {
    this.sources = Objects.requireNonNull(sources, "sources");
    this.borrowedManager = Objects.requireNonNull(borrowedManager, "borrowedManager");
    originalTrees = Objects.requireNonNull(trees, "trees");
    originalTypes = Objects.requireNonNull(types, "types");
    originalElements = Objects.requireNonNull(elements, "elements");
    this.lexicalView = Objects.requireNonNull(lexicalView, "lexicalView");
  }

  Optional<Input> assemble(ConstructorOccurrence occurrence, boolean expanded) throws IOException {
    var path = occurrence.path();
    var creation = occurrence.creation();
    var selected = occurrence.constructor();
    var owner = occurrence.owner();
    var created = occurrence.created();
    if (creation.getClassBody() != null && creation.getArguments().isEmpty()) {
      return Optional.empty();
    }
    var name = uniqueName(owner, "constructorSelection");
    var diamond = creation.getClassBody() == null && creation.getIdentifier() instanceof ParameterizedTypeTree parameterized
        && parameterized.getTypeArguments().isEmpty();
    var notional = new ConstructorNotionalMethod(selected, owner, originalTypes, originalElements, name, diamond,
        creation.getArguments().size(), expanded);
    var projection = Optional.<ConstructorReceiverProjection>empty();
    if (creation.getEnclosingExpression() != null) {
      if (!(owner.getEnclosingElement() instanceof TypeElement enclosingOwner)) {
        return Optional.empty();
      }
      projection = Optional.of(new ConstructorReceiverProjection(owner, created, uniqueName(enclosingOwner, "constructorReceiver"), diamond,
          originalTypes, originalElements));
    }
    var original = path.getCompilationUnit();
    var content = original.getSourceFile().getCharContent(true).toString();
    var enumConstant = path.getParentPath() != null && originalTrees.getElement(path.getParentPath()) != null
        && originalTrees.getElement(path.getParentPath()).getKind() == ElementKind.ENUM_CONSTANT;
    if (enumConstant && creation.getArguments().isEmpty()) {
      return Optional.empty();
    }
    var replaced = creation.getClassBody() != null && projection.isPresent()
        ? creation.getEnclosingExpression()
        : creation.getClassBody() == null && !enumConstant ? creation : creation.getArguments().getFirst();
    var start = position(original, replaced, false);
    var end = position(original, replaced, true);
    if (start < 0 || end < start) {
      return Optional.empty();
    }
    var edits = new ArrayList<SourceEdit>();
    var adaptations = new ArrayList<Replacement>();
    if (!addWitnessDeclaration(owner, notional.sourceMethod(), notional::adaptBinary, edits, adaptations)
        || (projection.isPresent() && !addWitnessDeclaration(projection.orElseThrow().owner(), projection.orElseThrow().sourceMethod(),
            projection.orElseThrow()::adaptBinary, edits, adaptations))) {
      return Optional.empty();
    }
    var selectionExpression = replacement(original, creation, created, content, name, diamond, expanded,
        selected.getParameters().size() - 1, projection, edits);
    var proof = creation.getClassBody() != null || enumConstant;
    // Only the original expression arm completes normally; proof arguments cannot change its flow.
    var expression = proof
        ? "switch (0) { case 1 -> { " + selectionExpression + "; throw null; } default -> " + sourceText(original, replaced, content, edits)
            + "; }"
        : selectionExpression;
    var replacement = proof && projection.isPresent() ? "(" + expression + ")" : expression;
    edits.addFirst(new SourceEdit(original.getSourceFile(), start, end, replacement));
    var selection = new WitnessCall(originalElements.getBinaryName(owner).toString(), name);
    var receiver = projection
        .map(value -> new ReceiverProof(new WitnessCall(originalElements.getBinaryName(value.owner()).toString(), value.name()),
            sourceText(original, creation.getEnclosingExpression(), content, edits.subList(1, edits.size()))));
    return Optional
        .of(new Input(editedSources(edits), adaptations, replacementRange(edits), new WitnessSelection(selection, receiver, expanded)));
  }

  private boolean addWitnessDeclaration(TypeElement owner, String method, Function<byte[], byte[]> adapt, List<SourceEdit> edits,
      List<Replacement> adaptations) throws IOException {
    var declaration = originalTrees.getPath(owner);
    if (declaration != null && declaration.getLeaf() instanceof ClassTree classTree) {
      var ownerUnit = declaration.getCompilationUnit();
      var ownerStart = position(ownerUnit, classTree, false);
      var ownerEnd = position(ownerUnit, classTree, true);
      var delimiter = lexicalView.apply(ownerUnit).closingBrace(ownerStart, ownerEnd);
      if (ownerStart < 0 || delimiter.isEmpty()) {
        return false;
      }
      var insertion = delimiter.orElseThrow().start();
      edits.add(new SourceEdit(ownerUnit.getSourceFile(), insertion, insertion, method));
    } else {
      var binary = binaryInput(owner);
      if (binary.isEmpty()) {
        return false;
      }
      var input = binary.orElseThrow();
      byte[] adapted;
      try (var stream = input.openInputStream()) {
        adapted = adapt.apply(stream.readAllBytes());
      }
      adaptations.add(new Replacement(originalElements.getBinaryName(owner).toString(), input.toUri(), adapted));
    }
    return true;
  }

  private static ReplacementRange replacementRange(List<SourceEdit> edits) {
    var replacement = edits.getFirst();
    var shift = edits.stream().filter(edit -> edit.input().toUri().equals(replacement.input().toUri()) && edit.end() <= replacement.start())
        .mapToInt(edit -> edit.replacement().length() - edit.end() + edit.start()).sum();
    var start = replacement.start() + shift;
    return new ReplacementRange(replacement.input().toUri(), start, start + replacement.replacement().length());
  }

  private List<JavaFileObject> editedSources(List<SourceEdit> edits) throws IOException {
    var inputs = new ArrayList<JavaFileObject>();
    for (var input : sources.compilationUnits()) {
      inputs.add(editedSource(input, edits));
    }
    return inputs;
  }

  private static JavaFileObject editedSource(JavaFileObject input, List<SourceEdit> edits) throws IOException {
    var content = new StringBuilder(input.getCharContent(true));
    var matching = edits.stream().filter(edit -> edit.input().toUri().equals(input.toUri()))
        .filter(edit -> edit.start() != edit.end() || edits.stream()
            .noneMatch(parent -> parent.input().toUri().equals(input.toUri()) && parent.start() != parent.end()
                && parent.start() <= edit.start() && edit.end() <= parent.end()))
        .sorted(Comparator.comparingInt(SourceEdit::start).reversed()).toList();
    for (var edit : matching) {
      content.replace(edit.start(), edit.end(), edit.replacement());
    }
    return SimpleJavaFileObject.forSource(input.toUri(), content.toString());
  }

  private String replacement(CompilationUnitTree unit, NewClassTree creation, DeclaredType created, String content, String name,
      boolean diamond, boolean expanded, int prefix, Optional<ConstructorReceiverProjection> projection, List<SourceEdit> edits) {
    var renderer = new ConstructorTypeRenderer(originalElements, Map.of());
    String qualifier;
    if (projection.isPresent()) {
      var memberArguments = creation.getIdentifier() instanceof ParameterizedTypeTree parameterized
          && !parameterized.getTypeArguments().isEmpty()
              ? parameterized.getTypeArguments().stream().map(argument -> sourceText(unit, argument, content, edits))
                  .collect(Collectors.joining(",", "<", ">"))
              : !diamond && !created.getTypeArguments().isEmpty()
                  ? created.getTypeArguments().stream().map(renderer::source).collect(Collectors.joining(",", "<", ">"))
                  : "";
      qualifier = "(" + sourceText(unit, creation.getEnclosingExpression(), content, edits) + ")." + memberArguments
          + projection.orElseThrow().name() + "()";
    } else {
      qualifier = "((" + (diamond ? renderer.diamondReceiver(created) : renderer.source(created)) + ")null)";
    }
    var arguments = new ArrayList<>(creation.getArguments().stream().map(argument -> sourceText(unit, argument, content, edits)).toList());
    if (expanded) {
      arguments.add(prefix, "null");
    }
    var explicit = creation.getTypeArguments().isEmpty()
        ? ""
        : creation.getTypeArguments().stream().map(argument -> sourceText(unit, argument, content, edits))
            .collect(Collectors.joining(",", "<", ">"));
    return qualifier + "." + explicit + name + "(" + String.join(",", arguments) + ")";
  }

  private String sourceText(CompilationUnitTree unit, Tree tree, String content, List<SourceEdit> declarations) {
    var start = position(unit, tree, false);
    var end = position(unit, tree, true);
    var copied = new StringBuilder(content.substring(start, end));
    var contained = declarations.stream()
        .filter(edit -> edit.input().toUri().equals(unit.getSourceFile().toUri()) && start <= edit.start() && edit.end() <= end)
        .sorted(Comparator.comparingInt(SourceEdit::start).reversed()).toList();
    for (var edit : contained) {
      copied.replace(edit.start() - start, edit.end() - start, edit.replacement());
    }
    return copied.toString();
  }

  private int position(CompilationUnitTree unit, Tree tree, boolean end) {
    var positions = originalTrees.getSourcePositions();
    var value = end ? positions.getEndPosition(unit, tree) : positions.getStartPosition(unit, tree);
    return Math.toIntExact(value);
  }

  private Optional<JavaFileObject> binaryInput(TypeElement owner) throws IOException {
    var name = originalElements.getBinaryName(owner).toString();
    var module = originalElements.getModuleOf(owner);
    if (!module.isUnnamed()) {
      var moduleName = module.getQualifiedName().toString();
      for (var location : List.of(StandardLocation.MODULE_PATH, StandardLocation.SYSTEM_MODULES)) {
        if (borrowedManager.hasLocation(location)) {
          var moduleLocation = borrowedManager.getLocationForModule(location, moduleName);
          if (moduleLocation != null) {
            var input = borrowedManager.getJavaFileForInput(moduleLocation, name, JavaFileObject.Kind.CLASS);
            if (input != null) {
              return Optional.of(input);
            }
          }
        }
      }
    }
    return Optional.ofNullable(borrowedManager.getJavaFileForInput(StandardLocation.CLASS_PATH, name, JavaFileObject.Kind.CLASS));
  }

  private String uniqueName(TypeElement owner, String prefix) {
    var names = originalElements.getAllMembers(owner).stream().map(element -> element.getSimpleName().toString()).toList();
    var name = prefix;
    for (var index = 0; names.contains(name); index++) {
      name = prefix + index;
    }
    return name;
  }

  private record SourceEdit(JavaFileObject input, int start, int end, String replacement) {
  }

  record Input(List<JavaFileObject> sources, List<Replacement> binaries, ReplacementRange replacementRange, WitnessSelection selection) {
    Input {
      sources = List.copyOf(sources);
      binaries = List.copyOf(binaries);
    }
  }

  record WitnessSelection(WitnessCall call, Optional<ReceiverProof> receiver, boolean expanded) {
  }

  record WitnessCall(String binaryOwner, String name) {
  }

  record ReceiverProof(WitnessCall call, String projectedSource) {
  }

  record ReplacementRange(URI uri, int start, int end) {
    boolean contains(URI source, long position) {
      return uri.equals(source) && start <= position && position < end;
    }
  }
}
