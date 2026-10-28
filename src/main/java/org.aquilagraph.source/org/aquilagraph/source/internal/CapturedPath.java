package org.aquilagraph.source.internal;

import org.aquilagraph.source.internal.CapturedPaths.Syntax;

import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Immutable lexical location in its captured syntax, independent of the host working directory. */
final class CapturedPath implements Path {
  private static final String CURRENT_DIRECTORY = ".";
  private static final String PARENT_DIRECTORY = "..";
  private final CapturedFileSystem fileSystem;
  private final String text;
  private final String root;
  private final boolean absolute;
  private final List<String> names;

  CapturedPath(CapturedFileSystem fileSystem, String text) {
    this.fileSystem = fileSystem;
    this.text = text;
    if (text.indexOf('\0') >= 0) {
      throw new InvalidPathException(text, "A path cannot contain NUL");
    }
    var windows = fileSystem.syntax() == Syntax.WINDOWS;
    var rootLength = rootLength(text, windows);
    root = text.substring(0, rootLength);
    absolute = windows ? unc(root) || (root.length() >= 3 && root.charAt(1) == ':') : !root.isEmpty();
    var remainder = text.substring(rootLength);
    names = remainder.isEmpty() ? (root.isEmpty() ? List.of("") : List.of()) : List.of(remainder.split(windows ? "[\\\\/]+" : "/+"));
    fileSystem.observeRoot(root);
  }

  private static int rootLength(String value, boolean windows) {
    if (value.isEmpty()) {
      return 0;
    }
    if (!windows) {
      return value.startsWith("/") ? 1 : 0;
    }
    if (value.length() >= 2 && CapturedPaths.driveLetter(value.charAt(0)) && value.charAt(1) == ':') {
      return value.length() >= 3 && separator(value.charAt(2)) ? 3 : 2;
    }
    if (unc(value)) {
      var serverEnd = nextSeparator(value, 2);
      if (serverEnd <= 2 || serverEnd + 1 >= value.length()) {
        throw new InvalidPathException(value, "A UNC root requires a server and share");
      }
      var shareEnd = nextSeparator(value, serverEnd + 1);
      return shareEnd < 0 ? value.length() : shareEnd + 1;
    }
    return separator(value.charAt(0)) ? 1 : 0;
  }

  private static int nextSeparator(String value, int start) {
    for (var index = start; index < value.length(); index++) {
      if (separator(value.charAt(index))) {
        return index;
      }
    }
    return -1;
  }

  private static boolean separator(char value) {
    return value == '\\' || value == '/';
  }
  private static boolean unc(String value) {
    return value.length() >= 2 && separator(value.charAt(0)) && separator(value.charAt(1));
  }
  Syntax syntax() {
    return fileSystem.syntax();
  }
  void requireOpen() {
    fileSystem.requireOpen();
  }
  private boolean empty() {
    return root.isEmpty() && names.equals(List.of(""));
  }
  private String comparison(String value) {
    if (fileSystem.syntax() != Syntax.WINDOWS) {
      return value;
    }
    var result = new StringBuilder(value.length());
    for (var index = 0; index < value.length(); index++) {
      var character = value.charAt(index);
      result.append(character == '/' ? '\\' : Character.toLowerCase(Character.toUpperCase(character)));
    }
    return result.toString();
  }
  private String key() {
    return comparison(root + String.join(fileSystem.getSeparator(), names));
  }
  private CapturedPath compatible(Path other) {
    if (!(other instanceof CapturedPath captured) || fileSystem.syntax() != captured.fileSystem.syntax()) {
      throw new ProviderMismatchException("Captured path operations require the same syntax");
    }
    return captured;
  }
  private Path build(String prefix, List<String> elements) {
    var separator = fileSystem.getSeparator();
    var join = String.join(separator, elements);
    if (prefix.isEmpty() || join.isEmpty() || prefix.endsWith(separator) || prefix.endsWith("/")) {
      return fileSystem.getPath(prefix + join);
    }
    return fileSystem.getPath(prefix + (prefix.endsWith(":") ? "" : separator) + join);
  }

