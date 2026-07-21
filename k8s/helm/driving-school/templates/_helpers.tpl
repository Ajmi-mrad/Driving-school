{{/* Common labels applied to every object. */}}
{{- define "ds.labels" -}}
app.kubernetes.io/part-of: driving-school
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/* Per-component selector labels. Call with a dict: (dict "name" <name>). */}}
{{- define "ds.selectorLabels" -}}
app.kubernetes.io/name: {{ .name }}
{{- end -}}

{{/* Secret name holding all passwords / client secrets. */}}
{{- define "ds.secretName" -}}
driving-school-secrets
{{- end -}}