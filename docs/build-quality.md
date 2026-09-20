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

No code-coverage tool is configured by this quality profile. If coverage is introduced
independently, non-executable package descriptors such as `**/package-info.class` must be excluded
from coverage measurement.

## Required Toolchain

- JDK 25, selected through Maven Toolchains when the Maven runtime does not already match.
- Maven 3.9.16, provided by the checked-in Maven Wrapper.
- The Maven Wrapper distribution is pinned by SHA-256 in
  `.mvn/wrapper/maven-wrapper.properties`.

Maven Enforcer rejects a different Java or Maven release, unpinned build plugins, dependencies
without explicit scopes, snapshot dependencies, dependency convergence failures, and reactor
version drift.

## Commands

Use the Wrapper so the Maven version and distribution checksum remain enforced:

```shell
./mvnw -B --no-transfer-progress -Dmaven.test.skip=true compile
./mvnw -B --no-transfer-progress spotless:apply
./mvnw -B --no-transfer-progress spotless:check
./mvnw -B --no-transfer-progress -Dmaven.test.skip=true verify
./mvnw -B --no-transfer-progress -Dmaven.test.skip=true -Psupply-chain verify
```

Equivalent Make targets are `compile`, `format`, `format-check`, `quality`, and
`supply-chain-check`. On Windows PowerShell, use `./mvnw.cmd` when invoking Maven directly.

## Responsibility Boundaries

| Gate | Responsibility |
| --- | --- |
| `javac -Xlint:all -Werror` | Treat all Java compiler warnings as build failures. |
| Error Prone | Detect compiler-integrated Java correctness defects. |
| NullAway with JSpecify | Enforce explicit package nullness and reject unsafe null flows in production code. |
| Spotless with Eclipse JDT | Own Java formatting and the 140-character line limit. |
| Checkstyle | Enforce production import hygiene, naming, structural rules, and package dependency boundaries, plus unnecessary fully qualified type references in tests. |
| PMD | Detect source-level correctness, maintainability, performance, and security patterns. |
| CPD | Reject substantial duplicated production Java blocks. |
| SpotBugs | Inspect compiled production bytecode at maximum effort and low confidence threshold. |
| Maven Dependency Analyzer | Reject used-but-undeclared and declared-but-unused production dependencies. |
| Maven Javadoc | Reject malformed or incomplete public API documentation through full doclint. |
| License Maven Plugin | Reject missing or non-allowlisted production dependency licenses. |
| CycloneDX | Produce JSON and XML production SBOMs in `target/`. |
| Renovate configuration | Propose pinned dependency and Wrapper updates without automatic merging. |

Spotless is the only formatter. Checkstyle deliberately contains no whitespace or layout policy.
NullAway with JSpecify is the only nullness system. Error Prone, PMD, and SpotBugs operate on
different analysis representations; overlap is retained only when independently useful.

## Architecture Policy

`config/checkstyle/import-control.xml` enforces the source dependency direction documented by the
component model:

- `model` has no dependency on another Aquila Graph package;
- `core` and `frontend` depend on `model`;
- `application` may depend on `core`, `frontend`, and `model`;
- `cli` may depend on `application` and `model`;
- `repository` exposes a technology-neutral contract and may depend on `model`;
- `repository.neo4j` may depend on `repository`, `model`, and the Neo4j driver; and
- the root package is the composition boundary.

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
