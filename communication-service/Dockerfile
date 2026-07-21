# ---- Stage 1 : build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests clean package

# ---- Stage 2 : runtime (image JRE légère) ----
# Base image pinned by digest so a base bump busts the buildx cache and re-runs
# `apk upgrade` — otherwise cached OS layers keep known-vulnerable packages.
FROM eclipse-temurin:17-jre-alpine@sha256:02320dd4ce20e243dfb915c686089cf9315c763084fafbb12d5c9993aee18b57 AS runtime
WORKDIR /app
RUN apk --no-cache upgrade \
    && apk add --no-cache curl \
    && addgroup -S app && adduser -S -G app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]