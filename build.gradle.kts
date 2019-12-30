import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "task"
version = "1.0"

plugins {
    application
    kotlin("jvm") version "1.3.61"
    id("org.jlleitschuh.gradle.ktlint") version "9.1.1"
    id("com.github.johnrengelman.shadow") version "5.2.0"
}

repositories {
    mavenCentral()
}

val vertxVersion = "3.8.4"
val junitVersion = "5.5.2"
val restAssuredVersion = "4.1.2"
val hsqldbVersion = "2.5.0"
val mockitoVersion = "3.2.4"
val mockitoKotlinVersion = "2.2.0"
val slf4jVersion = "1.7.30"
val swaggerValidationVersion = "2.8.3"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.3")

    // Vert.x
    implementation("io.vertx", "vertx-core", vertxVersion)
    implementation("io.vertx", "vertx-lang-kotlin", vertxVersion)
    implementation("io.vertx", "vertx-lang-kotlin-coroutines", vertxVersion)

    // REST server
    implementation("io.vertx", "vertx-web", vertxVersion)

    // DB
    implementation("io.vertx", "vertx-jdbc-client", vertxVersion)
    runtimeOnly("org.hsqldb", "hsqldb", hsqldbVersion)

    // Logging
    implementation("org.slf4j", "slf4j-api", slf4jVersion)
    runtimeOnly("org.slf4j", "slf4j-simple", slf4jVersion)

    // test framework (JUnit5)
    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("io.vertx", "vertx-junit5", vertxVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    // mocking
    testImplementation("org.mockito", "mockito-core", mockitoVersion)
    testImplementation("com.nhaarman.mockitokotlin2", "mockito-kotlin", mockitoKotlinVersion)

    // Test REST client
    testImplementation("io.rest-assured", "rest-assured", restAssuredVersion)
    testImplementation("io.rest-assured", "kotlin-extensions", restAssuredVersion)

    // OpenAPI validation during tests
    testImplementation("com.atlassian.oai", "swagger-request-validator-restassured", swaggerValidationVersion)
}

val vertxLogFactoryDelegate = "io.vertx.core.logging.SLF4JLogDelegateFactory"

application {
    mainClassName = "net.example.vertx.kotlin.MainKt"
    applicationDefaultJvmArgs = listOf("-Dvertx.logger-delegate-factory-class-name=$vertxLogFactoryDelegate")
}

tasks {
    test {
        systemProperty("vertx.logger-delegate-factory-class-name", vertxLogFactoryDelegate)
        useJUnitPlatform()
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "12"
}
