# Analysis observability

Aquila owns its analysis instrumentation, OpenTelemetry adapter, service
configuration, dashboards, and operational commands. The public library session owns
the observer lifetime; an embedding program closes that session. The adjacent AST comparison lab consumes this API and the
packaged adapter; it does not maintain a second telemetry implementation.

## Runtime and ownership

`AquilaGraph.open()` creates the configured analysis and observation session.
The CLI and the lab use this same entry point. The library owns the SDK, frontend,
and application composition, and closes observations with its session.
See the [library contract](library.md) for consumption and lifecycle.
`JavaFrontends.standard()` remains unobserved for low-level consumers;
`JavaObservations.open(configuration)` retains its explicit opt-in contract.
Recoverable observation lifecycle/configuration failures are reported separately
and do not replace an analysis result or exception.

The frontend emits the production operation and its environment capture,
parsing, written and attributed inventories, compiler attribution, source
translation, coverage reconciliation, syntax admission, and outcome admission.
Source discovery currently precedes the production observation. The observer
does not change AST identities, completeness rules, or admission.

The bundled `observability` classifier is built from `src/main/observability/`.
OpenTelemetry 1.68.0 publishes automatic JPMS modules. The adapter therefore
compiles on the classpath, with the same strict compiler and source checks, and
is discovered through `AnalysisObserverFactory`. The provider uses the OpenTelemetry JDK HTTP sender with `java.net.http`
resolved by the library module. Consumers do not need an `ALL-MODULE-PATH`
launcher override. The eight named product modules retain explicit module descriptors. The adapter has its own editor
project, captured compilation manifest, Javadoc, bytecode analysis, and coverage
inputs. Maven dependency analysis receives an aggregate of the separately
published module and adapter classes; compiler classpaths exclude that derived
analysis copy. Bytecode analysis also resolves the matching OpenTelemetry API
incubator artifact because the stable SDK references its optional configuration
types. This artifact is analysis tooling only; it is not a product dependency or
part of the runtime distribution. The adapter neither installs a global SDK nor
changes the host logging backend.
Analysis stage parentage is maintained internally on the analysis thread;
external instrumentation does not inherit an ambient Aquila span automatically.
The frontend's Flight Recorder dependency is confined to the internal
`JavaAnalysisStage` boundary. Architecture checks continue rejecting JFR types in
other frontend classes and in the source model, and prevent platform recording
types from entering the public API.

## Configuration

| Variable | Default | Meaning |
| --- | --- | --- |
| `AQUILA_OBSERVABILITY_ENABLED` | `true` | Library/CLI collection; explicitly set `false` to disable it. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://127.0.0.1:4318` | Base OTLP HTTP endpoint, without a signal suffix. |
| `OTEL_SERVICE_NAME` | `aquila-graph` | Host service identity; use `aquila-lab` for lab campaigns. |
| `AQUILA_VERSION` | Packaged implementation version, otherwise `development` | Optional artifact provenance override. |

These are the supported settings of this adapter. It is not the OpenTelemetry
Java agent and does not interpret every agent or SDK autoconfiguration option.

Span and log queues each hold at most 2,048 records, with batches of 256 and a
200 ms schedule. Export attempts have a two-second timeout. Metrics export every
five seconds. Close requests a final flush and shutdown, each with a five-second
wait bound, and reports unsuccessful delivery. The library session waits for its active operation before closing observations.
Low-level observer consumers must join their workers before close. A forced process termination can lose SDK-buffered telemetry.

