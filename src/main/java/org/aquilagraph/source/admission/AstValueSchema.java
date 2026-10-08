package org.aquilagraph.source.admission;

import java.lang.annotation.ElementType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.AstIdentity;
import org.aquilagraph.identity.api.BodyRef;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.DependencyKind;
import org.aquilagraph.identity.api.DependencySnapshot;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.NonwrittenBodyProvenance;
import org.aquilagraph.identity.api.SoftwareVersion;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AnalysisDiagnostic;
import org.aquilagraph.source.api.AstContractException;
import org.jspecify.annotations.Nullable;
import org.aquilagraph.source.api.AnalysisOutcomeKind;
import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.AttributedCoverageExpectations;
import org.aquilagraph.source.api.CoverageAnnotationValue;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.CoverageExecutableType;
import org.aquilagraph.source.api.CoverageFactReference;
import org.aquilagraph.source.api.CoverageFactValue;
import org.aquilagraph.source.api.CoveragePropertyValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.CoverageTypeStructure;
import org.aquilagraph.source.api.CoverageValidationEvidence;
import org.aquilagraph.source.api.DeclarationMemberOrder;
import org.aquilagraph.source.api.DeclarationTypeEvidence;
import org.aquilagraph.source.api.DiagnosticEmission;
import org.aquilagraph.source.api.DiagnosticSeverity;
import org.aquilagraph.source.api.DiagnosticStage;
import org.aquilagraph.source.api.ConstructorEvidence;
import org.aquilagraph.source.api.ConstructorContext;
import org.aquilagraph.source.api.CompilerInsertedInvocation;
import org.aquilagraph.source.api.EvidenceOrigin;
import org.aquilagraph.source.api.EvidenceState;
import org.aquilagraph.source.api.EvidenceValue;
import org.aquilagraph.source.api.FeatureContextOutcome;
import org.aquilagraph.source.api.InvocationApplicability;
import org.aquilagraph.source.api.AttributedBodyOccurrence;
import org.aquilagraph.source.api.InvocationEvidence;
import org.aquilagraph.source.api.InvocationApplicabilityMode;
import org.aquilagraph.source.api.InvocationForm;
import org.aquilagraph.source.api.InvocationMode;
import org.aquilagraph.source.api.InvocationSemantics;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaField;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaParameter;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaRecordComponent;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaSynthesizedDeclaration;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeKind;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.JavaTypeParameter;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.MethodEvidence;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.api.SyntaxAnalysisInput;
import org.aquilagraph.source.api.SyntaxChild;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNodeTuple;
import org.aquilagraph.source.api.SyntaxOperator;
import org.aquilagraph.source.api.SyntaxRangeTuple;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxSemanticEdge;
import org.aquilagraph.source.api.SyntaxSemanticRelation;
import org.aquilagraph.source.api.SyntaxSemanticTarget;
import org.aquilagraph.source.api.SyntaxSourceContribution;
import org.aquilagraph.source.api.SyntaxStructuralEdge;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.api.LocalBindingEvidence;
import org.aquilagraph.source.api.ConversionEvidence;
import org.aquilagraph.source.api.ChildUseEvidence;
import org.aquilagraph.source.api.UnmatchedSourceFact;
import org.aquilagraph.source.api.WrittenSourceAssociation;
import org.aquilagraph.source.api.WrittenTypeEvidence;

/** Closed retained-value tags and ordered public components. */
final class AstValueSchema {
  private static final String PARAMETERS_FIELD = "parameters";
  private static final String RETURN_TYPE_FIELD = "returnType";
  private static final String ADDRESS_FIELD = "address";
  private static final String ANNOTATIONS_FIELD = "annotations";
  private static final String ARGUMENTS_FIELD = "arguments";
  private static final String BOUNDS_FIELD = "bounds";
  private static final String CATEGORY_FIELD = "category";
  private static final String DECLARATION_KEY_FIELD = "declarationKey";
  private static final String EFFECTIVE_MODIFIERS_FIELD = "effectiveModifiers";
  private static final String ENCLOSING_TYPE_FIELD = "enclosingType";
  private static final String FORM_FIELD = "form";
  private static final String IDENTITY_FIELD = "identity";
  private static final String KIND_FIELD = "kind";
  private static final String LEXICAL_OWNER_KEY_FIELD = "lexicalOwnerKey";
  private static final String METHOD_KEY_FIELD = "methodKey";
  private static final String NAME_FIELD = "name";
  private static final String NODE_KEY_FIELD = "nodeKey";
  private static final String ORDINAL_FIELD = "ordinal";
  private static final String ORIGIN_FIELD = "origin";
  private static final String ORIGINS_FIELD = "origins";
  private static final String OWNER_KEY_FIELD = "ownerKey";
  private static final String OWNER_TYPE_KEY_FIELD = "ownerTypeKey";
  private static final String POSITION_FIELD = "position";
  private static final String QUALIFIED_NAME_FIELD = "qualifiedName";
  private static final String ROLE_FIELD = "role";
  private static final String SOURCE_DECLARED_FIELD = "sourceDeclared";
  private static final String SOURCE_FILE_KEY_FIELD = "sourceFileKey";
  private static final String SOURCE_KIND_FIELD = "sourceKind";
  private static final String TYPE_FIELD = "type";
  private static final String VALUE_FIELD = "value";
  private static final String VAR_ARGS_FIELD = "varArgs";
  private static final String WRITTEN_MODIFIERS_FIELD = "writtenModifiers";
  private static final Map<Class<?>, Shape> BY_TYPE;
  private static final Map<String, Shape> BY_TAG;

