package org.aquilagraph.source.internal;

import org.aquilagraph.source.api.AnnotationElementValue;
import org.aquilagraph.source.api.AnnotationScalar;
import org.aquilagraph.source.api.CoverageEvidenceValue;
import org.aquilagraph.source.api.CoverageSourceFact;
import org.aquilagraph.source.api.CoverageSyntaxFactValue;
import org.aquilagraph.source.api.JavaTypeStructure;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;

import org.aquilagraph.source.api.CoverageEvidenceValue.TypeIdentity;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import org.aquilagraph.source.api.CoverageEvidenceValue.Field;
import org.aquilagraph.source.api.CoverageEvidenceValue.Structure;
import org.aquilagraph.source.api.CoverageEvidenceValue.Tag;

/** Explicit lossless conversion from closed syntax values to closed neutral fact values. */
public final class CoverageSourceProjection {
  private static final String ANNOTATIONS_FIELD = "annotations";
  private static final String ARGUMENTS_FIELD = "arguments";
  private static final String DECLARATION_KEY_FIELD = "declarationKey";
  private static final String EFFECTIVE_MODIFIERS_FIELD = "effectiveModifiers";
  private static final String FORM_FIELD = "form";
  private static final String KIND_FIELD = "kind";
  private static final String NAME_FIELD = "name";
  private static final String ORDINAL_FIELD = "ordinal";
  private static final String OWNER_KEY_FIELD = "ownerKey";
  private static final String PARAMETERS_FIELD = "parameters";
  private static final String RETURN_TYPE_FIELD = "returnType";
  private static final String TARGETS_FIELD = "targets";
  private static final String TYPE_FIELD = "type";
  private static final String VALUE_FIELD = "value";
  private static final String VAR_ARGS_FIELD = "varArgs";
  private static final String WRITTEN_MODIFIERS_FIELD = "writtenModifiers";
  private CoverageSourceProjection() {
  }

  /**
   * Projects copied syntax facts into the closed source coverage representation.
   *
   * @param source
   *          source
   * @return the resulting coverage source fact
   */
  public static CoverageSourceFact copy(CoverageSyntaxFactValue source) {
    return new CoverageSourceFact(
        source.structuralPath().steps().stream().map(step -> new CoverageSourceFact.Step(step.role().name(), step.position())).toList(),
        source.sourceKind().name(), source.syntaxKind().name(), type(source.type()), value(source.value()),
        source.children().stream().map(child -> new CoverageSourceFact.Child(child.role().name(), child.ordinal(), child.nodeKey()))
            .toList(),
        source.semanticReference(),
        source.sourceAssociation().map(association -> new CoverageSourceFact.Association(association.nodeKey(), association.sourceFileKey(),
            association.ownerPath(), association.sourceKind().name(), association.origin(), association.normalizedExpression())));
  }

