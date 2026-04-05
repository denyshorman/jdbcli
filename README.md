# jdbcli

A Java CLI tool for querying any JDBC database from the terminal — install drivers on demand, save named connection profiles, and output results as JSON, CSV, or table.

## Why jdbcli?

jdbcli is built for engineers who work with multiple databases and need a lightweight, scriptable tool that fits into existing workflows without configuration overhead.

- **No bundled drivers** — drivers are downloaded on demand from Maven Central and cached locally. You decide which version to use.
- **Named profiles** — store connection details once, reference by name. No credentials on the command line, no repeating connection strings in scripts.
- **Pipe-friendly** — results go to `stdout`, logs and errors to `stderr`. Works naturally with `jq`, `grep`, `awk`, and redirects.
- **Runs anywhere Java runs** — a single fat JAR with no native dependencies. Works on any OS, in CI, in containers, and on servers where installing additional tooling is restricted.
- **Extensible** — add any database with a JDBC driver by dropping a JSON descriptor file in `~/.jdbcli/dbs/`.

## Supported Databases

13 databases are supported out of the box. Drivers are resolved automatically from Maven Central.

| ID | Database |
|---|---|
| `clickhouse` | ClickHouse |
| `db2` | IBM DB2 |
| `h2` | H2 |
| `mariadb` | MariaDB |
| `mongodb` | MongoDB |
| `mysql` | MySQL |
| `oracle` | Oracle Database |
| `postgres` | PostgreSQL |
| `redshift` | Amazon Redshift |
| `snowflake` | Snowflake |
| `sqlite` | SQLite |
| `sqlserver` | Microsoft SQL Server |
| `trino` | Trino |

Any other database with a JDBC driver can be added via a [custom descriptor](#custom-database-descriptors).

## Download

Pre-built fat JARs are published automatically on every release. Java 21+ is required.

| Channel | How to get it |
|---|---|
| **Latest stable** | [GitHub Releases → latest](https://github.com/denyshorman/jdbcli/releases/latest) → download `jdbcli.jar` |
| **Snapshot** (latest `main`) | [GitHub Releases → snapshot](https://github.com/denyshorman/jdbcli/releases/tag/snapshot) → download `jdbcli.jar` |

```bash
# Run after downloading
java -jar jdbcli.jar --help
```

## Build from Source

```bash
# Build the fat JAR (output: build/libs/jdbcli.jar)
./gradlew shadowJar

# Run
java -jar build/libs/jdbcli.jar --help
```

You can also run directly via Gradle during development:

```bash
./gradlew run --args="--help"
```

---

## Quick Start

All state is stored under `~/.jdbcli/`:

| Path | Contents |
|---|---|
| `~/.jdbcli/dbs/` | Database descriptors (JSON) |
| `~/.jdbcli/drivers/` | Downloaded JDBC driver JARs |
| `~/.jdbcli/profiles/` | Connection profiles (JSON) |

### 1. Browse built-in database descriptors

```bash
jdbcli db list
```

See the [Supported Databases](#supported-databases) section for the full list.

### 2. Install a JDBC driver

```bash
# Resolve the latest version from Maven Central automatically
jdbcli driver install --db postgres

# Or pin an exact JAR via URL
jdbcli driver install --db postgres --url https://jdbc.postgresql.org/download/postgresql-42.7.10.jar
```

### 3. Create a connection profile

```bash
jdbcli profile init --db postgres --name dev-db
```

This writes a template to `~/.jdbcli/profiles/dev-db.json`. Open it and fill in the real JDBC URL and credentials:

```json
{
  "name": "dev-db",
  "dbId": "postgres",
  "driver": {
    "class": "org.postgresql.Driver",
    "jar": "/home/you/.jdbcli/drivers/postgresql-42.7.5.jar"
  },
  "jdbcUrl": "jdbc:postgresql://localhost:5432/mydb",
  "auth": {
    "type": "plain",
    "username": "admin",
    "password": "secret"
  },
  "safety": {
    "readOnly": true,
    "maxRows": 1000,
    "timeoutSeconds": 30
  }
}
```

**Credential modes** (`auth.type`):

| Mode | Description |
|---|---|
| `plain` | Username and password stored directly in the profile file |
| `env` | Username and password read from environment variables at runtime |

`env` example:

```json
{
  "type": "env",
  "usernameEnv": "DB_USER",
  "passwordEnv": "DB_PASSWORD"
}
```

### 4. Run a query

Results go to **stdout**; logs and errors go to **stderr** — safe to pipe.

```bash
# Inline SQL, default JSON output
jdbcli query --profile dev-db --sql "SELECT * FROM users LIMIT 10"

# Read SQL from a file
jdbcli query --profile dev-db --file report.sql

# CSV output — redirect to file
jdbcli query --profile dev-db --sql "SELECT * FROM orders" --format csv > orders.csv

# Table output — human-readable
jdbcli query --profile dev-db --sql "SELECT * FROM users" --format table

# JSON output piped into jq
jdbcli query --profile dev-db --sql "SELECT count(*) FROM orders" | jq '.rows[0]'
```

Supported `--format` values: `json` (default), `csv`, `table`.

---

## Custom Database Descriptors

Scaffold a template for a new database:

```bash
jdbcli db init --name mydb
```

This creates `~/.jdbcli/dbs/mydb.json`. Edit it to match your database:

```json
{
  "id": "mydb",
  "displayName": "My Database",
  "driver": {
    "class": "com.example.Driver",
    "groupId": "com.example",
    "artifactId": "my-jdbc-driver"
  },
  "examples": {
    "jdbcUrl": "jdbc:example://localhost:1234/mydb",
    "testQuery": "SELECT 1"
  }
}
```

`examples.jdbcUrl` is used as a starting-point URL when `profile init` generates a new profile — edit the profile to point at your real server.

**Driver resolution order** (highest priority first):

1. `--url` passed to `driver install`
2. `driver.url` field in the descriptor (direct JAR link)
3. Latest version resolved from Maven Central via `driver.groupId` / `driver.artifactId`
4. `driver.defaultVersion` pinned in the descriptor (skips Maven Central lookup)

---

## Safety Limits

The `safety` block in a profile lets you cap runaway queries:

```json
{
  "readOnly": true,
  "maxRows": 1000,
  "timeoutSeconds": 30
}
```

| Field | Description |
|---|---|
| `readOnly` | Passes `Connection.setReadOnly(true)` to the JDBC driver |
| `maxRows` | Maximum rows returned per query |
| `timeoutSeconds` | Query execution timeout |

---

## Debugging

Set the `JDBCLI_DEBUG` environment variable to print full stack traces on error:

```bash
JDBCLI_DEBUG=1 jdbcli query --profile dev-db --sql "SELECT 1"
```
