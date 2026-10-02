package org.aquilagraph.model;

/** Typed immutable actual-fact values inspected again at input admission. */
public sealed interface CoverageFactValue {
  /**
   * Complete copied source occurrence.
   *
   * @param value
   *          lossless neutral source value
   */
  record Source(CoverageSourceFact value) implements CoverageFactValue {
    /** Requires the closed immutable source value. */
    public Source {
      CoverageValueValidation.requireValue(value);
    }
  }
  /**
   * Complete copied actual declaration type shape.
   *
   * @param value
   *          copied canonical declaration type components
   */
  record DeclarationType(DeclarationTypeEvidence value) implements CoverageFactValue {
    /** Requires the actual copied type shape. */
    public DeclarationType {
      CoverageValueValidation.requireValue(value);
    }
  }
  /**
   * Complete translated canonical member order.
   *
   * @param value
   *          copied direct-member order
   */
  record MemberOrder(DeclarationMemberOrder value) implements CoverageFactValue {
    /** Requires the copied order. */
    public MemberOrder {
      CoverageValueValidation.requireValue(value);
    }
  }
  /**
   * Complete type declaration value.
   *
   * @param value
   *          copied type declaration
   */
  record Type(JavaType value) implements CoverageFactValue {
    /** Requires the declaration. */
    public Type {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Complete executable declaration value.
   *
   * @param value
   *          copied executable declaration
   */
  record Method(JavaMethod value) implements CoverageFactValue {
    /** Requires the declaration. */
    public Method {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Complete field declaration value.
   *
   * @param value
   *          copied field declaration
   */
  record Field(JavaField value) implements CoverageFactValue {
    /** Requires the declaration. */
    public Field {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Complete formal parameter value.
   *
   * @param value
   *          copied formal parameter
   */
  record Parameter(JavaParameter value) implements CoverageFactValue {
    /** Requires the declaration. */
    public Parameter {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Complete record component value.
   *
   * @param value
   *          copied record component
   */
  record RecordComponent(JavaRecordComponent value) implements CoverageFactValue {
    /** Requires the declaration. */
    public RecordComponent {
      CoverageValueValidation.requireValue(value);
    }
  }

  /**
   * Complete compiler-derived counterpart value.
   *
   * @param value
   *          copied compiler-derived declaration
   */
  record SynthesizedDeclaration(JavaSynthesizedDeclaration value) implements CoverageFactValue {
    /** Requires the declaration. */
    public SynthesizedDeclaration {
      CoverageValueValidation.requireValue(value);
    }
  }
}
