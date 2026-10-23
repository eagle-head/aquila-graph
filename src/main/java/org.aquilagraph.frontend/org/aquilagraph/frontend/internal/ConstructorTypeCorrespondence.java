package org.aquilagraph.frontend.internal;

import com.sun.source.tree.ClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.jspecify.annotations.Nullable;

/** Checks unchanged source types across compiler tasks, including capture alpha-renaming. */
final class ConstructorTypeCorrespondence {
  private final Elements originalElements;
  private final Elements witnessElements;
  private final ConstructorIdentityLookup<TypeElement, TypeElement> copiedDeclarations = ConstructorIdentityLookup.usingReferenceIdentity();
  private final ConstructorIdentityLookup<TypeElement, TypeElement> copiedOriginals = ConstructorIdentityLookup.usingReferenceIdentity();
  private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> originals = ConstructorIdentityLookup
      .usingReferenceIdentity();
  private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> witnesses = ConstructorIdentityLookup
      .usingReferenceIdentity();
  private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> pairs = ConstructorIdentityLookup
      .usingReferenceIdentity();

  ConstructorTypeCorrespondence(Elements originalElements, Elements witnessElements) {
    this.originalElements = Objects.requireNonNull(originalElements, "originalElements");
    this.witnessElements = Objects.requireNonNull(witnessElements, "witnessElements");
  }

  boolean matchesArgument(TreePath original, Trees originalTrees, TreePath witness, Trees witnessTrees) throws IOException {
    if (!sourceText(original, originalTrees).equals(sourceText(witness, witnessTrees))) {
      return false;
    }
    var originals = declarations(original, originalTrees);
    var witnesses = declarations(witness, witnessTrees);
    if (originals.size() != witnesses.size()) {
      return false;
    }
    var attempt = new MatchAttempt();
    for (var index = 0; index < originals.size(); index++) {
      var originalDeclaration = originals.get(index);
      var witnessDeclaration = witnesses.get(index);
      if (originalDeclaration.getNestingKind() != witnessDeclaration.getNestingKind()
          || !originalDeclaration.getSimpleName().contentEquals(witnessDeclaration.getSimpleName())) {
        return false;
      }
      attempt.copiedDeclarations.remember(originalDeclaration, witnessDeclaration);
      attempt.copiedOriginals.remember(witnessDeclaration, originalDeclaration);
    }
    return attempt.acceptIfMatched(originalTrees.getTypeMirror(original), witnessTrees.getTypeMirror(witness));
  }

  private static String sourceText(TreePath path, Trees trees) throws IOException {
    var unit = path.getCompilationUnit();
    var positions = trees.getSourcePositions();
    var start = Math.toIntExact(positions.getStartPosition(unit, path.getLeaf()));
    var end = Math.toIntExact(positions.getEndPosition(unit, path.getLeaf()));
    return unit.getSourceFile().getCharContent(true).subSequence(start, end).toString();
  }

  private static List<TypeElement> declarations(TreePath path, Trees trees) {
    var declarations = new ArrayList<TypeElement>();
    new TreePathScanner<Void, Void>() {
      @Override
      public Void visitClass(ClassTree tree, Void ignored) {
        if (trees.getElement(getCurrentPath()) instanceof TypeElement declaration) {
          declarations.add(declaration);
        }
        return super.visitClass(tree, null);
      }
    }.scan(path, null);
    return declarations;
  }

  boolean matches(@Nullable TypeMirror original, @Nullable TypeMirror witness) {
    return new MatchAttempt().acceptIfMatched(original, witness);
  }

  /** Keeps recursive assumptions private until the complete type or argument comparison succeeds. */
  private final class MatchAttempt {
    private final ConstructorIdentityLookup<TypeElement, TypeElement> copiedDeclarations;
    private final ConstructorIdentityLookup<TypeElement, TypeElement> copiedOriginals;
    private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> originals;
    private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> witnesses;
    private final ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> pairs;

