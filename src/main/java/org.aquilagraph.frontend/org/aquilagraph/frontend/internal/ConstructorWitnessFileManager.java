package org.aquilagraph.frontend.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;

import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import org.jspecify.annotations.Nullable;

/** Overlays adapted dependencies without taking ownership of their manager. */
final class ConstructorWitnessFileManager extends ForwardingJavaFileManager<JavaFileManager> {
  private final Map<URI, BinaryWitness> witnesses;

  ConstructorWitnessFileManager(JavaFileManager manager, List<Replacement> replacements) {
    super(manager);
    witnesses = replacements.stream().collect(Collectors.toUnmodifiableMap(Replacement::originalUri,
        value -> new BinaryWitness(value.binaryName(), value.originalUri(), value.content())));
  }

  @Override
  public @Nullable JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
    var original = super.getJavaFileForInput(location, className, kind);
    var witness = original == null ? null : witnesses.get(original.toUri());
    return witness != null && kind == Kind.CLASS && className.equals(witness.binaryName) ? witness : original;
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
    var result = new ArrayList<JavaFileObject>();
    for (var input : super.list(location, packageName, kinds, recurse)) {
      var witness = witnesses.get(input.toUri());
      result.add(witness != null && input.getKind() == Kind.CLASS && witness.binaryName.equals(inferBinaryName(location, input))
          ? witness
          : input);
    }
    return result;
  }

  @Override
  public @Nullable String inferBinaryName(Location location, JavaFileObject file) {
    return file instanceof BinaryWitness binary ? binary.binaryName : super.inferBinaryName(location, file);
  }

  @Override
  public boolean isSameFile(FileObject first, FileObject second) {
    return (first instanceof BinaryWitness || second instanceof BinaryWitness)
        ? first.toUri().equals(second.toUri())
        : super.isSameFile(first, second);
  }

  @Override
  public void close() {
    // The witness acquisition retains manager ownership.
  }

  static final class Replacement {
    private final String sourceName;
    private final URI sourceUri;
    private final byte[] binaryContent;

    Replacement(String binaryName, URI originalUri, byte[] content) {
      sourceName = Objects.requireNonNull(binaryName, "binaryName");
      sourceUri = Objects.requireNonNull(originalUri, "originalUri");
      binaryContent = content.clone();
    }

    String binaryName() {
      return sourceName;
    }

    URI originalUri() {
      return sourceUri;
    }

    byte[] content() {
      return binaryContent.clone();
    }
  }

  private static final class BinaryWitness extends SimpleJavaFileObject {
    private final String binaryName;
    private final URI originalUri;
    private final byte[] content;

    private BinaryWitness(String binaryName, URI uri, byte[] content) {
      super(URI.create("memory:///" + binaryName.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
      this.binaryName = Objects.requireNonNull(binaryName, "binaryName");
      originalUri = Objects.requireNonNull(uri, "uri");
      this.content = content.clone();
    }

    @Override
    public URI toUri() {
      return originalUri;
    }

    @Override
    public InputStream openInputStream() {
      return new ByteArrayInputStream(content);
    }
  }
}
