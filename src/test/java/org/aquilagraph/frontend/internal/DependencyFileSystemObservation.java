package org.aquilagraph.frontend.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/** Observes a caller-owned real filesystem through the public provider input boundary. */
final class DependencyFileSystemObservation extends FileSystem {
  private final FileSystem nativeFileSystem;
  private final FileSystemProvider provider = new ObservedProvider();
  private boolean open = true;
  int inputOpenAttempts;
  IntConsumer inputOpenObserver = _ -> {};
  int inputCloseAttempts;
  int directoryOpenAttempts;
  int directoryCloseAttempts;
  int inputOpenFailureAttempt;
  IOException inputOpenFailure;
  int inputReadFailureAttempt;
  IOException inputReadFailure;
  int inputCloseFailureAttempt;
  IOException inputCloseFailure;
  IOException directoryOpenFailure;
  IOException directoryIterationFailure;
  long reportedSizeOffset;
  boolean reportOtherEntry;

  DependencyFileSystemObservation(Path nativePath) {
    nativeFileSystem = nativePath.getFileSystem();
  }

  Path observe(Path nativePath) {
    return new ObservedPath(nativePath);
  }

  private Path nativePath(Path path) {
    return path instanceof ObservedPath observed ? observed.nativePath : path;
  }

  @Override public FileSystemProvider provider() { return provider; }
  @Override public void close() { open = false; }
  @Override public boolean isOpen() { return open; }
  @Override public boolean isReadOnly() { return nativeFileSystem.isReadOnly(); }
  @Override public String getSeparator() { return nativeFileSystem.getSeparator(); }
  @Override public Iterable<Path> getRootDirectories() {
    var roots = new ArrayList<Path>();
    nativeFileSystem.getRootDirectories().forEach(path -> roots.add(observe(path)));
    return List.copyOf(roots);
  }
  @Override public Iterable<FileStore> getFileStores() { return nativeFileSystem.getFileStores(); }
  @Override public Set<String> supportedFileAttributeViews() { return nativeFileSystem.supportedFileAttributeViews(); }
  @Override public Path getPath(String first, String... more) { return observe(nativeFileSystem.getPath(first, more)); }
  @Override public PathMatcher getPathMatcher(String syntaxAndPattern) {
    var matcher = nativeFileSystem.getPathMatcher(syntaxAndPattern);
    return path -> matcher.matches(nativePath(path));
  }
  @Override public UserPrincipalLookupService getUserPrincipalLookupService() { return nativeFileSystem.getUserPrincipalLookupService(); }
  @Override public WatchService newWatchService() throws IOException { return nativeFileSystem.newWatchService(); }

  private final class ObservedProvider extends FileSystemProvider {
    @Override public String getScheme() { return "dependency-observation"; }
    @Override public FileSystem newFileSystem(URI uri, Map<String, ?> environment) { throw new UnsupportedOperationException(); }
    @Override public FileSystem getFileSystem(URI uri) { return DependencyFileSystemObservation.this; }
    @Override public Path getPath(URI uri) { return observe(Path.of(uri)); }

    @Override public InputStream newInputStream(Path path, OpenOption... options) throws IOException {
      var attempt = ++inputOpenAttempts;
      inputOpenObserver.accept(attempt);
      if (attempt == inputOpenFailureAttempt) {
        throw inputOpenFailure;
      }
      var input = Files.newInputStream(nativePath(path), options);
      return new FilterInputStream(input) {
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
          if (attempt == inputReadFailureAttempt) {
            throw inputReadFailure;
          }
          return super.read(bytes, offset, length);
        }
        @Override public void close() throws IOException {
          inputCloseAttempts++;
          super.close();
          if (attempt == inputCloseFailureAttempt) {
            throw inputCloseFailure;
          }
        }
      };
    }

