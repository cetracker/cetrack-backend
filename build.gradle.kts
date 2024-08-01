
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.5"
    id("io.spring.dependency-management") version "1.1.6"
    id("com.gorylenko.gradle-git-properties") version "2.4.2"
    id("com.google.devtools.ksp") version "2.0.0-1.0.22" // /for kmapper
    id("org.openapi.generator") version "7.6.0"
    checkstyle
    idea

    id("com.bmuschko.docker-remote-api") version "9.4.0"

    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    kotlin("plugin.jpa") version "2.0.0"
    kotlin("plugin.allopen") version "2.0.0" // https://spring.io/guides/tutorials/spring-boot-kotlin/
    /* lombok https://kotlinlang.org/docs/lombok.html
    kotlin("plugin.lombok") version "1.8.0"
    id("io.freefair.lombok") version "5.3.0"
    */
}

val kMapperVersion = "1.2.0"

gitProperties {
    keys = listOf("git.branch","git.commit.id","git.commit.time","git.commit.message.short","git.tags","git.commit.user.email")
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
version = "v0.2.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

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
    tasks.create("openApiGenerate-${it.key}", org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
        generatorName.set("kotlin-spring")
        library.set("spring-boot")
        generateApiTests.set(false)
        generateApiDocumentation.set(false)
        generateModelTests.set(false)
        generateModelDocumentation.set(true)
        inputSpec.set("$rootDir/${it.value}")
        outputDir.set("$buildDir/generated")
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
    tasks.create("openApiValidate-${it.key}", org.openapitools.generator.gradle.plugin.tasks.ValidateTask::class) {
        inputSpec.set("$rootDir/${it.value}")
    }
}
tasks.register("openApiGenerateAll") { dependsOn(openapiSpecs.keys.map { "openApiGenerate-$it" }) }
tasks.register("openApiValidateAll") { dependsOn(openapiSpecs.keys.map { "openApiValidate-$it" }) }
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
}


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

    implementation("io.github.microutils:kotlin-logging:3.0.5")

    implementation("io.swagger.core.v3:swagger-annotations:2.2.22")
    implementation("io.swagger.core.v3:swagger-models:2.2.20")

    // (data) classes mapper - https://github.com/s0nicyouth/kmapper
    implementation("io.github.s0nicyouth:processor_annotations:$kMapperVersion")
    implementation("io.github.s0nicyouth:converters:$kMapperVersion")
    ksp("io.github.s0nicyouth:processor:$kMapperVersion")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.mockk:mockk:1.13.11")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "17"
        // useK2 = true
    }
    dependsOn(openapiSpecs.keys.map { "openApiGenerate-$it" })
}

/*
   id("com.palantir.docker") version "0.34.0" ==> Deprecated!
 - https://bmuschko.github.io/gradle-docker-plugin/current/user-guide/#introduction
 - https://plugins.gradle.org/plugin/org.jetbrains.gradle.docker
 */
tasks.create("docker", DockerBuildImage::class) {
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
tasks.create("dockerPushRelease", DockerPushImage::class) {
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString().replace("-SNAPSHOT", "")}")
    dependsOn(tasks.getByName("docker"))
}
tasks.create("dockerBuildSnapshot", DockerBuildImage::class) {
    inputDir.set(file("."))
    images.add("ghcr.io/cetracker/cetrack-backend:${version.toString()}")
    files(tasks.bootJar.get().archiveFile)
    dependsOn(tasks.getByName(tasks.bootJar.name))
    labels.set(mutableMapOf<String, String>(
        "org.opencontainers.image.source" to "https://github.com/cetracker/cetrack-backend",
        "org.opencontainers.image.description" to "CETracker backend container image SNAPSHOT",
        "org.opencontainers.image.licenses" to "GPLv3"))
}
tasks.create("dockerPushSnapshot", DockerPushImage::class) {
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