  static {
    var types = new LinkedHashMap<Class<?>, Shape>();
    record(types, AnalysisEnvironmentSnapshot.class, "analysis-environment-snapshot", "compilerConfiguration", "compilerImplementation",
        "compilerVendor", "compilerVersion", "analysisOptions", "classPath", "modulePath");
    record(types, AstIdentity.class, "ast-identity", "astResultKey", "softwareSignature", "sourceModelVersion", "astProfileVersion",
        "semanticProductionRevision", "canonicalContentDigest");
    record(types, BodyRef.class, "body-ref", "astIdentity", METHOD_KEY_FIELD, "sourceOccurrenceKey", "nonwrittenProvenance");
    record(types, CompilerConfiguration.class, "compiler-configuration", "release", "classPath", "modulePath", "options");
    enumeration(types, DependencyKind.class, "dependency-kind");
    record(types, DependencySnapshot.class, "dependency-snapshot", "location", KIND_FIELD, "byteCount", "entryCount", "fingerprint");
    record(types, InputSnapshot.class, "input-snapshot", "sourceRoot", "digestAlgorithm", "snapshotDigest", "sourceFiles");
    enumeration(types, JavaRelease.class, "java-release");
    record(types, NonwrittenBodyProvenance.class, "nonwritten-body-provenance", ORIGIN_FIELD, SOURCE_FILE_KEY_FIELD, "ownerDeclarationKey");
    record(types, SoftwareVersion.class, "software-version", "sourceSignature", "environmentSignature");
    record(types, SourceFile.class, "source-file", SOURCE_FILE_KEY_FIELD, "relativePath", "contentDigest", "content");
    record(types, SourceSpan.class, "source-span", SOURCE_FILE_KEY_FIELD, "startOffset", "endOffsetExclusive");
    enumeration(types, AnalysisOutcomeKind.class, "analysis-outcome-kind");
    record(types, AnnotationElementValue.Nested.class, "annotation-element-value/nested", "annotationTypeKey");
    record(types, AnnotationElementValue.Array.class, "annotation-element-value/array", "elements");
    record(types, AnnotationScalar.BooleanValue.class, "annotation-scalar/boolean-value", VALUE_FIELD);
    record(types, AnnotationScalar.ByteValue.class, "annotation-scalar/byte-value", VALUE_FIELD);
    record(types, AnnotationScalar.ShortValue.class, "annotation-scalar/short-value", VALUE_FIELD);
    record(types, AnnotationScalar.CharValue.class, "annotation-scalar/char-value", VALUE_FIELD);
    record(types, AnnotationScalar.IntValue.class, "annotation-scalar/int-value", VALUE_FIELD);
    record(types, AnnotationScalar.LongValue.class, "annotation-scalar/long-value", VALUE_FIELD);
    record(types, AnnotationScalar.FloatValue.class, "annotation-scalar/float-value", VALUE_FIELD);
    record(types, AnnotationScalar.DoubleValue.class, "annotation-scalar/double-value", VALUE_FIELD);
    record(types, AnnotationScalar.StringValue.class, "annotation-scalar/string-value", VALUE_FIELD);
    record(types, AnnotationScalar.EnumConstant.class, "annotation-scalar/enum-constant", DECLARATION_KEY_FIELD, "enumTypeKey");
    record(types, AnnotationScalar.ClassLiteral.class, "annotation-scalar/class-literal", "operandType");
    record(types, AttributedCoverageExpectations.class, "attributed-coverage-expectations", "writtenInventory", "projections");
    record(types, AttributedCoverageExpectations.Projection.class, "attributed-coverage-expectations/projection", ADDRESS_FIELD,
        VALUE_FIELD, "ownerIdentity");
    record(types, CoverageAnnotationValue.Scalar.class, "coverage-annotation-value/scalar", KIND_FIELD, VALUE_FIELD);
    enumeration(types, CoverageAnnotationValue.Scalar.Kind.class, "coverage-annotation-value/scalar/kind");
    record(types, CoverageAnnotationValue.EnumConstant.class, "coverage-annotation-value/enum-constant", DECLARATION_KEY_FIELD,
        OWNER_TYPE_KEY_FIELD);
    record(types, CoverageAnnotationValue.ClassLiteral.class, "coverage-annotation-value/class-literal", "operand");
    record(types, CoverageAnnotationValue.TypeOperand.class, "coverage-annotation-value/type-operand", CATEGORY_FIELD,
        DECLARATION_KEY_FIELD, LEXICAL_OWNER_KEY_FIELD, ENCLOSING_TYPE_FIELD, "components");
    record(types, CoverageAnnotationValue.Annotation.class, "coverage-annotation-value/annotation", "typeKey", "explicitValues",
        "provenance");
    record(types, CoverageAnnotationValue.Entry.class, "coverage-annotation-value/entry", "elementKey", VALUE_FIELD);
    record(types, CoverageAnnotationValue.Array.class, "coverage-annotation-value/array", "elements");
    record(types, CoverageEvidenceValue.Text.class, "coverage-evidence-value/text", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Named.class, "coverage-evidence-value/named", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Integral.class, "coverage-evidence-value/integral", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Decimal.class, "coverage-evidence-value/decimal", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Flag.class, "coverage-evidence-value/flag", VALUE_FIELD);
    record(types, CoverageEvidenceValue.TypeIdentity.class, "coverage-evidence-value/type-identity", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Origin.class, "coverage-evidence-value/origin", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Invocation.class, "coverage-evidence-value/invocation", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Sequence.class, "coverage-evidence-value/sequence", "values");
    record(types, CoverageEvidenceValue.OptionalValue.class, "coverage-evidence-value/optional-value", VALUE_FIELD);
    record(types, CoverageEvidenceValue.Structure.class, "coverage-evidence-value/structure", "tag", "fields");
    record(types, CoverageEvidenceValue.Field.class, "coverage-evidence-value/field", NAME_FIELD, VALUE_FIELD);
    enumeration(types, CoverageEvidenceValue.Tag.class, "coverage-evidence-value/tag");
    record(types, CoverageExecutableType.class, "coverage-executable-type", "variables", PARAMETERS_FIELD, RETURN_TYPE_FIELD, "thrownTypes",
        "receiverType");
    record(types, CoverageExecutableType.Variable.class, "coverage-executable-type/variable", DECLARATION_KEY_FIELD, TYPE_FIELD);
    record(types, CoverageFactReference.class, "coverage-fact-reference", KIND_FIELD, IDENTITY_FIELD, SOURCE_FILE_KEY_FIELD, VALUE_FIELD,
        ORIGIN_FIELD);
    enumeration(types, CoverageFactReference.Kind.class, "coverage-fact-reference/kind");
    record(types, CoverageFactValue.Source.class, "coverage-fact-value/source", VALUE_FIELD);
    record(types, CoverageFactValue.DeclarationType.class, "coverage-fact-value/declaration-type", VALUE_FIELD);
    record(types, CoverageFactValue.MemberOrder.class, "coverage-fact-value/member-order", VALUE_FIELD);
    record(types, CoverageFactValue.Type.class, "coverage-fact-value/type", VALUE_FIELD);
    record(types, CoverageFactValue.Method.class, "coverage-fact-value/method", VALUE_FIELD);
    record(types, CoverageFactValue.Field.class, "coverage-fact-value/field", VALUE_FIELD);
    record(types, CoverageFactValue.Parameter.class, "coverage-fact-value/parameter", VALUE_FIELD);
    record(types, CoverageFactValue.RecordComponent.class, "coverage-fact-value/record-component", VALUE_FIELD);
    record(types, CoverageFactValue.SynthesizedDeclaration.class, "coverage-fact-value/synthesized-declaration", VALUE_FIELD);
    record(types, CoveragePropertyValue.class, "coverage-property-value", "state", "items");
    enumeration(types, CoveragePropertyValue.State.class, "coverage-property-value/state");
    record(types, CoveragePropertyValue.Item.class, "coverage-property-value/item", IDENTITY_FIELD, VALUE_FIELD, "provenance",
        "executableType", "typeIdentity", "modifierFlags", "annotationValue", "typeStructure");
    enumeration(types, CoveragePropertyValue.Provenance.class, "coverage-property-value/provenance");
    record(types, CoverageSourceFact.class, "coverage-source-fact", "structuralPath", SOURCE_KIND_FIELD, "syntaxKind", TYPE_FIELD,
        VALUE_FIELD, "children", "semanticReference", "sourceAssociation");
    record(types, CoverageSourceFact.Step.class, "coverage-source-fact/step", ROLE_FIELD, ORDINAL_FIELD);
    record(types, CoverageSourceFact.Child.class, "coverage-source-fact/child", ROLE_FIELD, ORDINAL_FIELD, NODE_KEY_FIELD);
    record(types, CoverageSourceFact.Association.class, "coverage-source-fact/association", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD,
        "ownerPath", SOURCE_KIND_FIELD, ORIGIN_FIELD);
    record(types, CoverageSyntaxFactValue.class, "coverage-syntax-fact-value", "structuralPath", SOURCE_KIND_FIELD, "syntaxKind",
        TYPE_FIELD, VALUE_FIELD, "children", "semanticReference", "sourceAssociation");
    record(types, CoverageSyntaxFactValue.Child.class, "coverage-syntax-fact-value/child", ROLE_FIELD, ORDINAL_FIELD, NODE_KEY_FIELD);
    record(types, CoverageTypeStructure.Simple.class, "coverage-type-structure/simple", CATEGORY_FIELD);
    record(types, CoverageTypeStructure.Declared.class, "coverage-type-structure/declared", DECLARATION_KEY_FIELD, LEXICAL_OWNER_KEY_FIELD,
        ENCLOSING_TYPE_FIELD, ARGUMENTS_FIELD);
    record(types, CoverageTypeStructure.Variable.class, "coverage-type-structure/variable", DECLARATION_KEY_FIELD);
    record(types, CoverageTypeStructure.Array.class, "coverage-type-structure/array", "component");
    record(types, CoverageTypeStructure.Wildcard.class, "coverage-type-structure/wildcard", "upperBound", "lowerBound");
    record(types, CoverageTypeStructure.Intersection.class, "coverage-type-structure/intersection", BOUNDS_FIELD);
    record(types, CoverageTypeStructure.Union.class, "coverage-type-structure/union", "alternatives");
    record(types, CoverageValidationEvidence.class, "coverage-validation-evidence", "writtenInventory", "attributedExpectations",
        "outcomes", "unmatchedFacts", "diagnosticEmissions");
    record(types, DeclarationMemberOrder.class, "declaration-member-order", SOURCE_FILE_KEY_FIELD, OWNER_TYPE_KEY_FIELD, "members");
    record(types, DeclarationMemberOrder.Member.class, "declaration-member-order/member", DECLARATION_KEY_FIELD, KIND_FIELD, "displayValue",
        "provenance", "writtenOrdinal");
    record(types, DeclarationTypeEvidence.class, "declaration-type-evidence", OWNER_TYPE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, IDENTITY_FIELD,
        "structure");
    record(types, DiagnosticEmission.class, "diagnostic-emission", ADDRESS_FIELD, "diagnostic", "cause", "contexts", "slot");
    record(types, DiagnosticEmission.Address.class, "diagnostic-emission/address", "producer", "owner", ORDINAL_FIELD);
    enumeration(types, DiagnosticSeverity.class, "diagnostic-severity");
    enumeration(types, DiagnosticStage.class, "diagnostic-stage");
    record(types, ConstructorEvidence.class, "constructor-evidence", METHOD_KEY_FIELD, "invocationNodeId", "ownerTypeKey",
        "selectedConstructorKey", FORM_FIELD, "corroboration");
    record(types, ConstructorContext.class, "constructor-context", "superclassParameterTypes", "insertedInvocation");
    record(types, CompilerInsertedInvocation.class, "compiler-inserted-invocation", SOURCE_FILE_KEY_FIELD, "compilerPath", "reference",
        "semantics", "applicability");
    record(types, EvidenceOrigin.class, "evidence-origin", METHOD_KEY_FIELD, NODE_KEY_FIELD, "slot", "sourceRange");
    enumeration(types, EvidenceState.class, "evidence-state");
    record(types, EvidenceValue.class, "evidence-value", "state", "knownValue", ORIGIN_FIELD, "explanation", "diagnostic");
    record(types, FeatureContextOutcome.class, "feature-context-outcome", ADDRESS_FIELD, "occurrence", "observation", "encounteredValue",
        "support", "emissions");
    record(types, FeatureContextOutcome.Support.class, "feature-context-outcome/support", "mode", "facts", "policyContext", "sourceFile",
        "negativeState");
    enumeration(types, FeatureContextOutcome.Mode.class, "feature-context-outcome/mode");
    record(types, InvocationApplicability.class, "invocation-applicability", METHOD_KEY_FIELD, "consumerNodeId", "mode", "formalCount",
        "varargArrayType", "erasedArrayType", "elementType");
    enumeration(types, InvocationApplicabilityMode.class, "invocation-applicability-mode");
    record(types, InvocationEvidence.class, "invocation-evidence", "applicability");
    enumeration(types, InvocationForm.class, "invocation-form");
    enumeration(types, InvocationMode.class, "invocation-mode");
    record(types, InvocationSemantics.class, "invocation-semantics", "mode", "qualifyingType", FORM_FIELD);
    enumeration(types, JavaDeclarationModifier.class, "java-declaration-modifier");
    record(types, JavaField.class, "java-field", "fieldKey", OWNER_TYPE_KEY_FIELD, NAME_FIELD, "staticType", "modifiers", "enumConstant",
        SOURCE_DECLARED_FIELD, ORIGINS_FIELD);
    record(types, JavaMethod.class, "java-method", METHOD_KEY_FIELD, OWNER_TYPE_KEY_FIELD, NAME_FIELD, "signature", RETURN_TYPE_FIELD,
        PARAMETERS_FIELD, "modifiers", "constructor", SOURCE_DECLARED_FIELD, "hasBody", ORIGINS_FIELD);
    record(types, JavaParameter.class, "java-parameter", "parameterKey", NAME_FIELD, "staticType", SOURCE_DECLARED_FIELD, ORIGINS_FIELD);
    record(types, JavaProgram.class, "java-program", "types");
    record(types, JavaRecordComponent.class, "java-record-component", "componentKey", OWNER_TYPE_KEY_FIELD, ORDINAL_FIELD, NAME_FIELD,
        "staticType", WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, VAR_ARGS_FIELD, "accessorKey", "backingFieldKey",
        "canonicalParameterKey", ORIGIN_FIELD);
    enumeration(types, JavaSourceKind.class, "java-source-kind");
    record(types, JavaSynthesizedDeclaration.class, "java-synthesized-declaration", DECLARATION_KEY_FIELD, OWNER_TYPE_KEY_FIELD,
        KIND_FIELD);
    enumeration(types, JavaSynthesizedDeclaration.Kind.class, "java-synthesized-declaration/kind");
    record(types, JavaType.class, "java-type", "typeKey", QUALIFIED_NAME_FIELD, KIND_FIELD, "modifiers", ENCLOSING_TYPE_FIELD,
        "directSuperclass", "directInterfaces", "permittedSubclasses", "typeParameters", SOURCE_DECLARED_FIELD, "fields",
        "recordComponents", "methods", "synthesizedDeclarations", ORIGINS_FIELD);
    enumeration(types, JavaTypeCategory.class, "java-type-category");
    record(types, JavaTypeIdentity.class, "java-type-identity", CATEGORY_FIELD, "displayName", "erasedName");
    enumeration(types, JavaTypeKind.class, "java-type-kind");
    enumeration(types, JavaTypeModifier.class, "java-type-modifier");
    record(types, JavaTypeParameter.class, "java-type-parameter", "typeParameterKey", OWNER_TYPE_KEY_FIELD, NAME_FIELD, BOUNDS_FIELD,
        SOURCE_DECLARED_FIELD, ORIGINS_FIELD);
    record(types, JavaTypeReference.class, "java-type-reference", IDENTITY_FIELD, DECLARATION_KEY_FIELD);
    record(types, JavaTypeStructure.Primitive.class, "java-type-structure/primitive", KIND_FIELD);
    record(types, JavaTypeStructure.Declared.class, "java-type-structure/declared", DECLARATION_KEY_FIELD, LEXICAL_OWNER_KEY_FIELD,
        ENCLOSING_TYPE_FIELD, ARGUMENTS_FIELD);
    record(types, JavaTypeStructure.LocalDeclared.class, "java-type-structure/local-declared", DECLARATION_KEY_FIELD,
        LEXICAL_OWNER_KEY_FIELD, KIND_FIELD, ENCLOSING_TYPE_FIELD, ARGUMENTS_FIELD);
    enumeration(types, JavaTypeStructure.LocalDeclared.Kind.class, "java-type-structure/local-declared/kind");
    record(types, JavaTypeStructure.Variable.class, "java-type-structure/variable", "parameterKey");
    record(types, JavaTypeStructure.Captured.class, "java-type-structure/captured", ORDINAL_FIELD, "upperBound", "lowerBound");
    record(types, JavaTypeStructure.CaptureReference.class, "java-type-structure/capture-reference", ORDINAL_FIELD);
    record(types, JavaTypeStructure.Array.class, "java-type-structure/array", "component");
    record(types, JavaTypeStructure.Wildcard.class, "java-type-structure/wildcard", KIND_FIELD, "bound");
    enumeration(types, JavaTypeStructure.Wildcard.BoundKind.class, "java-type-structure/wildcard/bound-kind");
    record(types, JavaTypeStructure.Intersection.class, "java-type-structure/intersection", BOUNDS_FIELD);
    record(types, JavaTypeStructure.Union.class, "java-type-structure/union", "alternatives");
    record(types, LanguageFeature.class, "language-feature", CATEGORY_FIELD, "identifier");
    enumeration(types, LanguageFeatureCategory.class, "language-feature-category");
    record(types, LanguageFeatureClassification.class, "language-feature-classification", "feature", "policy", "representationBoundary",
        "irrelevanceRationale", "sourceLocator", "diagnosticCode");
    record(types, LanguageFeatureObservation.class, "language-feature-observation", SOURCE_FILE_KEY_FIELD, "classification", ORIGINS_FIELD,
        "diagnostic");
    record(types, LanguageFeatureOccurrence.class, "language-feature-occurrence", SOURCE_FILE_KEY_FIELD, "classification", ORIGINS_FIELD);
    enumeration(types, LanguageFeaturePolicy.class, "language-feature-policy");
    enumeration(types, MethodBodyOrigin.class, "method-body-origin");
    record(types, MethodEvidence.class, "method-evidence", "uses", "invocations", "staticMembers", "stringExpressions", "constructors");
    record(types, PackageQualifierEvidence.class, "package-qualifier-evidence", SOURCE_KIND_FIELD, ORIGIN_FIELD);
    enumeration(types, ReferenceKind.class, "reference-kind");
    enumeration(types, ResolutionStatus.class, "resolution-status");
    record(types, SemanticReference.class, "semantic-reference", "referenceKind", "status", "symbolKey", "typeName", "typeAlternativeNames",
        "assignableToTypeNames");
    record(types, SourceContext.class, "source-context", ADDRESS_FIELD, "feature", ORIGINS_FIELD, "writtenValue", "requiresAttribution");
    enumeration(types, SourceContext.Partition.class, "source-context/partition");
    record(types, SourceContext.Address.class, "source-context/address", SOURCE_FILE_KEY_FIELD, "partition", "ownerPath", ROLE_FIELD,
        ORDINAL_FIELD);
    record(types, SourceCoverageInventory.class, "source-coverage-inventory", "snapshot", "release", "contractIdentity", "contexts");
    record(types, SourceOwnerPath.class, "source-owner-path", "steps");
    record(types, SourceOwnerPath.Step.class, "source-owner-path/step", ROLE_FIELD, ORDINAL_FIELD);
    enumeration(types, SourceOwnerPath.Role.class, "source-owner-path/role");
    record(types, StaticMemberEvidence.class, "static-member-evidence", METHOD_KEY_FIELD, "accessNodeId", "selectedMemberKey", KIND_FIELD,
        "declaringOwner", "constantVariable");
    enumeration(types, StaticMemberKind.class, "static-member-kind");
    record(types, StringExpressionEvidence.class, "string-expression-evidence", METHOD_KEY_FIELD, "binaryNodeId", "stringType",
        "leftIsString", "rightIsString", "compileTimeConstant");
    record(types, SyntaxAnalysisInput.class, "syntax-analysis-input", "snapshot", "environmentSnapshot", "program", "aggregate",
        "diagnostics", "languageFeatureCoverageResult");
    record(types, SyntaxChild.class, "syntax-child", ROLE_FIELD, POSITION_FIELD, "node");
    enumeration(types, SyntaxKind.class, "syntax-kind");
    record(types, SyntaxNodeTuple.class, "syntax-node-tuple", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, "structuralPath", SOURCE_KIND_FIELD);
    enumeration(types, SyntaxOperator.class, "syntax-operator");
    record(types, SyntaxRangeTuple.class, "syntax-range-tuple", NODE_KEY_FIELD, "range");
    enumeration(types, SyntaxRole.class, "syntax-role");
    record(types, SyntaxSemanticEdge.class, "syntax-semantic-edge", "sourceKey", "relation", "target");
    enumeration(types, SyntaxSemanticRelation.class, "syntax-semantic-relation");
    record(types, SyntaxSemanticTarget.AnnotationTypeLocation.Written.class, "syntax-semantic-target/annotation-type-location/written",
        NODE_KEY_FIELD);
    record(types, SyntaxSemanticTarget.AnnotationTypeLocation.Constructed.class,
        "syntax-semantic-target/annotation-type-location/constructed", DECLARATION_KEY_FIELD);
    record(types, SyntaxSemanticTarget.AnnotationApplication.class, "syntax-semantic-target/annotation-application", "ownerNodeKey",
        "declarationNodeKey", "typeLocation", "propagatedCounterparts", "fieldDeclarators");
    record(types, SyntaxSemanticTarget.AnnotationApplication.FieldDeclaratorApplication.class,
        "syntax-semantic-target/annotation-application/field-declarator-application", "fieldNodeKey", "declarationApplicable",
        "typeLocation");
    record(types, SyntaxSemanticTarget.AnnotationApplication.PropagatedCounterpart.class,
        "syntax-semantic-target/annotation-application/propagated-counterpart", "reference", "declarationApplicable", "typeLocation");
    record(types, SyntaxSemanticTarget.SourceOccurrence.class, "syntax-semantic-target/source-occurrence", NODE_KEY_FIELD);
    record(types, SyntaxSemanticTarget.Declaration.class, "syntax-semantic-target/declaration", "reference");
    record(types, SyntaxSourceContribution.class, "syntax-source-contribution", SOURCE_FILE_KEY_FIELD, "root", "nodes", "structuralEdges",
        "semanticEdges");
    record(types, SyntaxStructuralEdge.class, "syntax-structural-edge", "parentKey", ROLE_FIELD, POSITION_FIELD, "childKey");
    record(types, SyntaxStructuralPath.class, "syntax-structural-path", "steps");
    record(types, SyntaxStructuralPath.Step.class, "syntax-structural-path/step", ROLE_FIELD, POSITION_FIELD);
    enumeration(types, SyntaxType.NotApplicable.class, "syntax-type/not-applicable");
    enumeration(types, SyntaxType.Unresolved.class, "syntax-type/unresolved");
    enumeration(types, SyntaxType.Erroneous.class, "syntax-type/erroneous");
    record(types, SyntaxType.NoType.class, "syntax-type/no-type", KIND_FIELD);
    enumeration(types, SyntaxType.NoType.Kind.class, "syntax-type/no-type/kind");
    enumeration(types, SyntaxType.Unsupported.class, "syntax-type/unsupported");
    record(types, SyntaxType.StructureUnavailable.class, "syntax-type/structure-unavailable", IDENTITY_FIELD);
    record(types, SyntaxType.Resolved.class, "syntax-type/resolved", IDENTITY_FIELD, "structure");
    enumeration(types, SyntaxValue.DiamondType.class, "syntax-value/diamond-type");
    enumeration(types, SyntaxValue.AnnotationForm.class, "syntax-value/annotation-form");
    enumeration(types, SyntaxValue.AnnotationLocation.class, "syntax-value/annotation-location");
    record(types, SyntaxValue.AnnotationArgumentHeader.class, "syntax-value/annotation-argument-header", "explicitName");
    enumeration(types, SyntaxValue.None.class, "syntax-value/none");
    enumeration(types, SyntaxValue.CompilationUnitForm.class, "syntax-value/compilation-unit-form");
    record(types, SyntaxValue.QualifiedName.class, "syntax-value/qualified-name", "components");
    record(types, SyntaxValue.StatementLabel.class, "syntax-value/statement-label", NAME_FIELD);
    record(types, SyntaxValue.LocalDeclaration.class, "syntax-value/local-binding", NAME_FIELD, "writtenType", WRITTEN_MODIFIERS_FIELD,
        "bindingEvidence");
    record(types, LocalBindingEvidence.class, "local-binding-evidence", "declarationKey", "typeForm", "context", "initialized",
        "annotations", "sharedAnnotationKeys");
    record(types, SyntaxValue.Lambda.class, "syntax-value/lambda", "bodyKind", "parenthesized", PARAMETERS_FIELD, RETURN_TYPE_FIELD);
    record(types, SyntaxValue.LambdaParameter.class, "lambda-parameter", "typeForm", "variableArity");
    enumeration(types, SyntaxValue.LambdaBodyKind.class, "lambda-body-kind");
    enumeration(types, LocalBindingEvidence.TypeForm.class, "local-binding-type-form");
    enumeration(types, LocalBindingEvidence.Context.class, "local-binding-context");
    record(types, SyntaxValue.TypeDeclaration.Shape.class, "syntax-value/type-declaration/shape", ANNOTATIONS_FIELD, "typeParameters",
        "superclass", "interfaces", "permittedSubclasses", "recordComponents", "members");
    enumeration(types, SyntaxValue.VariableDeclaration.Kind.class, "syntax-value/variable-declaration/kind");
    record(types, SyntaxValue.RecordComponentDeclaration.Counterparts.class, "syntax-value/record-component-declaration/counterparts",
        "backingFieldKey", "accessorKey", "canonicalParameterKey", "accessorImplicit", "canonicalParameterDerived");
    enumeration(types, SyntaxValue.ExecutableDeclaration.ExecutableKind.class, "syntax-value/executable-declaration/executable-kind");
    record(types, SyntaxValue.ExecutableDeclaration.HeaderShape.class, "syntax-value/executable-declaration/header-shape",
        ANNOTATIONS_FIELD, "typeParameters", "receiver", PARAMETERS_FIELD, RETURN_TYPE_FIELD, "thrownTypes", "body", VAR_ARGS_FIELD);
    record(types, SyntaxValue.ExecutableDeclaration.AttributedHeader.class, "syntax-value/executable-declaration/attributed-header",
        "receiverType", "receiverOwnerKey", "receiverSimpleName", "enclosingTypeKey", "thrownTypes", "defaultValue");
    record(types, SyntaxValue.FieldAccess.class, "syntax-value/field-access", NAME_FIELD, "receiver");
    record(types, SyntaxValue.Invocation.class, "syntax-value/invocation", NAME_FIELD, FORM_FIELD, "semantics");
    record(types, SyntaxValue.Receiver.class, "syntax-value/receiver", KIND_FIELD);
    record(types, SyntaxValue.Literal.class, "syntax-value/literal", "text");
    record(types, SyntaxValue.Operator.class, "syntax-value/operator", "operator");
    record(types, SyntaxValue.TypeName.class, "syntax-value/type-name", NAME_FIELD);
    record(types, SyntaxValue.ArrayCreation.class, "syntax-value/array-creation", "writtenType", "initializer",
        "dimensionAnnotationCounts");
    record(types, SyntaxSemanticTarget.AnnotationTypeLocation.ArrayDimension.class,
        "syntax-semantic-target/annotation-type-location/array-dimension", NODE_KEY_FIELD, "dimension");
    enumeration(types, SyntaxValue.FieldReceiver.class, "syntax-value/field-receiver");
    enumeration(types, SyntaxValue.ReceiverKind.class, "syntax-value/receiver-kind");
    record(types, SyntaxValue.SwitchCase.class, "syntax-value/switch-case", FORM_FIELD, "labels");
    enumeration(types, SyntaxValue.SwitchCaseForm.class, "syntax-value/switch-case-form");
    enumeration(types, SyntaxValue.SwitchLabel.Default.class, "syntax-value/switch-label/default");
    record(types, SyntaxValue.SwitchLabel.Expression.class, "syntax-value/switch-label/expression", "childPosition");
    record(types, SyntaxValue.Unsupported.class, "syntax-value/unsupported", "compilerKind");
    record(types, ChildUseEvidence.class, "child-use-evidence", METHOD_KEY_FIELD, "consumerNodeId", ROLE_FIELD, POSITION_FIELD,
        "sourceType", "selectedTarget", "conversion", "referenceCheck");
    record(types, ConversionEvidence.class, "conversion-evidence", METHOD_KEY_FIELD, "consumerNodeId", ROLE_FIELD, POSITION_FIELD,
        "sourceType", "selectedTarget", "checkedReferenceTarget", "unboxedType", "effectivePrimitiveType", "preBoxPrimitiveType",
        "boxedType");
    record(types, UnmatchedSourceFact.class, "unmatched-source-fact", "owner", "fact", ORDINAL_FIELD, "reason", "emission");
    enumeration(types, UnmatchedSourceFact.Reason.class, "unmatched-source-fact/reason");
    record(types, WrittenSourceAssociation.class, "written-source-association", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, "ownerPath",
        SOURCE_KIND_FIELD, ORIGIN_FIELD);
    record(types, WrittenTypeEvidence.class, "written-type-evidence", SOURCE_KIND_FIELD, ORIGIN_FIELD, TYPE_FIELD);
    record(types, CopiedAstValues.class, "copied-ast-values", "snapshot", "sources", "bodies", "memberOrders", "associations",
        "declarationTypes");
    record(types, CopiedAstValues.Body.class, "copied-ast-values/body", METHOD_KEY_FIELD, "root", ORIGIN_FIELD, "uses", "invocations",
        "staticAccesses", "concatenations", "constructors", "attributedOccurrences");
    record(types, AttributedBodyOccurrence.class, "attributed-body-occurrence", METHOD_KEY_FIELD, "nodeKey", SOURCE_FILE_KEY_FIELD,
        "sourceKind", "compilerPath");
    value(types, AnalysisDiagnostic.class, "analysis-diagnostic", "diagnosticKey", "code", "stage", "severity", "message", ORIGINS_FIELD,
        "outcomeKind");
    value(types, SyntaxValue.SharedTypeAnnotations.class, "syntax-value/shared-type-annotations", "annotationNodeKeys");
    value(types, SyntaxValue.PrimitiveType.class, "syntax-value/primitive-type", KIND_FIELD);
    value(types, SyntaxValue.PackageQualifier.class, "syntax-value/package-qualifier", NAME_FIELD);
    value(types, SyntaxValue.ModuleDeclaration.class, "syntax-value/module-declaration", QUALIFIED_NAME_FIELD, "open");
    value(types, SyntaxValue.Requires.class, "syntax-value/requires", "module", "staticPhase", "transitive");
    value(types, SyntaxValue.Exports.class, "syntax-value/exports", "packageName", "targets");
    value(types, SyntaxValue.Opens.class, "syntax-value/opens", "packageName", "targets");
    value(types, SyntaxValue.Uses.class, "syntax-value/uses", "service");
    value(types, SyntaxValue.Provides.class, "syntax-value/provides", "service", "providers");
    value(types, SyntaxValue.AnnotationArgument.class, "syntax-value/annotation-argument", "explicitName", "selectedElementKey",
        VALUE_FIELD);
    value(types, SyntaxValue.TypeDeclaration.class, "syntax-value/type-declaration", NAME_FIELD, DECLARATION_KEY_FIELD,
        WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, "shape");
    value(types, SyntaxValue.VariableDeclaration.class, "syntax-value/variable-declaration", NAME_FIELD, OWNER_KEY_FIELD,
        DECLARATION_KEY_FIELD, KIND_FIELD, WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, "declaredType", ANNOTATIONS_FIELD,
        TYPE_FIELD, "initializer", "argumentList", ARGUMENTS_FIELD, "constantBody", "sharedDeclaratorKeys");
    value(types, SyntaxValue.RecordComponentDeclaration.class, "syntax-value/record-component-declaration", NAME_FIELD, OWNER_KEY_FIELD,
        ORDINAL_FIELD, WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, "declaredType", ANNOTATIONS_FIELD, VAR_ARGS_FIELD,
        "counterparts");
    value(types, SyntaxValue.TypeParameterDeclaration.class, "syntax-value/type-parameter-declaration", NAME_FIELD, OWNER_KEY_FIELD,
        ORDINAL_FIELD, ANNOTATIONS_FIELD, BOUNDS_FIELD, "upperBound");
    value(types, SyntaxValue.ExecutableDeclaration.class, "syntax-value/executable-declaration", NAME_FIELD, OWNER_KEY_FIELD,
        DECLARATION_KEY_FIELD, KIND_FIELD, WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, "shape", "attributedHeader");
    value(types, SyntaxValue.ParameterDeclaration.class, "syntax-value/parameter-declaration", DECLARATION_KEY_FIELD, NAME_FIELD,
        WRITTEN_MODIFIERS_FIELD, EFFECTIVE_MODIFIERS_FIELD, ANNOTATIONS_FIELD, TYPE_FIELD, VAR_ARGS_FIELD);
    value(types, SyntaxValue.ReceiverParameter.class, "syntax-value/receiver-parameter", OWNER_KEY_FIELD, WRITTEN_MODIFIERS_FIELD,
        EFFECTIVE_MODIFIERS_FIELD, ANNOTATIONS_FIELD);
    value(types, SyntaxValue.SourceSymbol.class, "syntax-value/source-symbol", NAME_FIELD);
    value(types, SyntaxValue.CompilationUnit.class, "syntax-value/compilation-unit", FORM_FIELD);
    value(types, SyntaxValue.PackageDeclaration.class, "syntax-value/package-declaration", QUALIFIED_NAME_FIELD, "qualifier");
    value(types, SyntaxValue.ImportDeclaration.class, "syntax-value/import-declaration", QUALIFIED_NAME_FIELD, "staticImport",
        "moduleImport", "onDemand");
    enumeration(types, ElementType.class, "annotation-target");
    BY_TYPE = Map.copyOf(types);
    var tags = new LinkedHashMap<String, Shape>();
    types.values().forEach(shape -> {
      if (tags.putIfAbsent(shape.tag(), shape) != null) {
        throw new IllegalStateException("Duplicate retained source tag: " + shape.tag());
      }
    });
    BY_TAG = Map.copyOf(tags);
  }

