# Observability stack (local)

A local telemetry stack for the Driving School microservices.

| Tool | Role | URL |
|------|------|-----|
| Prometheus | scrapes `/actuator/prometheus` on every service | http://localhost:9090 |
| Grafana | dashboards over Prometheus / Loki / Jaeger | http://localhost:3000 (admin/admin) |
| OpenTelemetry Collector | receives OTLP traces → Jaeger; tails container logs → Loki | :4317 (gRPC), :4318 (HTTP) |
| Jaeger | distributed tracing UI | http://localhost:16686 |
| Loki | log aggregation | http://localhost:3100 |
| Alertmanager | routes Prometheus alerts to Mailpit (email) | http://localhost:9093 |
| Mailpit | dev inbox where alert emails land (reused from auth-service stack) | http://localhost:8025 |
| cAdvisor / node-exporter | container & host infrastructure metrics | :8090 / :9100 |

## Pipeline (hybrid)

- **Metrics** — Prometheus *scrapes* each service's `/actuator/prometheus` directly.
  Enabled by `micrometer-registry-prometheus` in every service pom.
- **Traces** — services export OTLP to the Collector
  (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`), which forwards
  them to Jaeger.
- **Logs** — the Collector's `filelog` receiver tails Docker container logs and
  ships them to Loki over OTLP.
- **Alerts** — Prometheus evaluates `prometheus/alert-rules.yml`; firing alerts go to
  Alertmanager, which emails them to Mailpit.

## Prerequisites

The app stacks must be running first — the `auth-service` compose creates the shared
`auth-service_auto-ecole` network **and** the `mailpit` container this stack reuses:

```bash
docker compose -f auth-service/docker-compose.yml up -d
docker compose -f vehicle-service/docker-compose.yml up -d
docker compose -f communication-service/docker-compose.yml up -d
docker compose -f finance-service/docker-compose.yml up -d
# booking-service has no compose yet — run it from the IDE if you want its metrics
```

## Run

```bash
docker compose -f observability/docker-compose.yml up -d
```

Then generate some traffic through the API gateway / Swagger so metrics, traces and
logs appear.

## Verify

1. **Prometheus** → Status ▸ Targets: the `spring-actuator` job shows each service `UP`.
2. **Grafana** → dashboard *Driving School / Spring Boot Overview* renders; all three
   datasources pass "Save & test".
3. **Jaeger** → pick a service, find traces spanning gateway → downstream services.
4. **Loki** (Grafana ▸ Explore) → `{service_namespace="driving-school"}` shows app logs.
5. **Alerting** → `docker stop vehicle-service`; after ~1 min the `ServiceDown` alert
   fires (Prometheus ▸ Alerts, Alertmanager), and an email appears in Mailpit.

## Running services from the IDE instead of Docker

If you start services from your IDE (not via their compose):

- Traces still work — apps default `OTEL_EXPORTER_OTLP_ENDPOINT` to
  `http://localhost:4318`, which the Collector publishes.
- For Prometheus to scrape host-run apps, edit `prometheus/prometheus.yml` targets to
  `host.docker.internal:<port>` and add to the `prometheus` service in
  `docker-compose.yml`:
  ```yaml
  extra_hosts:
    - "host.docker.internal:host-gateway"
  ```
- Logs via the `filelog` receiver only capture **containerized** services; IDE-run
  services won't appear in Loki.

## Notes / follow-ups

- Trace↔log correlation (Loki derived field → Jaeger) works best once services emit
  the trace id into log lines; refining the `filelog` parsing is a follow-up.
- Replicating this stack as Helm templates for the k8s deployment is out of scope here.
