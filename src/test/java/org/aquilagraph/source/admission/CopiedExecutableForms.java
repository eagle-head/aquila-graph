package org.aquilagraph.source.admission;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.aquilagraph.source.admission.CopiedAstValues.Body;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxValue.None;
import org.aquilagraph.source.api.SyntaxValue.SwitchCase;
import org.aquilagraph.source.api.SyntaxValue.SwitchCaseForm;
import org.aquilagraph.source.api.SyntaxValue.SwitchLabel;

/** Supplies intrinsically typed excluded forms while preserving independent compiler observations. */
public final class CopiedExecutableForms {
  private CopiedExecutableForms() {
  }

  public static CopiedAstValues capture(JavaAstAggregate original) {
    var copied = CopiedAstValues.capture(original);
    var replacements = new IdentityHashMap<SyntaxNode, SyntaxNode>();
    var sources = copied.sources().stream().map(source -> new SyntaxSourceContribution(source.sourceFileKey(),
        reclassify(source.root(), replacements), source.nodes().stream().map(node -> reclassify(node, replacements)).toList(),
        source.structuralEdges(), source.semanticEdges())).toList();
    var bodies = copied.bodies().stream().map(body -> new Body(body.methodKey(), reclassify(body.root(), replacements), body.origin(),
        body.uses(), body.invocations(), body.staticAccesses(), body.concatenations(), body.constructors(), body.attributedOccurrences())).toList();
    return new CopiedAstValues(copied.snapshot(), sources, bodies, copied.memberOrders(), copied.associations(), copied.declarationTypes());
  }

  private static SyntaxNode reclassify(SyntaxNode node, Map<SyntaxNode, SyntaxNode> replacements) {
    var existing = replacements.get(node);
    if (existing != null) {
      return existing;
    }
    var children = node.children().stream()
        .map(child -> new SyntaxChild(child.role(), child.position(), reclassify(child.node(), replacements))).toList();
    var kind = node.syntaxKind();
    var value = node.value();
    if (kind == SyntaxKind.UNSUPPORTED && node.sourceKind() == JavaSourceKind.TRY) {
      kind = SyntaxKind.TRY;
      value = None.INSTANCE;
    } else if (kind == SyntaxKind.UNSUPPORTED && node.sourceKind() == JavaSourceKind.CASE) {
      kind = SyntaxKind.CASE;
      var labels = children.stream().filter(child -> child.role() == SyntaxRole.LABEL)
          .<SwitchLabel>map(child -> new SwitchLabel.Expression(child.position())).toList();
      value = new SwitchCase(SwitchCaseForm.STATEMENT_GROUP, labels.isEmpty() ? List.of(SwitchLabel.Default.INSTANCE) : labels);
    }
    var replacement = node.origin().isPresent()
        ? SyntaxNode.source(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), kind, node.type(), value, children,
            node.semanticReference(), node.origin().orElseThrow())
        : SyntaxNode.withoutDirectSource(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), kind, node.type(), value, children,
            node.semanticReference());
    replacements.put(node, replacement);
    return replacement;
  }
}