Each analysis has an execution ID and trace ID. Stage logs, diagnostics, spans,
and `org.aquilagraph.JavaAnalysisStage` JFR events share these identifiers.
Diagnostic emission is limited to 100 entries per analysis, followed by an
explicit omitted count. Log attribute strings are limited to 2,048 characters.
The cumulative `aquila.analysis.*` instruments retain bounded stage/profile/outcome
labels. Dedicated `aquila.execution.*` gauges carry execution and trace IDs so a
reused library session cannot mix different analyses. They represent current or
frozen execution snapshots, not cumulative totals across runs. Input paths and
messages remain outside metric labels. The registry retains active executions
plus the last 32 completions, flushes at completion before evicting old snapshots,
and uses backend retention for history. Active cardinality follows caller
concurrency; backend cardinality follows execution volume within the seven-day,
1 GB Prometheus retention boundary. This explicit per-execution series budget
replaces the earlier blanket prohibition on execution labels.

The execution dashboard selects exactly one execution, newest first, and never
offers an aggregate `All` selection. Only the new execution metrics populate the
selector; legacy cumulative measurements are not relabeled as individual runs.
Historical data is retained and remains available through an explicit selection
and time range. The JVM dashboard shares that selector. The infrastructure
dashboard describes shared services in the selected time window, with no AST
result aggregation. Its SDK queue and delivery panels select one SDK instance
without an aggregate `All` option. The execution identity table links directly
to that instance. Dashboard links preserve the execution and time selection.

Execution and stage gauges expose measured elapsed time while running and freeze
on completion. Input file count is available at start. Translated node count becomes available
after source translation, independently of final admission. Admitted node count is
absent until an AST outcome exists; unavailable is not zero. State codes are
0 running, 1 complete, 2 incomplete, 3 failed, 4 cancelled, 6 succeeded (stage),
7 aborted, and 8 AST unavailable. The dashboard derives 5, telemetry interrupted, when an unfinished
execution has no heartbeat for 20 seconds. It does not infer a timeout or successful
cancellation from a disconnected process. A terminal snapshot stays terminal.
Time-series panels stop extending a sample once the heartbeat is stale.

The frontend checks interruption between stages and during coverage reconciliation.
Acknowledged interruption throws `CancellationException`, retains the thread's
interrupt flag, and closes observations with `CANCELLED`. This is cooperative
cancellation, not a general hard deadline for compiler or arbitrary consumer code.
The lab remains the owner of its 240-second result deadline and waits up to 15
seconds for the interrupted worker before closing its JFR recording. A timeout
and its acknowledged cancellation are different facts, available respectively in
the lab diagnostics and the library telemetry. Bounded exporter waits preserve
the interrupt flag while allowing terminal observations to drain.

The JVM dashboard includes used/committed/limited memory, heap occupancy, live and
daemon threads, loaded classes, process CPU, available processors, and GC
collection count/time. CPU and GC values subtract baselines captured at execution
start, so earlier JDT/JavaParser activity does not inflate them. These still cover
the whole host JVM, including concurrent unrelated work. Never add resource
measurements from concurrent executions in the same process. Recent CPU usage
uses a derivative requiring two observations; short runs retain total CPU and
average cores. GC collection time is an MXBean measure, not a GC pause histogram.

Single-run stage durations are exact elapsed observations, including unfinished
stages. The parent production duration overlaps its children and must not be
summed with them. Histograms remain available for explicit aggregate studies;
the run dashboard does not present interpolated bucket quantiles as single-run
measurements. Diagnostics show emitted entries; the 100-entry cap and omitted
count remain explicit in logs.

JFR supplies method, allocation, CPU, lock, and GC details when enabled by the
host. Retain the JFR file alongside the campaign manifest and use its event IDs
to find the corresponding trace. No profile recording is started implicitly.

## Local service operation

The Java workload runs natively on Windows with JDK 25. Following the explicit
Docker decision, infrastructure runs as Linux containers under Docker Desktop.
The repository also requires Linux support; this Windows operation alone is not
Linux-host acceptance evidence.

From the repository root:

```powershell
.\infra\observability\Invoke-Observability.ps1 Start
.\infra\observability\Invoke-Observability.ps1 Check
.\infra\observability\Invoke-Observability.ps1 Status
.\infra\observability\Invoke-Observability.ps1 Stop
```