    @Override public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attributes)
        throws IOException {
      return Files.newByteChannel(nativePath(path), options, attributes);
    }

    @Override public DirectoryStream<Path> newDirectoryStream(Path directory, Filter<? super Path> filter) throws IOException {
      directoryOpenAttempts++;
      if (directoryOpenFailure != null) {
        throw directoryOpenFailure;
      }
      var stream = Files.newDirectoryStream(nativePath(directory), path -> filter.accept(observe(path)));
      return new DirectoryStream<>() {
        @Override public Iterator<Path> iterator() {
          var iterator = stream.iterator();
          return new Iterator<>() {
            private boolean encountered;
            @Override public boolean hasNext() {
              if (encountered && directoryIterationFailure != null) {
                throw new DirectoryIteratorException(directoryIterationFailure);
              }
              return iterator.hasNext();
            }
            @Override public Path next() {
              encountered = true;
              return observe(iterator.next());
            }
          };
        }
        @Override public void close() throws IOException {
          directoryCloseAttempts++;
          stream.close();
        }
      };
    }

    @Override public void createDirectory(Path directory, FileAttribute<?>... attributes) throws IOException {
      Files.createDirectory(nativePath(directory), attributes);
    }
    @Override public void delete(Path path) throws IOException { Files.delete(nativePath(path)); }
    @Override public void copy(Path source, Path target, CopyOption... options) throws IOException {
      Files.copy(nativePath(source), nativePath(target), options);
    }
    @Override public void move(Path source, Path target, CopyOption... options) throws IOException {
      Files.move(nativePath(source), nativePath(target), options);
    }
    @Override public boolean isSameFile(Path first, Path second) throws IOException { return Files.isSameFile(nativePath(first), nativePath(second)); }
    @Override public boolean isHidden(Path path) throws IOException { return Files.isHidden(nativePath(path)); }
    @Override public FileStore getFileStore(Path path) throws IOException { return Files.getFileStore(nativePath(path)); }
    @Override public void checkAccess(Path path, AccessMode... modes) throws IOException {
      nativeFileSystem.provider().checkAccess(nativePath(path), modes);
    }
    @Override public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
      return Files.getFileAttributeView(nativePath(path), type, options);
    }
    @Override public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
      var attributes = Files.readAttributes(nativePath(path), type, options);
      if (type != BasicFileAttributes.class || (!reportOtherEntry && reportedSizeOffset == 0)) {
        return attributes;
      }
      return type.cast(new BasicFileAttributes() {
        @Override public FileTime lastModifiedTime() { return attributes.lastModifiedTime(); }
        @Override public FileTime lastAccessTime() { return attributes.lastAccessTime(); }
        @Override public FileTime creationTime() { return attributes.creationTime(); }
        @Override public boolean isRegularFile() { return !reportOtherEntry && attributes.isRegularFile(); }
        @Override public boolean isDirectory() { return !reportOtherEntry && attributes.isDirectory(); }
        @Override public boolean isSymbolicLink() { return !reportOtherEntry && attributes.isSymbolicLink(); }
        @Override public boolean isOther() { return reportOtherEntry || attributes.isOther(); }
        @Override public long size() { return attributes.size() + reportedSizeOffset; }
        @Override public Object fileKey() { return attributes.fileKey(); }
      });
    }
    @Override public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
      return Files.readAttributes(nativePath(path), attributes, options);
    }
    @Override public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
      Files.setAttribute(nativePath(path), attribute, value, options);
    }
  }

  private final class ObservedPath implements Path {
    private final Path nativePath;
    private ObservedPath(Path nativePath) { this.nativePath = nativePath; }
    private Path optional(Path path) { return path == null ? null : observe(path); }
    @Override public FileSystem getFileSystem() { return DependencyFileSystemObservation.this; }
    @Override public boolean isAbsolute() { return nativePath.isAbsolute(); }
    @Override public Path getRoot() { return optional(nativePath.getRoot()); }
    @Override public Path getFileName() { return optional(nativePath.getFileName()); }
    @Override public Path getParent() { return optional(nativePath.getParent()); }
    @Override public int getNameCount() { return nativePath.getNameCount(); }
    @Override public Path getName(int index) { return observe(nativePath.getName(index)); }
    @Override public Path subpath(int begin, int end) { return observe(nativePath.subpath(begin, end)); }
    @Override public boolean startsWith(Path other) { return nativePath.startsWith(nativePath(other)); }
    @Override public boolean endsWith(Path other) { return nativePath.endsWith(nativePath(other)); }
    @Override public Path normalize() { return observe(nativePath.normalize()); }
    @Override public Path resolve(Path other) { return observe(nativePath.resolve(nativePath(other))); }
    @Override public Path relativize(Path other) { return observe(nativePath.relativize(nativePath(other))); }
    @Override public URI toUri() { return nativePath.toUri(); }
    @Override public Path toAbsolutePath() { return observe(nativePath.toAbsolutePath()); }
    @Override public Path toRealPath(LinkOption... options) throws IOException { return observe(nativePath.toRealPath(options)); }
    @Override public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) throws IOException {
      return nativePath.register(watcher, events, modifiers);
    }
    @Override public int compareTo(Path other) { return nativePath.compareTo(nativePath(other)); }
    @Override public boolean equals(Object other) {
      return other instanceof ObservedPath observed
          && observed.getFileSystem() == getFileSystem() && nativePath.equals(observed.nativePath);
    }
    @Override public int hashCode() { return 31 * System.identityHashCode(getFileSystem()) + nativePath.hashCode(); }
    @Override public String toString() { return nativePath.toString(); }
  }
}
