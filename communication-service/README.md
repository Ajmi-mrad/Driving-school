# communication-service

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-010101?logo=socketdotio&logoColor=white)
![OAuth2](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-database-4169E1?logo=postgresql&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)

Real-time **messaging and presence** service. It powers conversations and chat between users
(students, instructors, staff) over **WebSocket/STOMP**, tracks online presence and unread
counts, and persists messages and conversations in PostgreSQL. STOMP connections are
authenticated with Keycloak JWTs and the REST API is secured as an OAuth2 resource server.

## Details
- **Role:** Chat, notifications and presence
- **Port:** `8084`
- **Transport:** WebSocket + STOMP (authenticated channel interceptor)
- **Storage:** PostgreSQL + Flyway migrations
- **Security:** OAuth2 resource server (JWT)
- **Discovery:** Eureka client
- **Infra:** `docker-compose.yml` (PostgreSQL)

## Run
```bash
./mvnw spring-boot:run
```