  private static Structure value(SyntaxValue source) {
    return switch (source) {
      case SyntaxValue.ModuleTypeReference reference ->
        structure(Tag.MODULE_TYPE_REFERENCE, field(NAME_FIELD, text(reference.name().dottedName())));
      case SyntaxValue.None _ -> structure(Tag.NONE);
      case SyntaxValue.DiamondType _ -> structure(Tag.DIAMOND_TYPE);
      case SyntaxValue.SharedTypeAnnotations shared -> structure(Tag.SHARED_TYPE_ANNOTATIONS,
          field("annotationNodeKeys", sequence(shared.annotationNodeKeys(), CoverageSourceProjection::text)));
      case SyntaxValue.PrimitiveType primitive -> structure(Tag.PRIMITIVE_TYPE, field(KIND_FIELD, named(primitive.kind())));
      case SyntaxValue.PackageQualifier qualifier -> structure(Tag.PACKAGE_QUALIFIER, field(NAME_FIELD, text(qualifier.name())));
      case SyntaxValue.InferredRecordType record -> structure(Tag.INFERRED_RECORD_TYPE, field(NAME_FIELD, text(record.name())));
      case SyntaxValue.ModuleDeclaration module ->
        structure(Tag.MODULE_DECLARATION, field(NAME_FIELD, names(module.qualifiedName())), field("open", flag(module.open())));
      case SyntaxValue.Requires requires -> structure(Tag.REQUIRES, field("module", names(requires.module())),
          field("staticPhase", flag(requires.staticPhase())), field("transitive", flag(requires.transitive())));
      case SyntaxValue.Exports exports -> structure(Tag.EXPORTS, field("packageName", names(exports.packageName())),
          field(TARGETS_FIELD, optional(exports.targets(), targets -> sequence(targets, CoverageSourceProjection::names))));
      case SyntaxValue.Opens opens -> structure(Tag.OPENS, field("packageName", names(opens.packageName())),
          field(TARGETS_FIELD, optional(opens.targets(), targets -> sequence(targets, CoverageSourceProjection::names))));
      case SyntaxValue.Uses uses -> structure(Tag.USES, field("service", names(uses.service())));
      case SyntaxValue.Provides provides -> structure(Tag.PROVIDES, field("service", names(provides.service())),
          field("providers", sequence(provides.providers(), CoverageSourceProjection::names)));
      case SyntaxValue.LocalDeclaration local -> localDeclaration(local);
      case SyntaxValue.StatementLabel label ->
        structure(Tag.STATEMENT_LABEL, field(NAME_FIELD, optional(label.name(), CoverageSourceProjection::text)));
      case SyntaxValue.AnnotationArgument argument ->
        structure(Tag.ANNOTATION_ARGUMENT, field("explicitName", optional(argument.explicitName(), CoverageSourceProjection::text)),
            field("selectedElementKey", text(argument.selectedElementKey())), field(VALUE_FIELD, annotationValue(argument.value())));
      case SyntaxValue.Annotation annotation -> structure(Tag.ANNOTATION, field(FORM_FIELD, named(annotation.form())),
          field(ARGUMENTS_FIELD,
              sequence(annotation.arguments(), argument -> optional(argument.explicitName(), CoverageSourceProjection::text))),
          field("location", named(annotation.location())), field(TARGETS_FIELD, enumSet(annotation.applicableTargets())),
          field("closestTypeAdmissible", flag(annotation.closestTypeAdmissible())),
          field("argumentFailureCode", optional(annotation.argumentFailureCode(), CoverageSourceProjection::text)),
          field("repeatableContainerKey", optional(annotation.repeatableContainerKey(), CoverageSourceProjection::text)));
      case SyntaxValue.TypeDeclaration declaration -> structure(Tag.TYPE_DECLARATION, field(NAME_FIELD, text(declaration.name())),
          field(DECLARATION_KEY_FIELD, text(declaration.declarationKey())),
          field(WRITTEN_MODIFIERS_FIELD, enumSet(declaration.writtenModifiers())),
          field(EFFECTIVE_MODIFIERS_FIELD, enumSet(declaration.effectiveModifiers())), field("shape", typeShape(declaration.shape())));
      case SyntaxValue.VariableDeclaration declaration -> structure(Tag.VARIABLE_DECLARATION, field(NAME_FIELD, text(declaration.name())),
          field(OWNER_KEY_FIELD, text(declaration.ownerKey())), field(DECLARATION_KEY_FIELD, text(declaration.declarationKey())),
          field("sharedDeclaratorKeys", sequence(declaration.sharedDeclaratorKeys(), CoverageSourceProjection::text)),
          field(KIND_FIELD, named(declaration.kind())), field(WRITTEN_MODIFIERS_FIELD, enumSet(declaration.writtenModifiers())),
          field(EFFECTIVE_MODIFIERS_FIELD, enumSet(declaration.effectiveModifiers())),
          field("declaredType", new TypeIdentity(declaration.declaredType())),
          field(ANNOTATIONS_FIELD, integral(declaration.annotations())), field(TYPE_FIELD, flag(declaration.type())),
          field("initializer", flag(declaration.initializer())), field(ARGUMENTS_FIELD, integral(declaration.arguments())),
          field("argumentList", flag(declaration.argumentList())), field("constantBody", flag(declaration.constantBody())));
      case SyntaxValue.RecordComponentDeclaration declaration -> recordComponent(declaration);
      case SyntaxValue.TypeParameterDeclaration parameter -> structure(Tag.TYPE_PARAMETER, field(NAME_FIELD, text(parameter.name())),
          field(DECLARATION_KEY_FIELD, text(parameter.declarationKey())), field(OWNER_KEY_FIELD, text(parameter.ownerKey())),
          field(ORDINAL_FIELD, integral(parameter.ordinal())), field(ANNOTATIONS_FIELD, integral(parameter.annotations())),
          field("bounds", integral(parameter.bounds())), field("upperBound", typeStructure(parameter.upperBound())));
      case SyntaxValue.ExecutableDeclaration declaration ->
        structure(Tag.EXECUTABLE_DECLARATION, field(NAME_FIELD, text(declaration.name())),
            field(OWNER_KEY_FIELD, text(declaration.ownerKey())), field(DECLARATION_KEY_FIELD, text(declaration.declarationKey())),
            field(KIND_FIELD, named(declaration.kind())), field(WRITTEN_MODIFIERS_FIELD, enumSet(declaration.writtenModifiers())),
            field(EFFECTIVE_MODIFIERS_FIELD, enumSet(declaration.effectiveModifiers())),
            field("shape", executableShape(declaration.shape())), field("attributed", attributedHeader(declaration.attributedHeader())));
      case SyntaxValue.ParameterDeclaration parameter ->
        structure(Tag.PARAMETER, field(NAME_FIELD, text(parameter.name())), field(DECLARATION_KEY_FIELD, text(parameter.declarationKey())),
            field(WRITTEN_MODIFIERS_FIELD, enumSet(parameter.writtenModifiers())),
            field(EFFECTIVE_MODIFIERS_FIELD, enumSet(parameter.effectiveModifiers())),
            field(ANNOTATIONS_FIELD, integral(parameter.annotations())), field(TYPE_FIELD, flag(parameter.type())),
            field(VAR_ARGS_FIELD, flag(parameter.varArgs())));
      case SyntaxValue.ReceiverParameter receiver -> structure(Tag.RECEIVER_PARAMETER, field(NAME_FIELD, text(receiver.name())),
          field(OWNER_KEY_FIELD, text(receiver.ownerKey())), field(WRITTEN_MODIFIERS_FIELD, enumSet(receiver.writtenModifiers())),
          field(EFFECTIVE_MODIFIERS_FIELD, enumSet(receiver.effectiveModifiers())),
          field(ANNOTATIONS_FIELD, integral(receiver.annotations())));
      case SyntaxValue.SourceSymbol symbol -> structure(Tag.SOURCE_SYMBOL, field(NAME_FIELD, text(symbol.name())));
      case SyntaxValue.CompilationUnit unit -> structure(Tag.COMPILATION_UNIT, field(FORM_FIELD, named(unit.form())));
      case SyntaxValue.PackageDeclaration declaration -> structure(Tag.PACKAGE_DECLARATION,
          field(NAME_FIELD, text(declaration.qualifiedName())),
          field("qualifier", optional(declaration.qualifier(), qualifier -> structure(Tag.PACKAGE_QUALIFIER_EVIDENCE,
              field("sourceKind", named(qualifier.sourceKind())), field("origin", new CoverageEvidenceValue.Origin(qualifier.origin()))))));
      case SyntaxValue.ImportDeclaration declaration -> structure(Tag.IMPORT_DECLARATION,
          field(NAME_FIELD, text(declaration.qualifiedName())), field("staticImport", flag(declaration.staticImport())),
          field("moduleImport", flag(declaration.moduleImport())), field("onDemand", flag(declaration.onDemand())));
      case SyntaxValue.FieldAccess field ->
        structure(Tag.FIELD_ACCESS, field(NAME_FIELD, text(field.name())), field("receiver", named(field.receiver())));
      case SyntaxValue.Invocation invocation ->
        structure(Tag.INVOCATION, field(NAME_FIELD, text(invocation.name())), field(FORM_FIELD, named(invocation.form())),
            field("semantics", optional(invocation.semantics(), CoverageEvidenceValue.Invocation::new)));
      case SyntaxValue.Receiver receiver -> structure(Tag.RECEIVER, field(KIND_FIELD, named(receiver.kind())));
      case SyntaxValue.Literal literal -> literal.restrictedDecimal().isEmpty()
          ? structure(Tag.LITERAL, field("text", text(literal.text())))
          : structure(Tag.LITERAL, field("text", text(literal.text())),
              field("restrictedDecimal",
                  structure(Tag.DECIMAL_MINIMUM_OPERAND, field("category", named(literal.restrictedDecimal().orElseThrow().category())),
                      field("magnitude", text(literal.restrictedDecimal().orElseThrow().magnitude())))));
      case SyntaxValue.Operator operator -> structure(Tag.OPERATOR, field("operator", named(operator.operator())));
      case SyntaxValue.TypeName name -> structure(Tag.TYPE_NAME, field(NAME_FIELD, text(name.name())));
      case SyntaxValue.ArrayCreation array ->
        structure(Tag.ARRAY_CREATION, field("writtenType", flag(array.writtenType())), field("initializer", flag(array.initializer())),
            field("dimensionAnnotationCounts", sequence(array.dimensionAnnotationCounts(), count -> integral(count.longValue()))));
      case SyntaxValue.MemberReference reference ->
        structure(Tag.MEMBER_REFERENCE, field(NAME_FIELD, text(reference.name())), field("mode", named(reference.mode())),
            field("qualifier", named(reference.qualifier())), field("selectedType", type(reference.selectedType())),
            field("selection", named(reference.selection())), field("staticMember", flag(reference.staticMember())));
      case SyntaxValue.PatternBinding binding -> structure(Tag.PATTERN_BINDING, field("resolvedAccesses",
          optional(binding.resolvedAccesses(), accesses -> sequence(accesses, CoverageEvidenceValue.Origin::new))));
      case SyntaxValue.Lambda lambda -> structure(Tag.LAMBDA, field("bodyKind", named(lambda.bodyKind())),
          field("parenthesized", flag(lambda.parenthesized())), field(RETURN_TYPE_FIELD, type(lambda.returnType())),
          field(PARAMETERS_FIELD, sequence(lambda.parameters(), parameter -> structure(Tag.LAMBDA_PARAMETER,
              field("typeForm", named(parameter.typeForm())), field(VAR_ARGS_FIELD, flag(parameter.variableArity()))))));
      case SyntaxValue.SwitchCase value -> structure(Tag.SWITCH_CASE, field(FORM_FIELD, named(value.form())),
          field("labels", sequence(value.labels(), label -> switch (label) {
            case SyntaxValue.SwitchLabel.Default _ -> structure(Tag.DEFAULT_LABEL);
            case SyntaxValue.SwitchLabel.Pattern pattern ->
              structure(Tag.PATTERN_LABEL, field("childPosition", integral(pattern.childPosition())));
            case SyntaxValue.SwitchLabel.Expression expression ->
              structure(Tag.EXPRESSION_LABEL, field("childPosition", integral(expression.childPosition())));
          })));
      case SyntaxValue.Unsupported unsupported ->
        structure(Tag.UNSUPPORTED_SYNTAX, field("compilerKind", text(unsupported.compilerKind())));
    };
  }

