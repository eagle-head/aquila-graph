package org.aquilagraph.frontend.internal;

import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import org.aquilagraph.frontend.internal.ConstructorCorrelation.ArgumentFacts;
import org.aquilagraph.frontend.internal.ConstructorCorrelation.SelectedConstructorFacts;
import org.aquilagraph.frontend.internal.ConstructorWitnessCompiler.Attribution;
import org.aquilagraph.frontend.internal.ConstructorWitnessInputs.WitnessCall;
import org.aquilagraph.frontend.internal.ConstructorWitnessInputs.WitnessSelection;
import org.aquilagraph.source.api.JavaTypeIdentity;

/** Matches original and attributed constructor evidence before copying semantic facts. */
final class ConstructorWitnessCorrelation {
  private static final int SELECTED_CALL_COUNT = 1;
  private final Trees originalTrees;
  private final Types originalTypes;
  private final Elements originalElements;
  private final SemanticResolver originalSemantics;
  private final JavaTranslationPolicy policy;

  ConstructorWitnessCorrelation(Trees trees, Types types, Elements elements, SemanticResolver semantics, JavaTranslationPolicy policy) {
    originalTrees = trees;
    originalTypes = types;
    originalElements = elements;
    originalSemantics = semantics;
    this.policy = policy;
  }

  ConstructorCorrelation attributedSelection(ConstructorOccurrence occurrence, ExecutableType executable) {
    var arguments = occurrence.creation().getArguments().stream()
        .map(argument -> originalTrees.getTypeMirror(new TreePath(occurrence.path(), argument))).toList();
    if (arguments.stream().anyMatch(Objects::isNull)) {
      return ConstructorCorrelation.unavailable();
    }
    var identities = arguments.stream().map(originalSemantics::typeIdentity).toList();
    return new JavaInvocationApplicability(originalSemantics).select(executable, occurrence.constructor().isVarArgs(), arguments)
        .flatMap(selection -> copyFacts(originalSemantics, selection, arguments, identities,
            type -> Optional.of(originalSemantics.typeIdentity(type))))
        .map(ConstructorCorrelation::selected).orElseGet(ConstructorCorrelation::unavailable);
  }

  ConstructorCorrelation correlate(ConstructorOccurrence occurrence, WitnessSelection witness, Attribution attribution) throws IOException {
    var path = occurrence.path();
    var creation = occurrence.creation();
    var expanded = witness.expanded();
    var trees = attribution.trees();
    var resolver = new SemanticResolver(trees, attribution.types(), attribution.elements(),
        new DeclarationIdentityRegistry(trees, attribution.types(), attribution.elements(), policy));
    var calls = witnessCalls(attribution, witness.call());
    if (calls.size() != SELECTED_CALL_COUNT) {
      return ConstructorCorrelation.unavailable();
    }
    var call = calls.getFirst();
    var returned = trees.getTypeMirror(call);
    var correspondence = new ConstructorTypeCorrespondence(originalElements, attribution.elements());
    if (witness.receiver().isPresent()) {
      var receiver = witness.receiver().orElseThrow();
      var receivers = witnessCalls(attribution, receiver.call());
      if (receivers.size() != SELECTED_CALL_COUNT) {
        return ConstructorCorrelation.unavailable();
      }
      var receiverPath = receivers.getFirst();
      var receiverCall = (MethodInvocationTree) receiverPath.getLeaf();
      var receiverCorrespondence = new ConstructorTypeCorrespondence(originalElements, attribution.elements());
      if (!(receiverCall.getMethodSelect() instanceof MemberSelectTree select)
          || !(select.getExpression() instanceof ParenthesizedTree expression)
          || !receiverCorrespondence.matchesCapturedReceiver(new TreePath(path, creation.getEnclosingExpression()), originalTrees,
              originalTypes, new TreePath(new TreePath(new TreePath(receiverPath, select), expression), expression.getExpression()), trees,
              attribution.types(), receiver.projectedSource())) {
        return ConstructorCorrelation.unavailable();
      }
      correspondence.retainDeclarations(receiverCorrespondence);
    }
    if (!(call.getLeaf() instanceof MethodInvocationTree method)
        || !(trees.getElement(call) instanceof ExecutableElement witnessDeclaration)
        || !(trees.getTypeMirror(new TreePath(call, method.getMethodSelect())) instanceof ExecutableType executable)
        || !correspondence.matchesJavaType(occurrence.created(), returned)) {
      return ConstructorCorrelation.unavailable();
    }
    var arguments = new ArrayList<TypeMirror>();
    var originalArguments = new ArrayList<JavaTypeIdentity>();
    var prefix = occurrence.constructor().getParameters().size() - 1;
    if (method.getArguments().size() != creation.getArguments().size() + (expanded ? 1 : 0)) {
      return ConstructorCorrelation.unavailable();
    }
    var witnessArgumentPaths = method.getArguments().stream().map(argument -> new TreePath(call, argument)).toList();
    var originalArgumentPaths = creation.getArguments().stream().map(argument -> new TreePath(path, argument)).toList();
    for (var index = 0; index < creation.getArguments().size(); index++) {
      var witnessIndex = expanded && index >= prefix ? index + 1 : index;
      var witnessArgumentPath = witnessArgumentPaths.get(witnessIndex);
      var originalArgumentPath = originalArgumentPaths.get(index);
      var argument = trees.getTypeMirror(witnessArgumentPath);
      var originalArgument = originalTrees.getTypeMirror(originalArgumentPath);
      if (argument == null || originalArgument == null
          || !correspondence.matchesArgument(originalArgumentPath, originalTrees, witnessArgumentPath, trees)) {
        return ConstructorCorrelation.unavailable();
      }
      arguments.add(argument);
      originalArguments.add(originalSemantics.typeIdentity(originalArgument));
    }
    var selection = expanded
        ? expandedSelection(resolver, attribution.types(), executable, occurrence.constructor().getParameters().size(), arguments)
        : new JavaInvocationApplicability(resolver).select(executable, false, arguments);
    var facts = selection
        .filter(value -> value.argumentTargets().stream()
            .noneMatch(target -> ConstructorTypeCorrespondence.retainsWitnessParameter(target, witnessDeclaration))
            && value.variableArray().stream()
                .noneMatch(array -> ConstructorTypeCorrespondence.retainsWitnessParameter(array, witnessDeclaration)))
        .flatMap(value -> copyFacts(resolver, value, arguments, originalArguments,
            type -> selectedTypeIdentity(resolver, correspondence, attribution.types(), type)));
    return facts.map(ConstructorCorrelation::selected).orElseGet(ConstructorCorrelation::unavailable);
  }