    private MatchAttempt() {
      copiedDeclarations = ConstructorIdentityLookup.usingReferenceIdentity();
      copiedOriginals = ConstructorIdentityLookup.usingReferenceIdentity();
      originals = ConstructorIdentityLookup.usingReferenceIdentity();
      witnesses = ConstructorIdentityLookup.usingReferenceIdentity();
      pairs = ConstructorIdentityLookup.usingReferenceIdentity();
    }

    private boolean acceptIfMatched(@Nullable TypeMirror original, @Nullable TypeMirror witness) {
      if (!matches(original, witness)) {
        return false;
      }
      retain(copiedDeclarations, ConstructorTypeCorrespondence.this.copiedDeclarations);
      retain(copiedOriginals, ConstructorTypeCorrespondence.this.copiedOriginals);
      retainPairs(originals, ConstructorTypeCorrespondence.this.originals);
      retainPairs(witnesses, ConstructorTypeCorrespondence.this.witnesses);
      retainPairs(pairs, ConstructorTypeCorrespondence.this.pairs);
      return true;
    }

    private boolean matches(@Nullable TypeMirror original, @Nullable TypeMirror witness) {
      if (original == null || witness == null) {
        return original == null && witness == null;
      }
      if (original.getKind() != witness.getKind() || !annotations(original).equals(annotations(witness))) {
        return false;
      }
      if (paired(ConstructorTypeCorrespondence.this.pairs, original, witness) || paired(pairs, original, witness)) {
        return true;
      }
      row(pairs, original).remember(witness, Boolean.TRUE);
      return switch (original.getKind()) {
        case ARRAY -> matches(((ArrayType) original).getComponentType(), ((ArrayType) witness).getComponentType());
        case DECLARED -> declared((DeclaredType) original, (DeclaredType) witness);
        case TYPEVAR -> variable((TypeVariable) original, (TypeVariable) witness);
        case WILDCARD -> matches(((WildcardType) original).getExtendsBound(), ((WildcardType) witness).getExtendsBound())
            && matches(((WildcardType) original).getSuperBound(), ((WildcardType) witness).getSuperBound());
        case INTERSECTION -> ordered(((IntersectionType) original).getBounds(), ((IntersectionType) witness).getBounds());
        case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, NULL, NONE, VOID -> true;
        case ERROR, EXECUTABLE, PACKAGE, MODULE, UNION, OTHER -> false;
      };
    }

    private boolean declared(DeclaredType original, DeclaredType witness) {
      if (!(original.asElement() instanceof TypeElement originalOwner) || !(witness.asElement() instanceof TypeElement witnessOwner)) {
        return false;
      }
      var copied = copiedDeclarations.find(originalOwner)
          .or(() -> ConstructorTypeCorrespondence.this.copiedDeclarations.find(originalOwner));
      return (copied.isPresent()
          ? copied.orElseThrow().equals(witnessOwner)
          : originalElements.getBinaryName(originalOwner).contentEquals(witnessElements.getBinaryName(witnessOwner)))
          && originalElements.getModuleOf(originalOwner).getQualifiedName()
              .contentEquals(witnessElements.getModuleOf(witnessOwner).getQualifiedName())
          && matches(original.getEnclosingType(), witness.getEnclosingType())
          && ordered(original.getTypeArguments(), witness.getTypeArguments());
    }

    private boolean variable(TypeVariable original, TypeVariable witness) {
      var originalElement = original.asElement();
      var witnessElement = witness.asElement();
      return bind(ConstructorTypeCorrespondence.this.originals, originals, original, witness)
          && bind(ConstructorTypeCorrespondence.this.witnesses, witnesses, witness, original)
          && originalElement.getKind() == witnessElement.getKind()
          && originalElement.getSimpleName().contentEquals(witnessElement.getSimpleName())
          && matches(original.getUpperBound(), witness.getUpperBound()) && matches(original.getLowerBound(), witness.getLowerBound());
    }

    private boolean ordered(List<? extends TypeMirror> original, List<? extends TypeMirror> witness) {
      if (original.size() != witness.size()) {
        return false;
      }
      for (var index = 0; index < original.size(); index++) {
        if (!matches(original.get(index), witness.get(index))) {
          return false;
        }
      }
      return true;
    }
  }

