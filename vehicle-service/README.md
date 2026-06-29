# vehicle-service

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![OAuth2](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

Manages the driving school's **vehicle fleet** — registering vehicles, tracking status
(available, in maintenance, retired) and recording maintenance history. Exposes vehicle and
maintenance REST APIs that other services (e.g. `booking-service`) rely on to check
availability. Secured as an OAuth2 resource server with JPA auditing.

## Details
- **Role:** Fleet & maintenance management
- **Port:** `8082`
- **Storage:** PostgreSQL + Flyway migrations
- **Security:** Spring Security, OAuth2 resource server
- **Discovery:** Eureka client
- **Infra:** `docker-compose.yml` (PostgreSQL)

## Run
```bash
./mvnw spring-boot:run
```