  private static Structure localDeclaration(SyntaxValue.LocalDeclaration local) {
    var fields = new ArrayList<Field>();
    fields.add(field(NAME_FIELD, text(local.name())));
    fields.add(field("writtenType",
        optional(local.writtenType(), written -> structure(Tag.WRITTEN_TYPE, field("sourceKind", named(written.sourceKind())),
            field("origin", new CoverageEvidenceValue.Origin(written.origin())), field(TYPE_FIELD, type(written.type()))))));
    fields.add(field(WRITTEN_MODIFIERS_FIELD, sequence(local.writtenModifiers(), CoverageSourceProjection::text)));
    local.bindingEvidence()
        .ifPresent(binding -> fields.add(field("bindingEvidence",
            structure(Tag.LOCAL_BINDING, field("declarationKey", optional(binding.declarationKey(), CoverageSourceProjection::text)),
                field("typeForm", named(binding.typeForm())), field("context", named(binding.context())),
                field("initialized", flag(binding.initialized())), field("annotations", integral(binding.annotations())),
                field("sharedAnnotationKeys", sequence(binding.sharedAnnotationKeys(), CoverageSourceProjection::text))))));
    return new Structure(Tag.LOCAL_DECLARATION, fields);
  }

  private static Structure typeShape(SyntaxValue.TypeDeclaration.Shape shape) {
    return structure(Tag.TYPE_DECLARATION_SHAPE, field(ANNOTATIONS_FIELD, integral(shape.annotations())),
        field("typeParameters", integral(shape.typeParameters())), field("superclass", flag(shape.superclass())),
        field("interfaces", integral(shape.interfaces())), field("permittedSubclasses", integral(shape.permittedSubclasses())),
        field("recordComponents", integral(shape.recordComponents())), field("members", integral(shape.members())));
  }

