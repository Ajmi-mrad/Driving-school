# CI/CD Workflows

CI/CD for the backend microservices, built on GitHub Actions + Docker Hub.
Kubernetes deployment is **not** wired up yet — CD currently stops at "image pushed to Docker Hub".

## How it works

Each service has its own workflow (`auth-service.yml`, `booking-service.yml`, …). It is
**path-filtered**, so a service's pipeline runs only when files in its own directory change.

Each per-service workflow calls two shared reusable workflows:

| Workflow          | Runs on                     | What it does                                              |
| ----------------- | --------------------------- | -------------------------------------------------------- |
| `reusable-ci.yml` | every PR **and** push       | JDK 17 + `./mvnw -B verify` (unit + Testcontainers tests), then PMD + SpotBugs (report-only) |
| `reusable-cd.yml` | push to `main` only         | Build Docker image and push it to Docker Hub             |

So:

- **Pull request / feature branch push** → CI only (build + test).
- **Merge to `main`** → CI, then CD builds and pushes the image.

Services build independently and in parallel — a change touching two services triggers exactly
those two pipelines.

## Static analysis (PMD + SpotBugs)

Each service's `pom.xml` declares the `maven-pmd-plugin` and `spotbugs-maven-plugin`, both configured
to emit **SARIF**. CI runs them after the build (`pmd:pmd spotbugs:spotbugs`) and uploads the results
to **GitHub code scanning**, so findings appear in the repo's **Security → Code scanning** tab and as
annotations on pull requests. Each upload uses a distinct category (`pmd-<service>`, `spotbugs-<service>`).

They are **report-only** — `failOnViolation` / `failOnError` are `false`, so findings never fail the
build; the upload step is `continue-on-error` too.

> Code scanning is free on **public** repositories. On a private repo it needs GitHub Advanced Security.

Run locally the same way (produces `target/pmd.sarif.json` and `target/spotbugsSarif.json`):

```bash
cd auth-service && ./mvnw -B pmd:pmd spotbugs:spotbugs
```

To turn either into a blocking gate later, switch its `<configuration>` flag to `true` (or run the
`:check` goal in CI).

## Dependency updates (Dependabot)

`.github/dependabot.yml` opens weekly PRs for:
- **Maven** dependencies in each of the 7 service modules, and
- the **GitHub Actions** used in these workflows.

PRs are labelled `dependencies`. Each Maven PR touches one service, so its path-filtered CI runs and
validates the bump automatically.

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