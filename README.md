# CETracker Backend

This the Backend for the CETracker (Cycling Equipment Usage Tracker).
For more information about this project see the [project's homepage](https://cetracker.github.io/).

## Basic Building Instructions

### Libraries Used

The Backend is a Kotlin, SpringBoot application, providing business logic via a REST API. There is a [sample frontend](https://github.com/cetracker/cetrack-frontend). implementing an UI for the API available.

A specification first approach is used for the API. Code is being generated with the help of the [OpenAPI Generator](https://openapi-generator.tech/), and it's plugin for [Gradle](https://gradle.org/).
For mapping the data on it's journey through the onion layers [kmapper](https://stackoverflow.com/a/74864762/2664521) is being used.  
Setting up database schema on MySQL compatible databases is done with [Flyway scripts](https://flywaydb.org/).

A JDK of Java 17 is needed for building the project.

### Building

To build an artefact, all you  have to do is to run

```bash
./gradlew build
```

### Running locally

```bash
./gradlew bootRun -Dspring.profiles.active="h2db"
```

When running with the profile `h2db`, the database will be _destroyed_ when the service is terminated.
If you want the data to be persisted, you may connect it with a MariaDB oder MySQL instance.
A prepared profile named `mysql` is provided for this.
Have a look inside the `application.yaml` for the necessary connection parameters as well as for additionally
available Spring profiles. The above startup command needs to adapted to `-Dspring.profiles.active="mysql"`
in order to use MySQL instead of the in memory h2 database.

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
