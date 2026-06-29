# discovery-service

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)
![Netflix Eureka](https://img.shields.io/badge/Netflix-Eureka-FF6B6B?logo=spring&logoColor=white)
![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

Netflix **Eureka** service registry for the Driving School platform. Every other service
registers here on startup so they can discover and call each other by name instead of by
hard-coded host/port. Start this service **first** — the rest depend on it.

## Details
- **Role:** Service registry / discovery server
- **Port:** `8761` (Eureka dashboard at `http://localhost:8761`)
- **Health:** Spring Boot Actuator

## Run
```bash
./mvnw spring-boot:run
```