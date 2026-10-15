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

JaCoCo measures unit-test coverage only when the dedicated `coverage` target is invoked. It generates
HTML, XML, and CSV reports under `target/site/jacoco` without enforcing a coverage threshold.
Non-executable `package-info.java` descriptors and their compiled artifacts are excluded from measurement
through the `**/package-info*` pattern.

## Required Toolchain

- JDK 25, selected through Maven Toolchains when the Maven runtime does not already match.
- Maven 3.9.16, provided by the checked-in Maven Wrapper.
- The Maven Wrapper distribution is pinned by SHA-256 in
  `.mvn/wrapper/maven-wrapper.properties`.

Maven Enforcer rejects a different Java or Maven release, unpinned build plugins, dependencies
without explicit scopes, snapshot dependencies, dependency convergence failures, and reactor
version drift.

## Commands

The Makefile and PowerShell script use the checked-in Wrapper, which enforces the Maven version and
distribution checksum:

| Purpose | Linux and macOS | Windows PowerShell |
| --- | --- | --- |
| Compile | `make compile` | `.\Invoke-Build.ps1 compile` |
| Run `*Test` tests | `make test` | `.\Invoke-Build.ps1 test` |
| Run packaged-process `*ProcessIT` tests | `make integration-test` | `.\Invoke-Build.ps1 integration-test` |
| Run all `*IT` tests, including the database test | `make integration-test-neo4j` | `.\Invoke-Build.ps1 integration-test-neo4j` |
| Produce unit-test coverage reports | `make coverage` | `.\Invoke-Build.ps1 coverage` |
| Format production Java | `make format` | `.\Invoke-Build.ps1 format` |
| Check production Java formatting | `make format-check` | `.\Invoke-Build.ps1 format-check` |
| Check LF in Git text and reachable commits | `make line-endings-check` | `.\Invoke-Build.ps1 line-endings-check` |
| Run static quality gates without tests | `make quality` | `.\Invoke-Build.ps1 quality` |
| Run supply-chain gates without tests | `make supply-chain-check` | `.\Invoke-Build.ps1 supply-chain-check` |

Surefire runs the `*Test` group during Maven's `test` phase. Failsafe runs the `*ProcessIT` group during
`verify`; the `neo4j-integration` profile broadens that group to all `*IT` classes and requires a configured
Docker engine. The database suites acquire one isolated managed Neo4j per test engine and close it after the suites. The integration-only targets still compile test sources and package the application, but skip
Surefire execution. The `quality` and `supply-chain-check` targets skip test compilation and execution, so
run the relevant test targets separately when validating behavior.

The [database integration runbook](../deployment/neo4j/testing.md) defines portable memory limits, disposable volumes, explicit preservation, logs, and lifecycle verification. Maven starts with a 128 MiB heap and a 1 GiB maximum through [`.mvn/jvm.config`](../.mvn/jvm.config). Forked compilers have a 1 GiB maximum. Surefire and Failsafe use a 128 MiB initial heap and a 2 GiB maximum; packaged CLI test consumers use a 512 MiB maximum, and the independent retained-AST query consumer uses 1 GiB. These budgets are independent of the container's total memory limit.

Use fresh compiled outputs when switching operating systems in one checkout.
Keep `target/classes`, `target/test-classes`, `target/partitions`, and
`target/maven-status` local to one toolchain. The Maven artifact cache under
`target/maven-repository` can be reused.

## Product Compilation Partitions

The single Maven project compiles eight product partitions before assembling the ordinary
application classes. The [compiler executions](../pom.xml) retain the same Error Prone,
NullAway, JSpecify, warning, and release settings for every production source. Selecting a
source for one compilation partition does not exclude it from the formatting, Checkstyle,
PMD, CPD, SpotBugs, or Javadoc gates.

| Partition | Compilation inputs |
| --- | --- |
| Source Identity | Java base and the two permitted JSpecify annotation definitions |
| Source reader | Source Identity, Java base, and permitted nullness metadata |
| Source admission | Source reader and Source Identity, with private construction machinery |
| CFG reader | Immutable CFG API, Source reader, and Source Identity; no graph admission or construction machinery |
| CFG | Source reader and Source Identity; no admission, frontend, application, or repository classes |
| Java Frontend | Source reader, copied-value admission, Source Identity, and the supported public compiler modules |
| Application | Source reader, CFG reader, and Source Identity |
| Composition and storage adapters | Assembled product partitions and declared adapter dependencies |

Partition outputs reside under `target/partitions/`. The immutable aggregate has no public
construction operation. Four construction helpers share its Java package so they can use
its package-private constructor, but compile only into the separate source-admission
output: `AstAggregateAdmission`, `JavaAstAggregateBuilder`, `MethodDecisionRegistration`,
and `MethodDecisionValidation`. They are absent from source-reader output. The designated
[value-admission facade](../src/main/java/org/aquilagraph/source/admission/AstValueAdmission.java)
submits complete immutable copied values and returns no mutable builder. Import permissions
restrict the privileged bridge to this facade's package.

The CFG reader likewise excludes `GraphValueAdmission`, `CanonicalGraphEncoding`,
`LoweringPlan`, and `CfgAdmissionResult`. Qualified graph construction uses the
package-private aggregate constructor through its separate privileged admission
bridge. `CfgDerivers` is a composition factory outside that reader API; application
coordination receives the `CfgDeriver` port and cannot choose or access an internal
builder. The composition root selects the concrete implementation.

Explicit compiler classpaths and source lookup prevent the reader, CFG, and frontend
compilations from silently using other project sources. Source lookup points to the existing
compiled identity directory, which contains classfiles rather than Java source. This permits
normal builds from a fresh output directory without weakening `-Xlint:all -Werror`.

[SourcePartitionBoundaryTest](../src/test/java/org/aquilagraph/architecture/SourcePartitionBoundaryTest.java)
inspects every reader, CFG, and frontend classfile reference, including generic and annotation
metadata, and compiles both permitted and forbidden consumer controls against the actual
restricted outputs. [IdentityBoundaryTest](../src/test/java/org/aquilagraph/architecture/IdentityBoundaryTest.java)
also runs identity operations in a Java-base-only process without JSpecify definitions.
These gates establish their dependency and access properties; independent domain operations,
completion, canonical identities, retention, and query behavior require their own behavioral
verification.

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
- The Neo4j adapter realizes application storage ports and may use Source Model copied-value admission to decode AST results without frontend/compiler or CFG construction.

The eight [product partitions](#product-compilation-partitions), strict isolated compilation and classfile controls enforce the selected [dependency closure](design/java-analysis.md#target-dependency-closure-and-enforcement). Identity runtime domain operations close over Java base; `NullMarked` and `Nullable` are allowed only as designated annotation metadata. No configured nullness rule is disabled. [IdentityBoundaryTest](../src/test/java/org/aquilagraph/architecture/IdentityBoundaryTest.java) and [SourcePartitionBoundaryTest](../src/test/java/org/aquilagraph/architecture/SourcePartitionBoundaryTest.java) inspect signatures, generic/annotation references and compiled partitions, with isolated positive/negative compiler controls. These observations are narrower than full semantic acceptance.

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
