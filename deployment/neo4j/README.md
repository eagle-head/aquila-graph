# Local Neo4j

Aquila Graph runs as a local Java 25 process. Neo4j Community Edition runs in
Docker and retains its database in the `aquila-graph-neo4j-data` named volume.
The pinned `neo4j:2026.08.1` image exposes Browser only on local port `7474` and
Bolt only on local port `7687`.

## Supported verification scope

Neo4j Community Edition is the required database verification baseline for this
open-source project. The maintainer confirmed that paid-edition verification is
outside the project scope on October 1, 2026. A selective role-based account that
can create constraints while being denied constraint inspection is not applicable
to this baseline: Community has no roles and its users have implied administrator
privileges. That authorization-specific observation remains unexecuted; it is not
a passing test or a claim of Enterprise compatibility. [Neo4j user administration](https://neo4j.com/docs/operations-manual/current/authentication-authorization/manage-users/)
documents the edition distinction.

Constraint creation, `SHOW CONSTRAINTS`, and exact required-definition validation
remain part of the publication contract. Both identity constraints must be
verified before any analysis projection is written. Metadata query failures,
missing or incompatible definitions, and schema-session close failures still
prevent publication. `CREATE CONSTRAINT ... IF NOT EXISTS` alone cannot establish
the required definitions when an existing constraint blocks creation.
[Constraint creation](https://neo4j.com/docs/cypher-manual/current/schema/constraints/create-constraints/)
and [constraint inspection](https://neo4j.com/docs/cypher-manual/current/schema/constraints/list-constraints/)
describe those commands; the [adapter](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jAnalysisRunRepository.java:100)
and [definition validator](../../src/main/java/org/aquilagraph/repository/neo4j/Neo4jSchema.java:36)
own the executable checks.

All configured unit, static-analysis, packaged-process, and Community integration
gates remain required. The [build quality guide](../../docs/build-quality.md)
defines those gates; the [publication contract](../../docs/design/analysis-repository.md#stored-projection-and-verification-scope)
records the supported verification boundary.

## Credentials

Copy `.env.example` to `.env`, choose a local password, and keep `.env`
untracked. Compose interpolates that value into `NEO4J_AUTH`; the same value
must be available to the CLI and integration tests as `NEO4J_PASSWORD` or
`AQUILA_GRAPH_NEO4J_PASSWORD`.

## Operations

Run these commands from the repository root:

```powershell
docker compose -f deployment\neo4j\compose.yaml up -d
docker compose -f deployment\neo4j\compose.yaml ps
docker compose -f deployment\neo4j\compose.yaml logs neo4j
```

The Compose health check calls `cypher-shell` over Bolt and executes `RETURN 1`;
container startup alone is not treated as database readiness.

Analyze and persist the calculator after exporting the same password to the
local process:

```powershell
$env:NEO4J_PASSWORD = '<the value from deployment\neo4j\.env>'
java -jar target\aquila-graph-0.1.0-SNAPSHOT.jar `
  analysis-targets\calculator\src\main\java --release 25 --persist
```

Stop Neo4j without removing its data:

```powershell
docker compose -f deployment\neo4j\compose.yaml down
```

## Deliberate data removal

The following is destructive and is never part of normal shutdown. Run it only
when all data in the local Neo4j volume may be permanently discarded:

```powershell
docker compose -f deployment\neo4j\compose.yaml down
docker volume rm aquila-graph-neo4j-data
```

## References

- Neo4j Operations Manual, [Run Neo4j in a Docker container](https://neo4j.com/docs/operations-manual/current/docker/)
- Neo4j Operations Manual, [Deploy a Neo4j standalone server with Docker Compose](https://neo4j.com/docs/operations-manual/current/docker/docker-compose-standalone/)
- Neo4j Operations Manual, [Persisting data with Docker volumes](https://neo4j.com/docs/operations-manual/current/docker/mounting-volumes/)
