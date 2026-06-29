# booking-service

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![OAuth2](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

Manages driving **lessons and exam sessions** — booking, rescheduling, cancellation and
status transitions, with conflict detection and configurable booking rules. It validates
bookings across services by calling `auth-service` (instructors/students) and `vehicle-service`
(available vehicles) as an OAuth2 client, while protecting its own API as a resource server.

## Details
- **Role:** Session/booking management
- **Port:** `8083`
- **Storage:** PostgreSQL + Flyway migrations
- **Security:** OAuth2 resource server (own API) + OAuth2 client (cross-service calls)
- **Discovery:** Eureka client; REST clients for user & vehicle services

## Run
```bash
./mvnw spring-boot:run
```