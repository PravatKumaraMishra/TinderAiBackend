# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-21-alpine AS build


WORKDIR /app

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -DskipTests \
    && cp target/tinder-ai-backend-0.0.1-SNAPSHOT.jar /app.jar

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /app.jar app.jar


ENTRYPOINT ["java", "-jar", "app.jar"]