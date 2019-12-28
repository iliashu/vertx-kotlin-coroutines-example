import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "task"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.61"
}

repositories {
    mavenCentral()
}

val vertxVersion = "3.8.4"
val junitVersion = "5.5.2"
val log4jVersion = "2.13.0"
val restAssuredVersion = "4.1.2"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.3.3")

    implementation("io.vertx","vertx-core", vertxVersion)
    implementation("io.vertx","vertx-web", vertxVersion)
    implementation("io.vertx","vertx-lang-kotlin", vertxVersion)
    implementation("io.vertx","vertx-lang-kotlin-coroutines", vertxVersion)

    testImplementation("org.junit.jupiter", "junit-jupiter-api", junitVersion)
    testImplementation("io.vertx", "vertx-junit5", vertxVersion)
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", junitVersion)

    testImplementation("io.rest-assured", "rest-assured", restAssuredVersion)
    // rest-assured uses slf4j, we need to forward the logs to JUL via  his dependency to avoid warning
    testImplementation("org.slf4j", "slf4j-jdk14", "1.7.30")
}

tasks {
    test {
        useJUnitPlatform()
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "12"
}