  private static Structure executableShape(SyntaxValue.ExecutableDeclaration.HeaderShape shape) {
    return structure(Tag.EXECUTABLE_SHAPE, field(ANNOTATIONS_FIELD, integral(shape.annotations())),
        field("typeParameters", integral(shape.typeParameters())), field("receiver", flag(shape.receiver())),
        field(PARAMETERS_FIELD, integral(shape.parameters())), field(RETURN_TYPE_FIELD, flag(shape.returnType())),
        field("thrownTypes", integral(shape.thrownTypes())), field("body", flag(shape.body())),
        field(VAR_ARGS_FIELD, flag(shape.varArgs())));
  }

  private static Structure attributedHeader(SyntaxValue.ExecutableDeclaration.AttributedHeader header) {
    return structure(Tag.ATTRIBUTED_HEADER, field("receiverType", type(header.receiverType())),
        field("receiverOwnerKey", optional(header.receiverOwnerKey(), CoverageSourceProjection::text)),
        field("receiverSimpleName", optional(header.receiverSimpleName(), CoverageSourceProjection::text)),
        field("enclosingTypeKey", optional(header.enclosingTypeKey(), CoverageSourceProjection::text)),
        field("thrownTypes", sequence(header.thrownTypes(), CoverageSourceProjection::type)),
        field("defaultValue", optional(header.defaultValue(), CoverageSourceProjection::annotationValue)));
  }

