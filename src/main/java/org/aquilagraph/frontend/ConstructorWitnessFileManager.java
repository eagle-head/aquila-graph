package org.aquilagraph.frontend;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import org.jspecify.annotations.Nullable;

/** Overlays one adapted dependency without taking ownership of its manager. */
final class ConstructorWitnessFileManager extends ForwardingJavaFileManager<JavaFileManager> {
  private final BinaryWitness witness;

  ConstructorWitnessFileManager(JavaFileManager manager, String binaryName, URI originalUri, byte[] content) {
    super(manager);
    witness = new BinaryWitness(binaryName, originalUri, content);
  }

  @Override
  public @Nullable JavaFileObject getJavaFileForInput(Location location, String className, Kind kind) throws IOException {
    var original = super.getJavaFileForInput(location, className, kind);
    return original != null && kind == Kind.CLASS && className.equals(witness.binaryName) && original.toUri().equals(witness.toUri())
        ? witness
        : original;
  }

  @Override
  public Iterable<JavaFileObject> list(Location location, String packageName, Set<Kind> kinds, boolean recurse) throws IOException {
    var result = new ArrayList<JavaFileObject>();
    for (var input : super.list(location, packageName, kinds, recurse)) {
      result.add(input.getKind() == Kind.CLASS && witness.binaryName.equals(inferBinaryName(location, input))
          && input.toUri().equals(witness.toUri()) ? witness : input);
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
