package org.aquilagraph.source.internal;

import org.aquilagraph.source.internal.CapturedPaths.Syntax;

import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.regex.Pattern;

/** Read-only path syntax for retained locations, with no host filesystem authority. */
final class CapturedFileSystem extends FileSystem {
  private final Syntax pathSyntax;
  private final Set<String> roots = new ConcurrentSkipListSet<>();
  private volatile boolean open = true;

  CapturedFileSystem(Syntax syntax) {
    pathSyntax = syntax;
  }

  Syntax syntax() {
    return pathSyntax;
  }

  void requireOpen() {
    if (!open) {
      throw new ClosedFileSystemException();
    }
  }

  void observeRoot(String root) {
    if (!root.isEmpty()) {
      roots.add(root);
    }
  }

  @Override
  public FileSystemProvider provider() {
    return CapturedPathProvider.forSyntax(pathSyntax);
  }
  @Override
  public void close() {
    open = false;
    roots.clear();
  }
  @Override
  public boolean isOpen() {
    return open;
  }
  @Override
  public boolean isReadOnly() {
    return true;
  }
  @Override
  public String getSeparator() {
    return pathSyntax == Syntax.WINDOWS ? "\\" : "/";
  }
  @Override
  public Iterable<Path> getRootDirectories() {
    requireOpen();
    return roots.stream().map(this::getPath).toList();
  }
  @Override
  public Iterable<FileStore> getFileStores() {
    requireOpen();
    return List.of();
  }
  @Override
  public Set<String> supportedFileAttributeViews() {
    return Set.of("basic");
  }
  @Override
  public Path getPath(String first, String... more) {
    requireOpen();
    var text = new StringBuilder(first);
    for (var part : more) {
      if (!part.isEmpty()) {
        if (!text.isEmpty() && !text.toString().endsWith(getSeparator())) {
          text.append(getSeparator());
        }
        text.append(part);
      }
    }
    return new CapturedPath(this, text.toString());
  }
  @Override
  public PathMatcher getPathMatcher(String syntaxAndPattern) {
    requireOpen();
    var split = syntaxAndPattern.indexOf(':');
    if (split <= 0) {
      throw new IllegalArgumentException("A path matcher requires syntax:pattern");
    }
    var kind = syntaxAndPattern.substring(0, split);
    var expression = syntaxAndPattern.substring(split + 1);
    var regex = switch (kind) {
      case "regex" -> expression;
      case "glob" -> CapturedPathGlob.expression(expression);
      default -> throw new UnsupportedOperationException("Unsupported captured path matcher: " + kind);
    };
    var pattern = Pattern.compile(regex, pathSyntax == Syntax.WINDOWS ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0);
    return path -> pattern
        .matcher("glob".equals(kind) && pathSyntax == Syntax.WINDOWS ? path.toString().replace('\\', '/') : path.toString()).matches();
  }
  @Override
  public UserPrincipalLookupService getUserPrincipalLookupService() {
    throw new UnsupportedOperationException("Captured locations have no principal lookup");
  }
  @Override
  public WatchService newWatchService() throws IOException {
    throw new UnsupportedOperationException("Captured locations cannot be watched");
  }
}
