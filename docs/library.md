# Aquila Graph library

Aquila is available as a Java 25 library and a CLI. Both use the same
`AquilaGraph` session, which owns frontend/application composition, lazy storage
ports, and observability. Consumers receive native typed results. They do not
need to assemble frontend implementations, SDKs, exporters, or dependency POMs.

## Artifact and local installation

The Maven entry point is `org.aquilagraph:aquila-graph-library:0.1.0-SNAPSHOT`.
It resolves the product modules and observation provider transitively. The CLI
entry point is not a dependency of this library artifact. The build produces its
POM from the product version; consumers must not recreate that metadata.

On Windows, install into the product's local repository with
`.\Invoke-Build.ps1 install-library`. To install into the adjacent lab cache:

```powershell
.\Invoke-Build.ps1 install-library -MavenFlags @('-B', '--no-transfer-progress', '-Daquila.library.repository=D:\Projects\aquila-graph\lab\.cache\maven')
```

On Linux use `make install-library`, optionally supplying an absolute destination
with `MAVEN_FLAGS='-B --no-transfer-progress -Daquila.library.repository=/absolute/path'`.
Installation packages artifacts without executing tests; acceptance uses the
separate quality and coverage targets. Installation is local, not a publication
to Maven Central. SNAPSHOT artifacts are mutable. Preserve the source revision,
checkout state, and artifact digests when recording a comparison campaign.

Add one dependency to the consumer POM:

```xml
<dependency>
  <groupId>org.aquilagraph</groupId>
  <artifactId>aquila-graph-library</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

For JPMS, declare `requires org.aquilagraph.library;`. Its public application,
source, identity, and CFG API dependencies are transitive. The library can also
be used on the classpath. No CLI invocation or parsing of console output is part
of library consumption.

## Session and operations

```java
import org.aquilagraph.library.AquilaGraph;

try (var aquila = AquilaGraph.open()) {
  var outcome = aquila.produceAst(request);
}
```

The caller supplies an existing `AstProductionRequest`: captured input, compiler
configuration, and an explicit AST profile. The library preserves these domain
contracts, including truthful incomplete/rejected outcomes and diagnostics.

| Operation | Input | Result and effects |
| --- | --- | --- |
| `produceAst` | `AstProductionRequest` | `AstOutcome`; no CFG or publication. |
| `analyze` | `AnalysisComputationRequest` | `AnalysisRun`; optional selected CFG, no publication. |
| `execute` | `AnalysisRequest` | `AnalysisExecution`; explicit publication intent controls storage. |

Default Neo4j settings are captured from the environment, but repository
acquisition is lazy. Source-only analysis and `DO_NOT_SAVE` do not acquire a
database connection. An overload accepts the existing repository factory ports
when an embedding application selects its own persistence boundary.

One session serializes operations. `close()` waits for an active operation,
flushes observations, and closes their resources once. Calling an operation after
close throws `IllegalStateException`. Reuse a session for sequential analyses;
use separate sessions for concurrent analysis. There is no operation deadline or
forced cancellation in this facade. A non-cooperating analysis can delay close.
The frontend cooperates with thread interruption at stage boundaries and during
coverage reconciliation, throwing `CancellationException` and emitting a
`CANCELLED` observation when it acknowledges cancellation. The caller owns any
deadline and must await worker termination; an interruption request alone does
not establish that work stopped. See the [observation lifecycle](observability.md).
The library does not exit the process, print command results, register a global
OpenTelemetry SDK, or start Docker containers.

## Built-in observability

`AquilaGraph.open()` enables product-owned instrumentation and the bundled
provider by default, using the local OTLP HTTP endpoint. The CLI uses this same
configuration. `ObservabilityConfiguration` offers explicit overrides without
exposing SDK types. `ObservabilityConfiguration.disabled()` requests offline
execution without SDK/exporter activity. Environment settings and infrastructure
operations are defined in [observability](observability.md).

Opening a session does not require the telemetry services to be available.
Export failures remain visible in diagnostics while AST analysis continues.
A caller closes only the Aquila session, not each SDK component. Low-level
`JavaFrontends.standard()` and `JavaObservations` remain available for specialized
composition; their existing opt-in behavior is unchanged.

The comparison lab depends on this artifact and opens a session. Its remaining
adapter maps Aquila's result into the lab's cross-tool observation schema. That
schema belongs to the comparison experiment and is not a second Aquila API.

## References

- [Public session](../src/main/java/org.aquilagraph.library/org/aquilagraph/library/AquilaGraph.java).
- [Observation configuration](../src/main/java/org.aquilagraph.library/org/aquilagraph/library/ObservabilityConfiguration.java).
- [Module descriptor](../src/main/java/org.aquilagraph.library/module-info.java).
- [Artifact metadata and packaging](../pom.xml), `prepare-library-metadata`, `package-library`, and `install-library-*`.
- [CLI composition](../src/main/java/org.aquilagraph.cli/org/aquilagraph/AquilaGraphApplication.java).
- [Library behavior tests](../src/test/java/org/aquilagraph/library/AquilaGraphTest.java).
- [Library observation test](../src/test/java/org/aquilagraph/library/LibraryObservationTest.java).
- [Maven local artifact installation](https://maven.apache.org/plugins/maven-install-plugin/install-file-mojo.html).
