package org.aquilagraph.build;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Properties;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

/** Immutable ownership shared through durable records rather than ambient mutable policy. */
record InvocationContext(Path root, Path directory, Path toolRoot, String nonce, ProcessIdentity owner, String namespace) {
  private static final String OWNER_NAMESPACE = "owner.namespace";
  private static final String ROOT_PROPERTY = "root";
  private static final String TOOL_ROOT_PROPERTY = "tool.root";
  static InvocationContext create(BuildRequest request, String nonce) throws IOException {
    var directory = request.root().resolve(".build-state/invocations").resolve(nonce);
    var result = new InvocationContext(request.root(), directory, request.toolRoot(), nonce,
        ProcessIdentity.observe(ProcessHandle.current()), ProcessIdentity.namespace());
    var record = result.properties();
    record.setProperty("operation", request.operation().command());
    record.setProperty("goals", request.operation().goals().toString());
    record.setProperty("maven", request.maven().toString());
    record.setProperty("argument.count", Integer.toString(request.mavenArguments().size()));
    for (var index = 0; index < request.mavenArguments().size(); index++) {
      record.setProperty("argument." + index, request.mavenArguments().get(index));
    }
    BuildFiles.write(directory.resolve("invocation.properties"), record);
    return result;
  }

  static InvocationContext read(Path root, String nonce) throws IOException {
    if (!nonce.matches("[0-9a-f-]{36}")) {
      throw new IOException("Invalid local invocation nonce");
    }
    var canonical = root.toRealPath();
    var directory = canonical.resolve(".build-state/invocations").resolve(nonce);
    var record = BuildFiles.read(directory.resolve("invocation.properties"));
    if (!canonical.equals(Path.of(BuildFiles.required(record, ROOT_PROPERTY)).toRealPath())
        || !nonce.equals(BuildFiles.required(record, "nonce"))) {
      throw new IOException("Local build invocation belongs to another canonical root or nonce");
    }
    return new InvocationContext(canonical, directory, Path.of(BuildFiles.required(record, TOOL_ROOT_PROPERTY)).toRealPath(), nonce,
        new ProcessIdentity(Long.parseLong(BuildFiles.required(record, "owner.pid")),
            Instant.parse(BuildFiles.required(record, "owner.started"))),
        BuildFiles.required(record, OWNER_NAMESPACE));
  }

  static InvocationContext observation(Properties intent) throws IOException {
    var root = Path.of(BuildFiles.required(intent, ROOT_PROPERTY)).toRealPath();
    var nonce = UUID.randomUUID().toString();
    var result = new InvocationContext(root, root.resolve(".build-state/invocations").resolve(nonce),
        Path.of(BuildFiles.required(intent, TOOL_ROOT_PROPERTY)).toRealPath(), nonce, ProcessIdentity.observe(ProcessHandle.current()),
        ProcessIdentity.namespace());
    var record = result.properties();
    record.setProperty("operation", "process-observation");
    record.setProperty("goals", "[]");
    BuildFiles.write(result.directory().resolve("invocation.properties"), record);
    return result;
  }

  Properties properties() {
    var result = new Properties();
    result.setProperty(ROOT_PROPERTY, root.toString());
    result.setProperty(TOOL_ROOT_PROPERTY, toolRoot.toString());
    result.setProperty("nonce", nonce);
    result.setProperty("owner.pid", Long.toString(owner.pid()));
    result.setProperty("owner.started", owner.started().toString());
    result.setProperty("owner.platform", System.getProperty("os.name"));
    result.setProperty(OWNER_NAMESPACE, namespace);
    result.setProperty("launcher.java.home", System.getProperty("java.home"));
    result.setProperty("launcher.java.version", System.getProperty("java.version"));
    return result;
  }

  void requireIdentity(Properties record) throws IOException {
    requireIdentity(properties(), record);
  }

  static void requireIdentity(Properties expected, Properties record) throws IOException {
    var names = new ArrayList<>(List.of(ROOT_PROPERTY, TOOL_ROOT_PROPERTY, "nonce", "owner.pid", "owner.started"));
    if (expected.containsKey(OWNER_NAMESPACE)) {
      names.add(OWNER_NAMESPACE);
    } else if (expected.containsKey("owner.platform")) {
      names.add("owner.platform");
    }
    for (var name : names) {
      if (!BuildFiles.required(expected, name).equals(BuildFiles.required(record, name))) {
        throw new IOException("Build record differs from the exact invocation owner: " + name);
      }
    }
  }

  Path entry(String simpleName) {
    return toolRoot.resolve("config/build/org/aquilagraph/build").resolve(simpleName + ".java");
  }

  void requireLiveOwner() throws IOException {
    if (owner.liveProcess().isEmpty()) {
      throw new IOException("Local build owner is no longer alive; mutation is refused");
    }
  }
}
