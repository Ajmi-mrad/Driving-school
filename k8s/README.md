# Kubernetes (Helm) — Driving School backend

Runs the whole backend on Kubernetes: **shared Postgres** (all DBs), **Keycloak** (realm auto-import),
**Mailpit**, **Eureka**, **API gateway**, and the 5 domain services. Container images are pulled from
Docker Hub (`ajmimrad/driving-school-*`), published by the CI/CD pipeline — nothing is built locally.

## Prerequisites

| Tool | Notes |
| ---- | ----- |
| minikube | already installed |
| kubectl | **install:** `curl -LO "https://dl.k8s.io/release/$(curl -Ls https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl" && sudo install kubectl /usr/local/bin/` |
| helm | **install:** `curl https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 \| bash` |

The cluster runs ~11 pods, so it needs headroom — the `cluster-up` target starts minikube with
`--cpus=4 --memory=8192`.

## Quick start

```bash
# from the repo root
make -f k8s/Makefile cluster-up      # start minikube + ingress addon
make -f k8s/Makefile deploy          # sync realm + helm upgrade --install
make -f k8s/Makefile status          # watch pods/services come up
make -f k8s/Makefile urls            # prints the /etc/hosts line to add
```

Then add the printed line to `/etc/hosts` (maps the hostnames to `minikube ip`), e.g.:

```
192.168.49.2  api.driving-school.local keycloak.driving-school.local eureka.driving-school.local
```

### Verify

```bash
curl http://api.driving-school.local/actuator/health        # gateway -> 200
open  http://keycloak.driving-school.local                  # realm auto-ecole (owner / Owner123!)
open  http://eureka.driving-school.local                    # all services registered
```

Deploy a specific image tag (e.g. a commit SHA) instead of `latest`:

```bash
make -f k8s/Makefile deploy IMAGE_TAG=sha-cca705f
```

## How it maps to the docker-compose setup

- **One shared Postgres** StatefulSet creates all 6 databases (`authdb`, `vehicledb`, `bookingdb`,
  `communicationdb`, `financedb`, `keycloak`) via an init script — instead of 6 Postgres containers.
- **Eureka is kept** (the gateway routes `lb://SERVICE`). Every client sets
  `EUREKA_INSTANCE_PREFER_IP_ADDRESS=true` so it registers its routable pod IP.
- **Keycloak** validates tokens for services over the internal URL (`http://keycloak:8080`), while the
  browser (Swagger/login) uses the ingress host — services use `jwk-set-uri`, which doesn't check the
  issuer host, so both coexist.
- Secrets (DB passwords, Keycloak client secrets) live in one Kubernetes `Secret`, templated from
  `values.yaml`. Dev defaults match `realm-export.json` so local just works; override for cloud.

## Chart layout

```
helm/driving-school/
  Chart.yaml
  values.yaml            # all services + infra config
  values-local.yaml      # minikube overlay (ingress hosts, browser Keycloak URLs)
  files/realm-export.json # synced from auth-service/ by `make sync-realm` (gitignored)
  templates/
    _springservice.tpl   # reusable Deployment+Service for a Spring app
    springservices.yaml  # renders all 7 services
    postgres.yaml keycloak.yaml mailpit.yaml secret.yaml ingress.yaml
```

## Cloud later

`.github/workflows/deploy.yml` is a manual (`workflow_dispatch`) job that runs `helm upgrade` against a
cluster from a `KUBECONFIG` secret. It's inactive until you add the `KUBECONFIG` repo secret and have a
real cluster. For managed Postgres, override the `*_DB_URL` env + secrets to point at the external DB.

## Known gap

`booking-service` has no client in `realm-export.json` yet, so its service-to-service (client-credentials)
calls will fail until a `booking-service` confidential client + service account is added to the realm.
Pre-existing, unrelated to Kubernetes.