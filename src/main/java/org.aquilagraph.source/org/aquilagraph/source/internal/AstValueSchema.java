package org.aquilagraph.source.internal;

import org.aquilagraph.source.admission.CopiedAstValues;

import java.lang.annotation.ElementType;
import java.lang.invoke.MethodType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.aquilagraph.identity.api.CanonicalValueReader;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CapturedLocation;
import org.aquilagraph.identity.api.CapturedLocation.RootKind;
import org.aquilagraph.identity.api.CapturedLocation.Syntax;
import org.aquilagraph.source.admission.AstValueAdmission;
import org.aquilagraph.source.admission.CoverageAdmission;
import org.aquilagraph.source.api.JavaAstAggregate;
import org.aquilagraph.source.api.LanguageFeatureCoverage;
import org.aquilagraph.source.api.LanguageFeatureCoverageResult;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.identity.api.AnalysisEnvironmentSnapshot;
import org.aquilagraph.identity.api.DependencySemantics;
import org.aquilagraph.identity.api.ModuleCandidate;
import org.aquilagraph.identity.api.ModuleNameOrigin;
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
import org.aquilagraph.source.api.InvocationSelection;
import org.aquilagraph.source.api.JavaDeclarationModifier;
import org.aquilagraph.source.api.JavaField;
import org.aquilagraph.source.api.JavaMethod;
import org.aquilagraph.source.api.JavaParameter;
import org.aquilagraph.source.api.JavaProgram;
import org.aquilagraph.source.api.JavaRecordComponent;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.JavaSynthesizedDeclaration;
import org.aquilagraph.source.api.JavaType;
import org.aquilagraph.source.api.JavaTypeNesting;
import org.aquilagraph.source.api.JavaTypeCategory;
import org.aquilagraph.source.api.JavaTypeIdentity;
import org.aquilagraph.source.api.JavaTypeKind;
import org.aquilagraph.source.api.JavaTypeModifier;
import org.aquilagraph.source.api.JavaTypeParameter;
import org.aquilagraph.source.api.JavaTypeReference;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.JavaExecutableErasure;
import org.aquilagraph.source.api.LanguageFeature;
import org.aquilagraph.source.api.LanguageFeatureCategory;
import org.aquilagraph.source.api.LanguageFeatureClassification;
import org.aquilagraph.source.api.LanguageFeatureObservation;
import org.aquilagraph.source.api.LanguageFeatureOccurrence;
import org.aquilagraph.source.api.LanguageFeaturePolicy;
import org.aquilagraph.source.api.MethodBodyOrigin;
import org.aquilagraph.source.api.ExecutionEvidence;
import org.aquilagraph.source.api.ExecutionScope;
import org.aquilagraph.source.api.PackageQualifierEvidence;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.JavaTypeDeclarationIdentity;
import org.aquilagraph.source.api.SourceContext;
import org.aquilagraph.source.api.SourceCoverageInventory;
import org.aquilagraph.source.api.SourceOwnerPath;
import org.aquilagraph.source.api.StaticMemberEvidence;
import org.aquilagraph.source.api.StaticMemberKind;
import org.aquilagraph.source.api.StringExpressionEvidence;
import org.aquilagraph.source.admission.SyntaxAnalysisInput;
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
import org.aquilagraph.source.api.WrittenExpressionRegion;
import org.aquilagraph.source.api.NormalizedExpressionCorrespondence;
import org.aquilagraph.source.api.DecimalMinimumOperand;
import org.aquilagraph.source.api.CompilerExpressionAnchor;
import org.aquilagraph.source.api.WrittenTypeEvidence;