  private static Optional<JavaInvocationApplicability.Selection> expandedSelection(SemanticResolver resolver, Types types,
      ExecutableType executable, int formalCount, List<TypeMirror> arguments) {
    var formals = executable.getParameterTypes();
    var prefix = formalCount - 1;
    if (prefix < 0 || formals.size() != arguments.size() + 1 || !(formals.get(prefix) instanceof ArrayType array)) {
      return Optional.empty();
    }
    var applicability = new JavaInvocationApplicability(resolver);
    var targets = new ArrayList<TypeMirror>();
    for (var index = 0; index < arguments.size(); index++) {
      var target = formals.get(index < prefix ? index : index + 1);
      if ((index >= prefix && !types.isSameType(target, array.getComponentType()))
          || !applicability.looseConversion(arguments.get(index), target)) {
        return Optional.empty();
      }
      targets.add(target);
    }
    return Optional.of(
        new JavaInvocationApplicability.Selection(JavaInvocationApplicability.Phase.VARIABLE, formalCount, targets, Optional.of(array)));
  }

  private static List<TreePath> witnessCalls(Attribution attribution, WitnessCall call) {
    var calls = new ArrayList<TreePath>();
    var scanner = new TreePathScanner<Void, Void>() {
      @Override
      public Void visitMethodInvocation(MethodInvocationTree method, Void ignored) {
        var path = getCurrentPath();
        if (attribution.trees().getElement(path) instanceof ExecutableElement selected
            && selected.getSimpleName().contentEquals(call.name()) && selected.getEnclosingElement() instanceof TypeElement selectedOwner
            && attribution.elements().getBinaryName(selectedOwner).contentEquals(call.binaryOwner())) {
          calls.add(path);
        }
        return super.visitMethodInvocation(method, null);
      }
    };
    attribution.units().forEach(unit -> scanner.scan(unit, null));
    return calls;
  }

  private static Optional<SelectedConstructorFacts> copyFacts(SemanticResolver resolver, JavaInvocationApplicability.Selection selection,
      List<TypeMirror> arguments, List<JavaTypeIdentity> originalArguments,
      Function<TypeMirror, Optional<JavaTypeIdentity>> selectedIdentity) {
    var facts = new ArrayList<ArgumentFacts>();
    for (var index = 0; index < arguments.size(); index++) {
      var source = arguments.get(index);
      var target = selection.argumentTargets().get(index);
      var identity = selectedIdentity.apply(target);
      if (identity.isEmpty()) {
        return Optional.empty();
      }
      var preBox = Optional.<JavaTypeIdentity>empty();
      var boxed = Optional.<JavaTypeIdentity>empty();
      var unboxed = Optional.<JavaTypeIdentity>empty();
      var effective = Optional.<JavaTypeIdentity>empty();
      if (source instanceof PrimitiveType primitive && !target.getKind().isPrimitive()) {
        var wrapper = resolver.boxedType(primitive);
        if (!resolver.assignable(wrapper, target)) {
          return Optional.empty();
        }
        preBox = Optional.of(resolver.typeIdentity(primitive));
        boxed = Optional.of(resolver.typeIdentity(wrapper));
      } else if (!source.getKind().isPrimitive() && target.getKind().isPrimitive()) {
        var primitive = resolver.unboxedType(source);
        if (primitive.isEmpty() || !resolver.assignable(primitive.orElseThrow(), target)) {
          return Optional.empty();
        }
        unboxed = Optional.of(resolver.typeIdentity(primitive.orElseThrow()));
        effective = Optional.of(resolver.typeIdentity(target));
      }
      facts.add(new ArgumentFacts(originalArguments.get(index), identity.orElseThrow(), unboxed, effective, preBox, boxed));
    }
    var array = selection.variableArray();
    var arrayIdentity = array.flatMap(selectedIdentity);
    var elementIdentity = array.flatMap(value -> selectedIdentity.apply(value.getComponentType()));
    if (array.isPresent() && (arrayIdentity.isEmpty() || elementIdentity.isEmpty())) {
      return Optional.empty();
    }
    return Optional.of(new SelectedConstructorFacts(selection.mode(), selection.formalCount(), facts, arrayIdentity,
        array.map(value -> resolver.typeIdentity(resolver.erasedType(value))), elementIdentity));
  }

  private Optional<JavaTypeIdentity> selectedTypeIdentity(SemanticResolver resolver, ConstructorTypeCorrespondence correspondence,
      Types witnessTypes, TypeMirror type) {
    var original = correspondence.originalType(type, originalTypes, witnessTypes);
    if (original.isPresent()) {
      return original.map(originalSemantics::typeIdentity);
    }
    return ConstructorTypeCorrespondence.containsVariable(type) ? Optional.empty() : Optional.of(resolver.typeIdentity(type));
  }
}
