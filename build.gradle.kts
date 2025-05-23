
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.github.spotbugs.snom.Effort
import com.google.devtools.ksp.gradle.KspAATask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.4.5"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.gorylenko.gradle-git-properties") version "2.5.0"
    id("com.google.devtools.ksp") version "2.1.20-2.0.0" // /for kmapper
    id("org.openapi.generator") version "7.12.0"
    id("com.github.spotbugs") version "6.1.10"
    checkstyle
    idea

    id("com.bmuschko.docker-remote-api") version "9.4.0"

    id("com.ryandens.javaagent-test") version "0.8.0"

    val kotlinVersion = "2.1.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    kotlin("plugin.allopen") version kotlinVersion // https://spring.io/guides/tutorials/spring-boot-kotlin/
    /* lombok https://kotlinlang.org/docs/lombok.html
    kotlin("plugin.lombok") version "1.8.0"
    id("io.freefair.lombok") version "5.3.0"
    */
}

gitProperties {
    keys = listOf("git.branch","git.commit.id","git.commit.time","git.commit.message.short","git.tags","git.commit.user.email")
}

spotbugs {
    effort = Effort.DEFAULT
    reportsDir = file(layout.buildDirectory.file("reports/spotbugs"))
    excludeFilter = file(layout.projectDirectory.file("config/spotbugs/exclude.xml"))
}

tasks.spotbugsMain {
    reports.create("html") {
        required = true
        outputLocation = file(layout.buildDirectory.file("reports/spotbugs/spotbugs.html"))
        setStylesheet("fancy-hist.xsl")
    }
}


allOpen {
    // https://spring.io/guides/tutorials/spring-boot-kotlin/
    // Persistence with JPA
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}

/*
kotlinLombok {
    lombokConfigurationFile(file("lombok.config"))
}
*/

group = "de.cyclingsir"
version = "v0.2.7"
java.sourceCompatibility = JavaVersion.VERSION_21

repositories {
    mavenCentral()
//    mavenLocal()
}

// multiple api specs:
// https://stackoverflow.com/a/73081035/2664521
val openapiSpecs = mapOf(
    "part" to "api/parts-api.yaml",
    "bike" to "api/bike-api.yaml",
    "tour" to "api/tour-api.yaml"
)
openapiSpecs.forEach {
    println("$rootDir/${it.value}")
    tasks.register("openApiGenerate-${it.key}", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
        group = "apiGeneration"
        description = "generating files for an api spec"
        generatorName.set("kotlin-spring")
        library.set("spring-boot")
        generateApiTests.set(false)
        generateApiDocumentation.set(false)
        generateModelTests.set(false)
        generateModelDocumentation.set(true)
        inputSpec.set("$rootDir/${it.value}")
        outputDir.set("${layout.buildDirectory.get()}/generated")
        apiPackage.set("de.cyclingsir.cetrack.infrastructure.api.rest")
        modelPackage.set("de.cyclingsir.cetrack.infrastructure.api.model")
        configOptions.set(
            mapOf(
                "interfaceOnly" to "true",
//            delegatePattern: "true",
                "useSwaggerUI" to "false",
                "useSpringBoot3" to "true",
                // https://github.com/OpenAPITools/openapi-generator/pull/13620 ==> conflicts
                // ==> https://github.com/OpenAPITools/openapi-generator/pull/14369
                // https://github.com/OpenAPITools/openapi-generator/issues/14010
                // https://github.com/OpenAPITools/openapi-generator/issues/13578
                "useBeanValidation" to "true",
                "useTags" to "true",
//            skipDefaultInterface: "true",
//            "hideGenerationTimestamp" to "true"
            )
        )
        typeMappings.set(
            mapOf(
                "integer+int16" to "kotlin.Short"
            )
        )

//        sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME).java.srcDir("$buildDir/generated/openapi/src")
    }
    tasks.register("openApiValidate-${it.key}", org.openapitools.generator.gradle.plugin.tasks.ValidateTask::class) {
        group = "apiValidation"
        description = "validate the api"
        inputSpec.set("$rootDir/${it.value}")
    }
}
tasks.register("openApiGenerateAll") { group = "apiGeneration"; description = "generating all api files"; dependsOn(openapiSpecs.keys.map { "openApiGenerate-$it" }) }
tasks.register("openApiValidateAll") { group = "apiValidation"; description = "validating all api files"; dependsOn(openapiSpecs.keys.map { "openApiValidate-$it" }) }
/* not working yet
openApiValidate {
    inputSpec.set("$rootDir/api/parts-api.yaml")
}
openApiValidate {
    inputSpec.set("$rootDir/api/bike-api.yaml")
}

tasks.withType<org.openapitools.generator.gradle.plugin.tasks.ValidateTask> {
    dependsOn("openApiValidate-part", "openApiValidate-bike")
}
*/


