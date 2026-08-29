# Load Test Service

This is a V1 Java 17 and Spring Boot load-testing service. It accepts a load-test request, persists workflow state in MySQL, runs local Java worker threads, and exposes APIs to check status and final aggregate metrics.

## Architecutre at glance
LLD :- https://lucid.app/lucidchart/f2027b8d-3fd7-43ee-9aad-e128f2953263/edit?page=0_0#.
HLD:- https://lucid.app/lucidchart/a5348673-7c50-4b7d-8462-94423fec7725/edit?page=0_0&invitationId=inv_5b974e9b-bebe-49bf-ba74-9a94df86967f#.
Workflow Steps:- https://lucid.app/lucidchart/9a864740-e356-44a4-afd4-99f7b34acd6f/edit?page=0_0&invitationId=inv_d566e09f-91ca-46fa-ae6f-fd415aacde03#.

## Dependecies Used at a glance

```text
Mac
  └─ Colima Linux VM
      └─ MySQL container
          └─ load_test database

Spring Boot applicatiofn on the Mac
  └─ jdbc:mysql://localhost:3306/load_test
```

`compose.yaml` tells Docker which MySQL image to download, how to configure it, and which port to expose:

```text
Mac: localhost:3306
        ↓
MySQL container: 3306
```

The Spring Boot application connects through that port using the datasource configuration in `src/main/resources/application.properties`.

## Prerequisites

- JDK 17
- Homebrew
- Colima, Docker CLI, and Docker Compose

Install the container tooling without Docker Desktop:

```zsh
brew install colima docker docker-compose
mkdir -p ~/.docker/cli-plugins
ln -sfn "$(brew --prefix)/opt/docker-compose/bin/docker-compose" \
  ~/.docker/cli-plugins/docker-compose
colima start
```

