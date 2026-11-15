package org.aquilagraph.identity.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** An immutable lexical location with an explicitly selected syntax and exact case-preserving spelling. */
public final class CapturedLocation {
  private static final Pattern POSIX_COMPONENT = Pattern.compile("[^/]+");
  private static final Pattern WINDOWS_COMPONENT = Pattern.compile("[^/\\\\]+");
  private static final Pattern INVALID_WINDOWS_CHARACTER = Pattern.compile("[<>:\"|?*\\x00-\\x1f]");

  private static final String CURRENT_DIRECTORY = ".";
  private static final String PARENT_DIRECTORY = "..";
  private static final int DRIVE_PREFIX_LENGTH = 2;
  private static final int UNC_PREFIX_LENGTH = 2;

  private final Syntax locationSyntax;
  private final Root capturedRoot;
  private final List<String> pathComponents;
  private final String normalizedSpelling;

  private CapturedLocation(Syntax syntax, Root root, List<Component> normalized) {
    locationSyntax = syntax;
    capturedRoot = root;
    pathComponents = List.copyOf(normalized.stream().map(Component::name).toList());
    var text = new StringBuilder(root.spelling());
    for (var component : normalized) {
      if (!text.isEmpty() && !separator(syntax, text.charAt(text.length() - 1))) {
        text.append(component.separator());
      }
      text.append(component.name());
    }
    normalizedSpelling = text.toString();
  }

  /**
   * Parses and normalizes a location without consulting a filesystem or working directory.
   *
   * @param syntax
   *          explicit lexical syntax
   * @param spelling
   *          input location text
   * @return a detached location with interior dot segments resolved
   * @throws IllegalArgumentException
   *           if the root is invalid, the text contains NUL, or traversal crosses its root
   */
  public static CapturedLocation parse(Syntax syntax, String spelling) {
    Objects.requireNonNull(syntax, "syntax");
    Objects.requireNonNull(spelling, "spelling");
    if (spelling.indexOf('\0') >= 0) {
      throw new IllegalArgumentException("A captured location cannot contain NUL");
    }
    var root = root(syntax, spelling);
    var normalized = new ArrayList<Component>();
    var previousEnd = root.spelling().length();
    var names = (syntax == Syntax.WINDOWS ? WINDOWS_COMPONENT : POSIX_COMPONENT).matcher(spelling).region(previousEnd, spelling.length());
    while (names.find()) {
      var name = names.group();
      if (PARENT_DIRECTORY.equals(name)) {
        if (normalized.isEmpty()) {
          throw new IllegalArgumentException("Captured location traverses beyond its root: " + spelling);
        }
        normalized.removeLast();
      } else if (!CURRENT_DIRECTORY.equals(name)) {
        requireName(syntax, name);
        normalized.add(new Component(spelling.substring(previousEnd, names.start()), name));
      }
      previousEnd = names.end();
    }
    return new CapturedLocation(syntax, root, normalized);
  }

  /**
   * Returns the lexical syntax used to parse and resolve this location.
   *
   * @return captured syntax
   */
  public Syntax syntax() {
    return locationSyntax;
  }

  /**
   * Returns the captured root kind.
   *
   * @return relative, POSIX absolute, Windows drive, or Windows UNC root kind
   */
  public RootKind rootKind() {
    return capturedRoot.kind();
  }

  /**
   * Returns the components that identify the parsed root independently of its separator spelling.
   *
   * @return an empty list for rootless and POSIX absolute paths, the drive letter, or the UNC server and share
   */
  public List<String> rootComponents() {
    return capturedRoot.components();
  }

  /**
   * Returns the immutable sequence of normalized path components below the root.
   *
   * @return case-preserving location names in order
   */
  public List<String> components() {
    return pathComponents;
  }

  /**
   * Returns the normalized text with the captured case and surviving separator spelling.
   *
   * @return normalized captured location text
   */
  public String spelling() {
    return normalizedSpelling;
  }

  /**
   * Tests whether this location has an absolute root in its own syntax.
   *
   * @return whether the location is absolute
   */
  public boolean isAbsolute() {
    return capturedRoot.kind() != RootKind.RELATIVE;
  }

  /**
   * Tests a case-sensitive, component-wise prefix under the same syntax and root.
   *
   * @param prefix
   *          candidate containing location
   * @return whether the prefix contains this location lexically, including an exact match
   */
  public boolean startsWith(CapturedLocation prefix) {
    Objects.requireNonNull(prefix, "prefix");
    return locationSyntax == prefix.locationSyntax && capturedRoot.kind() == prefix.capturedRoot.kind()
        && capturedRoot.components().equals(prefix.capturedRoot.components()) && pathComponents.size() >= prefix.pathComponents.size()
        && pathComponents.subList(0, prefix.pathComponents.size()).equals(prefix.pathComponents);
  }