  private static Structure recordComponent(SyntaxValue.RecordComponentDeclaration declaration) {
    var counterparts = declaration.counterparts();
    return structure(Tag.RECORD_COMPONENT, field(NAME_FIELD, text(declaration.name())),
        field(OWNER_KEY_FIELD, text(declaration.ownerKey())), field(DECLARATION_KEY_FIELD, text(declaration.declarationKey())),
        field(ORDINAL_FIELD, integral(declaration.ordinal())), field(WRITTEN_MODIFIERS_FIELD, enumSet(declaration.writtenModifiers())),
        field(EFFECTIVE_MODIFIERS_FIELD, enumSet(declaration.effectiveModifiers())),
        field("declaredType", new TypeIdentity(declaration.declaredType())), field(ANNOTATIONS_FIELD, integral(declaration.annotations())),
        field(VAR_ARGS_FIELD, flag(declaration.varArgs())),
        field("counterparts",
            structure(Tag.RECORD_COUNTERPARTS, field("backingFieldKey", text(counterparts.backingFieldKey())),
                field("accessorKey", text(counterparts.accessorKey())),
                field("canonicalParameterKey", text(counterparts.canonicalParameterKey())),
                field("accessorImplicit", flag(counterparts.accessorImplicit())),
                field("canonicalParameterDerived", flag(counterparts.canonicalParameterDerived())))));
  }

  private static Structure type(SyntaxType type) {
    return switch (type) {
      case SyntaxType.NotApplicable _ -> structure(Tag.TYPE_NOT_APPLICABLE);
      case SyntaxType.Unresolved _ -> structure(Tag.TYPE_UNRESOLVED);
      case SyntaxType.Erroneous _ -> structure(Tag.TYPE_ERRONEOUS);
      case SyntaxType.NoType none -> structure(Tag.TYPE_NONE, field(KIND_FIELD, named(none.kind())));
      case SyntaxType.Unsupported _ -> structure(Tag.TYPE_UNSUPPORTED);
      case SyntaxType.StructureUnavailable unavailable ->
        structure(Tag.TYPE_STRUCTURE_UNAVAILABLE, field("identity", new TypeIdentity(unavailable.identity())));
      case SyntaxType.Resolved resolved -> structure(Tag.TYPE_RESOLVED, field("identity", new TypeIdentity(resolved.identity())),
          field("structure", optional(resolved.structure(), CoverageSourceProjection::typeStructure)));
    };
  }

