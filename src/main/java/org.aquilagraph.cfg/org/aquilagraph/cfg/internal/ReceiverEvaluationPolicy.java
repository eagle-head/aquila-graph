package org.aquilagraph.cfg.internal;

import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.SyntaxValue.FieldReceiver;

/** Receiver evaluation and consumption for the bounded graph interpretation. */
final class ReceiverEvaluationPolicy {
  private ReceiverEvaluationPolicy() {
  }

  static boolean evaluatesExpression(InvocationForm form) {
    return form.hasReceiverExpression();
  }

  static boolean evaluatesExpression(FieldReceiver form) {
    return form.hasReceiverExpression();
  }

  static boolean consumesReceiver(InvocationForm form) {
    return switch (form) {
      case INSTANCE_IMPLICIT, INSTANCE_EXPRESSION, INSTANCE_THIS, INSTANCE_QUALIFIED_THIS, SUPER, QUALIFIED_SUPER, SUPER_CONSTRUCTOR,
          QUALIFIED_SUPER_CONSTRUCTOR ->
        true;
      case STATIC_UNQUALIFIED, STATIC_TYPE, STATIC_EXPRESSION, THIS_CONSTRUCTOR, OBJECT_CREATION, QUALIFIED_OBJECT_CREATION -> false;
    };
  }

  static boolean consumesReceiver(FieldReceiver form) {
    return !form.isStatic();
  }

  static boolean canFailOnNullReceiver(InvocationForm form) {
    return form == InvocationForm.INSTANCE_EXPRESSION || form == InvocationForm.QUALIFIED_SUPER_CONSTRUCTOR;
  }
}
