# Build Quality Gates

## Purpose

The project treats production code under `src/main/java` as a build contract. A focused Checkstyle
policy also rejects unnecessary fully qualified type references under `src/test/java`; the remaining
static-analysis and formatting gates apply only to production sources. The default `verify`
lifecycle checks deterministic properties that can be evaluated from the repository and resolved
Maven artifacts. The opt-in `supply-chain` profile creates a production software bill of materials
(SBOM).

These gates do not replace behavioral tests or a dependency-vulnerability assessment. The SBOM is
an inventory of resolved production components; it does not determine whether those components
have published vulnerabilities or whether vulnerable code is reachable in Aquila Graph.

JaCoCo measures unit-test coverage when the `coverage` target is invoked. It generates
HTML, XML, and CSV reports under `target/site/jacoco` without enforcing a coverage threshold.
Non-executable `package-info.java` descriptors and their compiled artifacts are excluded from measurement
through the `**/package-info*` pattern.

## Required Toolchain

Required build and validation platforms are Windows and Linux, as defined by the
[repository platform rule](../AGENTS.md#supported-validation-platforms). macOS is
outside the project objectives and acceptance scope.

- JDK 25, selected through Maven Toolchains when the Maven runtime does not already match.
- Maven 3.9.16, provided by the checked-in Maven Wrapper.
- The Maven Wrapper distribution is pinned by SHA-256 in
  `.mvn/wrapper/maven-wrapper.properties`.

Maven Enforcer rejects a different Java or Maven release, unpinned build plugins, dependencies
without explicit scopes, snapshot dependencies, dependency convergence failures, and reactor
version drift.

## Development Verification Scope

The user decision of October 28, 2026 separates development verification from
real-environment integration campaigns. The active development scope is unit tests
throughout the project, including application, frontend, source/CFG, CLI, repository
and build-tool behavior. In-process collaboration tests may exercise multiple real
components while substituting external boundaries with stubs, mocks or fakes.
Classify a test by the dependencies it exercises, not its architectural layer or
whether its name contains "integration".

External HTTP/network calls, database drivers and other service boundaries must
use controlled test doubles in this scope. Prefer real domain objects and narrow
fakes; assert observable results, state changes and failure handling rather than
only calls to a mock. Controlled temporary files and the in-process compiler API
may support isolated tests without starting an external environment. A mock's
configured result alone does not verify the repository or caller that consumes it.

Controlled in-process protocol fakes may use test-owned loopback transport, as in
[BoltCommitAcknowledgementProxyTest](../src/test/java/org/aquilagraph/repository/internal/neo4j/BoltCommitAcknowledgementProxyTest.java).
They create and close their own endpoints without depending on an existing
service, external network or real database.

Real Neo4j/Docker, external service endpoints, packaged child processes, native process
lifecycle controls and nested Maven/compiler process campaigns remain preserved
but deferred until a new explicit user decision. Finishing implementation, reaching
acceptance, editing or removing code, starting a review or selecting another task
does not authorize them. This decision applies to every epic and task through the
[shared lifecycle](epics/README.md#current-verification-scope-for-every-epic-and-task).
Each plan names its isolated deliverables and manual-only owners for deferred
real-environment requirements. The accepted [build development validation](tasks/incremental-verification-build/complete-local-validation.md)
and deferred [build integrated validation](tasks/incremental-verification-build/build-verification-integration.md)
are one application of that separation, not its scope limit.

Scope/command revisions execute no tests or acceptance campaigns. The earlier
authorization for build development validation's complete isolated suite and fresh
coverage remains recorded in its accepted checkpoint; it does not authorize tests
during the other epic/task revisions or reactivate any real-environment campaign.
Future project-wide isolated testing and coverage occur at the appropriate
authorized verification step, not during this planning revision.
Isolated evidence cannot establish real database query/transaction behavior,
external service compatibility, process containment or complete integrated success.
Keep known failures and unexecuted obligations visible in their owning task.

The command implementation uses [`BuildOperation`](../config/build/org/aquilagraph/build/BuildOperation.java)
and [`BuildRequest`](../config/build/org/aquilagraph/build/BuildRequest.java), with
Surefire/Failsafe selection in the [POM](../pom.xml). The separation includes moving
the existing Git observation, restricted child-JVM observation, protected-Windows
process observations and external compilation/invalidation cases into process-tagged `*ProcessIT` classes, retaining
their assertions and their process-free companion tests. Source inspection is not
a whole-suite runtime certification of isolation.

## Commands

The Makefile and PowerShell script use the checked-in Wrapper, which enforces the Maven version and
distribution checksum:

| Purpose | Linux | Windows PowerShell |
| --- | --- | --- |
| Compile | `make compile` | `.\Invoke-Build.ps1 compile` |
| Run development `*Test` tests when verification is requested | `make test` | `.\Invoke-Build.ps1 test` |
| Produce unit-test coverage reports | `make coverage` | `.\Invoke-Build.ps1 coverage` |
| Format production Java | `make format` | `.\Invoke-Build.ps1 format` |
| Check production Java formatting | `make format-check` | `.\Invoke-Build.ps1 format-check` |
| Check LF in Git text and reachable commits | `make line-endings-check` | `.\Invoke-Build.ps1 line-endings-check` |
| Run static quality gates without tests | `make quality` | `.\Invoke-Build.ps1 quality` |
| Run supply-chain gates without tests | `make supply-chain-check` | `.\Invoke-Build.ps1 supply-chain-check` |

The following commands remain available for a later explicit decision. Their
presence is not authorization to execute them during development or task closure.

| Deferred scope | Linux | Windows PowerShell |
| --- | --- | --- |
| Packaged/native process integrations | `make integration-test` | `.\Invoke-Build.ps1 integration-test` |
| All real integrations, including Neo4j | `make integration-test-neo4j` | `.\Invoke-Build.ps1 integration-test-neo4j` |
| Focused process integrations | `make check-process` | `.\Invoke-Build.ps1 check-process` |
| Focused persistence integrations | `make check-persistence` | `.\Invoke-Build.ps1 check-persistence` |
| Focused resource lifecycle integrations | `make check-lifecycle` | `.\Invoke-Build.ps1 check-lifecycle` |
| Fresh complete unit/integration/static composition | `make verify-all` | `.\Invoke-Build.ps1 verify-all` |

Surefire runs the `*Test` group during Maven's `test` phase. `test` and `coverage`
stop at that phase and do not dispatch Failsafe. Focused unit selection accepts
comma-separated explicit simple or qualified class names ending in `Test`, with
optional `#method` selectors. For example, use
`make test MAVEN_FLAGS='-B --no-transfer-progress -Dtest=BuildRequestTest'` or
`.\Invoke-Build.ps1 test -MavenFlags @('-B', '--no-transfer-progress', '-Dtest=BuildRequestTest')`.
Class wildcards, regex selectors and `*IT` names are refused because Surefire's
[`test` parameter](https://maven.apache.org/surefire/maven-surefire-plugin/test-mojo.html#test)
overrides its ordinary includes/excludes. `coverage` retains the complete active
unit selection rather than accepting a narrowed selector.

Failsafe's manual operations run `*ProcessIT`; the `neo4j-integration` profile
broadens selection to all `*IT` and requires Docker. Focused integration commands
remain integration commands even when only one class is selected. The integration
targets compile test sources and package the application but skip Surefire.
Development test compilation still includes shared test sources; execution
separation does not claim separate compilation roots. `quality` and
`supply-chain-check` skip test compilation and execution. `prepare-consumers`
packages and prepares the independent query module without executing tests.

The [database integration runbook](../deployment/neo4j/testing.md) defines portable memory limits, disposable volumes, explicit preservation, logs, and lifecycle verification. Maven starts with a 128 MiB heap and a 1 GiB maximum through [`.mvn/jvm.config`](../.mvn/jvm.config). Forked compilers have a 1 GiB maximum. Surefire and Failsafe use a 128 MiB initial heap and a 2 GiB maximum; packaged CLI test consumers use a 512 MiB maximum, and the independent retained-AST query consumer uses 1 GiB. These budgets are independent of the container's total memory limit.

Use fresh compiled outputs when switching operating systems in one checkout.
For the resource settings of the opt-in profile with 16 unit-test forks and three
integration forks, see the
[high-capacity local execution procedure](../deployment/neo4j/testing.md#high-capacity-local-execution).
Selecting a resource profile does not authorize a deferred integration campaign.

Keep `target/classes`, `target/test-classes`, `target/partitions`, and
`target/maven-status` local to one toolchain. The Maven artifact cache under
`target/maven-repository` can be reused.

## Validation Cadence

When test execution is authorized, run the smallest existing isolated check that reaches
the changed behavior and distinguishes the required result. Reproduce a failure,
correct its shared cause and affected callers, then rerun that focused check and
the directly affected regressions. Batch related confirmed corrections before
starting expensive validation. Do not run complete coverage, all integration
groups or unrelated static gates after each small edit.

At an authorized development acceptance step, when implementation and wiring are
coherent and focused checks pass, run
the complete active isolated scope with fresh unit coverage. This covers the
project's layers without requiring a real environment. Preserve valid
evidence for unchanged behavior and rerun checks whose observations or validity
the correction affects. A later correction returns work to the focused loop;
repeat affected isolated checks when the corrected candidate is ready again.
Real integration campaigns require a new explicit user decision; readiness alone
does not start them or make them a prerequisite for development delivery.
Documentation or checkpoint edits alone do not require executable test campaigns.

Stop the remaining owned campaign when an observed failure or error already
requires a correction; do not finish unrelated tests merely to collect more
failures. Preserve the original diagnostic, partial reports and actual nonzero or
canceled result. Close owned processes, forks, databases and volumes before
editing or starting another run. With concurrent forks, other cases can already
be running; cancel owned work and record which cases completed, failed or were
not executed. An interrupted campaign is incomplete and cannot satisfy final
acceptance. Never relabel unexecuted cases as passing or suppress the first failure.

Inspect the failure at its actual boundary, fix it and use focused verification
before another complete campaign. Do not immediately restart the same broad run
without a correction, restored prerequisite or other evidence-changing condition.
The current task checkpoint records the next unmet obligation and reusable evidence;
do not create a separate tracking workflow for this cadence.

## Coverage Freshness

When development verification is performed, run the `coverage` target before
accepting changed production code or tests on the final candidate, following the
[validation cadence](#validation-cadence) and the user's current execution scope.
Intermediate corrections use focused checks; they do not each trigger complete
coverage. One Maven
invocation prepares the JaCoCo agent, runs the complete unit suite, and generates
the report. A failed phase stops the invocation before the later goals. Do not
skip unit tests when validating changes.

The `coverage` target deletes `target/jacoco.exec` before starting
the main Maven invocation. Agent append mode combines only the current run's
forks; it does not retain earlier execution data. The pinned
[JaCoCo file output implementation](https://github.com/jacoco/jacoco/blob/v0.8.15/org.jacoco.agent.rt/src/org/jacoco/agent/rt/internal/output/FileOutput.java)
locks the shared output while each agent writes. Inspect the report from
the successful current run; an older report left by a failed run is not current
validation. These entry points are defined in [`Invoke-Build.ps1`](../Invoke-Build.ps1)
and [`Makefile`](../Makefile), with the agent configured in [`pom.xml`](../pom.xml).

## Validation After Removals

After removals, validate the complete active isolated scope at the later
development acceptance step. Use focused checks for intermediate corrections.
`make coverage` on Linux or `.\Invoke-Build.ps1 coverage` on Windows runs
that complete scope with fresh JaCoCo reports, so a separate unit run is redundant.
Preserve static quality gates and their separate results. A removal does not
expand the user's current execution authorization.

Real-environment integration obligations remain deferred under the
[development verification scope](#development-verification-scope). If the user
later requests that work, `integration-test-neo4j` retains the complete `*IT`
selection, including process, database, packaged-consumer and lifecycle cases.
Until then, report them as deferred, not passed; their nonexecution does not
block delivery within the revised development scope. A failure in an executed
check still requires an honest failed result and a correction within its scope.

## Local Build Process Identity

New Linux process records identify a birth by PID, boot identity and native
start ticks. Wall-clock start time remains diagnostic metadata; it is not the
comparison key when native birth evidence is present. Invocation ownership,
source-launch acknowledgments and resource journals preserve these coordinates
through the shared [ProcessIdentity](../config/build/org/aquilagraph/build/ProcessIdentity.java)
reader/writer. Missing or malformed native fields fail closed. Epoch-only records
remain readable with their original exact timestamp comparison; native birth
evidence is never inferred for an older record. If a legacy Linux timestamp
differs while its PID is live, the ambiguous identity refuses verification;
it cannot certify process exit. Windows retains its existing PID/creation-time
identity.

The Linux [start-time field](https://man7.org/linux/man-pages/man5/proc_pid_stat.5.html)
is measured from boot in clock ticks. The kernel's
[boot identity](https://www.kernel.org/doc/html/latest/admin-guide/sysctl/kernel.html#random)
remains fixed after its first retrieval. These coordinates avoid the per-JVM boot
epoch conversion used by [OpenJDK's Linux ProcessHandle implementation](https://github.com/openjdk/jdk25u/blob/master/src/java.base/linux/native/libjava/ProcessHandleImpl_linux.c).
The [native reader](../config/build/org/aquilagraph/build/LinuxProcessStart.java)
validates the recorded PID and handles parentheses in the process command.

[ProcessIdentityTest](../src/test/java/org/aquilagraph/build/ProcessIdentityTest.java)
and [LinuxProcessStartTest](../src/test/java/org/aquilagraph/build/LinuxProcessStartTest.java)
cover exact birth comparison, persistence, refusal and controlled native-file
inputs. [LinuxProcessIdentityProcessIT](../src/test/java/org/aquilagraph/build/LinuxProcessIdentityProcessIT.java)
observes the live Linux owner with controlled shifted JVM metadata; it remains a
separately authorized process integration, not isolated evidence of a real host.

## Identity API Verification

Maven temporary files remain under the working directory's `target` through
[`.mvn/jvm.config`](../.mvn/jvm.config).

`StableId` and `CapturedValueEncoding` are static API namespaces represented by
enums with no constants. The [Java 25 enum contract](https://docs.oracle.com/javase/specs/jls/se25/html/jls-8.html#jls-8.9)
admits no instances beyond declared constants, so these namespaces have no domain
instances or handwritten constructor guards. Their public hashing and encoding
operations retain their existing signatures and canonical formats.

Qualified AST and graph constructors reject null and blank supplied keys before
deriving the key from every qualification component. Invalid key text remains an
argument error even when SHA-256 is unavailable. Each independent qualification
component retains its nonblank requirement. Environment options use `List.copyOf`
to establish both immutability and rejection of null elements at the copying
boundary.

[`StableIdTest`](../src/test/java/org/aquilagraph/identity/api/StableIdTest.java)
checks a fixed digest for a multibyte UTF-8 component whose byte length is
`0x01010101`. Every byte of the four-byte length prefix is significant. The fixed
reference uses big-endian unsigned 32-bit lengths followed by the UTF-8 bytes of
the namespace and ordered component, hashed with SHA-256.

## Product Compilation Partitions

The single Maven project compiles seven named product modules through the
`compile-product-modules` execution in [pom.xml](../pom.xml). Production inputs
use `src/main/java/<module-name>/`; canonical output is
`target/classes/<module-name>/`, with module JARs under `target/modules/`.
The compiler uses the module source path and the explicit seven-module list,
with the configured Java release, Error Prone, NullAway, JSpecify,
`-Xlint:all`, and `-Werror`. Module ownership does not exclude sources from
formatting, Checkstyle, PMD, CPD, SpotBugs, or Javadoc.

| Module | Dependency and export boundary |
| --- | --- |
| [Identity](../src/main/java/org.aquilagraph.identity/module-info.java) | Java base domain operations; static JSpecify metadata; exports immutable identity values. |
| [Source](../src/main/java/org.aquilagraph.source/module-info.java) | Identity; public reader/producer contracts; admission qualified to frontend and retention qualified to repository. |
| [CFG](../src/main/java/org.aquilagraph.cfg/module-info.java) | Identity and Source; exports reader/derivation contracts and composition; graph construction remains encapsulated. |
| [Frontend](../src/main/java/org.aquilagraph.frontend/module-info.java) | Identity, Source, and public compiler modules; exports producer composition; compiler/translation implementations remain encapsulated. |
| [Application](../src/main/java/org.aquilagraph.application/module-info.java) | Identity, Source, and CFG contracts; exports API/SPI; repository observation support is qualified to the repository module. |
| [Repository](../src/main/java/org.aquilagraph.repository/module-info.java) | Identity, Source, CFG, Application, and Neo4j driver; exports adapter composition/settings/failure detail; storage implementations remain encapsulated. |
| [CLI](../src/main/java/org.aquilagraph.cli/module-info.java) | Composes the other product modules and presents application outcomes; exports no product API. |

[ModuleDependencyBoundaryTest](../src/test/java/org/aquilagraph/architecture/ModuleDependencyBoundaryTest.java)
inspects canonical classfile references and public signatures, including generic
and annotation metadata, and compiles permitted/forbidden application consumers.
[IdentityBoundaryTest](../src/test/java/org/aquilagraph/architecture/IdentityBoundaryTest.java)
checks identity classfile closure. Delivered-module controls in
[ModuleBoundaryProcessIT](../src/test/java/org/aquilagraph/architecture/ModuleBoundaryProcessIT.java)
and the Java-base-only observation in
[IdentityRuntimeProcessIT](../src/test/java/org/aquilagraph/architecture/IdentityRuntimeProcessIT.java)
retain the separate [integration scope](#development-verification-scope).
These are configured checks, not executions performed by this document;
domain behavior, persistence, and implementation acceptance require their own evidence.

The generated Eclipse projects under `target/editor/` attach canonical sources
through repository-relative linked resources. Named modules use Eclipse project
references for module dependencies. The test and build projects use linked
resources for class folders and the owned Maven cache, so Windows and Linux
preparation can share the same editor model. The
[`validate-editor-model` execution](../pom.xml) runs after product compilation
in test and quality lifecycles. [Editor model validation](../config/build/org/aquilagraph/build/EditorModelValidation.java)
rejects missing source owners, unresolved sources or dependencies, host-specific
paths, duplicate source ownership, and shared editor compilation output.
This gate verifies generated configuration. Actual editor import and diagnostics
require observation in the IDE. Eclipse documents the
[path variables](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.user/concepts/cpathvars.htm)
used by portable linked resources.

[`SourceFile`](../src/main/java/org.aquilagraph.identity/org/aquilagraph/identity/api/SourceFile.java)
admits normalized rootless relative paths that remain below the source root.
Its validation independently rejects absolute paths, root-relative paths, and
leading parent-directory components. The
[`source-file tests`](../src/test/java/org/aquilagraph/source/api/SourceFileTest.java)
cover path rejection, normalization, and fixed content-derived identity values.

The identity digest APIs require a registered SHA-256 provider. The Java
[`MessageDigest.getInstance` contract](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/MessageDigest.html#getInstance(java.lang.String))
reports a missing algorithm when no registered provider supplies it, and
[`Security.removeProvider`](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/security/Security.html#removeProvider(java.lang.String))
can change that runtime prerequisite.
[`Sha256AvailabilityTest`](../src/test/java/org/aquilagraph/identity/api/Sha256AvailabilityTest.java)
exercises this failure through the public identity APIs in the instrumented test
JVM. JUnit's [`@Isolated` contract](https://docs.junit.org/6.1.2/api/org.junit.jupiter.api/org/junit/jupiter/api/parallel/Isolated.html)
prevents other test classes from executing concurrently while the provider
registry is temporarily empty. A `finally` block restores all original providers,
and the test verifies their exact registration order. The failure assertions check
the platform prerequisite diagnostic and the retained `NoSuchAlgorithmException`
cause for each public digest entry point.

## Text Line Endings

The versioned [.gitattributes](../.gitattributes) normalizes tracked text to LF
in Git. Known text extensions are explicitly classified so that files previously
tracked with CRLF are normalized too. The [.editorconfig](../.editorconfig)
leaves `end_of_line` unspecified; each developer may choose the working-tree
style in local Git and editor settings. For example,
`git config --local core.autocrlf true` selects CRLF on checkout, while
`git config --local core.autocrlf input` keeps LF on checkout. The Unix Maven wrapper is fixed to LF
because it must run as a shell script. Git converts text when files enter the
index and when they are checked out, not during push.

Checkstyle still requires a final newline in Java source, accepting the local
checkout's LF or CRLF. The Git index and history check owns the stricter LF
requirement for versioned text.
Spotless preserves each Java file's local line endings while checking its
formatting rules; it does not require every developer's checkout to use the
same line-ending style.

The `line-endings-check` target rejects CRLF or mixed line endings in the Git
index and carriage returns in text blobs reachable from the current commit.
`quality` runs this check before Maven. A remote build must run the same target
and protect the publishing branch to enforce the policy centrally; this
repository does not configure remote CI.

## Responsibility Boundaries

| Gate | Responsibility |
| --- | --- |
| `javac -Xlint:all -Werror` | Treat all Java compiler warnings as build failures. |
| Error Prone | Detect compiler-integrated Java correctness defects. |
| NullAway with JSpecify | Enforce explicit package nullness and reject unsafe null flows in production code. |
| Spotless with Eclipse JDT | Own Java formatting and the 140-character line limit. |
| Checkstyle | Enforce production import hygiene, naming, structural rules, final newlines, and package dependency boundaries, plus unnecessary fully qualified type references in tests. |
| PMD | Detect source-level correctness, maintainability, performance, and security patterns. |
| CPD | Reject substantial duplicated production Java blocks. |
| SpotBugs | Inspect compiled production bytecode at maximum effort and low confidence threshold. |
| Maven Dependency Analyzer | Reject used-but-undeclared and declared-but-unused production dependencies. |
| Maven Javadoc | Reject malformed or incomplete public API documentation through full doclint. |
| Editor model validation | Require canonical Java source ownership and resolvable portable editor classpaths after product compilation. |
| JaCoCo | Measure unit-test coverage and produce HTML, XML, and CSV reports on demand. |
| License Maven Plugin | Reject missing or non-allowlisted production dependency licenses. |
| CycloneDX | Produce JSON and XML production SBOMs in `target/`. |
| Renovate configuration | Propose pinned dependency and Wrapper updates without automatic merging. |

Spotless is the only formatter. Checkstyle deliberately contains no whitespace or layout policy.
NullAway with JSpecify is the only nullness system. Error Prone, PMD, and SpotBugs operate on
different analysis representations; overlap is retained only when independently useful.

## Architecture Policy

`config/checkstyle/import-control.xml` enforces the current import permissions.
The [Java analysis design](design/java-analysis.md#architecture-and-ownership) and
[repository design](design/analysis-repository.md#responsibilities-and-dependencies)
define the responsibility direction. In particular:

- Source Identity permits Java base domain dependencies, identity values and the designated JSpecify nullness annotations.
- Source Model cannot import application, CLI, CFG, frontend or repository code; its reader excludes admission/construction APIs.
- Frontend imports the Source Model production/admission contracts and compiler APIs, with no application or CFG dependency.
- CFG imports only Source Model reader values and identity, with no producer, source-admission or mutable source-builder access.
- Application imports public source/CFG reader values and its own ports; concrete frontend, CFG construction and repository implementations remain inaccessible.
- CLI uses application and public graph reader contracts; the root selects concrete adapters.
- The Neo4j adapter realizes application storage ports and uses qualified `source.retention.AstCodec` for intrinsic AST readmission without frontend/compiler or CFG construction. It cannot access frontend-only `source.admission`.

The seven [named product modules](#product-compilation-partitions), qualified exports, import controls, and classfile/consumer checks define the selected [dependency closure](design/java-analysis.md#target-dependency-closure-and-enforcement). Identity domain operations close over Java base; `NullMarked` and `Nullable` are designated annotation metadata. The configured nullness rules remain in force. Module/classfile inspection and isolated consumer compilation establish their observed access properties only; they do not establish full semantic acceptance.

Behavioral controls cover [required/optional source facts](design/java-analysis.md#initial-source-profile), [public graph payload/query contracts](design/java-analysis.md#public-cfg-values-and-queries) and [repository failure crossings](contracts/analysis.md#target-repository-failure-crossing). Compiler fixtures observe actual producer facts; copied-fact and driver faults observe their isolated supplied conditions; Community integration cases observe actual save/query/schema effects. The [owning verification record](design/java-analysis.md#model-acceptance-and-verification) identifies pending independent acceptance and the limits on reusing prior evidence; no diagram or documentation check supplies executable evidence.

Only `frontend` may import the supported compiler-tree APIs in `com.sun.source` and `javax.tools`.
Imports from `com.sun.tools.javac`, `jdk.internal`, and `sun` are rejected throughout production
code.

## Failure and Exception Policy

Fix the cause of a failed gate. Do not add suppressions, baselines, ignore lists, exclusions, or
reduced severities merely to make the build pass. A technically unavoidable exception requires
an explicit, narrowly scoped review that records the diagnostic, affected code, rationale,
tradeoffs, and removal condition.

The default quality build is designed to be reproducible after Maven artifacts are cached. The
`supply-chain` profile is intentionally separate because SBOM generation is an explicit component
inventory operation. Renovate execution requires an external repository integration; this
repository only owns its policy configuration.

## References

- [Git attributes and line-ending conversion](https://git-scm.com/docs/gitattributes).
- [Git configuration for checkout line endings](https://git-scm.com/docs/git-config).
- [Checkstyle final newline options](https://checkstyle.org/checks/misc/newlineatendoffile.html).
- [Spotless Maven line-ending modes](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md#line-endings-and-encodings-invisible-stuff).
- [Maven Surefire test inclusion](https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html)
- [Maven Failsafe test inclusion](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/inclusion-exclusion.html)
- [Maven Failsafe test skipping](https://maven.apache.org/surefire/maven-failsafe-plugin/examples/skipping-tests.html)
- [Apache Maven Enforcer built-in rules](https://maven.apache.org/enforcer/enforcer-rules/index.html)
- [Apache Maven Toolchains Plugin](https://maven.apache.org/plugins/maven-toolchains-plugin/)
- [Maven Dependency Plugin dependency analysis](https://maven.apache.org/plugins/maven-dependency-plugin/examples/failing-the-build-on-dependency-analysis-warnings.html)
- [NullAway JSpecify support](https://github.com/uber/NullAway/wiki/JSpecify-Support)
- [Checkstyle ImportControl](https://checkstyle.org/checks/imports/importcontrol.html)
- [Checkstyle UnnecessaryFullyQualifiedType](https://checkstyle.org/checks/imports/unnecessaryfullyqualifiedtype.html)
- [PMD Maven Plugin](https://pmd.github.io/pmd/pmd_userdocs_tools_maven.html)
- [SpotBugs Maven Plugin](https://spotbugs.github.io/spotbugs-maven-plugin/)
- [CycloneDX Maven Plugin](https://cyclonedx.github.io/cyclonedx-maven-plugin/)
- [License Maven Plugin third-party report](https://www.mojohaus.org/license-maven-plugin/examples/example-thirdparty.html)
- [Renovate configuration options](https://docs.renovatebot.com/configuration-options/)