  @Override
  public FileSystem getFileSystem() {
    return fileSystem;
  }
  @Override
  public boolean isAbsolute() {
    return absolute;
  }
  @Override
  public @Nullable Path getRoot() {
    return root.isEmpty() ? null : fileSystem.getPath(root);
  }
  @Override
  public @Nullable Path getFileName() {
    return names.isEmpty() ? null : fileSystem.getPath(names.getLast());
  }
  @Override
  public @Nullable Path getParent() {
    if (names.isEmpty() || (root.isEmpty() && names.size() <= 1)) {
      return null;
    }
    return build(root, names.subList(0, names.size() - 1));
  }
  @Override
  public int getNameCount() {
    return names.size();
  }
  @Override
  public Path getName(int index) {
    if (index < 0 || index >= names.size()) {
      throw new IllegalArgumentException("Invalid captured path name index");
    }
    return fileSystem.getPath(names.get(index));
  }
  @Override
  public Path subpath(int beginIndex, int endIndex) {
    if (beginIndex < 0 || endIndex > names.size() || beginIndex >= endIndex) {
      throw new IllegalArgumentException("Invalid captured path subsequence");
    }
    return build("", names.subList(beginIndex, endIndex));
  }
  @Override
  public boolean startsWith(Path other) {
    if (!(other instanceof CapturedPath prefix) || fileSystem.syntax() != prefix.fileSystem.syntax()
        || !comparison(root).equals(comparison(prefix.root)) || names.size() < prefix.names.size()) {
      return false;
    }
    for (var index = 0; index < prefix.names.size(); index++) {
      if (!comparison(names.get(index)).equals(comparison(prefix.names.get(index)))) {
        return false;
      }
    }
    return true;
  }
  @Override
  public boolean endsWith(Path other) {
    if (!(other instanceof CapturedPath suffix) || fileSystem.syntax() != suffix.fileSystem.syntax()
        || names.size() < suffix.names.size()) {
      return false;
    }
    if (!suffix.root.isEmpty()) {
      return equals(suffix);
    }
    var offset = names.size() - suffix.names.size();
    for (var index = 0; index < suffix.names.size(); index++) {
      if (!comparison(names.get(index + offset)).equals(comparison(suffix.names.get(index)))) {
        return false;
      }
    }
    return true;
  }
  @Override
  public Path normalize() {
    if (names.stream().noneMatch(name -> CURRENT_DIRECTORY.equals(name) || PARENT_DIRECTORY.equals(name))) {
      return this;
    }
    var normalized = new ArrayList<String>();
    for (var name : names) {
      if (CURRENT_DIRECTORY.equals(name)) {
        continue;
      }
      if (PARENT_DIRECTORY.equals(name) && !normalized.isEmpty() && !PARENT_DIRECTORY.equals(normalized.getLast())) {
        normalized.removeLast();
      } else if (!PARENT_DIRECTORY.equals(name) || !absolute) {
        normalized.add(name);
      }
    }
    return build(root, normalized);
  }
  @Override
  public Path resolve(Path other) {
    if (other.isAbsolute()) {
      return other;
    }
    var suffix = compatible(other);
    if (suffix.empty()) {
      return this;
    }
    if (empty()) {
      return suffix;
    }
    if (!suffix.root.isEmpty()) {
      if (comparison(root).startsWith(comparison(suffix.root))) {
        return build(root, suffix.names);
      }
      return suffix;
    }
    var joined = new ArrayList<>(names);
    joined.addAll(suffix.names);
    return build(root, joined);
  }
  @Override
  public Path relativize(Path other) {
    var target = compatible(other);
    if (absolute != target.absolute || !comparison(root).equals(comparison(target.root))) {
      throw new IllegalArgumentException("Cannot relativize captured paths with different roots");
    }
    if (empty()) {
      return target;
    }
    var common = 0;
    while (common < names.size() && common < target.names.size()
        && comparison(names.get(common)).equals(comparison(target.names.get(common)))) {
      common++;
    }
    var relative = new ArrayList<String>();
    for (var index = common; index < names.size(); index++) {
      relative.add(PARENT_DIRECTORY);
    }
    relative.addAll(target.names.subList(common, target.names.size()));
    return build("", relative);
  }
  @Override
  public URI toUri() {
    try {
      var captured = toAbsolutePath().toString();
      var location = syntax() == Syntax.WINDOWS ? captured.replace('\\', '/') : captured;
      @Nullable
      String authority = null;
      if (syntax() == Syntax.WINDOWS) {
        if (unc(captured)) {
          var serverEnd = location.indexOf('/', 2);
          authority = location.substring(2, serverEnd);
          location = location.substring(serverEnd);
        } else {
          location = "/" + location;
        }
      }
      return new URI(fileSystem.provider().getScheme(), authority, location, null, null);
    } catch (URISyntaxException failure) {
      throw new IllegalArgumentException("Captured path cannot be represented as a URI", failure);
    }
  }
  @Override
  public Path toAbsolutePath() {
    if (!absolute) {
      throw new IOError(new IOException("A captured relative path requires its captured root"));
    }
    return this;
  }
  @Override
  public Path toRealPath(LinkOption... options) throws IOException {
    throw CapturedPathProvider.unavailable(this);
  }
  @Override
  public WatchKey register(WatchService watcher, Kind<?>[] events, Modifier... modifiers) {
    throw new UnsupportedOperationException("Captured locations cannot be watched");
  }
  @Override
  public int compareTo(Path other) {
    if (!(other instanceof CapturedPath captured) || syntax() != captured.syntax()) {
      throw new ClassCastException("Captured paths can only be ordered within their syntax");
    }
    return key().compareTo(captured.key());
  }
  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof CapturedPath captured && fileSystem.syntax() == captured.fileSystem.syntax() && key().equals(captured.key());
  }
  @Override
  public int hashCode() {
    return 31 * fileSystem.syntax().hashCode() + key().hashCode();
  }
  @Override
  public String toString() {
    return text;
  }
}
