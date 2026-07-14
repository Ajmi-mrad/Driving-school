# Chaos testing

The platform is chaos-tested at two levels: **automated** network-fault tests that run in the
build, and **interactive** application-level assaults you drive by hand and observe in the
observability stack.

## 1. Automated network chaos (Toxiproxy) — runs in `mvn verify`

`booking-service` and `finance-service` ship integration tests that put a
[Toxiproxy](https://github.com/Shopify/toxiproxy) (via Testcontainers) in front of WireMock-stubbed
downstreams and inject latency / connection faults to assert the resilience contract:

| Test | Fault | Asserted behaviour |
|------|-------|--------------------|
| `booking …/it/SessionResilienceChaosTest` | finance stalled (6s latency) | `PATCH /sessions/{id}/complete` still returns `COMPLETED`; bounded by the 3s read timeout (< 5s), not hanging |
| | mandatory dep (auth/vehicle) stalled | `POST /sessions` fails fast (bounded 5xx), no infinite hang |
| | fault removed | consumption reporting resumes |
| `finance …/it/PaymentReminderChaosTest` | communication healthy | `POST /payments/{id}/remind` → `204`, notification delivered |
| | communication stalled | reminder degrades to a bounded `502` (best-effort), not a hang or `500` |

These rely on the connect/read timeouts added to the load-balanced `RestClient` in each service's
`RestClientConfig` (2s connect / 3s read). Run them with:

```bash
mvn -f booking-service/pom.xml verify
mvn -f finance-service/pom.xml verify
```

(They need a Docker daemon for Testcontainers — Postgres + Toxiproxy.)

## 2. Interactive chaos (Chaos Monkey for Spring Boot) + observability

`booking-service` and `finance-service` bundle
[Chaos Monkey for Spring Boot](https://codecentric.github.io/chaos-monkey-spring-boot/). It is
**inert** until you start the service with the `chaos-monkey` profile; even then every assault
starts **disabled**. This is a hands-on way to watch resilience (and its absence) in Grafana/Jaeger.

### Start a service with chaos enabled

```bash
# from the service directory (or pass -Dspring-boot.run.profiles)
SPRING_PROFILES_ACTIVE=chaos-monkey ./mvnw spring-boot:run
```

Make sure the observability stack is up (`docker compose -f observability/docker-compose.yml up -d`)
and the service is being scraped / sending traces.

### Turn on a latency assault at runtime

```bash
# Inject 2–4s latency into @Service / @RestController calls
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H 'Content-Type: application/json' \
  -d '{"level":3,"latencyActive":true,"latencyRangeStart":2000,"latencyRangeEnd":4000}'

# Check status / list watchers
curl http://localhost:8083/actuator/chaosmonkey
```

Other assaults: set `"exceptionsActive":true` to make calls throw, or `"killApplicationActive":true`
to randomly kill the app. Disable everything again:

```bash
curl -X POST http://localhost:8083/actuator/chaosmonkey/assaults \
  -H 'Content-Type: application/json' \
  -d '{"latencyActive":false,"exceptionsActive":false,"killApplicationActive":false}'
```

(Ports: booking-service `8083`, finance-service `8085`.)

### Observe the impact

Drive some traffic through the gateway, then watch:

- **Grafana → Driving School / Spring Boot Overview**: HTTP p95 latency and 5xx rate for the
  assaulted service spike.
- **Jaeger** (`http://localhost:16686`): traces show the injected latency inside the assaulted spans.
- **Prometheus / Alertmanager**: sustained latency or errors trip the `HighHttp5xxRate` alert →
  email in Mailpit.

This closes the loop: chaos injected in the app, seen end-to-end in metrics, traces, and alerts.

## 3. Out of scope (follow-up)

Cluster-level chaos (pod kills, network partitions) against the Helm deployment via
Chaos Mesh / LitmusChaos.
