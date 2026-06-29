# api-gateway

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![Spring Cloud Gateway](https://img.shields.io/badge/Spring%20Cloud-Gateway-6DB33F?logo=spring&logoColor=white)
![OAuth2](https://img.shields.io/badge/Spring%20Security-OAuth2-6DB33F?logo=springsecurity&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

**Spring Cloud Gateway** — the single entry point for the Driving School platform. It routes
incoming requests to the right downstream service (discovered via Eureka) and enforces
security as an OAuth2 resource server, validating Keycloak-issued JWTs before forwarding traffic.

## Details
- **Role:** API gateway / edge router and security boundary
- **Port:** `8222`
- **Discovery:** Eureka client (routes resolved by service name)
- **Security:** OAuth2 resource server (JWT validation)

## Run
```bash
./mvnw spring-boot:run
```