  private static boolean bind(ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> accepted,
      ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> provisional, TypeMirror first,
      TypeMirror second) {
    var bound = provisional.find(first).or(() -> accepted.find(first));
    if (bound.isPresent()) {
      return bound.orElseThrow().find(second).isPresent();
    }
    var identity = ConstructorIdentityLookup.<TypeMirror, Boolean>usingReferenceIdentity();
    identity.remember(second, Boolean.TRUE);
    provisional.remember(first, identity);
    return true;
  }

  private static ConstructorIdentityLookup<TypeMirror, Boolean> row(
      ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> table, TypeMirror key) {
    var existing = table.find(key);
    if (existing.isPresent()) {
      return existing.orElseThrow();
    }
    var value = ConstructorIdentityLookup.<TypeMirror, Boolean>usingReferenceIdentity();
    table.remember(key, value);
    return value;
  }

  private static boolean paired(ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> table,
      TypeMirror original, TypeMirror witness) {
    return table.find(original).flatMap(row -> row.find(witness)).isPresent();
  }

  private static <K, V> void retain(ConstructorIdentityLookup<K, V> provisional, ConstructorIdentityLookup<K, V> accepted) {
    for (var entry : provisional.entries()) {
      accepted.remember(entry.key(), entry.value());
    }
  }

  private static void retainPairs(ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> provisional,
      ConstructorIdentityLookup<TypeMirror, ConstructorIdentityLookup<TypeMirror, Boolean>> accepted) {
    for (var pair : provisional.entries()) {
      for (var witness : pair.value().entries()) {
        row(accepted, pair.key()).remember(witness.key(), witness.value());
      }
    }
  }

  Optional<TypeMirror> originalType(TypeMirror witness, Types originalTypes, Types witnessTypes) {
    for (var pair : pairs.entries()) {
      for (var entry : pair.value().entries()) {
        var candidate = entry.key();
        if (annotations(candidate).equals(annotations(witness)) && witnessTypes.isSameType(candidate, witness)) {
          return Optional.of(pair.key());
        }
      }
    }
    if (!witness.getAnnotationMirrors().isEmpty()) {
      return Optional.empty();
    }
    return switch (witness.getKind()) {
      case TYPEVAR -> Optional.empty();
      case ARRAY -> originalType(((ArrayType) witness).getComponentType(), originalTypes, witnessTypes).map(originalTypes::getArrayType);
      case DECLARED -> originalDeclared((DeclaredType) witness, originalTypes, witnessTypes);
      case WILDCARD -> originalWildcard((WildcardType) witness, originalTypes, witnessTypes);
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE -> Optional.of(originalTypes.getPrimitiveType(witness.getKind()));
      case NULL -> Optional.of(originalTypes.getNullType());
      case NONE, VOID -> Optional.of(originalTypes.getNoType(witness.getKind()));
      case INTERSECTION, ERROR, EXECUTABLE, PACKAGE, MODULE, UNION, OTHER -> Optional.empty();
    };
  }

  private Optional<TypeMirror> originalDeclared(DeclaredType witness, Types originalTypes, Types witnessTypes) {
    if (!(witness.asElement() instanceof TypeElement witnessOwner)) {
      return Optional.empty();
    }
    var copied = copiedOriginals.find(witnessOwner);
    if (copied.isPresent()) {
      return Optional.of(copied.orElseThrow().asType());
    }
    var moduleName = witnessElements.getModuleOf(witnessOwner).getQualifiedName();
    var module = originalElements.getModuleElement(moduleName);
    var owner = module == null ? null : originalElements.getTypeElement(module, witnessOwner.getQualifiedName());
    if (owner == null || !originalElements.getBinaryName(owner).contentEquals(witnessElements.getBinaryName(witnessOwner))) {
      return Optional.empty();
    }
    var arguments = new TypeMirror[witness.getTypeArguments().size()];
    for (var index = 0; index < arguments.length; index++) {
      var argument = originalType(witness.getTypeArguments().get(index), originalTypes, witnessTypes);
      if (argument.isEmpty()) {
        return Optional.empty();
      }
      arguments[index] = argument.orElseThrow();
    }
    var enclosing = witness.getEnclosingType();
    if (enclosing instanceof DeclaredType) {
      var originalEnclosing = originalType(enclosing, originalTypes, witnessTypes);
      return originalEnclosing.filter(DeclaredType.class::isInstance)
          .map(value -> originalTypes.getDeclaredType((DeclaredType) value, owner, arguments));
    }
    return Optional.of(originalTypes.getDeclaredType(owner, arguments));
  }

