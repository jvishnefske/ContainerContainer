{{/* Name of the chart, overridable. */}}
{{- define "containercontainer.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Full name of the release's objects. */}}
{{- define "containercontainer.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "containercontainer.selectorLabels" -}}
app.kubernetes.io/name: {{ include "containercontainer.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "containercontainer.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{ include "containercontainer.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/* JAVA_OPTIONS: the external admin groups as a system property, then the user's options. */}}
{{- define "containercontainer.javaOptions" -}}
{{- $opts := list }}
{{- with .Values.security.externalAdminGroups }}
{{- range . }}
{{- if or (contains "," .) (contains " " .) }}
{{- fail (printf "security.externalAdminGroups: group %q must not contain a comma or a space" .) }}
{{- end }}
{{- end }}
{{- $opts = append $opts (printf "-Dartifactory.security.externalAdminGroups=%s" (join "," .)) }}
{{- end }}
{{- with .Values.javaOptions }}
{{- $opts = append $opts . }}
{{- end }}
{{- join " " $opts }}
{{- end }}
