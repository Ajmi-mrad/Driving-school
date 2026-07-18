# Driving School — local Kubernetes (minikube) helper.
# Works from any directory (paths resolved relative to this Makefile).
#
#   make cluster-up      # start minikube with the ingress addon
#   make deploy          # sync realm + helm upgrade --install (uses values-local.yaml)
#   make status          # pods / services in the namespace
#   make logs SVC=auth-service
#   make urls            # print the /etc/hosts line you need
#   make down            # uninstall the release
#   make cluster-delete  # delete the whole minikube cluster

MKFILE_DIR := $(dir $(abspath $(lastword $(MAKEFILE_LIST))))
REPO_ROOT  := $(abspath $(MKFILE_DIR)..)
CHART      := $(MKFILE_DIR)driving-school
REALM_SRC  := $(REPO_ROOT)/auth-service/realm-export.json
REALM_DST  := $(CHART)/files/realm-export.json

NAMESPACE ?= driving-school
RELEASE   ?= driving-school
IMAGE_TAG ?= latest

.PHONY: cluster-up sync-realm deploy status logs urls down cluster-delete

cluster-up:
	minikube start --cpus=4 --memory=8192 --addons=ingress
	@echo "Cluster ready. Run 'make deploy' next."

sync-realm:
	@mkdir -p $(CHART)/files
	cp "$(REALM_SRC)" "$(REALM_DST)"
	@echo "Synced realm-export.json into the chart."

deploy: sync-realm
	helm upgrade --install $(RELEASE) $(CHART) \
	  --namespace $(NAMESPACE) --create-namespace \
	  -f $(CHART)/values-local.yaml \
	  --set global.imageTag=$(IMAGE_TAG)

status:
	kubectl get pods,svc,ingress -n $(NAMESPACE)

logs:
	kubectl logs -n $(NAMESPACE) deploy/$(SVC) -f

urls:
	@echo "Add this line to /etc/hosts:"
	@echo "$$(minikube ip)  api.driving-school.local keycloak.driving-school.local eureka.driving-school.local"

down:
	helm uninstall $(RELEASE) -n $(NAMESPACE) || true

cluster-delete:
	minikube delete