Colima runs a lightweight Linux VM and provides a Docker-compatible engine. See the [Colima installation documentation](https://colima.run/docs/installation/) for platform-specific details.

## Start the project locally

From the repository root:

```zsh
docker compose up -d
./mvnw spring-boot:run
```

When startup succeeds, the application listens on port `8080`.

```zsh
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Useful Docker and MySQL commands

```zsh
# List project containers and health status.
docker compose ps

# Follow MySQL startup logs. Press Ctrl+C to stop watching logs only.
docker compose logs -f mysql

# Stop or restart MySQL while preserving its data volume.
docker compose stop mysql
docker compose start mysql

# Open the MySQL command-line client inside the container.
docker compose exec mysql mysql -u loadtest -p load_test
```

The MySQL password is `loadtest_dev_password` for local development. Inside the SQL prompt:

```sql
SHOW TABLES;
SELECT id, name, status, created_at FROM load_tests;
```

Avoid `docker compose down -v` unless you intentionally want to delete all local database data.

## Flyway: database schema migrations

Flyway owns the database schema. It reads versioned SQL files from:

```text
src/main/resources/db/migration/
```

The first migration is:

```text
V1__create_load_test_schema.sql
```

On startup, Flyway records completed migrations in the `flyway_schema_history` table:

```text
Is V1 already applied?
  ├─ no  → run V1__create_load_test_schema.sql
  └─ yes → do nothing
```

For a future schema change, add a new migration rather than editing `V1`:

```text
V2__add_worker_heartbeat.sql
V3__add_cancelled_status.sql
```

## Hibernate and JPA: Java objects to database rows

Hibernate maps Java entities to MySQL tables. Spring Data JPA provides repositories that use Hibernate underneath.

```text
Java LoadTest object
  ↓ Hibernate / JPA
load_tests table row in MySQL
```

For example, `LoadTestRepository.save(loadTest)` causes Hibernate to create the required SQL `INSERT` or `UPDATE` statement.

This project uses:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

This means Hibernate does not create or change tables. It only validates that entity mappings match the schema created by Flyway. This keeps schema changes explicit, versioned, and safe.

## Spring Boot, beans, and dependency injection

Run the application with:

```zsh
./mvnw spring-boot:run
```

This compiles the project if needed, starts Spring Boot, creates the Spring-managed objects called *beans*, and starts embedded Tomcat on port `8080`.

Key annotations:

| Annotation | Meaning |
|---|---|
| `@RestController` | Creates a bean that handles HTTP API requests. |
| `@Service` | Creates a bean for business logic. |
| `@Repository` | Marks database-access components; Spring Data creates repository implementations automatically. |
| `@RequiredArgsConstructor` | Lombok generates a constructor for `final` dependencies. Spring uses it for constructor injection. |

Lombok reduces repetitive Java code. It can generate constructors, getters, setters, and JPA's required empty constructor during compilation. Lombok does not create singleton objects; Spring does that when it creates beans.

## Jakarta annotations

`jakarta.*` packages provide standard annotations used for database mapping and request validation.

### JPA mapping annotations

| Annotation | Meaning |
|---|---|
| `@Entity` | This Java class is persisted in a database. |
| `@Table(name = "load_tests")` | Maps the class to the `load_tests` table. |
| `@Id` | Marks the primary-key field. |
| `@OneToMany` | One `LoadTest` owns many request definitions. |
| `@ManyToOne` | Many request definitions belong to one `LoadTest`. |
| `@JoinColumn` | Specifies the database foreign-key column. |
| `@Enumerated(EnumType.STRING)` | Stores enum values as readable strings such as `RUNNING`. |
| `@Version` | Enables optimistic locking to avoid conflicting updates from multiple workflow servers. |

### Request validation annotations

| Annotation | Meaning |
|---|---|
| `@NotBlank` | A string must not be null, empty, or whitespace. |
| `@Min(1)` | A number must be at least one. |
| `@Valid` | Tells Spring to validate an incoming request DTO before calling the service. |

Invalid input returns `400 Bad Request` instead of creating incomplete database records.

## Dependencies in `pom.xml`

| Dependency | Why it is used |
|---|---|
| `spring-boot-starter-webmvc` | REST controllers, request routing, JSON HTTP APIs, and embedded Tomcat. |
| `spring-boot-starter-data-jpa` | Spring Data repositories, Hibernate, and transaction support. |
| `spring-boot-starter-validation` | Jakarta request validation annotations. |
| `spring-boot-starter-flyway` and `flyway-mysql` | Versioned MySQL schema migrations. |
| `mysql-connector-j` | JDBC driver that allows Java to connect to MySQL. |
| `spring-boot-starter-actuator` | Operational endpoints such as `/actuator/health`. |
| `lombok` | Compile-time generation of repetitive Java code. |
| `h2` (test scope) | Temporary in-memory database used only by automated tests. |
| `*-test` dependencies | Spring Boot testing support for web, JPA, Flyway, validation, and Actuator. |

Maven starters are bundles of compatible libraries. For example, `spring-boot-starter-data-jpa` also brings in Hibernate and HikariCP, the database connection pool.
[LocalWorkerRuntime.java](src/main/java/com/example/loadtest/service/LocalWorkerRuntime.java)
## Request flow

```text
HTTP JSON request
  ↓
Tomcat receives the request
  ↓
Spring MVC routes it to LoadTestController
  ↓[LocalWorkerRuntime.java](src/main/java/com/example/loadtest/service/LocalWorkerRuntime.java)
Jackson converts JSON to a request DTO
  ↓
Jakarta Validation validates the DTO
  ↓
LoadTestService applies business logic
  ↓
Repository + Hibernate write SQL
  ↓
MySQL stores LoadTest and WorkRequest rows
  ↓
Jackson converts the Java response object to JSON
  ↓
HTTP 202 Accepted response
```

## Current APIs

```text
POST /api/v1/load-tests
GET  /api/v1/load-tests/{loadTestId}
```


## Run a local smoke test

Use this test to verify the complete V1 workflow without any external authentication or HTTPS certificate setup. It makes requests to this application's own Actuator health endpoint.

Start MySQL and the application first:

```zsh
docker compose up -d
./mvnw spring-boot:run
```

In a second terminal, create a two-minute test at 300 RPM (5 requests per second). The expected total is  `300 / 60 * 120 = 600` requests.

```zsh
curl -i -X POST 'http://localhost:8080/api/v1/load-tests' \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "local actuator smoke test",
    "durationSeconds": 120,
    "requestsPerMinute": 300,
    "requests": [
      {
        "name": "local actuator health",
        "method": "GET",
        "url": "http://localhost:8080/actuator/health",
        "headers": {
          "Accept": "application/json"
        },
        "authentication": {
          "type": "NONE"
        }
      }
    ]
  }'
