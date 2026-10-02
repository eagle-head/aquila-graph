package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.UnionTypeTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.model.AttributedCoverageExpectations;
import org.aquilagraph.model.CoveragePropertyValue;
import org.aquilagraph.model.CoveragePropertyValue.Item;
import org.aquilagraph.model.CoveragePropertyValue.Provenance;
import org.aquilagraph.model.CoveragePropertyValue.State;
import org.aquilagraph.model.InputSnapshot;
import org.aquilagraph.model.LanguageFeature;
import org.aquilagraph.model.LanguageFeatureCategory;
import org.aquilagraph.model.LanguageFeatureCoverage;
import org.aquilagraph.model.SourceContext;
import org.aquilagraph.model.SourceContext.Partition;
import org.aquilagraph.model.SourceCoverageInventory;
import org.aquilagraph.model.SourceFile;
import org.aquilagraph.model.SourceOwnerPath.Role;

/** Captures written then attributed obligations before either corresponding output producer runs. */
final class JavaSourceCoverageCapture {
  private static final Set<Role> RECURSIVE_TYPE_ROLES = Set.of(Role.TYPE_NAME, Role.TYPE_ARGUMENT, Role.COMPONENT_TYPE,
      Role.UNDERLYING_TYPE, Role.UPPER_BOUND, Role.LOWER_BOUND, Role.INTERSECTION_BOUND, Role.UNION_ALTERNATIVE, Role.TYPE_QUALIFIER);
  private final SourceCoverageInventory writtenInventory;
  private final Map<SourceContext.Address, SemanticDescriptor> descriptors;
  private final JavaSourceEvidence sourceEvidence;

  private JavaSourceCoverageCapture(SourceCoverageInventory inventory, Map<SourceContext.Address, SemanticDescriptor> descriptors,
      JavaSourceEvidence sourceEvidence) {
    writtenInventory = inventory;
    this.descriptors = Map.copyOf(descriptors);
    this.sourceEvidence = sourceEvidence;
  }

