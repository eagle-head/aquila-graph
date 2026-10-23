package org.aquilagraph.frontend.internal;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.WrittenTypeEvidence;

/** Copies binding identity and written type shape before compiler objects leave the producer. */
final class JavaLocalBindingProjector {
  private final SemanticResolver semantics;
  private final JavaSourceEvidence sourceEvidence;
  private final ParsedTreeInventory inventory;

  JavaLocalBindingProjector(SemanticResolver semantics, JavaSourceEvidence sourceEvidence, ParsedTreeInventory inventory) {
    this.semantics = Objects.requireNonNull(semantics, "semantics");
    this.sourceEvidence = Objects.requireNonNull(sourceEvidence, "sourceEvidence");
    this.inventory = Objects.requireNonNull(inventory, "inventory");
  }

  SyntaxValue.LocalDeclaration project(CompilationUnitTree unit, SourceFile source, VariableTree variable, TreePath path) {
    var typeForm = inventory.bindingTypeForm(variable);
    var writtenType = typeForm == LocalBindingEvidence.TypeForm.DECLARED ? variable.getType() : null;
    var typeEvidence = writtenType == null
        ? Optional.<WrittenTypeEvidence>empty()
        : sourceEvidence.origin(unit, source, writtenType)
            .map(range -> new WrittenTypeEvidence(JavaSourceKind.valueOf(writtenType.getKind().name()), range,
                semantics.explicitType(new TreePath(path, writtenType))));
    var element = Optional.ofNullable(semantics.element(path));
    var context = context(path);
    var name = variable.getName().length() == 0 ? "_" : variable.getName().toString();
    return new SyntaxValue.LocalDeclaration(name, typeEvidence,
        inventory.writtenModifiers(variable.getModifiers()).stream().map(Object::toString).sorted().toList(),
        Optional.of(new LocalBindingEvidence(element.flatMap(semantics::declarationKey), typeForm, context,
            variable.getInitializer() != null, 0, List.of())));
  }

  private static LocalBindingEvidence.Context context(TreePath path) {
    var parentPath = path.getParentPath();
    if (parentPath == null) {
      return LocalBindingEvidence.Context.LOCAL;
    }
    return switch (parentPath.getLeaf().getKind()) {
      case CATCH -> LocalBindingEvidence.Context.CATCH;
      case ENHANCED_FOR_LOOP -> LocalBindingEvidence.Context.ENHANCED_FOR;
      case TRY -> LocalBindingEvidence.Context.RESOURCE;
      case LAMBDA_EXPRESSION -> LocalBindingEvidence.Context.LAMBDA;
      case BINDING_PATTERN -> LocalBindingEvidence.Context.PATTERN;
      default -> LocalBindingEvidence.Context.LOCAL;
    };
  }
}
