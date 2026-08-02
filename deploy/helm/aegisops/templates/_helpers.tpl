{{- define "aegisops.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "aegisops.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "aegisops.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "aegisops.labels" -}}
app.kubernetes.io/name: {{ include "aegisops.name" . }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end -}}

{{- define "aegisops.selectorLabels" -}}
app.kubernetes.io/name: {{ include "aegisops.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "aegisops.componentServiceAccountName" -}}
{{- $root := index . 0 -}}
{{- $component := index . 1 -}}
{{- $configured := index $root.Values.serviceAccount.names $component -}}
{{- if $root.Values.serviceAccount.create -}}
{{- default (printf "%s-%s" (include "aegisops.fullname" $root) $component) $configured -}}
{{- else -}}
{{- default "default" $configured -}}
{{- end -}}
{{- end -}}

{{- define "aegisops.validateComponentServiceAccounts" -}}
{{- $root := . -}}
{{- $seen := dict -}}
{{- range $component := list "app" "agent" "runner" -}}
{{- $app := index $root.Values.apps $component -}}
{{- if $app.enabled -}}
{{- $configured := index $root.Values.serviceAccount.names $component -}}
{{- if and (not $root.Values.serviceAccount.create) (empty $configured) -}}
{{- fail (printf "serviceAccount.names.%s is required when serviceAccount.create=false" $component) -}}
{{- end -}}
{{- $resolved := include "aegisops.componentServiceAccountName" (list $root $component) -}}
{{- if eq $resolved "default" -}}
{{- fail (printf "serviceAccount.names.%s must not use the default ServiceAccount" $component) -}}
{{- end -}}
{{- if hasKey $seen $resolved -}}
{{- fail (printf "component ServiceAccount names must be distinct: %s and %s both use %s" (index $seen $resolved) $component $resolved) -}}
{{- end -}}
{{- $_ := set $seen $resolved $component -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "aegisops.componentSecretName" -}}
{{- $root := index . 0 -}}
{{- $component := index . 1 -}}
{{- $configured := index $root.Values.security.existingSecrets $component -}}
{{- default (printf "%s-%s-security" (include "aegisops.fullname" $root) $component) $configured -}}
{{- end -}}

{{- define "aegisops.validateIngressNamespaceSelector" -}}
{{- $selector := default (dict) .Values.networkPolicy.ingressNamespaceSelector -}}
{{- $matchLabels := default (dict) (get $selector "matchLabels") -}}
{{- $matchExpressions := default (list) (get $selector "matchExpressions") -}}
{{- if and (empty $matchLabels) (empty $matchExpressions) -}}
{{- fail "networkPolicy.ingressNamespaceSelector must select at least one namespace label" -}}
{{- end -}}
{{- end -}}

{{- define "aegisops.validateNetworkPolicyEgressCidrs" -}}
{{- $cidrs := default (list) .Values.networkPolicy.egress.allowedCidrs -}}
{{- if empty $cidrs -}}
{{- fail "networkPolicy.egress.allowedCidrs must contain at least one restricted CIDR when networkPolicy.enabled=true" -}}
{{- end -}}
{{- range $cidr := $cidrs -}}
{{- $value := toString $cidr -}}
{{- if regexMatch ".*/0+$" $value -}}
{{- fail (printf "networkPolicy.egress.allowedCidrs must not contain world-open CIDR %s" $cidr) -}}
{{- end -}}
{{- $ipv4 := regexMatch `^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])/(0|[1-9]|[12][0-9]|3[0-2])$` $value -}}
{{- $ipv6 := regexMatch `^[0-9A-Fa-f]{0,4}(:[0-9A-Fa-f]{0,4})+/(0|[1-9]|[1-9][0-9]|1[01][0-9]|12[0-8])$` $value -}}
{{- if not (or $ipv4 $ipv6) -}}
{{- fail (printf "networkPolicy.egress.allowedCidrs must contain valid IPv4 or IPv6 CIDRs: %s" $cidr) -}}
{{- end -}}
{{- end -}}
{{- end -}}

{{- define "aegisops.image" -}}
{{- $root := index . 0 -}}
{{- $image := index . 1 -}}
{{- if $root.Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" $root.Values.global.imageRegistry $image.repository $image.tag -}}
{{- else -}}
{{- printf "%s:%s" $image.repository $image.tag -}}
{{- end -}}
{{- end -}}
