FROM openjdk:13-alpine
COPY build/libs/vertx-kotlin-base-1.0-all.jar application.jar
CMD ["java", "-jar", "application.jar"]