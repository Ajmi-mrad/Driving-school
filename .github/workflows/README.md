# CI/CD Workflows

CI/CD for the backend microservices, built on GitHub Actions + Docker Hub.
Kubernetes deployment is **not** wired up yet — CD currently stops at "image pushed to Docker Hub".

## How it works

Each service has its own workflow (`auth-service.yml`, `booking-service.yml`, …). It is
**path-filtered**, so a service's pipeline runs only when files in its own directory change.

Each per-service workflow calls two shared reusable workflows:

| Workflow          | Runs on                     | What it does                                              |
| ----------------- | --------------------------- | -------------------------------------------------------- |
| `reusable-ci.yml` | every PR **and** push       | JDK 17 + `./mvnw -B verify` (unit + Testcontainers tests) |
| `reusable-cd.yml` | push to `main` only         | Build Docker image and push it to Docker Hub             |

So:

- **Pull request / feature branch push** → CI only (build + test).
- **Merge to `main`** → CI, then CD builds and pushes the image.

Services build independently and in parallel — a change touching two services triggers exactly
those two pipelines.

## Images

Published as:

```
<DOCKERHUB_USERNAME>/driving-school-<service>
```

Tagged with:

- the **short git SHA** (immutable, e.g. `a1b2c3d`)
- `latest`

CD uses each service's existing multi-stage `Dockerfile` unchanged.

## Required repository secrets

Add these under **Settings → Secrets and variables → Actions**:

| Secret               | Value                                                        |
| -------------------- | ----------------------------------------------------------- |
| `DOCKERHUB_USERNAME` | Your Docker Hub username (also the image namespace)         |
| `DOCKERHUB_TOKEN`    | A Docker Hub **access token** (not your account password)   |

CI runs without secrets; only the CD (push) step needs them.

## Adding a new service

1. Copy any per-service workflow (e.g. `auth-service.yml`) to `<new-service>.yml`.
2. Replace every occurrence of the old service name with the new directory name.

## Next steps (not yet implemented)

- Kubernetes manifests (Helm charts) for the services + Postgres / Keycloak / Eureka.
- A CD deploy step (`helm upgrade` / image-tag bump) once a cluster exists.