// https://kotlinlang.org/docs/ksp-quickstart.html#make-ide-aware-of-generated-code
// for kMapper and openApiGenerator
kotlin {
    sourceSets.main {
        kotlin.srcDirs("build/generated/ksp/main/kotlin", "build/generated/src/main")
    }
    sourceSets.test {
        kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
    jvmToolchain(21)
}


val kMapperVersion = "1.2.0"
val kotlinLoggingVersion = "7.0.7"
val swaggerVersion = "2.2.30"
val mockKVersion = "1.13.17"
val byeBuddyVersion = "1.17.5"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // temporarily needed for upload html form - until being replaced by frontend
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.github.oshai:kotlin-logging-jvm:$kotlinLoggingVersion")

    implementation("io.swagger.core.v3:swagger-annotations:$swaggerVersion")
    implementation("io.swagger.core.v3:swagger-models:$swaggerVersion")

    // (data) classes mapper - https://github.com/s0nicyouth/kmapper
    implementation("io.github.s0nicyouth:processor_annotations:$kMapperVersion")
    implementation("io.github.s0nicyouth:converters:$kMapperVersion")
    ksp("io.github.s0nicyouth:processor:$kMapperVersion")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:$mockKVersion")
    testJavaagent("net.bytebuddy:byte-buddy-agent:$byeBuddyVersion")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        // useK2 = true
    }
    dependsOn(openapiSpecs.keys.map { "openApiGenerate-$it" })
}

tasks.withType<KspAATask> {
    dependsOn(openapiSpecs.keys.map { "openApiGenerate-$it" })
}

/*
   id("com.palantir.docker") version "0.34.0" ==> Deprecated!
 - https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/#introduction
 - https://plugins.gradle.org/plugin/org.jetbrains.gradle.docker
 */
tasks.register("docker", DockerBuildImage::class) {
    group = "docker"
    description = "build container image"
    inputDir.set(file("."))
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString().replace("-SNAPSHOT", "")}")
    images.add("ghcr.io/cetracker/cetrack-backend:latest")
    files(tasks.bootJar.get().archiveFile)
    // https://docs.gradle.org/8.0.1/userguide/validation_problems.html#implicit_dependency
    dependsOn(tasks.getByName(tasks.bootJar.name))
    labels.set(mutableMapOf<String, String>(
        "org.opencontainers.image.source" to "https://github.com/cetracker/cetrack-backend",
        "org.opencontainers.image.description" to "CETracker backend container image",
        "org.opencontainers.image.licenses" to "GPLv3"))
}
tasks.register("dockerPushRelease", DockerPushImage::class) {
    group = "docker"
    description = "push container image"
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString().replace("-SNAPSHOT", "")}")
    dependsOn(tasks.getByName("docker"))
}
tasks.register("dockerBuildSnapshot", DockerBuildImage::class) {
    group = "docker"
    description = "build container snapshot image"
    inputDir.set(file("."))
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString()}")
    files(tasks.bootJar.get().archiveFile)
    dependsOn(tasks.getByName(tasks.bootJar.name))
    labels.set(mutableMapOf<String, String>(
        "org.opencontainers.image.source" to "https://github.com/cetracker/cetrack-backend",
        "org.opencontainers.image.description" to "CETracker backend container image SNAPSHOT",
        "org.opencontainers.image.licenses" to "GPLv3"))
}
tasks.register("dockerPushSnapshot", DockerPushImage::class) {
    group = "docker"
    description = "push container snapshot image"
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString()}")
    dependsOn(tasks.getByName("dockerBuildSnapshot"))
}

tasks.bootRun {
    val properties: java.util.Properties = System.getProperties()
    val mapOfProperties = mutableMapOf<String, Any>()
    properties.forEach { k, v -> mapOfProperties.put(k.toString(), v) }
    systemProperties(mapOfProperties)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.getByName<Jar>("jar") {
    enabled = false
}
