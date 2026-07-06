# DataBridge

> A type-safe, resumable data migration framework for Java enterprise systems.

![Build](https://img.shields.io/github/actions/workflow/status/pmsantanna/databridge/ci.yml?label=build)
![Java](https://img.shields.io/badge/Java-17-blue)
![License](https://img.shields.io/badge/license-MIT-green)

---

## The problem

Migrating data between legacy enterprise systems is painful:

- A failure at record 48,000 means starting over from zero
- No visibility into progress during long-running jobs
- Type mismatches between source and target silently corrupt data
- Each project reinvents the same boilerplate

**DataBridge solves this** with a single abstract class that handles paging, checkpointing, logging, and progress output — you only write the SQL.

---

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| Java | 17+ | |
| Maven | 3.8+ | |
| Docker | any recent | Required for tests and demo. See below. |

### Docker setup

Integration tests use [Testcontainers](https://testcontainers.com), which spins up real PostgreSQL containers during `mvn test`. Docker must be running on the machine.

**Option A — Docker Desktop** (full Docker experience)
→ https://www.docker.com/products/docker-desktop/

**Option B — Testcontainers Desktop** (lighter alternative, purpose-built for Testcontainers)
→ https://testcontainers.com/desktop/

To verify Docker is available:
```bash
docker --version
docker ps
```

To skip tests and build only the JAR:
```bash
mvn clean package -DskipTests
```

---

## Quick start

```bash
# 1. Clone and build (skipping tests if Docker is not available)
git clone https://github.com/seu-usuario/databridge
cd databridge
mvn clean package -DskipTests

# 2. Run the full demo (requires Docker)
chmod +x run-demo.sh && ./run-demo.sh
```

---

## Usage

```bash
java -jar target/databridge.jar migrate \
  --src-url  "jdbc:postgresql://localhost:5433/source_db" \
  --src-user postgres \
  --tgt-url  "jdbc:postgresql://localhost:5434/target_db" \
  --tgt-user postgres \
  --page-size 500
```

```
▶ DataBridge Migration Starting
[10:32:01] employees                  page=1    total=500
[10:32:02] employees                  page=2    total=1000
[10:32:03] employees                  page=3    total=1500
...
✔ Done! fetched=25000  inserted=24987  skipped=13
```

---

## Implementing a migration routine

Extend `AbstractMigrationRoutine<T>` and implement two methods:

```java
public class MigratePayrollEntries extends AbstractMigrationRoutine<Map<String, Object>> {

    @Override
    public String routineName() { return "payroll_entries"; }

    @Override
    protected List<Map<String, Object>> fetchPage(MigrationContext ctx, int offset, int limit) {
        return ctx.source().queryForList("""
            SELECT id, employee_id, amount, competence, type
            FROM payroll_entries
            ORDER BY id
            LIMIT ? OFFSET ?
        """, limit, offset);
    }

    @Override
    protected void persist(List<Map<String, Object>> records, MigrationContext ctx) {
        for (var r : records) {
            ctx.upsertIgnore(
                "payroll_entries",
                "id",
                new String[]{"id", "employee_id", "amount", "competence", "type"},
                new Object[]{r.get("id"), r.get("employee_id"), r.get("amount"),
                             r.get("competence"), r.get("type")}
            );
        }
    }
}
```

That's it. Paging, checkpointing, and progress output are handled automatically.

---

## Features

| Feature | Description |
|---|---|
| ✅ Resumable | Checkpointing saves progress; re-run to continue from where it stopped |
| ✅ Type-safe | Generics enforce consistency between `fetchPage` and `persist` |
| ✅ Multi-source | PostgreSQL and SQL Server — extensible via `DbConfig` |
| ✅ Progress output | ANSI-colored terminal output with page and total counts |
| ✅ Hooks | `beforeMigration` / `afterMigration` for DDL, index creation, cleanup |
| ✅ Dialect-aware upsert | `ctx.upsertIgnore()` generates the right SQL per database automatically |
| ✅ Exit codes | Returns `0` on success, `1` on failure — CI/CD friendly |

---

## Architecture

```
DataBridgeCLI (Picocli)
    └── MigrateCommand
            └── MigrationContext  ←  DbConfig (source + target + dialect)
                    └── AbstractMigrationRoutine<T>
                            ├── fetchPage()      →  source JdbcTemplate
                            ├── persist()        →  target JdbcTemplate
                            ├── upsertIgnore()   →  dialect-aware SQL generation
                            └── checkpoint       →  MigrationContext.metadata
```

---

## Running tests

Tests require Docker to be running (uses Testcontainers with real PostgreSQL containers).

```bash
# Run all tests
mvn test

# Build without running tests
mvn clean package -DskipTests
```

On first run, Testcontainers will pull the `postgres:16-alpine` image (~80MB). Subsequent runs reuse the cached image.

---

## Tech stack

- **Java 17** — records, text blocks, sealed types
- **Picocli 4.7** — CLI parsing, help generation, interactive password prompt
- **Spring JDBC 6.1** — `JdbcTemplate`, `DriverManagerDataSource`
- **PostgreSQL 16** — primary supported database
- **SQL Server** — supported via dialect-aware upsert
- **Testcontainers 1.19** — integration tests with real PostgreSQL containers
- **JUnit 5** — test framework
- **Docker Compose** — local demo environment

---

## License

MIT
