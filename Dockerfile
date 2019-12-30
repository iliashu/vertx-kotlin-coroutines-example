# Please run `./gradlew shadowJar` before running docker build
FROM openjdk:13-alpine
COPY build/libs/vertx-kotlin-base-1.0-all.jar application.jar
CMD ["java", "-jar", "-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory", "application.jar"]