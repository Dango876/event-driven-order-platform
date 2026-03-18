{{- define "edop.commonLabels" -}}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: edop
{{- end }}