  /**
   * Appends a normalized rootless location of compatible syntax.
   *
   * @param relative
   *          a non-escaping relative location
   * @return combined detached location
   * @throws IllegalArgumentException
   *           if the supplied location is rooted or uses different syntax
   */
  public CapturedLocation resolve(CapturedLocation relative) {
    Objects.requireNonNull(relative, "relative");
    if (relative.locationSyntax != locationSyntax || relative.isAbsolute()) {
      throw new IllegalArgumentException("A captured location requires a rootless name of the same syntax");
    }
    if (relative.normalizedSpelling.isEmpty()) {
      return this;
    }
    if (normalizedSpelling.isEmpty()) {
      return relative;
    }
    var lastSeparator = locationSyntax == Syntax.WINDOWS
        ? Math.max(normalizedSpelling.lastIndexOf('/'), normalizedSpelling.lastIndexOf('\\'))
        : normalizedSpelling.lastIndexOf('/');
    if (lastSeparator == normalizedSpelling.length() - 1) {
      return parse(locationSyntax, normalizedSpelling + relative.normalizedSpelling);
    }
    var join = lastSeparator < 0 ? (locationSyntax == Syntax.WINDOWS ? '\\' : '/') : normalizedSpelling.charAt(lastSeparator);
    return parse(locationSyntax, normalizedSpelling + join + relative.normalizedSpelling);
  }

  /**
   * Returns the slash-separated name of a rootless location for source ordering and identity.
   *
   * @return canonical relative name
   * @throws IllegalArgumentException
   *           if the location has a root
   */
  public String canonicalRelativeName() {
    if (isAbsolute()) {
      throw new IllegalArgumentException("A canonical relative name requires a rootless captured location");
    }
    return String.join("/", pathComponents);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CapturedLocation location && locationSyntax == location.locationSyntax
        && normalizedSpelling.equals(location.normalizedSpelling);
  }

  @Override
  public int hashCode() {
    return Objects.hash(locationSyntax, normalizedSpelling);
  }

  @Override
  public String toString() {
    return normalizedSpelling;
  }

  private static Root root(Syntax syntax, String text) {
    if (text.isEmpty()) {
      return new Root(RootKind.RELATIVE, List.of(), "");
    }
    if (syntax == Syntax.POSIX) {
      var afterRoot = nextComponent(syntax, text, 0);
      return afterRoot == 0
          ? new Root(RootKind.RELATIVE, List.of(), "")
          : new Root(RootKind.POSIX_ABSOLUTE, List.of(), text.substring(0, afterRoot));
    }
    if (text.length() >= DRIVE_PREFIX_LENGTH && text.charAt(1) == ':' && driveLetter(text.charAt(0))) {
      var afterRoot = nextComponent(syntax, text, DRIVE_PREFIX_LENGTH);
      if (afterRoot == DRIVE_PREFIX_LENGTH) {
        throw new IllegalArgumentException("A captured Windows drive must be absolute: " + text);
      }
      return new Root(RootKind.WINDOWS_DRIVE, List.of(text.substring(0, 1)), text.substring(0, afterRoot));
    }
    if (separator(syntax, text.charAt(0))) {
      if (text.length() >= UNC_PREFIX_LENGTH && separator(syntax, text.charAt(1))) {
        return uncRoot(text);
      }
      throw new IllegalArgumentException("A captured Windows root requires a drive or a UNC share: " + text);
    }
    return new Root(RootKind.RELATIVE, List.of(), "");
  }

  private static Root uncRoot(String text) {
    var names = WINDOWS_COMPONENT.matcher(text).region(UNC_PREFIX_LENGTH, text.length());
    if (!names.lookingAt()) {
      throw new IllegalArgumentException("A captured UNC root requires a server and a share: " + text);
    }
    var server = names.group();
    if (!names.find()) {
      throw new IllegalArgumentException("A captured UNC root requires a server and a share: " + text);
    }
    var share = names.group();
    if (CURRENT_DIRECTORY.equals(server) || PARENT_DIRECTORY.equals(server) || CURRENT_DIRECTORY.equals(share)
        || PARENT_DIRECTORY.equals(share)) {
      throw new IllegalArgumentException("A captured UNC root requires a named server and share: " + text);
    }
    requireName(Syntax.WINDOWS, server);
    requireName(Syntax.WINDOWS, share);
    return new Root(RootKind.WINDOWS_UNC, List.of(server, share), text.substring(0, names.end()));
  }

  private static void requireName(Syntax syntax, String name) {
    if (syntax == Syntax.WINDOWS && INVALID_WINDOWS_CHARACTER.matcher(name).find()) {
      throw new IllegalArgumentException("Invalid name in captured Windows location: " + name);
    }
  }

  private static int nextComponent(Syntax syntax, String text, int start) {
    var index = start;
    while (index < text.length() && separator(syntax, text.charAt(index))) {
      index++;
    }
    return index;
  }

  private static boolean separator(Syntax syntax, char character) {
    return character == '/' || (syntax == Syntax.WINDOWS && character == '\\');
  }

  private static boolean driveLetter(char character) {
    return (character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z');
  }

  private record Root(RootKind kind, List<String> components, String spelling) {
  }

  private record Component(String separator, String name) {
  }

  /** Lexical syntax of a captured location. */
  public enum Syntax {
    /** Forward slash separates components and backslash is a regular character. */
    POSIX,
    /** Backslash and forward slash separate components. */
    WINDOWS
  }

  /** Root kind of a captured location, independent of its separator spelling. */
  public enum RootKind {
    /** Rootless and relative. */
    RELATIVE,
    /** Rooted at a POSIX slash. */
    POSIX_ABSOLUTE,
    /** Rooted at a Windows drive. */
    WINDOWS_DRIVE,
    /** Rooted at a Windows UNC server and share. */
    WINDOWS_UNC
  }
}
