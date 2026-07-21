{{/*
Reusable template: renders a Service + Deployment for one Spring Boot service.
Call with: (dict "name" <serviceName> "svc" <serviceValues> "root" $)
Common env (SERVER_PORT, JWKS, Eureka) is injected here from flags so it isn't
repeated per service in values.yaml.
*/}}
{{- define "ds.springservice" -}}
{{- $name := .name -}}
{{- $svc := .svc -}}
{{- $root := .root -}}
apiVersion: v1
kind: Service
metadata:
  name: {{ $name }}
  labels:
    {{- include "ds.labels" $root | nindent 4 }}
    {{- include "ds.selectorLabels" (dict "name" $name) | nindent 4 }}
spec:
  selector:
    {{- include "ds.selectorLabels" (dict "name" $name) | nindent 4 }}
  ports:
    - port: {{ $svc.port }}
      targetPort: {{ $svc.port }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $name }}
  labels:
    {{- include "ds.labels" $root | nindent 4 }}
    {{- include "ds.selectorLabels" (dict "name" $name) | nindent 4 }}
spec:
  replicas: {{ $svc.replicas | default 1 }}
  selector:
    matchLabels:
      {{- include "ds.selectorLabels" (dict "name" $name) | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "ds.labels" $root | nindent 8 }}
        {{- include "ds.selectorLabels" (dict "name" $name) | nindent 8 }}
    spec:
      {{- if $svc.waitForPostgres }}
      initContainers:
        - name: wait-for-postgres
          image: busybox:1.36
          command: ["sh", "-c", "until nc -z postgres 5432; do echo waiting for postgres; sleep 3; done"]
      {{- end }}
      containers:
        - name: {{ $name }}
          image: "{{ $root.Values.global.imageRepoPrefix }}-{{ $name }}:{{ $root.Values.global.imageTag }}"
          imagePullPolicy: {{ $root.Values.global.imagePullPolicy }}
          ports:
            - containerPort: {{ $svc.port }}
          env:
            - name: SERVER_PORT
              value: {{ $svc.port | quote }}
            {{- if $svc.jwks }}
            - name: KEYCLOAK_JWKS_URI
              value: {{ printf "%s/realms/%s/protocol/openid-connect/certs" $root.Values.keycloak.internalUrl $root.Values.realm | quote }}
            {{- end }}
            {{- if $svc.eurekaClient }}
            - name: EUREKA_ENABLED
              value: "true"
            - name: EUREKA_SERVER_URL
              value: "http://discovery-service:8761/eureka"
            - name: EUREKA_INSTANCE_PREFER_IP_ADDRESS
              value: "true"
            {{- end }}
            {{- range $k, $v := $svc.env }}
            - name: {{ $k }}
              value: {{ $v | quote }}
            {{- end }}
            {{- range $envName, $secretKey := $svc.secretEnv }}
            - name: {{ $envName }}
              valueFrom:
                secretKeyRef:
                  name: {{ include "ds.secretName" $root }}
                  key: {{ $secretKey }}
            {{- end }}
          startupProbe:
            httpGet:
              path: /actuator/health
              port: {{ $svc.port }}
            failureThreshold: 30
            periodSeconds: 10
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: {{ $svc.port }}
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: {{ $svc.port }}
            periodSeconds: 20
{{- end -}}