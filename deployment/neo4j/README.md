# Local Neo4j

Aquila Graph runs as a local Java 25 process. Neo4j Community Edition runs in
Docker and retains its database in the `aquila-graph-neo4j-data` named volume.
The pinned `neo4j:2026.08.1` image exposes Browser only on local port `7474` and
Bolt only on local port `7687`.

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
