# Integration database ownership

## Commands and portable resource budgets

Run `make integration-test-neo4j` on Linux and macOS, or
`.\Invoke-Build.ps1 integration-test-neo4j` on Windows. Docker must be available.
The tests start their own pinned Community Neo4j with a generated password and
a dynamically allocated Bolt port. Existing application connection variables do
not redirect the tests to a development database.

The canonical [integration configuration](../../src/test/resources/neo4j-integration.properties)
sets an 8 GiB container memory limit, a two-CPU quota, a 512 MiB initial heap,
a 5 GiB maximum heap, a 512 MiB direct-buffer limit, and a 512 MiB page cache. The total transaction-memory
budget is 4 GiB. Swap is capped at the same total as memory, so the container
cannot hide excess memory consumption in additional swap. Transaction logs rotate
at 32 MiB and retain two files subject to Neo4j checkpoint requirements. Docker
stdout/stderr logs rotate at 10 MiB with two files. These are test-instance
settings; the persistent development Compose service has its own lifecycle.
The pinned Linux JVM trims unused native heap every 10 seconds. This returns
free native allocator pages; it does not release live objects or replace the
heap, page-cache, direct-buffer, and container limits.

Maven has a 1 GiB maximum heap through [`.mvn/jvm.config`](../../.mvn/jvm.config).
Forked compilers have a 1 GiB maximum. Surefire and Failsafe have a 2 GiB maximum through [`pom.xml`](../../pom.xml).
Packaged CLI consumers have 512 MiB maximum heaps; the independent retained-AST
query consumer has a 1 GiB maximum and retains its 60-second completion deadline.
Account for these processes, native memory, and the operating system when sizing
a runner. Provision 16 GiB of RAM for the complete database suite, including
headroom for the operating system and native memory. A container memory cap
does not bound all build processes.

AST property writes and relationship repair comparisons process at most 512
nodes or relationships per query through the
[repository adapter](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAstResultRepository.java).
All batches remain within the same write transaction: a later failure rolls back
earlier writes. This bounds individual query payloads; the complete transaction
still retains its database write state and its memory grows with the projection.
Unknown-entity deletion receives compact key-to-label and key-to-source indexes
for the full subject, instead of a complete relationship property map. Direct
NIO buffers have their own limit; the heap limit alone does not bound them.
On lookup, driver records are consumed and released as they become stored values,
before the source envelope is decoded and re-admitted. The codec compares each
reconstructed node and relationship against the stored values without retaining
a second complete storage projection. Missing and extra values remain errors.
Namespace, identity, materialization, and failure-phase checks remain part of
that lookup.

AST fixture cleanup uses transactions of 1,000 rows to avoid constructing one
large delete transaction for a completed scenario. Publication and replacement
retain their single atomic transaction.

## High-capacity local execution

The opt-in `high-capacity-tests` Maven profile uses 16 reusable Surefire forks and
16 PIT mutation workers in separate phases, with sequential JUnit execution inside
each process. Each test JVM retains its 2 GiB maximum heap; either phase therefore
has a combined maximum Java heap of 32 GiB, before Maven and native memory.
Each managed Neo4j receives
an eight-CPU quota and retains the portable 8 GiB memory limit. Maven filters only
`neo4j-integration.properties`, so independent child processes load the same
database budget from their test classpath. Other test resources remain unfiltered.
The default profile retains one fork per group and a two-CPU database quota.

Run unit tests with fresh JaCoCo coverage, then PIT, sequentially on Windows:

```powershell
.\Invoke-Build.ps1 mutation-test -MavenFlags @('-B', '--no-transfer-progress', '-Phigh-capacity-tests')
```

