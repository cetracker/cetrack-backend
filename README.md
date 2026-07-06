# CETracker Backend

This the Backend for the CETracker (Cycling Equipment Usage Tracker).
For more information about this project see the [project's homepage](https://cetracker.github.io/).

## Basic Building Instructions

### Libraries Used

The Backend is a Kotlin, SpringBoot application, providing business logic via a REST API. There is a [sample frontend](https://github.com/cetracker/cetrack-frontend). implementing an UI for the API available.

A specification first approach is used for the API. Code is being generated with the help of the [OpenAPI Generator](https://openapi-generator.tech/), and it's plugin for [Gradle](https://gradle.org/).
For mapping the data on it's journey through the onion layers [kmapper](https://stackoverflow.com/a/74864762/2664521) is being used.  
Setting up the database schema on PostgreSQL is done with [Flyway scripts](https://flywaydb.org/).

A JDK of Java 25 is needed for building the project.

### Building

To build an artefact, all you  have to do is to run

```bash
./gradlew build
```

### Running tests

The fast unit test suite (no DB, no Docker required):

```bash
./gradlew test
```

**Integration tests** run Flyway migrations and DB-constraint assertions against a real
PostgreSQL 17 container via Testcontainers. A running Docker (or Podman with a
Docker-compatible socket) daemon is required:

```bash
./gradlew integrationTest
```

CI runs `integrationTest` automatically in the `integration-test` GitHub Actions job on
`ubuntu-latest`, which ships Docker pre-installed. `./gradlew build` remains Docker-free.

### Running locally

```bash
./gradlew bootRun -Dspring.profiles.active="local,postgres"
```

The `postgres` profile expects a running PostgreSQL 17 (defaults match the compose file in
`CUET_DDD_Model/database/`: localhost:5442, db `cuetdata`, user `cuet`); Flyway migrates the
schema on startup. `local` adds dev conveniences and has no datasource of its own.
Have a look inside the `application.yaml` for the connection parameters (env-var overridable)
as well as for additionally available Spring profiles.

Once the service has completely started, the API is accessible at `http://localhost:8080/api`

#### Demo Data

For exploring the tool including some demo data, one may start the backend with the `demo` SpringBoot profile group.

```bash
./gradlew bootRun -Dspring.profiles.active="demo"
```

## Running the app as a Container Image

A container image will be available at `https://ghcr.io/cetracker/cetrack-backend:VERSION`.
The recommended and easiest way is to run it via docker-compose.
A `docker-compose` file as well as instructions are available in the
[cetrack-compose repository](https://github.com/cetracker/cetrack-compose).

### Building a container image locally

If you want test your modification inside a container image you may want to build your own image locally.

A container image can be build with:

```bash
./gradlew dockerBuildSnapshot
```