  private AstValueSchema() {
  }

  static Shape type(Class<?> type) {
    var shape = BY_TYPE.get(type);
    if (shape == null) {
      throw new IllegalArgumentException("Unregistered retained source value: " + type.getName());
    }
    return shape;
  }

  static Shape tag(String tag) {
    var shape = BY_TAG.get(tag);
    if (shape == null) {
      throw new IllegalArgumentException("Unknown retained source tag: " + tag);
    }
    return shape;
  }

  private static void record(Map<Class<?>, Shape> types, Class<?> type, String tag, String... fields) {
    var actual = Arrays.stream(Objects.requireNonNull(type.getRecordComponents(), "record components"))
        .map(component -> component.getName()).toList();
    if (!actual.equals(List.of(fields))) {
      throw new IllegalStateException("Retained record schema changed without a format revision: " + tag);
    }
    value(types, type, tag, fields);
  }

  private static void value(Map<Class<?>, Shape> types, Class<?> type, String tag, String... fields) {
    try {
      var accessors = new Method[fields.length];
      var parameters = new Class<?>[fields.length];
      for (var index = 0; index < fields.length; index++) {
        accessors[index] = type.getMethod(fields[index]);
        parameters[index] = accessors[index].getReturnType();
      }
      var constructor = type.getConstructor(parameters);
      if (types.putIfAbsent(type, new Shape(type, tag, List.of(fields), List.of(accessors), constructor)) != null) {
        throw new IllegalStateException("Duplicate retained source type: " + type.getName());
      }
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Retained source schema does not match its public value contract: " + tag, exception);
    }
  }