  static JavaSourceCoverageCapture capture(InputSnapshot snapshot, ParsedJavaSource parsed, SnapshotSourceCatalog sources,
      LanguageFeatureCoverage coverage) {
    var owners = JavaCoverageTopology.owners(parsed.units(), sources, parsed.sourceEvidence());
    var contexts = new ArrayList<SourceContext>();
    for (var owner : owners) {
      contexts.add(new SourceContext(address(owner, Partition.TREE, "tree", 0),
          Optional.of(new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, owner.tree().getKind().name())),
          owner.origin().stream().toList(), Optional.empty(), false));
    }
    var descriptors = new LinkedHashMap<SourceContext.Address, SemanticDescriptor>();
    var typeUses = new ArrayList<SourceContext>();
    captureHeaderTypes(parsed, sources, owners, contexts, descriptors, typeUses);
    captureCatchUnionTypeUses(owners, typeUses);
    contexts.addAll(typeUses);
    for (var owner : owners) {
      JavaCoverageTopology.surface(owner.tree()).ifPresent(surface -> {
        for (var accessor : JavaCoveragePropertyProjection.accessors(surface, coverage)) {
          var value = JavaCoveragePropertyProjection.query(accessor, owner.tree(), owner, parsed.sourceEvidence(), Optional.empty());
          contexts.add(new SourceContext(address(owner, Partition.WRITTEN_PROPERTY, accessor.classification().feature().identifier(), 0),
              Optional.of(accessor.classification().feature()), owner.origin().stream().toList(), Optional.of(value), false));
        }
      });
    }
    for (var owner : owners) {
      var surface = owner.tree() instanceof ClassTree
          ? TypeElement.class
          : owner.tree() instanceof MethodTree ? ExecutableElement.class : null;
      if (surface == null) {
        continue;
      }
      for (var accessor : JavaCoveragePropertyProjection.accessors(surface, coverage)) {
        var address = address(owner, Partition.ATTRIBUTED_PROPERTY, accessor.classification().feature().identifier(), 0);
        contexts.add(new SourceContext(address, Optional.of(accessor.classification().feature()), owner.origin().stream().toList(),
            Optional.empty(), true));
        descriptors.put(address, descriptor(owner, Optional.of(accessor), owner.treePath()));
      }
    }
    return new JavaSourceCoverageCapture(new SourceCoverageInventory(snapshot, coverage.release(), coverage.contractIdentity(), contexts),
        descriptors, parsed.sourceEvidence());
  }

  static SourceCoverageInventory empty(InputSnapshot snapshot, LanguageFeatureCoverage coverage) {
    return new SourceCoverageInventory(snapshot, coverage.release(), coverage.contractIdentity(), List.of());
  }

  SourceCoverageInventory inventory() {
    return writtenInventory;
  }

  AttributedCoverageExpectations captureAttributed(SemanticResolver semantics) {
    var projections = new ArrayList<AttributedCoverageExpectations.Projection>();
    for (var context : writtenInventory.contexts()) {
      if (!context.requiresAttribution()) {
        continue;
      }
      projections.add(captureProjection(context, semantics));
    }
    return new AttributedCoverageExpectations(writtenInventory, projections);
  }

  private AttributedCoverageExpectations.Projection captureProjection(SourceContext context, SemanticResolver semantics) {
    var descriptor = Objects.requireNonNull(descriptors.get(context.address()), "captured semantic descriptor");
    CoveragePropertyValue value;
    Optional<String> ownerIdentity = Optional.empty();
    if (descriptor.accessor().isEmpty()) {
      value = typeValue(semantics.attributedType(descriptor.path()));
    } else {
      var element = semantics.element(descriptor.path());
      ownerIdentity = element == null ? Optional.empty() : semantics.declarationKey(element);
      value = element instanceof TypeElement || element instanceof ExecutableElement
          ? JavaCoveragePropertyProjection.query(descriptor.accessor().orElseThrow(), element, descriptor.owner(), sourceEvidence,
              Optional.of(semantics))
          : new CoveragePropertyValue(State.UNAVAILABLE, List.of());
    }
    return new AttributedCoverageExpectations.Projection(context.address(), value, ownerIdentity);
  }

  private static CoveragePropertyValue typeValue(JavaTypeAttribution attribution) {
    var kind = switch (attribution) {
      case JavaTypeAttribution.Resolved resolved -> Optional.of(resolved.kind().name());
      case JavaTypeAttribution.NoType noType -> Optional.of(noType.kind().name());
      case JavaTypeAttribution.Erroneous ignored -> Optional.of("ERROR");
      case JavaTypeAttribution.Unsupported ignored -> Optional.of("OTHER");
      case JavaTypeAttribution.Unresolved ignored -> Optional.<String>empty();
    };
    return kind.map(value -> new CoveragePropertyValue(State.SCALAR, List.of(new Item("type-kind", value, Provenance.WRITTEN))))
        .orElseGet(() -> new CoveragePropertyValue(State.UNAVAILABLE, List.of()));
  }

  private static void captureHeaderTypes(ParsedJavaSource parsed, SnapshotSourceCatalog sources, List<JavaCoverageTopology.Owner> owners,
      List<SourceContext> contexts, Map<SourceContext.Address, SemanticDescriptor> descriptors, List<SourceContext> typeUses) {
    for (var unit : parsed.units()) {
      var source = sources.find(unit).orElseThrow();
      captureHeaderUnit(parsed, owners, contexts, descriptors, typeUses, unit, source);
    }
  }

  private static void captureHeaderUnit(ParsedJavaSource parsed, List<JavaCoverageTopology.Owner> owners, List<SourceContext> contexts,
      Map<SourceContext.Address, SemanticDescriptor> descriptors, List<SourceContext> typeUses, CompilationUnitTree unit,
      SourceFile source) {
    new TreePathScanner<Void, SourceFile>() {

      @Override
      public Void visitCompilationUnit(CompilationUnitTree tree, SourceFile file) {
        tree.getTypeDecls().stream().filter(ClassTree.class::isInstance).forEach(declaration -> scan(declaration, file));
        return null;
      }

      @Override
      public Void visitClass(ClassTree tree, SourceFile file) {
        capture(tree.getExtendsClause());
        tree.getImplementsClause().forEach(this::capture);
        tree.getPermitsClause().forEach(this::capture);
        tree.getTypeParameters().forEach(parameter -> scan(parameter, file));
        tree.getMembers().stream()
            .filter(member -> member instanceof ClassTree || member instanceof MethodTree || member instanceof VariableTree)
            .forEach(member -> scan(member, file));
        return null;
      }

      @Override
      public Void visitMethod(MethodTree tree, SourceFile file) {
        capture(tree.getReturnType());
        if (tree.getReceiverParameter() != null) {
          scan(tree.getReceiverParameter(), file);
        }
        tree.getThrows().forEach(this::capture);
        tree.getTypeParameters().forEach(parameter -> scan(parameter, file));
        tree.getParameters().stream().filter(
            parameter -> !JavaCoverageTopology.isDerivedCompactParameter(parameter, getCurrentPath(), unit, file, parsed.sourceEvidence()))
            .forEach(parameter -> scan(parameter, file));
        return null;
      }

      @Override
      public Void visitVariable(VariableTree tree, SourceFile file) {
        if (parsed.treeInventory().isWritten(tree)) {
          capture(tree.getType());
        }
        return null;
      }

      @Override
      public Void visitTypeParameter(TypeParameterTree tree, SourceFile file) {
        tree.getBounds().forEach(this::capture);
        return null;
      }

      private void capture(Tree tree) {
        if (tree == null || !parsed.treeInventory().isWritten(tree)) {
          return;
        }
        var path = new TreePath(getCurrentPath(), tree);
        var owner = owners.stream().filter(candidate -> samePath(candidate.treePath(), path)).findFirst()
            .orElseThrow(() -> new IllegalStateException("Written header type is absent from its exact parsed owner topology"));
        var step = owner.path().lastStep().orElseThrow();
        var address = address(owner, Partition.TYPE, step.role().name(), step.ordinal());
        contexts.add(new SourceContext(address, Optional.empty(), owner.origin().stream().toList(), Optional.empty(), true));
        descriptors.put(address, new SemanticDescriptor(owner, Optional.empty(), path));
        captureWrittenTypeUses(owner, owners, typeUses);
      }
    }.scan(unit, source);
  }

  private static void captureWrittenTypeUses(JavaCoverageTopology.Owner owner, List<JavaCoverageTopology.Owner> owners,
      List<SourceContext> contexts) {
    contexts.add(new SourceContext(address(owner, Partition.WRITTEN_TYPE_USE, "written-type-use", 0), Optional.empty(),
        owner.origin().stream().toList(), Optional.empty(), false));
    for (var child : JavaParsedChildRoles.children(owner.tree())) {
      if (!RECURSIVE_TYPE_ROLES.contains(child.step().role())) {
        continue;
      }
      var childOwner = writtenTypeChild(owner, child.tree(), owners);
      captureWrittenTypeUses(childOwner, owners, contexts);
    }
  }

  private static void captureCatchUnionTypeUses(List<JavaCoverageTopology.Owner> owners, List<SourceContext> contexts) {
    for (var owner : owners) {
      var parent = owner.treePath().getParentPath();
      if (owner.tree() instanceof VariableTree variable && variable.getType() instanceof UnionTypeTree && parent != null
          && parent.getLeaf() instanceof CatchTree) {
        captureWrittenTypeUses(writtenTypeChild(owner, variable.getType(), owners), owners, contexts);
      }
    }
  }

  private static JavaCoverageTopology.Owner writtenTypeChild(JavaCoverageTopology.Owner owner, Tree tree,
      List<JavaCoverageTopology.Owner> owners) {
    var path = new TreePath(owner.treePath(), tree);
    return owners.stream().filter(candidate -> samePath(candidate.treePath(), path)).findFirst()
        .orElseThrow(() -> new IllegalStateException("Recursive written type syntax requires its exact parsed parent role"));
  }

  private static SemanticDescriptor descriptor(JavaCoverageTopology.Owner owner, Optional<JavaCoveragePropertyProjection.Accessor> accessor,
      TreePath path) {
    return new SemanticDescriptor(owner, accessor, path);
  }

  private static boolean samePath(TreePath first, TreePath second) {
    var left = first;
    var right = second;
    while (left != null && right != null) {
      if (!JavaProjectionIdentity.sameInstance(left.getLeaf(), right.getLeaf())) {
        return false;
      }
      left = left.getParentPath();
      right = right.getParentPath();
    }
    return left == null && right == null;
  }

  private static SourceContext.Address address(JavaCoverageTopology.Owner owner, Partition partition, String role, int ordinal) {
    return new SourceContext.Address(owner.source().sourceFileKey(), partition, owner.path(), role, ordinal);
  }

  private record SemanticDescriptor(JavaCoverageTopology.Owner owner, Optional<JavaCoveragePropertyProjection.Accessor> accessor,
      TreePath path) {
  }
}