On Linux and macOS, use
`make mutation-test MAVEN_FLAGS='-B --no-transfer-progress -Phigh-capacity-tests'`.
This target runs the complete unit suite and generates a new JaCoCo report before
starting PIT, following the [coverage freshness contract](../../docs/build-quality.md#coverage-freshness).

Run the complete integration group only during final validation. The profile uses
three Failsafe forks and does not reuse a JVM across test classes. Each class's
JUnit root store closes its managed database before its fork completes. The
resource-lifecycle class runs its child database journeys sequentially in its own
JVM. Database classes can therefore run with up to three isolated databases at
once. Their combined container memory limit is 24 GiB; the three test JVMs add up
to 6 GiB of maximum heap, before Maven, native memory, and child consumers. Use
`.\Invoke-Build.ps1 integration-test-neo4j -MavenFlags @('-B', '--no-transfer-progress', '-Phigh-capacity-tests')`
on Windows or `make integration-test-neo4j MAVEN_FLAGS='-B --no-transfer-progress -Phigh-capacity-tests'`
on Linux and macOS for that final group.

For the local 64 GiB Windows host, the initial experimental WSL budget is 20
logical processors and 28 GiB. These are global WSL settings, applied at VM
restart; they are not repository defaults or exclusive CPU reservations. Account
for up to three active databases and the test JVMs. Sample Windows available physical memory throughout
execution and retain at least 8 GiB. If that margin cannot be maintained, stop the
owned run, verify resource cleanup, and reduce concurrency before repeating it.
Do not overlap unit, integration, or mutation campaigns in the same checkout.

For every complete integration campaign, retain CPU and memory samples and compare
the Docker container and volume inventories before and after execution. Inspect
the exact container and mounted-volume identities recorded for the campaign,
including the resource-lifecycle child processes. Verify removal after success,
test failure, or an interrupted owner. Remove any remaining campaign-owned
containers and volumes explicitly; never prune unrelated Docker resources. A
failed test must remain reported as failed even when resource cleanup succeeds.

## Disposable storage and completion

[`Neo4jIntegration`](../../src/test/java/org/aquilagraph/testsupport/Neo4jIntegration.java)
shares one database across the actual database suites in a test engine. Its root
JUnit store owns the close operation. The
[`managed database`](../../src/test/java/org/aquilagraph/testsupport/ManagedNeo4jDatabase.java)
uses automatic container removal and disposable anonymous `/data` and `/logs`
volumes. No database directory is bind-mounted into the repository.

Normal completion and exceptions close the database and verify that the container
and disposable volumes disappeared. Automatic removal also ties anonymous volume
cleanup to container exit. Testcontainers' Ryuk resource reaper remains required
to clean up when the owner JVM is forcibly terminated. If Docker itself is
unavailable, cleanup cannot be verified until that engine is available again.

Logs and non-secret resource identities are written under ignored
`target/neo4j-integration/<database-identity>/`. They remain available after the
database is discarded. They are diagnostic artifacts, not retained database
directories. Old logs can be removed independently of a running database.

## Explicit preservation and resumption

Set `AQUILA_GRAPH_TEST_PRESERVE_DATA=true` before starting the integration command
to retain the database in a newly created named volume. Only `/data` is retained;
the container and its anonymous `/logs` volume are still discarded. The command
prints the volume name and an ignored `preserved-data.properties` descriptor.
That descriptor contains the generated password and must remain private.

For example, in PowerShell:

```powershell
$env:AQUILA_GRAPH_TEST_PRESERVE_DATA = 'true'
.\Invoke-Build.ps1 integration-test-neo4j
Remove-Item Env:AQUILA_GRAPH_TEST_PRESERVE_DATA
```

On Linux or macOS:

```sh
AQUILA_GRAPH_TEST_PRESERVE_DATA=true make integration-test-neo4j
```

To attach the preserved volume to a new managed instance, also set
`AQUILA_GRAPH_TEST_PRESERVED_DATA` to the descriptor's path. Preservation must
remain explicitly enabled. Resumption validates the volume's ownership label,
uses the original password, and preserves the same volume on close. Test data
cleanup still runs inside the test cases; preservation retains the resulting
database state and is not an instruction to disable test cleanup.

After the retained data is no longer needed, remove the exact named volume with
`docker volume rm <the printed volume name>`. Preserve required data before doing
so. This operation is separate from normal test cleanup and never prunes unrelated
Docker resources.

## Windows, native Linux, and repeated execution

The same container settings and Java lifecycle apply to Docker Desktop and native
Linux Docker engines. WSL memory reclamation settings belong to the Windows host;
the test command does not edit them or shut down WSL.

Repeat integration runs within the same host or CI job when checking accumulation.
Compare Docker container and volume inventories, owned Java processes, and resource
identities before and after each run. Distinguish retained logs, cached images,
Linux filesystem cache, and Docker Desktop's VHDX allocation from live database
resources. A VHDX need not shrink after every run even when its internal space is
free. On Windows, measure the host's WSL memory separately from the database
container and the Windows JVMs.

[`Neo4jResourceLifecycleNeo4jIT`](../../src/test/java/org/aquilagraph/testsupport/Neo4jResourceLifecycleNeo4jIT.java)
drives real child processes and Docker observations for success, failure, forced
owner termination, database-container termination, and preserved-data resumption.
These controls supplement the actual repository, packaged CLI, and independent
query suites; they do not replace those behavioral gates.

## References

- [WSL resource settings](https://learn.microsoft.com/en-us/windows/wsl/wsl-config#main-wsl-settings).
- [Failsafe fork configuration](https://maven.apache.org/surefire/maven-failsafe-plugin/integration-test-mojo.html#forkCount).
- [Docker automatic removal](https://docs.docker.com/reference/cli/docker/container/run/#clean-up---rm).
- [Docker memory constraints](https://docs.docker.com/engine/containers/resource_constraints/).
- [Testcontainers resource reaper configuration](https://java.testcontainers.org/features/configuration/#customizing-ryuk-resource-reaper).
- [Neo4j memory configuration](https://neo4j.com/docs/operations-manual/current/performance/memory-configuration/).
- [JVM direct-buffer limit](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html#advanced-runtime-options-for-java).
- [Neo4j transaction logging and checkpoint-dependent pruning](https://neo4j.com/docs/operations-manual/current/database-internals/transaction-logs/).
- [Docker Desktop WSL backend](https://docs.docker.com/desktop/features/wsl/).
- [Microsoft virtual disk compaction](https://learn.microsoft.com/en-us/windows-server/administration/windows-commands/compact-vdisk).