  private static Structure typeStructure(JavaTypeStructure type) {
    return switch (type) {
      case JavaTypeStructure.Primitive primitive -> structure(Tag.TYPE_PRIMITIVE, field(KIND_FIELD, named(primitive.kind())));
      case JavaTypeStructure.Declared declared -> {
        var components = new ArrayList<>(List.of(field(DECLARATION_KEY_FIELD, text(declared.declarationKey())),
            field("lexicalOwnerKey", optional(declared.lexicalOwnerKey(), CoverageSourceProjection::text)),
            field("enclosingType", optional(declared.enclosingType(), CoverageSourceProjection::typeStructure)),
            field(ARGUMENTS_FIELD, sequence(declared.arguments(), CoverageSourceProjection::typeStructure))));
        declared.enclosingOwnerType().ifPresent(owner -> components.add(field("enclosingOwnerType", typeStructure(owner))));
        yield new Structure(Tag.TYPE_DECLARED, components);
      }
      case JavaTypeStructure.LocalDeclared local ->
        structure(Tag.TYPE_LOCAL_DECLARED, field(DECLARATION_KEY_FIELD, text(local.declarationKey())),
            field("lexicalOwnerKey", text(local.lexicalOwnerKey())), field(KIND_FIELD, named(local.kind())),
            field("enclosingType", optional(local.enclosingType(), CoverageSourceProjection::typeStructure)),
            field(ARGUMENTS_FIELD, sequence(local.arguments(), CoverageSourceProjection::typeStructure)));
      case JavaTypeStructure.Variable variable -> structure(Tag.TYPE_VARIABLE, field("parameterKey", text(variable.parameterKey())));
      case JavaTypeStructure.Captured captured -> structure(Tag.TYPE_CAPTURED, field(ORDINAL_FIELD, integral(captured.ordinal())),
          field("upperBound", typeStructure(captured.upperBound())),
          field("lowerBound", optional(captured.lowerBound(), CoverageSourceProjection::typeStructure)));
      case JavaTypeStructure.CaptureReference reference ->
        structure(Tag.TYPE_CAPTURE_REFERENCE, field(ORDINAL_FIELD, integral(reference.ordinal())));
      case JavaTypeStructure.Array array -> structure(Tag.TYPE_ARRAY, field("component", typeStructure(array.component())));
      case JavaTypeStructure.Wildcard wildcard -> structure(Tag.TYPE_WILDCARD, field(KIND_FIELD, named(wildcard.kind())),
          field("bound", optional(wildcard.bound(), CoverageSourceProjection::typeStructure)));
      case JavaTypeStructure.Intersection intersection ->
        structure(Tag.TYPE_INTERSECTION, field("bounds", sequence(intersection.bounds(), CoverageSourceProjection::typeStructure)));
      case JavaTypeStructure.Union union ->
        structure(Tag.TYPE_UNION, field("alternatives", sequence(union.alternatives(), CoverageSourceProjection::typeStructure)));
      case JavaTypeStructure.Executable executable ->
        structure(Tag.TYPE_EXECUTABLE, field("variables", sequence(executable.variables(), CoverageSourceProjection::typeStructure)),
            field(PARAMETERS_FIELD, sequence(executable.parameters(), CoverageSourceProjection::type)),
            field(RETURN_TYPE_FIELD, type(executable.returnType())),
            field("receiver", optional(executable.receiver(), CoverageSourceProjection::type)),
            field("thrownTypes", sequence(executable.thrownTypes(), CoverageSourceProjection::type)),
            field("erasure",
                structure(Tag.TYPE_EXECUTABLE_ERASURE,
                    field(PARAMETERS_FIELD, sequence(executable.erasure().parameters(), CoverageSourceProjection::type)),
                    field(RETURN_TYPE_FIELD, type(executable.erasure().returnType())))));
    };
  }

