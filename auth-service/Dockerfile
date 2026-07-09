# ---- Stage 1 : build ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
# Cache des dépendances : on copie d'abord le pom seul
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
# Puis le code source
COPY src src
RUN mvn -B -q -DskipTests clean package

# ---- Stage 2 : runtime (image JRE légère) ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# curl pour le healthcheck docker-compose
RUN apk add --no-cache curl \
    && addgroup -S app && adduser -S -G app app
COPY --from=build /app/target/*.jar app.jar
USER app
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
