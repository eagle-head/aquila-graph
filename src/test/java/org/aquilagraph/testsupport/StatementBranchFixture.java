package org.aquilagraph.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.aquilagraph.frontend.JavaFrontends;
import org.aquilagraph.identity.api.CanonicalValueWriter;
import org.aquilagraph.identity.api.CompilerConfiguration;
import org.aquilagraph.identity.api.InputSnapshot;
import org.aquilagraph.identity.api.JavaRelease;
import org.aquilagraph.identity.api.SourceFile;
import org.aquilagraph.identity.api.SourceSpan;
import org.aquilagraph.source.api.AstOutcome;
import org.aquilagraph.source.api.AstProductionRequest;
import org.aquilagraph.source.api.AstProfile;
import org.aquilagraph.source.api.JavaSourceKind;
import org.aquilagraph.source.api.ReferenceKind;
import org.aquilagraph.source.api.ResolutionStatus;
import org.aquilagraph.source.api.SemanticReference;
import org.aquilagraph.source.api.SourceInput;
import org.aquilagraph.source.api.SyntaxKind;
import org.aquilagraph.source.api.SyntaxNode;
import org.aquilagraph.source.api.SyntaxRole;
import org.aquilagraph.source.api.SyntaxStructuralPath;
import org.aquilagraph.source.api.SyntaxType;
import org.aquilagraph.source.api.SyntaxValue;
import org.aquilagraph.source.internal.RetainedValueCodec;

public final class StatementBranchFixture {
  public static final List<JavaSourceKind> DECLARATIONS = List.of(JavaSourceKind.VARIABLE, JavaSourceKind.CLASS,
      JavaSourceKind.INTERFACE, JavaSourceKind.ENUM, JavaSourceKind.RECORD, JavaSourceKind.ANNOTATION_TYPE);

  private StatementBranchFixture() {
  }

  public static SyntaxNode declaration(String source, SyntaxStructuralPath path, JavaSourceKind kind, boolean unsupported,
      Optional<SourceSpan> origin) {
    var syntax = unsupported ? SyntaxKind.UNSUPPORTED
        : kind == JavaSourceKind.VARIABLE ? SyntaxKind.LOCAL_DECLARATION : SyntaxKind.TYPE_DECLARATION;
    SyntaxValue value = unsupported ? new SyntaxValue.Unsupported(kind.name())
        : kind == JavaSourceKind.VARIABLE ? new SyntaxValue.LocalDeclaration("value")
            : new SyntaxValue.TypeDeclaration("Local", "type:Local", Set.of(), Set.of(),
                new SyntaxValue.TypeDeclaration.Shape(0, 0, false, 0, 0, 0, 0));
    SyntaxType type = syntax.requiresType() ? SyntaxType.Unresolved.INSTANCE : SyntaxType.NotApplicable.INSTANCE;
    var reference = syntax == SyntaxKind.LOCAL_DECLARATION
        ? Optional.of(new SemanticReference(ReferenceKind.LOCAL, ResolutionStatus.RESOLVED, Optional.of("local:value"),
            Optional.empty(), List.of(), List.of()))
        : Optional.<SemanticReference>empty();
    return origin.isPresent()
        ? SyntaxNode.source(source, path, kind, syntax, type, value, List.of(), reference, origin.orElseThrow())
        : SyntaxNode.withoutDirectSource(source, path, kind, syntax, type, value, List.of(), reference);
  }

  public static AstOutcome produce(Path directory, AstProfile profile) {
    var snapshot = InputSnapshot.fromSources(directory, List.of(SourceFile.fromContent(Path.of("Branches.java"),
        "class Branches { void run(boolean flag) { if (flag) ; else ; } }")));
    return JavaFrontends.standard().produce(new AstProductionRequest(new SourceInput.Snapshot(snapshot),
        CompilerConfiguration.forRelease(JavaRelease.JAVA_25), profile));
  }

  public static SyntaxNode branch(AstOutcome outcome, SyntaxRole role) {
    return outcome.result().orElseThrow().aggregate().nodes().stream().filter(node -> node.syntaxKind() == SyntaxKind.IF)
        .findFirst().orElseThrow().requiredChild(role);
  }

  public static byte[] withDeclaration(byte[] bytes, String format, SyntaxNode leaf, JavaSourceKind kind, boolean unsupported) {
    var replacement = declaration(leaf.sourceFileKey(), leaf.structuralPath(), kind, unsupported, leaf.origin());
    var originalFrame = header(leaf, format);
    var replacementFrame = header(replacement, format);
    var offset = -1;
    var matches = 0;
    for (var index = 0; index <= bytes.length - originalFrame.length; index++) {
      if (Arrays.equals(bytes, index, index + originalFrame.length, originalFrame, 0, originalFrame.length)) {
        offset = index;
        matches++;
      }
    }
    assertEquals(1, matches, "Exactly one selected leaf header must be replaced");
    var result = new ByteArrayOutputStream();
    result.write(bytes, 0, offset);
    result.writeBytes(replacementFrame);
    result.write(bytes, offset + originalFrame.length, bytes.length - offset - originalFrame.length);
    return result.toByteArray();
  }

  private static byte[] header(SyntaxNode node, String format) {
    var writer = new CanonicalValueWriter("");
    var codec = new RetainedValueCodec(false, format);
    for (var field : List.of(node.sourceFileKey(), node.structuralPath(), node.sourceKind(), node.syntaxKind(), node.type(), node.value(),
        node.children(), node.semanticReference(), node.origin())) {
      codec.write(writer, field);
    }
    return Arrays.copyOfRange(writer.toByteArray(), Integer.BYTES, writer.toByteArray().length);
  }
}