  private Optional<TypeMirror> originalWildcard(WildcardType witness, Types originalTypes, Types witnessTypes) {
    var upper = witness.getExtendsBound();
    var lower = witness.getSuperBound();
    var originalUpper = upper == null ? Optional.<TypeMirror>empty() : originalType(upper, originalTypes, witnessTypes);
    var originalLower = lower == null ? Optional.<TypeMirror>empty() : originalType(lower, originalTypes, witnessTypes);
    if ((upper != null && originalUpper.isEmpty()) || (lower != null && originalLower.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(originalTypes.getWildcardType(originalUpper.orElse(null), originalLower.orElse(null)));
  }

  private static List<String> annotations(TypeMirror type) {
    return type.getAnnotationMirrors().stream().map(Object::toString).toList();
  }

  static boolean containsVariable(TypeMirror type) {
    return switch (type.getKind()) {
      case TYPEVAR -> true;
      case DECLARED -> {
        var declared = (DeclaredType) type;
        yield containsVariable(declared.getEnclosingType())
            || declared.getTypeArguments().stream().anyMatch(ConstructorTypeCorrespondence::containsVariable);
      }
      case ARRAY -> containsVariable(((ArrayType) type).getComponentType());
      case INTERSECTION -> ((IntersectionType) type).getBounds().stream().anyMatch(ConstructorTypeCorrespondence::containsVariable);
      case WILDCARD -> {
        var wildcard = (WildcardType) type;
        var upper = wildcard.getExtendsBound();
        var lower = wildcard.getSuperBound();
        yield (upper != null && containsVariable(upper)) || (lower != null && containsVariable(lower));
      }
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, NULL, NONE, VOID, ERROR, EXECUTABLE, PACKAGE, MODULE, UNION, OTHER ->
        false;
    };
  }

  static boolean retainsWitnessParameter(TypeMirror type, ExecutableElement declaration) {
    return retainsWitnessParameter(type, declaration, ConstructorIdentityLookup.usingReferenceIdentity());
  }

  private static boolean retainsWitnessParameter(TypeMirror type, ExecutableElement declaration,
      ConstructorIdentityLookup<TypeMirror, Boolean> visited) {
    if (visited.find(type).isPresent()) {
      return false;
    }
    visited.remember(type, Boolean.TRUE);
    return switch (type.getKind()) {
      case TYPEVAR -> {
        var variable = (TypeVariable) type;
        yield Objects.equals(variable.asElement().getEnclosingElement(), declaration)
            || retainsWitnessParameter(variable.getUpperBound(), declaration, visited)
            || retainsWitnessParameter(variable.getLowerBound(), declaration, visited);
      }
      case DECLARED -> {
        var declared = (DeclaredType) type;
        yield retainsWitnessParameter(declared.getEnclosingType(), declaration, visited)
            || declared.getTypeArguments().stream().anyMatch(argument -> retainsWitnessParameter(argument, declaration, visited));
      }
      case ARRAY -> retainsWitnessParameter(((ArrayType) type).getComponentType(), declaration, visited);
      case INTERSECTION ->
        ((IntersectionType) type).getBounds().stream().anyMatch(bound -> retainsWitnessParameter(bound, declaration, visited));
      case WILDCARD -> {
        var wildcard = (WildcardType) type;
        var upper = wildcard.getExtendsBound();
        var lower = wildcard.getSuperBound();
        yield (upper != null && retainsWitnessParameter(upper, declaration, visited))
            || (lower != null && retainsWitnessParameter(lower, declaration, visited));
      }
      case BOOLEAN, BYTE, SHORT, INT, LONG, CHAR, FLOAT, DOUBLE, NULL, NONE, VOID, ERROR, EXECUTABLE, PACKAGE, MODULE, UNION, OTHER ->
        false;
    };
  }
}
