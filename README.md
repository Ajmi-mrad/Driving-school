# Driving School

A Spring Boot microservices platform for managing a driving school.

## Services

| Service | Description |
| --- | --- |
| `discovery-service` | Eureka service registry for service discovery |
| `api-gateway` | Spring Cloud Gateway — single entry point and routing |
| `auth-service` | Authentication and authorization (Keycloak realm) |
| `booking-service` | Lesson and exam booking management |
| `vehicle-service` | Fleet and vehicle management |
| `communication-service` | Notifications and messaging |

## Tech stack

- Java / Spring Boot, Spring Cloud
- Maven (per-service `mvnw` wrapper)
- Docker (`Dockerfile` per service, `docker-compose.yml` where needed)

## Running a service

```bash
cd <service>
./mvnw spring-boot:run
```

Start `discovery-service` first, then `api-gateway`, then the domain services.

## Documentation

See `cahier_des_charges.pdf` / `cahier_des_charges.tex` for the full specification.
