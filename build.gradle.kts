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
val log4jVersion = "2.13.0"
val restAssuredVersion = "4.1.2"
val hsqldbVersion = "2.5.0"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.3")

    implementation("io.vertx", "vertx-core", vertxVersion)
    implementation("io.vertx", "vertx-web", vertxVersion)
    implementation("io.vertx", "vertx-lang-kotlin", vertxVersion)
    implementation("io.vertx", "vertx-lang-kotlin-coroutines", vertxVersion)
    implementation("io.vertx", "vertx-jdbc-client", vertxVersion)

    runtimeOnly("org.hsqldb", "hsqldb", hsqldbVersion)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("io.vertx", "vertx-junit5", vertxVersion)
    testImplementation("org.mockito", "mockito-core", "3.2.4")
    testImplementation("com.nhaarman.mockitokotlin2", "mockito-kotlin", "2.2.0")

    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    testImplementation("io.rest-assured", "rest-assured", restAssuredVersion)
    testImplementation("io.rest-assured", "kotlin-extensions", restAssuredVersion)
    // rest-assured uses slf4j, we need to forward the logs to JUL via  his dependency to avoid warning
    testImplementation("org.slf4j", "slf4j-jdk14", "1.7.30")

    testImplementation("com.atlassian.oai", "swagger-request-validator-restassured", "2.8.3")
}

application {
    mainClassName = "net.example.vertx.kotlin.MainKt"
}

tasks {
    test {
        useJUnitPlatform()
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "12"
}