/** Closed retained-value tags and ordered public components. */
final class AstValueSchema {
  static final String OCCURRENCE_REFERENCE = "source-node-reference";
  private static final String STRUCTURAL_PATH_FIELD = "structuralPath";
  private static final String CHILDREN = "children";
  private static final String MODE_FIELD = "mode";
  private static final String THROWN_TYPES_FIELD = "thrownTypes";
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
  private static final String STATIC_TYPE_FIELD = "staticType";
  private static final String LEXICAL_OWNER_KEY_FIELD = "lexicalOwnerKey";
  private static final String METHOD_KEY_FIELD = "methodKey";
  private static final String KEY_FIELD = "key";
  private static final String SCOPE_KEY_FIELD = "scopeKey";
  private static final String NAME_FIELD = "name";
  private static final String NODE_KEY_FIELD = "nodeKey";
  private static final String ORDINAL_FIELD = "ordinal";
  private static final String ORIGIN_FIELD = "origin";
  private static final String ORIGINS_FIELD = "origins";
  private static final String OWNER_KEY_FIELD = "ownerKey";
  private static final String OWNER_PATH_FIELD = "ownerPath";
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
    scalars(types);
    special(types, CapturedLocation.class, "captured-location", AstValueSchema::location, "syntax", "rootKind", "rootComponents",
        "components", "spelling");
    enumeration(types, Syntax.class, "captured-location/syntax");
    enumeration(types, RootKind.class, "captured-location/root-kind");
    record(types, DependencySemantics.class, "dependency-semantics", "state", "modules");
    enumeration(types, DependencySemantics.State.class, "dependency-semantics/state");
    record(types, ModuleCandidate.class, "module-candidate", "member", "lookupName", "descriptorName", "nameOrigin");
    enumeration(types, ModuleNameOrigin.class, "module-name-origin");
    record(types, AstIdentity.class, "ast-identity", "astResultKey", "softwareSignature", "sourceModelVersion", "astProfileVersion",
        "semanticProductionRevision", "canonicalContentDigest");
    record(types, BodyRef.class, "body-ref", "astIdentity", METHOD_KEY_FIELD, "sourceOccurrenceKey", "nonwrittenProvenance");
    record(types, CompilerConfiguration.class, "compiler-configuration", "release", "classPath", "modulePath", "options");
    enumeration(types, DependencyKind.class, "dependency-kind");
    record(types, DependencySnapshot.class, "dependency-snapshot", "location", KIND_FIELD, "byteCount", "entryCount", "fingerprint",
        "semantics");
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
    record(types, CoverageExecutableType.class, "coverage-executable-type", "variables", PARAMETERS_FIELD, RETURN_TYPE_FIELD,
        THROWN_TYPES_FIELD, "receiverType");
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
    record(types, CoverageSourceFact.class, "coverage-source-fact", STRUCTURAL_PATH_FIELD, SOURCE_KIND_FIELD, "syntaxKind", TYPE_FIELD,
        VALUE_FIELD, CHILDREN, "semanticReference", "sourceAssociation");
    record(types, CoverageSourceFact.Step.class, "coverage-source-fact/step", ROLE_FIELD, ORDINAL_FIELD);
    record(types, CoverageSourceFact.Child.class, "coverage-source-fact/child", ROLE_FIELD, ORDINAL_FIELD, NODE_KEY_FIELD);
    record(types, CoverageSourceFact.Association.class, "coverage-source-fact/association", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD,
        OWNER_PATH_FIELD, SOURCE_KIND_FIELD, ORIGIN_FIELD, "normalizedExpression");
    record(types, CoverageSyntaxFactValue.class, "coverage-syntax-fact-value", STRUCTURAL_PATH_FIELD, SOURCE_KIND_FIELD, "syntaxKind",
        TYPE_FIELD, VALUE_FIELD, CHILDREN, "semanticReference", "sourceAssociation");
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
    record(types, DeclarationMemberOrder.class, "declaration-member-order", SOURCE_FILE_KEY_FIELD, OWNER_TYPE_KEY_FIELD, "members",
        "attributedTreeOrder");
    record(types, DeclarationMemberOrder.TreeOrder.class, "declaration-member-order/tree-order", OWNER_PATH_FIELD, "members");
    record(types, DeclarationMemberOrder.Member.class, "declaration-member-order/member", DECLARATION_KEY_FIELD, KIND_FIELD, "displayValue",
        "provenance", "writtenOrdinal");
    record(types, DeclarationTypeEvidence.class, "declaration-type-evidence", OWNER_TYPE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, IDENTITY_FIELD,
        "structure");
    record(types, DiagnosticEmission.class, "diagnostic-emission", ADDRESS_FIELD, "diagnostic", "cause", "contexts", "slot");
    record(types, DiagnosticEmission.Address.class, "diagnostic-emission/address", "producer", "owner", ORDINAL_FIELD);
    enumeration(types, DiagnosticSeverity.class, "diagnostic-severity");
    enumeration(types, DiagnosticStage.class, "diagnostic-stage");
    record(types, ConstructorEvidence.class, "constructor-evidence", SCOPE_KEY_FIELD, "invocationNodeId", "ownerTypeKey",
        "selectedConstructorKey", FORM_FIELD, "corroboration");
    record(types, ConstructorContext.class, "constructor-context", "superclassParameterTypes", "insertedInvocation");
    record(types, CompilerInsertedInvocation.class, "compiler-inserted-invocation", SOURCE_FILE_KEY_FIELD, "compilerPath", "reference",
        "semantics", "applicability");
    record(types, EvidenceOrigin.class, "evidence-origin", SCOPE_KEY_FIELD, NODE_KEY_FIELD, "slot", "sourceRange");
    enumeration(types, EvidenceState.class, "evidence-state");
    record(types, EvidenceValue.class, "evidence-value", "state", "knownValue", ORIGIN_FIELD, "explanation", "diagnostic");
    record(types, FeatureContextOutcome.class, "feature-context-outcome", ADDRESS_FIELD, "occurrence", "observation", "encounteredValue",
        "support", "emissions");
    record(types, FeatureContextOutcome.Support.class, "feature-context-outcome/support", MODE_FIELD, "facts", "policyContext",
        "sourceFile", "negativeState");
    enumeration(types, FeatureContextOutcome.Mode.class, "feature-context-outcome/mode");
    record(types, InvocationApplicability.class, "invocation-applicability", SCOPE_KEY_FIELD, "consumerNodeId", MODE_FIELD, "formalCount",
        "varargArrayType", "erasedArrayType", "elementType");
    enumeration(types, InvocationApplicabilityMode.class, "invocation-applicability-mode");
    record(types, InvocationEvidence.class, "invocation-evidence", "applicability");
    enumeration(types, InvocationForm.class, "invocation-form");
    enumeration(types, InvocationMode.class, "invocation-mode");
    enumeration(types, InvocationSelection.class, "invocation-selection");
    record(types, InvocationSemantics.class, "invocation-semantics", MODE_FIELD, "qualifyingType", FORM_FIELD, "selection");
    enumeration(types, JavaDeclarationModifier.class, "java-declaration-modifier");
    record(types, JavaField.class, "java-field", "fieldKey", OWNER_TYPE_KEY_FIELD, NAME_FIELD, STATIC_TYPE_FIELD, "modifiers",
        "enumConstant", SOURCE_DECLARED_FIELD, ORIGINS_FIELD);
    record(types, JavaMethod.class, "java-method", METHOD_KEY_FIELD, OWNER_TYPE_KEY_FIELD, NAME_FIELD, "signature", RETURN_TYPE_FIELD,
        PARAMETERS_FIELD, "modifiers", "constructor", SOURCE_DECLARED_FIELD, "hasBody", ORIGINS_FIELD);
    record(types, JavaParameter.class, "java-parameter", "parameterKey", NAME_FIELD, STATIC_TYPE_FIELD, SOURCE_DECLARED_FIELD,
        ORIGINS_FIELD);
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
        "recordComponents", "methods", "synthesizedDeclarations", ORIGINS_FIELD, "nesting", STATIC_TYPE_FIELD, "displayName");
    record(types, JavaTypeNesting.class, "java-type-nesting", KIND_FIELD, "lexicalOwnerKey", "ownerDetails");
    record(types, JavaTypeNesting.OwnerDetails.class, "java-type-nesting/owner-details", KIND_FIELD, "displayName", "written");
    enumeration(types, JavaTypeNesting.Kind.class, "java-type-nesting/kind");
    enumeration(types, JavaTypeCategory.class, "java-type-category");
    record(types, JavaTypeIdentity.class, "java-type-identity", CATEGORY_FIELD, "displayName", "erasedName");
    enumeration(types, JavaTypeKind.class, "java-type-kind");
    enumeration(types, JavaTypeModifier.class, "java-type-modifier");
    record(types, JavaTypeParameter.class, "java-type-parameter", "typeParameterKey", OWNER_TYPE_KEY_FIELD, NAME_FIELD, BOUNDS_FIELD,
        SOURCE_DECLARED_FIELD, ORIGINS_FIELD);
    record(types, JavaTypeReference.class, "java-type-reference", IDENTITY_FIELD, DECLARATION_KEY_FIELD);
    record(types, JavaTypeStructure.Primitive.class, "java-type-structure/primitive", KIND_FIELD);
    record(types, JavaTypeStructure.Declared.class, "java-type-structure/declared", DECLARATION_KEY_FIELD, LEXICAL_OWNER_KEY_FIELD,
        ENCLOSING_TYPE_FIELD, ARGUMENTS_FIELD, "enclosingOwnerType");
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
    record(types, JavaTypeStructure.Executable.class, "java-type-structure/executable", "variables", PARAMETERS_FIELD, RETURN_TYPE_FIELD,
        "receiver", THROWN_TYPES_FIELD, "erasure");
    record(types, JavaExecutableErasure.class, "java-executable-erasure", PARAMETERS_FIELD, RETURN_TYPE_FIELD);
    record(types, LanguageFeature.class, "language-feature", CATEGORY_FIELD, "identifier");
    enumeration(types, LanguageFeatureCategory.class, "language-feature-category");
    record(types, LanguageFeatureClassification.class, "language-feature-classification", "feature", "policy", "representationBoundary",
        "irrelevanceRationale", "sourceLocator", "diagnosticCode");
    record(types, LanguageFeatureObservation.class, "language-feature-observation", SOURCE_FILE_KEY_FIELD, "classification", ORIGINS_FIELD,
        "diagnostic");
    record(types, LanguageFeatureOccurrence.class, "language-feature-occurrence", SOURCE_FILE_KEY_FIELD, "classification", ORIGINS_FIELD);
    enumeration(types, LanguageFeaturePolicy.class, "language-feature-policy");
    enumeration(types, MethodBodyOrigin.class, "method-body-origin");
    record(types, ExecutionEvidence.class, "method-evidence", "uses", "invocations", "staticMembers", "stringExpressions", "constructors");
    record(types, PackageQualifierEvidence.class, "package-qualifier-evidence", SOURCE_KIND_FIELD, ORIGIN_FIELD);
    enumeration(types, ReferenceKind.class, "reference-kind");
    enumeration(types, ResolutionStatus.class, "resolution-status");
    record(types, SemanticReference.class, "semantic-reference", "referenceKind", "status", "symbolKey", "typeName", "typeAlternativeNames",
        "assignableToTypeNames", "declarationIdentity");
    record(types, JavaTypeDeclarationIdentity.class, "java-type-declaration-identity", "moduleName", "binaryName");
    record(types, SyntaxValue.ModuleTypeReference.class, "syntax-value/module-type-reference", NAME_FIELD);
    record(types, SourceContext.class, "source-context", ADDRESS_FIELD, "feature", ORIGINS_FIELD, "writtenValue", "requiresAttribution");
    enumeration(types, SourceContext.Partition.class, "source-context/partition");
    record(types, SourceContext.Address.class, "source-context/address", SOURCE_FILE_KEY_FIELD, "partition", OWNER_PATH_FIELD, ROLE_FIELD,
        ORDINAL_FIELD);
    record(types, SourceCoverageInventory.class, "source-coverage-inventory", "snapshot", "release", "contractIdentity", "contexts",
        "writtenExpressions");
    record(types, SourceOwnerPath.class, "source-owner-path", "steps");
    record(types, SourceOwnerPath.Step.class, "source-owner-path/step", ROLE_FIELD, ORDINAL_FIELD);
    enumeration(types, SourceOwnerPath.Role.class, "source-owner-path/role");
    record(types, StaticMemberEvidence.class, "static-member-evidence", SCOPE_KEY_FIELD, "accessNodeId", "selectedMemberKey", KIND_FIELD,
        "declaringOwner", "constantVariable");
    enumeration(types, StaticMemberKind.class, "static-member-kind");
    record(types, StringExpressionEvidence.class, "string-expression-evidence", SCOPE_KEY_FIELD, "binaryNodeId", "stringType",
        "leftIsString", "rightIsString", "compileTimeConstant");
    record(types, SyntaxAnalysisInput.class, "syntax-analysis-input", "snapshot", "environmentSnapshot", "program", "aggregate",
        "diagnostics", "languageFeatureCoverageResult");
    record(types, SyntaxChild.class, "syntax-child", ROLE_FIELD, POSITION_FIELD, "node");
    enumeration(types, SyntaxKind.class, "syntax-kind");
    record(types, SyntaxNodeTuple.class, "syntax-node-tuple", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, STRUCTURAL_PATH_FIELD,
        SOURCE_KIND_FIELD);
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
    enumeration(types, SyntaxValue.CompilationUnitRange.class, "syntax-value/compilation-unit-range");
    record(types, SyntaxValue.QualifiedName.class, "syntax-value/qualified-name", "components");
    record(types, SyntaxValue.StatementLabel.class, "syntax-value/statement-label", NAME_FIELD);
    record(types, SyntaxValue.LocalDeclaration.class, "syntax-value/local-declaration", NAME_FIELD, "writtenType", WRITTEN_MODIFIERS_FIELD,
        "bindingEvidence");
    record(types, LocalBindingEvidence.class, "local-binding-evidence", "declarationKey", "typeForm", "context", "initialized",
        "annotations", "sharedAnnotationKeys");
    record(types, SyntaxValue.Lambda.class, "syntax-value/lambda", "bodyKind", "parenthesized", PARAMETERS_FIELD, RETURN_TYPE_FIELD);
    record(types, SyntaxValue.MemberReference.class, "syntax-value/member-reference", "name", MODE_FIELD, "qualifier", "selectedType",
        "selection", "staticMember");
    record(types, SyntaxValue.PatternBinding.class, "syntax-value/pattern-binding", "resolvedAccesses");
    enumeration(types, SyntaxValue.ReferenceMode.class, "reference-mode");
    enumeration(types, SyntaxValue.ReferenceQualifier.class, "reference-qualifier");
    enumeration(types, SyntaxValue.ReferenceSelection.class, "reference-selection");
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
        ANNOTATIONS_FIELD, "typeParameters", "receiver", PARAMETERS_FIELD, RETURN_TYPE_FIELD, THROWN_TYPES_FIELD, "body", VAR_ARGS_FIELD);
    record(types, SyntaxValue.ExecutableDeclaration.AttributedHeader.class, "syntax-value/executable-declaration/attributed-header",
        "receiverType", "receiverOwnerKey", "receiverSimpleName", "enclosingTypeKey", THROWN_TYPES_FIELD, "defaultValue");
    record(types, SyntaxValue.FieldAccess.class, "syntax-value/field-access", NAME_FIELD, "receiver");
    record(types, SyntaxValue.Invocation.class, "syntax-value/invocation", NAME_FIELD, FORM_FIELD, "semantics");
    record(types, SyntaxValue.Receiver.class, "syntax-value/receiver", KIND_FIELD);
    record(types, SyntaxValue.Literal.class, "syntax-value/literal", "text", "restrictedDecimal");
    record(types, CompilerExpressionAnchor.Execution.class, "compiler-expression-anchor/execution", "path");
    record(types, CompilerExpressionAnchor.AnnotationSource.class, "compiler-expression-anchor/annotation-source", OWNER_PATH_FIELD);
    record(types, DecimalMinimumOperand.class, "decimal-minimum-operand", CATEGORY_FIELD, "magnitude");
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
    record(types, SyntaxValue.SwitchLabel.Pattern.class, "syntax-value/switch-label/pattern", "childPosition");
    record(types, SyntaxValue.Unsupported.class, "syntax-value/unsupported", "compilerKind");
    record(types, ChildUseEvidence.class, "child-use-evidence", SCOPE_KEY_FIELD, "consumerNodeId", ROLE_FIELD, POSITION_FIELD, "sourceType",
        "selectedTarget", "conversion", "referenceCheck");
    record(types, ConversionEvidence.class, "conversion-evidence", SCOPE_KEY_FIELD, "consumerNodeId", ROLE_FIELD, POSITION_FIELD,
        "sourceType", "selectedTarget", "checkedReferenceTarget", "unboxedType", "effectivePrimitiveType", "preBoxPrimitiveType",
        "boxedType");
    record(types, UnmatchedSourceFact.class, "unmatched-source-fact", "owner", "fact", ORDINAL_FIELD, "reason", "emission");
    enumeration(types, UnmatchedSourceFact.Reason.class, "unmatched-source-fact/reason");
    record(types, WrittenSourceAssociation.class, "written-source-association", NODE_KEY_FIELD, SOURCE_FILE_KEY_FIELD, OWNER_PATH_FIELD,
        SOURCE_KIND_FIELD, ORIGIN_FIELD, "normalizedExpression");
    record(types, WrittenExpressionRegion.class, "written-expression-region", "compilerOwner", ORIGIN_FIELD, "occurrences");
    record(types, WrittenExpressionRegion.Occurrence.class, "written-expression-region/occurrence", "path", SOURCE_KIND_FIELD, ORIGIN_FIELD,
        "compilerOperand", "restrictedDecimal", "literalValue");
    record(types, NormalizedExpressionCorrespondence.class, "normalized-expression-correspondence", "region", "occurrencePath",
        "compilerAnchor", "compilerKind", "compilerType", "derivedConstant", "derivedConstantExpression", "observations");
    record(types, NormalizedExpressionCorrespondence.Observation.class, "normalized-expression-correspondence/observation",
        OWNER_PATH_FIELD, SOURCE_KIND_FIELD, ORIGIN_FIELD, TYPE_FIELD, "constant", "constantExpression");
    record(types, WrittenTypeEvidence.class, "written-type-evidence", SOURCE_KIND_FIELD, ORIGIN_FIELD, TYPE_FIELD);
    record(types, CopiedAstValues.class, "copied-ast-values", "snapshot", "sources", "bodies", "memberOrders", "associations",
        "declarationTypes", "scopes");
    record(types, ExecutionScope.Method.class, "execution-scope/method", KEY_FIELD);
    record(types, ExecutionScope.Field.class, "execution-scope/field", KEY_FIELD, OWNER_KEY_FIELD);
    record(types, ExecutionScope.EnumConstant.class, "execution-scope/enum-constant", KEY_FIELD, OWNER_KEY_FIELD, "selectedConstructor");
    record(types, ExecutionScope.Initializer.class, "execution-scope/initializer", KEY_FIELD, OWNER_KEY_FIELD, ORIGIN_FIELD, KIND_FIELD);
    record(types, CopiedAstValues.Scope.class, "copied-ast-values/scope", "scope", "root", "uses", "invocations", "staticAccesses",
        "concatenations", "attributedOccurrences");
    record(types, CopiedAstValues.Body.class, "copied-ast-values/body", METHOD_KEY_FIELD, "root", ORIGIN_FIELD, "uses", "invocations",
        "staticAccesses", "concatenations", "constructors", "attributedOccurrences");
    record(types, AttributedBodyOccurrence.class, "attributed-body-occurrence", SCOPE_KEY_FIELD, "nodeKey", SOURCE_FILE_KEY_FIELD,
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
    value(types, SyntaxValue.InferredRecordType.class, "syntax-value/inferred-record-type", NAME_FIELD);
    value(types, SyntaxValue.CompilationUnit.class, "syntax-value/compilation-unit", FORM_FIELD, "range");
    value(types, SyntaxValue.PackageDeclaration.class, "syntax-value/package-declaration", QUALIFIED_NAME_FIELD, "qualifier");
    value(types, SyntaxValue.ImportDeclaration.class, "syntax-value/import-declaration", QUALIFIED_NAME_FIELD, "staticImport",
        "moduleImport", "onDemand");
    enumeration(types, ElementType.class, "annotation-target");
    special(types, SyntaxNode.class, "source-node", AstValueSchema::node, SOURCE_FILE_KEY_FIELD, STRUCTURAL_PATH_FIELD, SOURCE_KIND_FIELD,
        "syntaxKind", TYPE_FIELD, "value", CHILDREN, "semanticReference", "origin");
    register(types, JavaAstAggregate.class,
        new Fields(
            JavaAstAggregate.class, "source-aggregate", List.of(new Field("copiedValues", CopiedAstValues.class,
                value -> SourceValueCapture.capture((JavaAstAggregate) value), true, UnaryOperator.identity())),
            values -> AstValueAdmission.admit(required(values.getFirst(), CopiedAstValues.class)), false));
    special(types, LanguageFeatureCoverage.class, "source-coverage-contract", AstValueSchema::coverageContract, "release",
        "classifications");
    special(types, LanguageFeatureCoverageResult.class, "source-coverage-result", AstValueSchema::coverageResult, "coverage",
        "validationEvidence");
    special(types, SyntaxValue.Annotation.class, "syntax-value/annotation", AstValueSchema::annotation, FORM_FIELD, "arguments", "location",
        "applicableTargets", "closestTypeAdmissible", "argumentFailureCode", "repeatableContainerKey");
    configureField(types, InputSnapshot.class, "sourceRoot", false, UnaryOperator.identity());
    configureField(types, DependencySnapshot.class, "location", false, UnaryOperator.identity());
    configureField(types, SyntaxNode.class, CHILDREN, true, AstValueSchema::canonicalSyntaxChildren);
    configureField(types, CoverageSourceFact.class, CHILDREN, true, AstValueSchema::canonicalCoverageChildren);
    BY_TYPE = Map.copyOf(types);
    var tags = new LinkedHashMap<String, Shape>();
    types.values().forEach(shape -> {
      if (tags.putIfAbsent(shape.tag(), shape) != null) {
        throw new IllegalStateException("Duplicate retained source tag: " + shape.tag());
      }
    });
    for (var container : Container.values()) {
      if (tags.putIfAbsent(container.tag(), container) != null) {
        throw new IllegalStateException("Duplicate retained container tag: " + container.tag());
      }
    }
    BY_TAG = Map.copyOf(tags);
  }

  private AstValueSchema() {
  }

  static Shape shape(Object value) {
    return switch (value) {
      case Optional<?> optional -> optional.isPresent() ? Container.PRESENT : Container.ABSENT;
      case List<?> _ -> Container.LIST;
      case Set<?> _ -> Container.SET;
      case JavaAstAggregate _ -> type(JavaAstAggregate.class);
      case LanguageFeatureCoverageResult _ -> type(LanguageFeatureCoverageResult.class);
      case Enum<?> enumeration -> type(enumeration.getDeclaringClass());
      default -> type(value.getClass());
    };
  }

  private static Shape type(Class<?> type) {
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

  private static void value(Map<Class<?>, Shape> types, Class<?> type, String tag, String... names) {
    var fields = fields(type, names);
    try {
      var parameters = fields.stream().map(Method::getReturnType).toArray(Class<?>[]::new);
      var constructor = type.getConstructor(parameters);
      register(types, type,
          new Fields(type, tag, fields.stream().map(AstValueSchema::field).toList(), values -> construct(constructor, values), false));
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Retained source schema does not match its public value contract: " + tag, exception);
    }
  }

  private static void special(Map<Class<?>, Shape> types, Class<?> type, String tag, Function<List<Object>, Object> construct,
      String... names) {
    register(types, type,
        new Fields(type, tag, fields(type, names).stream().map(AstValueSchema::field).toList(), construct, type == SyntaxNode.class));
  }

  private static List<Method> fields(Class<?> type, String... names) {
    var accessors = new ArrayList<Method>(names.length);
    try {
      for (var name : names) {
        accessors.add(type.getMethod(name));
      }
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Missing retained source accessor: " + type.getName(), exception);
    }
    return List.copyOf(accessors);
  }

  private static Field field(Method accessor) {
    return new Field(accessor.getName(), accessor.getGenericReturnType(), value -> invoke(accessor, value), true, UnaryOperator.identity());
  }

  private static void configureField(Map<Class<?>, Shape> types, Class<?> type, String name, boolean identifies,
      UnaryOperator<Object> canonical) {
    var shape = required(Objects.requireNonNull(types.get(type), "registered fields"), Fields.class);
    if (shape.fields().stream().noneMatch(field -> field.name().equals(name))) {
      throw new IllegalStateException("No retained component " + name + " for " + shape.tag());
    }
    var fields = shape.fields().stream()
        .map(field -> field.name().equals(name) ? new Field(name, field.type(), field.access(), identifies, canonical) : field).toList();
    types.put(type, new Fields(shape.valueType(), shape.tag(), fields, shape.construct(), shape.occurrence()));
  }

  private static void register(Map<Class<?>, Shape> types, Class<?> type, Shape shape) {
    if (types.putIfAbsent(type, shape) != null) {
      throw new IllegalStateException("Duplicate retained source type: " + type.getName());
    }
  }

  private static void enumeration(Map<Class<?>, Shape> types, Class<?> type, String tag) {
    var constants = Objects.requireNonNull(type.getEnumConstants(), "registered enum constants");
    register(types, type, new Scalar(type, tag, (writer, value) -> writer.text(((Enum<?>) value).name()), reader -> {
      var name = reader.text();
      return Arrays.stream(constants).filter(value -> ((Enum<?>) value).name().equals(name)).findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unknown retained enum value: " + tag + "/" + name));
    }));
  }

  private static void scalars(Map<Class<?>, Shape> types) {
    scalar(types, String.class, "text", CanonicalValueWriter::text, CanonicalValueReader::text);
    scalar(types, Boolean.class, "flag", CanonicalValueWriter::flag, CanonicalValueReader::flag);
    scalar(types, Integer.class, "int32", CanonicalValueWriter::integer, CanonicalValueReader::integer);
    scalar(types, Long.class, "int64", CanonicalValueWriter::longInteger, CanonicalValueReader::longInteger);
    scalar(types, Float.class, "float32", CanonicalValueWriter::floating, CanonicalValueReader::floating);
    scalar(types, Double.class, "float64", CanonicalValueWriter::floating, CanonicalValueReader::doubleFloating);
    scalar(types, Byte.class, "int8", (writer, number) -> writer.integer(number),
        reader -> (byte) boundedInteger(reader, Byte.MIN_VALUE, Byte.MAX_VALUE));
    scalar(types, Short.class, "int16", (writer, number) -> writer.integer(number),
        reader -> (short) boundedInteger(reader, Short.MIN_VALUE, Short.MAX_VALUE));
    scalar(types, Character.class, "char16", (writer, character) -> writer.integer(character),
        reader -> (char) boundedInteger(reader, Character.MIN_VALUE, Character.MAX_VALUE));
    scalar(types, BigDecimal.class, "decimal", (writer, number) -> writer.text(number.toString()), reader -> new BigDecimal(reader.text()));
  }

  private static <T> void scalar(Map<Class<?>, Shape> types, Class<T> type, String tag, BiConsumer<CanonicalValueWriter, T> write,
      Function<CanonicalValueReader, T> read) {
    register(types, type, new Scalar(type, tag, (writer, value) -> write.accept(writer, required(value, type)), read::apply));
  }

  private static int boundedInteger(CanonicalValueReader reader, int minimum, int maximum) {
    var value = reader.integer();
    if (value < minimum || value > maximum) {
      throw new IllegalArgumentException("Retained integer is outside its tagged width");
    }
    return value;
  }

  private static CapturedLocation location(List<Object> values) {
    var syntax = required(values.get(0), Syntax.class);
    var spelling = required(values.get(4), String.class);
    var location = CapturedLocation.parse(syntax, spelling);
    if (location.rootKind() != required(values.get(1), RootKind.class) || !location.rootComponents().equals(values.get(2))
        || !location.components().equals(values.get(3)) || !location.spelling().equals(spelling)) {
      throw new IllegalArgumentException("Retained location components contradict its normalized spelling");
    }
    return location;
  }

  private static SyntaxNode node(List<Object> values) {
    var fileKey = required(values.get(0), String.class);
    var path = required(values.get(1), SyntaxStructuralPath.class);
    var sourceKind = required(values.get(2), JavaSourceKind.class);
    var kind = required(values.get(3), SyntaxKind.class);
    var type = required(values.get(4), SyntaxType.class);
    var value = required(values.get(5), SyntaxValue.class);
    var children = sequence(values.get(6), SyntaxChild.class);
    var reference = optional(values.get(7), SemanticReference.class);
    var origin = optional(values.get(8), SourceSpan.class);
    return origin.isPresent()
        ? SyntaxNode.source(fileKey, path, sourceKind, kind, type, value, children, reference, origin.orElseThrow())
        : SyntaxNode.withoutDirectSource(fileKey, path, sourceKind, kind, type, value, children, reference);
  }

  private static LanguageFeatureCoverage coverageContract(List<Object> values) {
    return LanguageFeatureCoverage.forRelease(required(values.get(0), JavaRelease.class),
        sequence(values.get(1), LanguageFeatureClassification.class));
  }

  private static LanguageFeatureCoverageResult coverageResult(List<Object> values) {
    var coverage = required(values.get(0), LanguageFeatureCoverage.class);
    var evidence = required(values.get(1), CoverageValidationEvidence.class);
    return CoverageAdmission.fromEvidence(coverage, evidence.writtenInventory(), evidence.attributedExpectations(), evidence.outcomes(),
        evidence.unmatchedFacts(), evidence.diagnosticEmissions());
  }

  private static SyntaxValue.Annotation annotation(List<Object> values) {
    var form = required(values.get(0), SyntaxValue.AnnotationForm.class);
    var arguments = sequence(values.get(1), SyntaxValue.AnnotationArgumentHeader.class);
    var location = required(values.get(2), SyntaxValue.AnnotationLocation.class);
    var targets = ((Set<?>) values.get(3)).stream().map(target -> required(target, ElementType.class))
        .collect(Collectors.toUnmodifiableSet());
    var admissible = required(values.get(4), Boolean.class);
    var failure = optional(values.get(5), String.class);
    var container = optional(values.get(6), String.class);
    var annotation = failure.isPresent()
        ? SyntaxValue.Annotation.withArgumentFailure(form, arguments, location, targets, admissible, failure.orElseThrow())
        : new SyntaxValue.Annotation(form, arguments, location, targets, admissible);
    return annotation.withRepeatableContainer(container);
  }

  private static List<SyntaxChild> canonicalSyntaxChildren(Object value) {
    return sequence(value, SyntaxChild.class).stream()
        .sorted(Comparator.comparing((SyntaxChild child) -> child.role().stableId()).thenComparingInt(SyntaxChild::position)).toList();
  }

  private static List<CoverageSourceFact.Child> canonicalCoverageChildren(Object value) {
    return sequence(value, CoverageSourceFact.Child.class).stream()
        .sorted(Comparator.comparing((CoverageSourceFact.Child child) -> SyntaxRole.valueOf(child.role()).stableId())
            .thenComparingInt(CoverageSourceFact.Child::ordinal))
        .toList();
  }

  private static <T> T required(Object value, Class<T> type) {
    return Objects.requireNonNull(type.cast(value), "retained component");
  }

  private static <T> List<T> sequence(Object value, Class<T> type) {
    return ((List<?>) value).stream().map(component -> required(component, type)).toList();
  }

  private static <T> Optional<T> optional(Object value, Class<T> type) {
    return ((Optional<?>) value).map(component -> required(component, type));
  }

  sealed interface Shape permits Scalar, Fields, Container {
    String tag();

    Class<?> valueType();
  }

  record Scalar(Class<?> valueType, String tag, BiConsumer<CanonicalValueWriter, Object> write,
      Function<CanonicalValueReader, Object> read) implements Shape {
  }

  record Fields(Class<?> valueType, String tag, List<Field> fields, Function<List<Object>, Object> construct,
      boolean occurrence) implements Shape {
    List<Component> components(Object value, boolean canonical) {
      return fields.stream().filter(field -> !canonical || field.identifies())
          .map(field -> new Component(field.name(), field.component(value, canonical))).toList();
    }

    Object reconstruct(List<Object> values) {
      for (var index = 0; index < fields.size(); index++) {
        var field = fields.get(index);
        requireType(field.type(), values.get(index), tag + "/" + field.name());
      }
      return construct.apply(values);
    }
  }

  record Field(String name, Type type, Function<Object, Object> access, boolean identifies, UnaryOperator<Object> canonical) {
    Object component(Object value, boolean useCanonical) {
      var component = access.apply(value);
      return useCanonical ? canonical.apply(component) : component;
    }
  }

  record Component(String name, Object value) {
  }

  enum Container implements Shape {
    PRESENT("optional/present", Optional.class), ABSENT("optional/absent", Optional.class), LIST("list", List.class), SET("set", Set.class);

    private final String retainedTag;
    private final Class<?> containerClass;

    Container(String tag, Class<?> type) {
      retainedTag = tag;
      containerClass = type;
    }

    @Override
    public Class<?> valueType() {
      return containerClass;
    }

    @Override
    public String tag() {
      return retainedTag;
    }

    List<?> members(Object value, Function<Set<?>, List<?>> orderSet) {
      return switch (this) {
        case PRESENT, ABSENT -> ((Optional<?>) value).stream().toList();
        case LIST -> (List<?>) value;
        case SET -> orderSet.apply((Set<?>) value);
      };
    }
  }

  static void requireAssignable(Type type, Class<?> actual, String field) {
    if (!carrierType(type).isAssignableFrom(actual)) {
      throw new ClassCastException("Wrong retained value type: " + field);
    }
  }

  private static Class<?> carrierType(Type type) {
    return switch (type) {
      case Class<?> exact -> exact.isPrimitive() ? MethodType.methodType(exact).wrap().returnType() : exact;
      case ParameterizedType parameterized -> carrierType(parameterized.getRawType());
      case TypeVariable<?> variable -> carrierType(variable.getBounds()[0]);
      case WildcardType wildcard -> carrierType(wildcard.getUpperBounds()[0]);
      default -> Object.class;
    };
  }

  private static void requireType(Type type, Object value, String field) {
    requireAssignable(type, value.getClass(), field);
    if (type instanceof ParameterizedType parameterized) {
      var elementType = parameterized.getActualTypeArguments()[0];
      switch (value) {
        case Optional<?> optional -> optional.ifPresent(element -> requireType(elementType, element, field));
        case List<?> list -> list.forEach(element -> requireType(elementType, element, field));
        case Set<?> set -> set.forEach(element -> requireType(elementType, element, field));
        case EvidenceValue<?> evidence -> evidence.knownValue().ifPresent(element -> requireType(elementType, element, field));
        default -> throw new IllegalStateException("Unregistered parameterized retained field: " + field);
      }
    }
  }

  private static Object construct(Constructor<?> constructor, List<Object> values) {
    try {
      return constructor.newInstance(values.toArray());
    } catch (InvocationTargetException exception) {
      throw constructorFailure(exception);
    } catch (ReflectiveOperationException exception) {
      throw new IllegalStateException("Cannot construct registered retained source value: " + constructor.getDeclaringClass(), exception);
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