  private static Structure annotationValue(AnnotationElementValue value) {
    return switch (value) {
      case AnnotationScalar.BooleanValue scalar -> structure(Tag.ANNOTATION_BOOLEAN, field(VALUE_FIELD, flag(scalar.value())));
      case AnnotationScalar.ByteValue scalar -> structure(Tag.ANNOTATION_BYTE, field(VALUE_FIELD, integral(scalar.value())));
      case AnnotationScalar.ShortValue scalar -> structure(Tag.ANNOTATION_SHORT, field(VALUE_FIELD, integral(scalar.value())));
      case AnnotationScalar.CharValue scalar -> structure(Tag.ANNOTATION_CHAR, field(VALUE_FIELD, integral(scalar.value())));
      case AnnotationScalar.IntValue scalar -> structure(Tag.ANNOTATION_INT, field(VALUE_FIELD, integral(scalar.value())));
      case AnnotationScalar.LongValue scalar -> structure(Tag.ANNOTATION_LONG, field(VALUE_FIELD, integral(scalar.value())));
      case AnnotationScalar.FloatValue scalar ->
        structure(Tag.ANNOTATION_FLOAT, field(VALUE_FIELD, new CoverageEvidenceValue.Decimal(scalar.value())));
      case AnnotationScalar.DoubleValue scalar ->
        structure(Tag.ANNOTATION_DOUBLE, field(VALUE_FIELD, new CoverageEvidenceValue.Decimal(scalar.value())));
      case AnnotationScalar.StringValue scalar -> structure(Tag.ANNOTATION_STRING, field(VALUE_FIELD, text(scalar.value())));
      case AnnotationScalar.EnumConstant scalar -> structure(Tag.ANNOTATION_ENUM,
          field(DECLARATION_KEY_FIELD, text(scalar.declarationKey())), field("enumTypeKey", text(scalar.enumTypeKey())));
      case AnnotationScalar.ClassLiteral scalar ->
        structure(Tag.ANNOTATION_CLASS_LITERAL, field("operandType", typeStructure(scalar.operandType())));
      case AnnotationElementValue.Nested nested ->
        structure(Tag.ANNOTATION_NESTED, field("annotationTypeKey", text(nested.annotationTypeKey())));
      case AnnotationElementValue.Array array ->
        structure(Tag.ANNOTATION_ARRAY, field("elements", sequence(array.elements(), CoverageSourceProjection::annotationValue)));
    };
  }

  private static Structure structure(Tag tag, Field... fields) {
    return new Structure(tag, List.of(fields));
  }

  private static Field field(String name, CoverageEvidenceValue value) {
    return new Field(name, value);
  }

  private static CoverageEvidenceValue.Text text(String value) {
    return new CoverageEvidenceValue.Text(value);
  }

  private static CoverageEvidenceValue.Named named(Enum<?> value) {
    return new CoverageEvidenceValue.Named(value.name());
  }

  private static CoverageEvidenceValue.Flag flag(boolean value) {
    return new CoverageEvidenceValue.Flag(value);
  }

  private static CoverageEvidenceValue.Integral integral(long value) {
    return new CoverageEvidenceValue.Integral(value);
  }

  private static CoverageEvidenceValue.Sequence names(SyntaxValue.QualifiedName value) {
    return sequence(value.components(), CoverageSourceProjection::text);
  }

  private static CoverageEvidenceValue.Sequence enumSet(Collection<? extends Enum<?>> values) {
    return sequence(values.stream().map(Enum::name).sorted().toList(), CoverageEvidenceValue.Named::new);
  }

  private static <T> CoverageEvidenceValue.Sequence sequence(Collection<T> values, Function<T, ? extends CoverageEvidenceValue> copy) {
    return new CoverageEvidenceValue.Sequence(values.stream().map(value -> (CoverageEvidenceValue) copy.apply(value)).toList());
  }

  private static <T> CoverageEvidenceValue.OptionalValue optional(Optional<T> value, Function<T, ? extends CoverageEvidenceValue> copy) {
    return new CoverageEvidenceValue.OptionalValue(value.map(item -> copy.apply(item)));
  }
}
