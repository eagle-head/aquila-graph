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
Neo4j instance. The integration-only targets still compile test sources and package the application, but skip
Surefire execution. The `quality` and `supply-chain-check` targets skip test compilation and execution, so
run the relevant test targets separately when validating behavior.

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

- `model` and `syntax` cannot import application, CLI, core, frontend, or repository packages;
- `core` cannot import application, CLI, frontend, or repository packages;
- `frontend` may import `application.port` but not other application packages, CLI, core, or repository packages;
- `application` cannot import CLI, frontend, or repository packages, so the accepted application-owned repository ports do not require an application-to-storage dependency;
- `cli` cannot import core, frontend, or repository packages;
- the general `repository` package cannot import application, CLI, core, or frontend, while `repository.neo4j` may import `application.port` and the Neo4j driver; and
- the root package is the composition boundary that selects the concrete adapter.

These are the configured package restrictions, not proof that every allowed
import expresses an accepted model dependency. The repository port and factory
are defined in `application.port`; the storage adapter realizes that port.

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
