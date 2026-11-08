package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.ProcessHandle.Info;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

@Tag("process")
@EnabledOnOs(OS.LINUX)
class LinuxProcessIdentityProcessIT {
  @TempDir Path directory;

  @Test
  void givenLiveNativeBirthAndShiftedJvmClockMetadata_whenDurableOwnerIsReopened_thenExactBirthSurvivesAndForeignBirthIsRefused() throws Exception {
    // Arrange
    var process = ProcessHandle.current();
    var original = ProcessIdentity.observe(process);
    var shifted = original.started().plusSeconds(1);
    var info = (Info) Proxy.newProxyInstance(Info.class.getClassLoader(), new Class<?>[] {Info.class},
        (proxy, method, arguments) -> {
          if ("startInstant".equals(method.getName())) {
            return Optional.of(shifted);
          }
          throw new UnsupportedOperationException(method.getName());
        });
    var metadata = (ProcessHandle) Proxy.newProxyInstance(ProcessHandle.class.getClassLoader(), new Class<?>[] {ProcessHandle.class},
        (proxy, method, arguments) -> switch (method.getName()) {
          case "info" -> info;
          case "pid" -> process.pid();
          case "isAlive" -> process.isAlive();
          default -> throw new UnsupportedOperationException(method.getName());
        });
    var nonce = UUID.randomUUID().toString();
    var root = directory.toRealPath();
    var invocation = root.resolve(".build-state/invocations").resolve(nonce);

    // Act
    var observed = ProcessIdentity.observe(metadata);
    var context = new InvocationContext(root, invocation, Path.of("").toRealPath(), nonce, observed, ProcessIdentity.namespace());
    BuildFiles.write(invocation.resolve("invocation.properties"), context.properties());
    var reopened = InvocationContext.read(root, nonce);
    reopened.requireLiveOwner();
    var nativeStart = original.linuxStart().orElseThrow();
    var foreign = new ProcessIdentity(process.pid(), original.started(),
        Optional.of(new LinuxProcessStart(nativeStart.bootId(), nativeStart.ticks() + 1)));
    var refused = new InvocationContext(root, invocation, context.toolRoot(), nonce, foreign, context.namespace());
    var failure = assertThrows(IOException.class, refused::requireLiveOwner);
    var legacy = new ProcessIdentity(process.pid(), shifted);
    var unknown = assertThrows(IOException.class, legacy::liveProcess);

    // Assert
    assertEquals(shifted, reopened.owner().started());
    assertEquals(original, reopened.owner());
    assertEquals(original.linuxStart(), reopened.owner().linuxStart());
    assertEquals(process.pid(), reopened.owner().liveProcess().orElseThrow().pid());
    assertTrue(process.isAlive());
    assertEquals("Local build owner is no longer alive; mutation is refused", failure.getMessage());
    assertEquals("Legacy Linux process identity cannot resolve a live PID without native birth evidence: " + process.pid(), unknown.getMessage());
  }
}
