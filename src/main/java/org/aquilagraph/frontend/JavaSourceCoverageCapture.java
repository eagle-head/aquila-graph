package org.aquilagraph.frontend;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeParameterTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.AnyPatternTree;
import org.aquilagraph.source.api.CoverageActualFacts;
import org.aquilagraph.source.api.SyntaxType;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoveragePropertyValue.Item;
import org.aquilagraph.source.api.CoveragePropertyValue.Provenance;
import org.aquilagraph.source.api.CoveragePropertyValue.State;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceContext.Partition;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath.Role;

/** Captures written then attributed obligations before either corresponding output producer runs. */
final class JavaSourceCoverageCapture {
  private static final Set<Role> RECURSIVE_TYPE_ROLES = Set.of(Role.TYPE_NAME, Role.TYPE_ARGUMENT, Role.COMPONENT_TYPE,
      Role.UNDERLYING_TYPE, Role.UPPER_BOUND, Role.LOWER_BOUND, Role.INTERSECTION_BOUND, Role.UNION_ALTERNATIVE, Role.TYPE_QUALIFIER);
  private static final Set<Role> BODY_TYPE_ROLES = Set.of(Role.DECLARED_TYPE, Role.CAST_TYPE, Role.CONSTRUCTED_TYPE, Role.TYPE_ARGUMENT,
      Role.ARRAY_ELEMENT_TYPE, Role.TESTED_TYPE, Role.DECONSTRUCTOR);
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
    return capture(snapshot, parsed, sources, coverage, false);
  }

  static JavaSourceCoverageCapture capture(InputSnapshot snapshot, ParsedJavaSource parsed, SnapshotSourceCatalog sources,
      LanguageFeatureCoverage coverage, boolean enumInitialization) {
    return capture(snapshot, parsed, sources, coverage, enumInitialization, false);
  }

  static JavaSourceCoverageCapture capture(InputSnapshot snapshot, ParsedJavaSource parsed, SnapshotSourceCatalog sources,
      LanguageFeatureCoverage coverage, boolean enumInitialization, boolean declarationFidelity) {
    var owners = JavaCoverageTopology.owners(parsed.units(), sources, parsed.sourceEvidence()).stream()
        .filter(owner -> !enumInitialization || !JavaEnumConstantSyntax.isImplicitEnvelope(owner.treePath(), parsed.sourceEvidence()))
        .toList();
    var contexts = new ArrayList<SourceContext>();
    for (var owner : owners) {
      contexts.add(new SourceContext(address(owner, Partition.TREE, "tree", 0),
          Optional.of(new LanguageFeature(LanguageFeatureCategory.COMPILER_TREE_KIND, owner.tree().getKind().name())),
          owner.origin().stream().toList(), Optional.empty(), false));
    }
    var descriptors = new LinkedHashMap<SourceContext.Address, SemanticDescriptor>();
    var typeUses = new ArrayList<SourceContext>();
    captureHeaderTypes(parsed, sources, owners, contexts, descriptors, typeUses);
    for (var owner : owners) {
      if (owner.tree() instanceof AnyPatternTree) {
        var address = address(owner, Partition.TYPE, CoverageActualFacts.MATCH_ALL_TYPE_ROLE, 0);
        contexts.add(new SourceContext(address, Optional.empty(), owner.origin().stream().toList(), Optional.empty(), true));
        descriptors.put(address, descriptor(owner, Optional.empty(), owner.treePath()));
      }
    }
    captureBodyTypeUses(parsed, owners, typeUses);
    contexts.addAll(typeUses);
    for (var owner : owners) {
      JavaCoverageTopology.surface(owner.tree()).ifPresent(surface -> {
        for (var accessor : JavaCoveragePropertyProjection.accessors(surface, coverage)) {
          var value = JavaCoveragePropertyProjection.query(accessor, owner.tree(), owner, parsed.sourceEvidence(), Optional.empty(),
              enumInitialization);
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
    if (declarationFidelity) {
      for (var owner : owners) {
        if (!(owner.tree() instanceof ClassTree declaration) || owner.origin().isEmpty()) {
          continue;
        }
        var emptyDeclarations = parsed.treeInventory().emptyBodyDeclarations(declaration);
        for (var ordinal = 0; ordinal < emptyDeclarations.size(); ordinal++) {
          var range = emptyDeclarations.get(ordinal);
          var path = parsed.treeInventory().emptyDeclarationPath(owner.treePath(), ordinal);
          contexts.add(new SourceContext(
              new SourceContext.Address(range.sourceFileKey(), Partition.EMPTY_BODY_DECLARATION, path, "empty-body-declaration", ordinal),
              Optional.empty(), List.of(range), Optional.empty(), false));
        }
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
      if (CoverageActualFacts.MATCH_ALL_TYPE_ROLE.equals(context.address().role())) {
        var type = semantics.type(descriptor.path());
        value = type instanceof SyntaxType.Resolved
            ? CoverageActualFacts.patternTypeValue(type)
            : typeValue(semantics.attributedType(descriptor.path()));
      } else {
        value = typeValue(semantics.attributedType(descriptor.path()));
      }
    } else {
      var element = semantics.element(descriptor.path());
      ownerIdentity = element == null ? Optional.empty() : semantics.declarationKey(element);
      value = ownerIdentity.isPresent() && (element instanceof TypeElement || element instanceof ExecutableElement)
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
      case JavaTypeAttribution.Erroneous _ -> Optional.of("ERROR");
      case JavaTypeAttribution.Unsupported _ -> Optional.of("OTHER");
      case JavaTypeAttribution.Unresolved _ -> Optional.<String>empty();
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
        tree.getMembers().stream().filter(member -> member instanceof ClassTree || member instanceof MethodTree
            || member instanceof VariableTree || member instanceof BlockTree).forEach(member -> scan(member, file));
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
        if (tree.getBody() != null) {
          JavaNestedDeclarationTraversal.scan(new TreePath(getCurrentPath(), tree.getBody()), path -> scan(path, file));
        }
        return null;
      }

      @Override
      public Void visitVariable(VariableTree tree, SourceFile file) {
        if (parsed.treeInventory().isWritten(tree)) {
          if (!JavaEnumConstantSyntax.isConstant(getCurrentPath(), parsed.sourceEvidence())
              || owners.stream().anyMatch(owner -> JavaProjectionIdentity.sameInstance(owner.tree(), tree.getType()))) {
            capture(tree.getType());
          }
        }
        if (tree.getInitializer() != null) {
          JavaNestedDeclarationTraversal.scan(new TreePath(getCurrentPath(), tree.getInitializer()), path -> scan(path, file));
        }
        return null;
      }

      @Override
      public Void visitBlock(BlockTree tree, SourceFile file) {
        JavaNestedDeclarationTraversal.scan(getCurrentPath(), path -> scan(path, file));
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

  private static void captureBodyTypeUses(ParsedJavaSource parsed, List<JavaCoverageTopology.Owner> owners, List<SourceContext> contexts) {
    for (var owner : owners) {
      for (var child : JavaParsedChildRoles.children(owner.tree())) {
        var classOperand = owner.tree() instanceof MemberSelectTree selection && selection.getIdentifier().contentEquals("class")
            && child.step().role() == Role.TYPE_QUALIFIER;
        if ((!BODY_TYPE_ROLES.contains(child.step().role()) && !classOperand) || !parsed.treeInventory().isWritten(child.tree())) {
          continue;
        }
        var childPath = Objects.requireNonNull(TreePath.getPath(owner.treePath(), child.tree()), "parsed type child path");
        if (JavaEnumConstantSyntax.isImplicitEnvelope(childPath, parsed.sourceEvidence())
            && owners.stream().noneMatch(candidate -> samePath(candidate.treePath(), childPath))) {
          continue;
        }
        var childOwner = writtenTypeChild(owner, child.tree(), owners);
        var address = address(childOwner, Partition.WRITTEN_TYPE_USE, "written-type-use", 0);
        if (contexts.stream().noneMatch(context -> context.address().equals(address))) {
          captureWrittenTypeUses(childOwner, owners, contexts);
        }
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