`Start` pulls the pinned images, creates persistent directories, starts Compose,
checks readiness, and sets the local Grafana administrator credentials to
`admin` / `admin`, including an existing database. `Stop`
removes the containers and network while retaining data. Services publish only
loopback ports. This configuration is for one trusted development machine; remote
or shared deployments require their own authentication and transport boundary.

| Service | Pinned version | Local endpoint |
| --- | --- | --- |
| OpenTelemetry Collector Contrib | 0.165.0 | OTLP HTTP `4318`; health `13133` |
| Prometheus | 3.16.0 | <http://127.0.0.1:9090> |
| Loki | 3.7.10 | <http://127.0.0.1:3100> |
| Jaeger | 2.23.0 | <http://127.0.0.1:16686> |
| Grafana | 13.3.1 | <http://127.0.0.1:3000> |

The Prometheus YAML file explicitly selects the repository's OTLP-aware editor
schema. It reuses SchemaStore's existing field schemas and adds the documented
OTLP receiver fields missing from that schema. Validation remains enabled, with
unknown OTLP fields and invalid value types rejected. Runtime validation remains
`promtool check config`; editor schema acceptance alone is not runtime evidence.

Image digests are fixed in [Compose](../infra/observability/compose.yaml).
Grafana login and password are both `admin`, as required for this loopback-only
development profile. No password file is read. Existing unrelated credentials
and persistent observations are not deleted.
Dashboard and datasource provisioning is versioned under `infra/observability/`.
Dashboard files are polled every 30 seconds because Windows Docker bind mounts
may not propagate filesystem watch events.

The bundled Grafana Jaeger datasource 13.0.8 requests `/api/services`, which
Jaeger 2.23.0 no longer serves. Therefore Grafana log/exemplar links open the
Jaeger UI directly. No incompatible datasource is provisioned. Jaeger readiness
and service discovery use `/api/v3/services`. Revisit native Grafana trace
queries when a compatible datasource release is available.

## Storage, failure, and operations

Persistent service data lives under ignored `.local/observability/` on the
project drive, outside Maven clean output. Prometheus has seven-day and 1 GB
retention limits. Loki and Jaeger retain seven days; they have no aggregate
filesystem size quota in this local profile. Monitor free disk space and stop
campaigns before capacity is exhausted. Docker logs rotate at three 10 MB files
per service. Compose sets explicit CPU and memory limits.

The Collector uses disk-backed queues, bounded to 1,000 requests per exporter,
with two consumers, bounded batches, memory limiting, and retries for up to five
minutes. Queue fullness, receive refusal, export failure, and loss must remain
visible through Collector logs and internal metrics. Persistence protects queued
data across Collector restarts; it is not a guarantee against disk exhaustion,
corruption, prolonged outage, or a process killed before a receiver accepts data.
The local Badger-backed Jaeger deployment is persistent but single-node.

Prometheus evaluates service, queue pressure, rejected telemetry, and analysis
failure, acknowledged cancellation, and interrupted-observation rules. Alerts are locally inspectable; no external notification receiver
is configured. SDK loss and analysis failure rules also inspect the first sample
of a new instance, since a short CLI process may export only once. Rule tests in
`infra/observability/alerts-test.yaml` cover first samples, successful completion,
expiration of an unchanged historical failure, cancellation isolation, and a
missing heartbeat without misclassifying a completed execution. Run them with the pinned
Prometheus image's `promtool test rules` command, together with
`infra/observability/dashboard-queries-test.yaml`. The dashboard query cases
distinguish live, disconnected, and completed executions and assert that selecting
one execution excludes another execution's file count. The infrastructure dashboard includes five service scrape states, queues,
accepted/exported/refused telemetry, send/enqueue failures, SDK queues and
processed/dropped records by instance,
service CPU/memory, Prometheus series/storage/persistence failures, Jaeger storage,
and Loki ingestion/WAL usage. Metrics absent from a service are left unavailable,
not fabricated as healthy zeros. Scrape status is not an end-to-end delivery test.
The SDK's legacy `queueSize_ratio` metric is a record count despite its translated
suffix. It is displayed as a count; no capacity percentage is inferred from it.
Host free disk, per-method allocation, lock contention, and pause distributions
are not inferred from these gauges; use host tooling and the retained JFR recording.
JMeter is not required for
observing a CLI/library workload.

