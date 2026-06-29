# ---- Stage 1 : build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -B -q -DskipTests clean package

# ---- Stage 2 : runtime (image JRE légère) ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r app && useradd -r -g app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "/app/app.jar"]