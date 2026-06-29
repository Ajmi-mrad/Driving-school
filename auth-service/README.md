# auth-service

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![Keycloak](https://img.shields.io/badge/Keycloak-OAuth2-4D4D4D?logo=keycloak&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

Authentication and user-management service. It backs the platform's identity layer on top of
**Keycloak**: it provisions and synchronises users/roles through the Keycloak Admin API,
exposes user CRUD and `/me` endpoints, and stores user records in PostgreSQL with JPA auditing.
Acts as an OAuth2 resource server for its own protected endpoints.

## Details
- **Role:** Identity, users and roles (Keycloak integration)
- **Port:** `8081` (Keycloak itself runs on `8080`)
- **Storage:** PostgreSQL + Flyway migrations
- **Security:** Spring Security, OAuth2 resource server, Keycloak realm (`realm-export.json`)
- **Infra:** `docker-compose.yml` (Keycloak + PostgreSQL); copy `.env.example` to `.env`

## Run
```bash
docker compose up -d      # Keycloak + PostgreSQL
./mvnw spring-boot:run
```