```

The response is `202 Accepted`. Copy its `loadTestId` value:

```json
{
  "loadTestId": "<load-test-id>",
  "workRequestId": "<work-request-id>",
  "loadTestStatus": "ACCEPTED",
  "workRequestStatus": "ACCEPTED"
}
```

Check progress while the test runs:

```zsh
curl -s 'http://localhost:8080/api/v1/load-tests/<load-test-id>'
```

During execution, the important fields are:

```json
{
  "loadTestStatus": "RUNNING",
  "workRequestStatus": "RUNNING",
  "currentStep": "RUN_LOAD",
  "totalRequests": null
}
```

After the duration and cleanup complete, run the same GET request again. A successful result looks like:

```json
{
  "loadTestStatus": "COMPLETED",
  "workRequestStatus": "COMPLETED",
  "currentStep": "COMPLETE",
  "totalRequests": 600,
  "successfulRequests": 600,
  "failedRequests": 0,
  "statusCodeCounts": {
    "200": 600
  }
}
```

The exact total can be slightly lower if the target cannot respond fast enough. The worker schedules requests at the configured rate; it does not burst extra requests to catch up.

### Watch the database while a test runs

Open MySQL in another terminal:

```zsh
docker compose exec mysql mysql -u loadtest -p load_test
```

Enter the local password `loadtest_dev_password`, then run these queries. Replace `<load-test-id>` with the ID returned from the POST response.

```sql
-- Load-test and workflow state.
SELECT
    load_test.id,
    load_test.name,
    load_test.status AS load_test_status,
    load_test.started_at,
    load_test.ends_at,
    work_request.id AS work_request_id,
    work_request.status AS work_request_status,
    work_request.current_step,
    work_request.lease_owner,
    work_request.lease_until,
    work_request.lease_generation
FROM load_tests AS load_test
JOIN work_requests AS work_request ON work_request.load_test_id = load_test.id
WHERE load_test.id = '<load-test-id>'\G

-- Local worker state and heartbeat.
SELECT
    id,
    status,
    owner_node_id,
    allocated_requests_per_minute,
    started_at,
    completed_at,
    last_heartbeat,
    last_error
FROM workers
WHERE load_test_id = '<load-test-id>'\G

-- Latest metric snapshots. A worker writes these about every five seconds.
SELECT
    worker_id,
    captured_at,
    total_requests,
    successful_requests,
    failed_requests,
    status_code_counts
FROM worker_metric_snapshots
WHERE worker_id IN (
    SELECT id FROM workers WHERE load_test_id = '<load-test-id>'
)
ORDER BY captured_at DESC;

-- Durable workflow events created for worker start and stop.
SELECT id, event_type, status, created_at, processed_at, last_error
FROM workflow_events
WHERE work_request_id = (
    SELECT id FROM work_requests WHERE load_test_id = '<load-test-id>'
);

-- Final aggregate, available after COLLECT_RESULTS.
SELECT
    total_requests,
    successful_requests,
    failed_requests,
    status_code_counts,
    collected_at
FROM load_test_results
WHERE load_test_id = '<load-test-id>'\
```