## Validation responsibilities

- Run `./Invoke-Build.ps1 quality` and `./Invoke-Build.ps1 coverage` on Windows
  (`make quality` and `make coverage` on Linux). The adapter is part of the product
  quality and coverage surface.
- Isolated checks compare admitted AST identities, parent/child spans,
  correlated logs/JFR events, bounded metric attributes, concurrent execution
  context, and preservation of analysis behavior when observations fail.
- Service acceptance separately requires real OTLP ingestion and queries in all
  destinations, persistence after restart, exporter outage/recovery, and an
  observed short-lived process shutdown. Readiness alone does not establish these.
- Measure overhead with the same captured corpus, JVM options, warmup, and
  alternating enabled/disabled runs. Report absolute times and the observation
  boundary; a small sample cannot establish full-corpus performance.

## References

- [Frontend stage boundary](../src/main/java/org.aquilagraph.frontend/org/aquilagraph/frontend/internal/JavaAnalysisStage.java).
- [SDK adapter](../src/main/observability/org/aquilagraph/observability/OpenTelemetryObserverFactory.java).
- [OpenTelemetry HTTP senders](https://opentelemetry.io/docs/languages/java/sdk/#senders).
- [OpenTelemetry Java instrumentation](https://opentelemetry.io/docs/languages/java/instrumentation/).
- [OpenTelemetry 1.68.0 legacy span processor metrics](https://github.com/open-telemetry/opentelemetry-java/blob/v1.68.0/sdk/trace/src/main/java/io/opentelemetry/sdk/trace/export/LegacySpanProcessorInstrumentation.java).
- [Collector resiliency](https://opentelemetry.io/docs/collector/resiliency/).
- [Collector internal telemetry](https://opentelemetry.io/docs/collector/internal-telemetry/).
- [Prometheus configuration schema](../config/schemas/prometheus.schema.json).
- [SchemaStore Prometheus base schema](https://json.schemastore.org/prometheus.json).
- [Prometheus configuration reference](https://prometheus.io/docs/prometheus/latest/configuration/configuration/).
- [Prometheus OTLP receiver](https://prometheus.io/docs/guides/opentelemetry/).
- [Loki OTLP ingestion](https://grafana.com/docs/loki/latest/send-data/otel/).
- [Jaeger Badger storage](https://www.jaegertracing.io/docs/2.23/storage/badger/).
- [Grafana Jaeger 13.0.8 HTTP client](https://github.com/grafana/grafana-jaeger-datasource/blob/v13.0.8/pkg/jaeger/client.go).
- [Grafana dashboard update detection](https://grafana.com/docs/grafana/latest/administration/provisioning/#detect-updates-to-provisioned-dashboards-files).
- [Jaeger 2.23 HTTP routes](https://github.com/jaegertracing/jaeger/blob/v2.23.0/cmd/jaeger/internal/extension/jaegerquery/internal/apiv3/http_gateway.go).
- [Grafana administrator CLI](https://grafana.com/docs/grafana/latest/administration/cli/).
- [Prometheus query functions](https://prometheus.io/docs/prometheus/latest/querying/functions/).
- [Java 25 GC collection measurements](https://docs.oracle.com/en/java/javase/25/docs/api/java.management/java/lang/management/GarbageCollectorMXBean.html#getCollectionTime()).
- [Java 25 process CPU time](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/ProcessHandle.Info.html#totalCpuDuration()).
- [JFR on Java 25](https://docs.oracle.com/en/java/javase/25/jfapi/index.html).