  private static void enumeration(Map<Class<?>, Shape> types, Class<?> type, String tag) {
    types.put(type, new Shape(type, tag, List.of(), List.of(), null));
  }

  record Shape(Class<?> type, String tag, List<String> fields, List<Method> accessors, @Nullable Constructor<?> constructor) {
    List<Object> components(Object value) {
      return accessors.stream().map(accessor -> invoke(accessor, value)).toList();
    }

    Object construct(List<Object> values) {
      if (values.size() != fields.size()) {
        throw new IllegalArgumentException("Wrong retained field count for " + tag);
      }
      try {
        return Objects.requireNonNull(constructor, "value constructor").newInstance(values.toArray());
      } catch (InvocationTargetException exception) {
        throw constructorFailure(exception);
      } catch (ReflectiveOperationException exception) {
        throw new IllegalStateException("Cannot construct registered retained source value: " + tag, exception);
      }
    }
  }

  private static Object invoke(Method accessor, Object value) {
    try {
      return Objects.requireNonNull(accessor.invoke(value), "retained component");
    } catch (InvocationTargetException exception) {
      throw constructorFailure(exception);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot read registered retained source component", exception);
    }
  }

  private static AstContractException constructorFailure(InvocationTargetException exception) {
    var cause = Objects.requireNonNull(exception.getCause(), "constructor cause");
    if (cause instanceof Error error) {
      throw error;
    }
    return new AstContractException("Registered retained source value construction failed", cause);
  }
}
