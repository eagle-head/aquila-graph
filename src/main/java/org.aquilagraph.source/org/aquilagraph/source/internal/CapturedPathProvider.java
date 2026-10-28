package org.aquilagraph.source.internal;

import org.aquilagraph.source.internal.CapturedPaths.Syntax;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.ReadOnlyFileSystemException;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Provides lexical path views while refusing file access through captured provenance. */
final class CapturedPathProvider extends FileSystemProvider {
  private static final CapturedPathProvider WINDOWS = new CapturedPathProvider(Syntax.WINDOWS);
  private static final CapturedPathProvider POSIX = new CapturedPathProvider(Syntax.POSIX);
  private final Syntax syntax;

  private CapturedPathProvider(Syntax syntax) {
    this.syntax = syntax;
  }
  static CapturedPathProvider forSyntax(Syntax syntax) {
    return syntax == Syntax.WINDOWS ? WINDOWS : POSIX;
  }
  @Override
  public String getScheme() {
    return syntax == Syntax.WINDOWS ? "captured-windows" : "captured-posix";
  }
  @Override
  public FileSystem newFileSystem(URI uri, Map<String, ?> environment) {
    throw new UnsupportedOperationException("Captured locations are not mounted filesystems");
  }
  @Override
  public FileSystem getFileSystem(URI uri) {
    throw new FileSystemNotFoundException(uri.toString());
  }
  @Override
  public Path getPath(URI uri) {
    var location = uri.getPath();
    var authority = uri.getAuthority();
    if (!getScheme().equalsIgnoreCase(uri.getScheme()) || location == null || !location.startsWith("/")
        || (syntax == Syntax.POSIX && authority != null) || uri.getQuery() != null || uri.getFragment() != null) {
      throw new IllegalArgumentException("URI does not describe this captured path syntax");
    }
    var text = syntax == Syntax.WINDOWS
        ? (authority == null ? location.substring(1) : "//" + authority + location).replace('/', '\\')
        : location;
    var path = new CapturedFileSystem(syntax).getPath(text);
    if (!path.isAbsolute()) {
      throw new IllegalArgumentException("Captured path URI must retain its absolute root");
    }
    return path;
  }
  static FileSystemException unavailable(Path path) {
    requireOpen(path);
    return new FileSystemException(path.toString(), null, "Captured location metadata does not grant filesystem access");
  }
  private static void requireOpen(Path path) {
    if (!(path instanceof CapturedPath captured)) {
      throw new ProviderMismatchException("Captured file access requires a captured path");
    }
    captured.requireOpen();
  }
  private static ReadOnlyFileSystemException readOnly(Path path) {
    requireOpen(path);
    return new ReadOnlyFileSystemException();
  }
  @Override
  public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes)
      throws IOException {
    if (options.contains(StandardOpenOption.WRITE) || options.contains(StandardOpenOption.APPEND)
        || options.contains(StandardOpenOption.CREATE) || options.contains(StandardOpenOption.CREATE_NEW)
        || options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
      throw readOnly(path);
    }
    throw unavailable(path);
  }
  @Override
  public DirectoryStream<Path> newDirectoryStream(Path directory, Filter<? super Path> filter) throws IOException {
    throw unavailable(directory);
  }
  @Override
  public void createDirectory(Path directory, FileAttribute<?>... attributes) {
    throw readOnly(directory);
  }
  @Override
  public void delete(Path path) {
    throw readOnly(path);
  }
  @Override
  public void copy(Path source, Path target, CopyOption... options) {
    throw readOnly(source);
  }
  @Override
  public void move(Path source, Path target, CopyOption... options) {
    throw readOnly(source);
  }
  @Override
  public boolean isSameFile(Path first, Path second) throws IOException {
    if (first.equals(second)) {
      return true;
    }
    throw unavailable(first);
  }
  @Override
  public boolean isHidden(Path path) throws IOException {
    throw unavailable(path);
  }
  @Override
  public FileStore getFileStore(Path path) throws IOException {
    throw unavailable(path);
  }
  @Override
  public void checkAccess(Path path, AccessMode... modes) throws IOException {
    throw unavailable(path);
  }
  @Override
  public <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
    requireOpen(path);
    return type == BasicFileAttributeView.class ? type.cast(new CapturedAttributes(path)) : null;
  }
  @Override
  public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
    throw unavailable(path);
  }
  @Override
  public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
    throw unavailable(path);
  }
  @Override
  public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
    throw readOnly(path);
  }

  private record CapturedAttributes(Path path) implements BasicFileAttributeView {
    @Override
    public String name() {
      return "basic";
    }
    @Override
    public BasicFileAttributes readAttributes() throws IOException {
      throw unavailable(path);
    }
    @Override
    public void setTimes(@Nullable FileTime modified, @Nullable FileTime accessed, @Nullable FileTime created) {
      throw readOnly(path);
    }
  }
}
