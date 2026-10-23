package org.aquilagraph.cfg.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.MethodSyntax;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxValue;

/** Immutable consumer decisions prepared from the independently admitted source facts. */
record MethodLoweringPlan(Map<Use, ConversionEvidence> conversions, Map<Use, Boolean> referenceChecks,
    Map<String, InvocationApplicability> invocations, Map<String, ConstructorEvidence> constructors,
    Map<String, EnumBindingPlan> enumBindings, Map<String, StaticMemberEvidence> staticMembers, Map<String, Boolean> initialization,
    Map<String, StringExpressionEvidence> strings, Map<String, StringOperandPlan> stringOperands) {
  MethodLoweringPlan {
    conversions = Map.copyOf(conversions);
    referenceChecks = Map.copyOf(referenceChecks);
    invocations = Map.copyOf(invocations);
    constructors = Map.copyOf(constructors);
    enumBindings = Map.copyOf(enumBindings);
    staticMembers = Map.copyOf(staticMembers);
    initialization = Map.copyOf(initialization);
    strings = Map.copyOf(strings);
    stringOperands = Map.copyOf(stringOperands);
  }

  static MethodLoweringPlan from(MethodSyntax syntax) {
    var executableNodes = ExecutableSyntaxTraversal.nodes(syntax.root()).stream().map(SyntaxNode::nodeId).collect(Collectors.toSet());
    var conversions = new HashMap<Use, ConversionEvidence>();
    var referenceChecks = new HashMap<Use, Boolean>();
    syntax.evidence().uses().forEach(value -> {
      var use = new Use(value.consumerNodeId(), value.role(), value.position());
      value.conversion().knownValue().ifPresent(classification -> conversions.put(use, classification));
      value.referenceCheck().knownValue().ifPresent(required -> referenceChecks.put(use, required));
    });
    var invocations = new HashMap<String, InvocationApplicability>();
    syntax.evidence().invocations().forEach(
        value -> value.applicability().knownValue().ifPresent(classification -> invocations.put(value.consumerNodeId(), classification)));
    var constructors = new HashMap<String, ConstructorEvidence>();
    var enumBindings = new HashMap<String, EnumBindingPlan>();
    syntax.evidence().constructors().forEach(value -> value.corroboration().knownValue().ifPresent(context -> {
      constructors.put(value.invocationNodeId(), value);
      context.insertedInvocation().flatMap(insertion -> insertion.applicability().applicability().knownValue())
          .ifPresent(classification -> invocations.put(value.invocationNodeId(), classification));
      if (syntax.source().nodes().stream()
          .anyMatch(owner -> owner.sourceKind() == JavaSourceKind.ENUM && owner.value() instanceof SyntaxValue.TypeDeclaration declaration
              && declaration.declarationKey().equals(value.ownerTypeKey()))) {
        var binding = EnumBindingPlan.from(value);
        enumBindings.put(value.invocationNodeId(), binding);
        if (binding.form() == InvocationForm.SUPER_CONSTRUCTOR) {
          invocations.put(value.invocationNodeId(),
              new InvocationApplicability(syntax.methodKey(), value.invocationNodeId(), InvocationApplicabilityMode.FIXED,
                  context.superclassParameterTypes().size(), Optional.empty(), Optional.empty(), Optional.empty()));
        }
      }
    }));
    var staticMembers = new HashMap<String, StaticMemberEvidence>();
    var initialization = new HashMap<String, Boolean>();
    syntax.evidence().staticMembers().stream().filter(value -> executableNodes.contains(value.accessNodeId())).forEach(value -> {
      staticMembers.put(value.accessNodeId(), value);
      initialization.put(value.accessNodeId(), ClassInitializationPolicy.required(value));
    });
    var strings = new HashMap<String, StringExpressionEvidence>();
    var stringOperands = new HashMap<String, StringOperandPlan>();
    syntax.evidence().stringExpressions().stream().filter(value -> executableNodes.contains(value.binaryNodeId())).forEach(value -> {
      strings.put(value.binaryNodeId(), value);
      stringOperands.put(value.binaryNodeId(), StringOperandPlan.from(value));
    });
    return new MethodLoweringPlan(conversions, referenceChecks, invocations, constructors, enumBindings, staticMembers, initialization,
        strings, stringOperands);
  }

  record Use(String nodeKey, SyntaxRole role, int position) {
  }
}
