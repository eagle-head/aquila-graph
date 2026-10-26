package org.aquilagraph.build;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class PowerShellDiagnosticsTest {
  @Test
  void givenProgressAndWarnings_whenDiagnosticsAreAdmitted_thenOnlyInformationalProgressIsAccepted() throws Exception {
    // Arrange
    var envelope = "#< CLIXML\n<Objs xmlns=\"http://schemas.microsoft.com/powershell/2004/04\"><Obj S=\"%s\"><S>message</S></Obj></Objs>";

    // Act
    PowerShellDiagnostics.requireProgressOnly(envelope.formatted("progress"));
    var warning = assertThrows(IOException.class, () -> PowerShellDiagnostics.requireProgressOnly(envelope.formatted("warning")));
    var error = assertThrows(IOException.class, () -> PowerShellDiagnostics.requireProgressOnly(envelope.formatted("error")));

    // Assert
    assertTrue(warning.getMessage().contains("non-progress"));
    assertTrue(error.getMessage().contains("non-progress"));
  }
}
