package org.aquilagraph.frontend;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.Predicate;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.StandardJavaFileManager;
import org.jspecify.annotations.Nullable;

/** Owns the compiler manager and closeable class loaders obtained through it. */
final class CompilerFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
  private final List<Closeable> classLoaders = new ArrayList<>();
  private final LoaderOwnership ownership = LoaderOwnership.create();

  CompilerFileManager(StandardJavaFileManager manager) {
    super(manager);
  }

  StandardJavaFileManager standardFileManager() {
    return fileManager;
  }

  @Override
  public @Nullable ClassLoader getClassLoader(Location location) {
    var loader = super.getClassLoader(location);
    if (loader instanceof Closeable) {
      ownClassLoader(Closeable.class.cast(loader));
    }
    return loader;
  }

  private void ownClassLoader(Closeable loader) {
    if (ownership.register().test(loader)) {
      classLoaders.add(loader);
    }
  }

  @Override
  public void close() throws IOException {
    var manager = fileManager;
    try (manager) {
      try {
        closeClassLoaders(0);
      } finally {
        classLoaders.clear();
        ownership.clear().run();
      }
    }
  }

  private void closeClassLoaders(int index) throws IOException {
    if (index < classLoaders.size()) {
      var loader = classLoaders.get(index);
      try (loader) {
        closeClassLoaders(index + 1);
      }
    }
  }

  private record LoaderOwnership(Predicate<Closeable> register, Runnable clear) {
    private static LoaderOwnership create() {
      var identities = new IdentityHashMap<Closeable, Boolean>();
      return new LoaderOwnership(loader -> identities.put(loader, Boolean.TRUE) == null, identities::clear);
    }
  }
}
