---
title: Phase8.2：Private Deployment / Helm / Offline Package
type: design
status: accepted
phase: global
owner: ai
created: 2026-06-30
updated: 2026-06-30
related: []
---
# Phase8.2：Private Deployment / Helm / Offline Package

> Phase8.2 目标：把 AegisOps 做成可私有化交付的部署形态。
> 这一阶段只做 **容器镜像、Helm Chart、私有化 values、离线镜像包、初始化 Secret、部署校验脚本**。
> 不新增业务能力，不调 Runner，不增加 Webhook / Ansible / SSH 执行逻辑。

---

# 1. Phase8.2 定位

Phase8.0 / 8.1 已经完成：

```txt
Phase8.0 SaaS Multi-tenant Hardening
- tenant required
- internal agent token
- tenant rate limit
- security event audit

Phase8.1 Plugin System
- plugin descriptor
- tenant plugin enablement
- frontend manifest
- agent tool allowlist
```

Phase8.2 交付：

```txt
1. Docker image hardening
2. Helm chart
3. values-private.yaml
4. values-offline.yaml
5. offline image package
6. init secret script
7. deployment verification script
8. production security checklist
```

---

# 2. 不做什么

```txt
1. 不新增 execution 能力
2. 不新增 Runner adapter
3. 不调 Webhook / Ansible / SSH
4. 不做云厂商专属部署
5. 不做完整 Operator
6. 不做 HA PostgreSQL / ClickHouse / Redis 编排
7. 不在 Helm 中内置生产数据库
8. 不把 secret 明文写死到 values.yaml
```

外部依赖推荐：

```txt
PostgreSQL
Redis
ClickHouse
MinIO
VictoriaMetrics
```

生产环境由客户已有中间件或单独中间件 chart 提供。

---

# 3. 目录结构

新增：

```txt
deploy/
  docker/
    java-app.Dockerfile
    aiops-agent.Dockerfile
  helm/
    aegisops/
      Chart.yaml
      values.yaml
      values-private.yaml
      values-offline.yaml
      templates/
        _helpers.tpl
        configmap.yaml
        secret.yaml
        deployment.yaml
        service.yaml
        ingress.yaml
        serviceaccount.yaml
        networkpolicy.yaml
        NOTES.txt
      tests/
        test_helm_values.py
        test_offline_values.py
  offline/
    images.txt
    README.md

scripts/
  deploy/
    build-images.sh
    generate-secrets.sh
    package-offline.sh
    load-offline-images.sh
    render-helm.sh
    verify-offline-package.sh

docs/
  deployment/
    private-deployment.md
    offline-package.md
    production-security-checklist.md
```

---

# 4. Docker 镜像

## 4.1 Java 应用通用 Dockerfile

路径：

```txt
deploy/docker/java-app.Dockerfile
```

```dockerfile
# syntax=docker/dockerfile:1.7

ARG MAVEN_IMAGE=maven:3.9.9-eclipse-temurin-21
ARG RUNTIME_IMAGE=eclipse-temurin:21-jre-alpine
ARG APP_MODULE=apps/aiops-server
ARG APP_NAME=aiops-server

FROM ${MAVEN_IMAGE} AS build
WORKDIR /workspace

COPY pom.xml ./
COPY apps ./apps
COPY modules ./modules

RUN --mount=type=cache,target=/root/.m2 \
    mvn -pl ${APP_MODULE} -am -DskipTests package

FROM ${RUNTIME_IMAGE} AS runtime

ARG APP_MODULE=apps/aiops-server
ARG APP_NAME=aiops-server

ENV TZ=UTC
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENV SPRING_PROFILES_ACTIVE=private

RUN addgroup -S aiops && adduser -S aiops -G aiops \
    && mkdir -p /app /var/log/aegisops \
    && chown -R aiops:aiops /app /var/log/aegisops

WORKDIR /app

COPY --from=build /workspace/${APP_MODULE}/target/*.jar /app/app.jar

USER aiops

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
```

---

## 4.2 Python Agent Dockerfile

路径：

```txt
deploy/docker/aiops-agent.Dockerfile
```

```dockerfile
# syntax=docker/dockerfile:1.7

ARG PYTHON_IMAGE=python:3.12-slim-bookworm

FROM ${PYTHON_IMAGE} AS runtime

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV AIOPS_AGENT_HOST=0.0.0.0
ENV AIOPS_AGENT_PORT=8000

RUN groupadd --system aiops \
    && useradd --system --gid aiops --home-dir /app aiops \
    && mkdir -p /app \
    && chown -R aiops:aiops /app

WORKDIR /app

COPY apps/aiops-agent/pyproject.toml ./pyproject.toml
COPY apps/aiops-agent/src ./src

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir .

USER aiops

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).read()" || exit 1

CMD ["sh", "-c", "uvicorn aiops_agent.main:app --host ${AIOPS_AGENT_HOST} --port ${AIOPS_AGENT_PORT}"]
```

---

# 5. Helm Chart

## 5.1 `Chart.yaml`

路径：

```txt
deploy/helm/aegisops/Chart.yaml
```

```yaml
apiVersion: v2
name: aegisops
description: AegisOps private deployment chart
type: application
version: 0.1.0
appVersion: "0.1.0"
kubeVersion: ">=1.24.0"
keywords:
  - aiops
  - observability
  - incident
  - agent
maintainers:
  - name: AegisOps
    email: ops@example.com
```

---

## 5.2 `values.yaml`

路径：

```txt
deploy/helm/aegisops/values.yaml
```

```yaml
global:
  imageRegistry: ""
  imagePullSecrets: []
  imagePullPolicy: IfNotPresent
  storageClass: ""
  timezone: UTC

nameOverride: ""
fullnameOverride: ""

serviceAccount:
  create: true
  name: ""
  annotations: {}

podSecurityContext:
  runAsNonRoot: true
  seccompProfile:
    type: RuntimeDefault

containerSecurityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: false
  capabilities:
    drop:
      - ALL

commonLabels: {}
commonAnnotations: {}

config:
  springProfilesActive: private
  javaOpts: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
  logLevel: INFO

security:
  internalAgentToken: ""
  jwtSecret: ""
  internalAgentTokenRequired: true
  tenantRequired: true
  publicApiRequestsPerMinute: 600
  internalAgentRequestsPerMinute: 1200

external:
  postgres:
    host: "postgresql.default.svc.cluster.local"
    port: 5432
    database: "aegisops"
    username: "aegisops"
    password: ""
  redis:
    host: "redis.default.svc.cluster.local"
    port: 6379
    password: ""
  clickhouse:
    host: "clickhouse.default.svc.cluster.local"
    httpPort: 8123
    tcpPort: 9000
    database: "aegisops"
    username: "default"
    password: ""
  minio:
    endpoint: "http://minio.default.svc.cluster.local:9000"
    accessKey: ""
    secretKey: ""
    bucket: "aegisops"
  victoriaMetrics:
    baseUrl: "http://victoria-metrics.default.svc.cluster.local:8428"

apps:
  server:
    enabled: true
    replicaCount: 1
    image:
      repository: "aegisops/aiops-server"
      tag: "0.1.0"
    port: 8080
    env: {}
    resources:
      requests:
        cpu: 200m
        memory: 512Mi
      limits:
        cpu: "1"
        memory: 1Gi
  worker:
    enabled: true
    replicaCount: 1
    image:
      repository: "aegisops/aiops-worker"
      tag: "0.1.0"
    port: 8080
    env: {}
    resources:
      requests:
        cpu: 100m
        memory: 384Mi
      limits:
        cpu: "1"
        memory: 768Mi
  runner:
    enabled: true
    replicaCount: 1
    image:
      repository: "aegisops/aiops-runner"
      tag: "0.1.0"
    port: 8080
    env: {}
    resources:
      requests:
        cpu: 100m
        memory: 384Mi
      limits:
        cpu: "1"
        memory: 768Mi
  agent:
    enabled: true
    replicaCount: 1
    image:
      repository: "aegisops/aiops-agent"
      tag: "0.1.0"
    port: 8000
    env: {}
    resources:
      requests:
        cpu: 100m
        memory: 256Mi
      limits:
        cpu: "1"
        memory: 512Mi

ingress:
  enabled: false
  className: ""
  annotations: {}
  hosts:
    - host: aegisops.local
      paths:
        - path: /
          pathType: Prefix
          service: server
          port: 8080
  tls: []

networkPolicy:
  enabled: false
  ingressNamespaceSelector: {}
  egressEnabled: true

offline:
  enabled: false
```

---

## 5.3 `values-private.yaml`

路径：

```txt
deploy/helm/aegisops/values-private.yaml
```

```yaml
global:
  imagePullPolicy: IfNotPresent

config:
  springProfilesActive: private
  logLevel: INFO

security:
  internalAgentToken: "CHANGE_ME_INTERNAL_AGENT_TOKEN"
  jwtSecret: "CHANGE_ME_JWT_SECRET"

external:
  postgres:
    host: "postgresql.aegisops-infra.svc.cluster.local"
    port: 5432
    database: "aegisops"
    username: "aegisops"
    password: "CHANGE_ME_POSTGRES_PASSWORD"
  redis:
    host: "redis.aegisops-infra.svc.cluster.local"
    port: 6379
    password: "CHANGE_ME_REDIS_PASSWORD"
  clickhouse:
    host: "clickhouse.aegisops-infra.svc.cluster.local"
    httpPort: 8123
    tcpPort: 9000
    database: "aegisops"
    username: "default"
    password: "CHANGE_ME_CLICKHOUSE_PASSWORD"
  minio:
    endpoint: "http://minio.aegisops-infra.svc.cluster.local:9000"
    accessKey: "CHANGE_ME_MINIO_ACCESS_KEY"
    secretKey: "CHANGE_ME_MINIO_SECRET_KEY"
    bucket: "aegisops"
  victoriaMetrics:
    baseUrl: "http://victoria-metrics.aegisops-infra.svc.cluster.local:8428"

apps:
  server:
    replicaCount: 2
  worker:
    replicaCount: 1
  runner:
    replicaCount: 1
  agent:
    replicaCount: 2

ingress:
  enabled: true
  className: nginx
  hosts:
    - host: aegisops.example.local
      paths:
        - path: /
          pathType: Prefix
          service: server
          port: 8080
```

---

## 5.4 `values-offline.yaml`

路径：

```txt
deploy/helm/aegisops/values-offline.yaml
```

```yaml
global:
  imageRegistry: "registry.local/aegisops"
  imagePullPolicy: IfNotPresent

offline:
  enabled: true

security:
  internalAgentToken: "CHANGE_ME_INTERNAL_AGENT_TOKEN"
  jwtSecret: "CHANGE_ME_JWT_SECRET"

apps:
  server:
    image:
      repository: "aiops-server"
      tag: "0.1.0"
  worker:
    image:
      repository: "aiops-worker"
      tag: "0.1.0"
  runner:
    image:
      repository: "aiops-runner"
      tag: "0.1.0"
  agent:
    image:
      repository: "aiops-agent"
      tag: "0.1.0"

ingress:
  enabled: false
```

---

## 5.5 `_helpers.tpl`

路径：

```txt
deploy/helm/aegisops/templates/_helpers.tpl
```

```tpl
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

{{- define "aegisops.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "aegisops.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
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
```

---

## 5.6 `configmap.yaml`

路径：

```txt
deploy/helm/aegisops/templates/configmap.yaml
```

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "aegisops.fullname" . }}-config
  labels:
    {{- include "aegisops.labels" . | nindent 4 }}
data:
  SPRING_PROFILES_ACTIVE: {{ .Values.config.springProfilesActive | quote }}
  JAVA_OPTS: {{ .Values.config.javaOpts | quote }}
  LOG_LEVEL: {{ .Values.config.logLevel | quote }}
  TZ: {{ .Values.global.timezone | quote }}

  POSTGRES_HOST: {{ .Values.external.postgres.host | quote }}
  POSTGRES_PORT: {{ .Values.external.postgres.port | quote }}
  POSTGRES_DB: {{ .Values.external.postgres.database | quote }}
  POSTGRES_USER: {{ .Values.external.postgres.username | quote }}

  REDIS_HOST: {{ .Values.external.redis.host | quote }}
  REDIS_PORT: {{ .Values.external.redis.port | quote }}

  CLICKHOUSE_HOST: {{ .Values.external.clickhouse.host | quote }}
  CLICKHOUSE_HTTP_PORT: {{ .Values.external.clickhouse.httpPort | quote }}
  CLICKHOUSE_TCP_PORT: {{ .Values.external.clickhouse.tcpPort | quote }}
  CLICKHOUSE_DB: {{ .Values.external.clickhouse.database | quote }}
  CLICKHOUSE_USER: {{ .Values.external.clickhouse.username | quote }}

  MINIO_ENDPOINT: {{ .Values.external.minio.endpoint | quote }}
  MINIO_BUCKET: {{ .Values.external.minio.bucket | quote }}

  VICTORIA_METRICS_BASE_URL: {{ .Values.external.victoriaMetrics.baseUrl | quote }}

  AIOPS_PUBLIC_API_RPM: {{ .Values.security.publicApiRequestsPerMinute | quote }}
  AIOPS_INTERNAL_AGENT_RPM: {{ .Values.security.internalAgentRequestsPerMinute | quote }}
  AIOPS_TENANT_REQUIRED: {{ .Values.security.tenantRequired | quote }}
  AIOPS_INTERNAL_AGENT_TOKEN_REQUIRED: {{ .Values.security.internalAgentTokenRequired | quote }}

  AIOPS_AGENT_EVIDENCE_API_BASE_URL: "http://{{ include "aegisops.fullname" . }}-server:8080"
  AIOPS_AGENT_KNOWLEDGE_API_BASE_URL: "http://{{ include "aegisops.fullname" . }}-server:8080"
  AIOPS_AGENT_CHECKPOINT_API_BASE_URL: "http://{{ include "aegisops.fullname" . }}-server:8080"
  AIOPS_AGENT_MEMORY_API_BASE_URL: "http://{{ include "aegisops.fullname" . }}-server:8080"
```

---

## 5.7 `secret.yaml`

路径：

```txt
deploy/helm/aegisops/templates/secret.yaml
```

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: {{ include "aegisops.fullname" . }}-secret
  labels:
    {{- include "aegisops.labels" . | nindent 4 }}
type: Opaque
stringData:
  AIOPS_INTERNAL_AGENT_TOKEN: {{ required "security.internalAgentToken is required" .Values.security.internalAgentToken | quote }}
  AIOPS_AGENT_INTERNAL_AGENT_TOKEN: {{ required "security.internalAgentToken is required" .Values.security.internalAgentToken | quote }}
  AIOPS_JWT_SECRET: {{ required "security.jwtSecret is required" .Values.security.jwtSecret | quote }}

  POSTGRES_PASSWORD: {{ required "external.postgres.password is required" .Values.external.postgres.password | quote }}
  REDIS_PASSWORD: {{ .Values.external.redis.password | quote }}
  CLICKHOUSE_PASSWORD: {{ .Values.external.clickhouse.password | quote }}
  MINIO_ACCESS_KEY: {{ .Values.external.minio.accessKey | quote }}
  MINIO_SECRET_KEY: {{ .Values.external.minio.secretKey | quote }}
```

---

## 5.8 `serviceaccount.yaml`

路径：

```txt
deploy/helm/aegisops/templates/serviceaccount.yaml
```

```yaml
{{- if .Values.serviceAccount.create }}
apiVersion: v1
kind: ServiceAccount
metadata:
  name: {{ include "aegisops.serviceAccountName" . }}
  labels:
    {{- include "aegisops.labels" . | nindent 4 }}
  annotations:
    {{- toYaml .Values.serviceAccount.annotations | nindent 4 }}
{{- end }}
```

---

## 5.9 `deployment.yaml`

路径：

```txt
deploy/helm/aegisops/templates/deployment.yaml
```

```yaml
{{- $root := . -}}
{{- range $appName, $app := .Values.apps }}
{{- if $app.enabled }}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "aegisops.fullname" $root }}-{{ $appName }}
  labels:
    {{- include "aegisops.labels" $root | nindent 4 }}
    app.kubernetes.io/component: {{ $appName }}
spec:
  replicas: {{ $app.replicaCount }}
  selector:
    matchLabels:
      {{- include "aegisops.selectorLabels" $root | nindent 6 }}
      app.kubernetes.io/component: {{ $appName }}
  template:
    metadata:
      labels:
        {{- include "aegisops.selectorLabels" $root | nindent 8 }}
        app.kubernetes.io/component: {{ $appName }}
      annotations:
        checksum/config: {{ include (print $.Template.BasePath "/configmap.yaml") $root | sha256sum }}
        checksum/secret: {{ include (print $.Template.BasePath "/secret.yaml") $root | sha256sum }}
    spec:
      serviceAccountName: {{ include "aegisops.serviceAccountName" $root }}
      imagePullSecrets:
        {{- toYaml $root.Values.global.imagePullSecrets | nindent 8 }}
      securityContext:
        {{- toYaml $root.Values.podSecurityContext | nindent 8 }}
      containers:
        - name: {{ $appName }}
          image: {{ include "aegisops.image" (list $root $app.image) | quote }}
          imagePullPolicy: {{ $root.Values.global.imagePullPolicy }}
          securityContext:
            {{- toYaml $root.Values.containerSecurityContext | nindent 12 }}
          ports:
            - name: http
              containerPort: {{ $app.port }}
          envFrom:
            - configMapRef:
                name: {{ include "aegisops.fullname" $root }}-config
            - secretRef:
                name: {{ include "aegisops.fullname" $root }}-secret
          env:
            - name: AIOPS_APP_NAME
              value: {{ $appName | quote }}
            {{- range $key, $value := $app.env }}
            - name: {{ $key }}
              value: {{ $value | quote }}
            {{- end }}
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 20
            periodSeconds: 10
            failureThreshold: 6
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: http
            initialDelaySeconds: 60
            periodSeconds: 20
            failureThreshold: 6
          resources:
            {{- toYaml $app.resources | nindent 12 }}
{{- end }}
{{- end }}
```

> Python Agent 的 health path 是 `/health`，Java 是 `/actuator/health`。如果你当前 agent deployment 使用这个统一模板，需要给 agent 单独覆盖 probe。下面给修正版模板的 probe 分支。

把上面 readiness/liveness 替换成：

```yaml
readinessProbe:
  httpGet:
    path: { { ternary "/health" "/actuator/health" (eq $appName "agent") } }
    port: http
  initialDelaySeconds: 20
  periodSeconds: 10
  failureThreshold: 6
livenessProbe:
  httpGet:
    path: { { ternary "/health" "/actuator/health" (eq $appName "agent") } }
    port: http
  initialDelaySeconds: 60
  periodSeconds: 20
  failureThreshold: 6
```

---

## 5.10 `service.yaml`

路径：

```txt
deploy/helm/aegisops/templates/service.yaml
```

```yaml
{{- $root := . -}}
{{- range $appName, $app := .Values.apps }}
{{- if $app.enabled }}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ include "aegisops.fullname" $root }}-{{ $appName }}
  labels:
    {{- include "aegisops.labels" $root | nindent 4 }}
    app.kubernetes.io/component: {{ $appName }}
spec:
  type: ClusterIP
  selector:
    {{- include "aegisops.selectorLabels" $root | nindent 4 }}
    app.kubernetes.io/component: {{ $appName }}
  ports:
    - name: http
      port: {{ $app.port }}
      targetPort: http
{{- end }}
{{- end }}
```

---

## 5.11 `ingress.yaml`

路径：

```txt
deploy/helm/aegisops/templates/ingress.yaml
```

```yaml
{{- if .Values.ingress.enabled }}
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: {{ include "aegisops.fullname" . }}
  labels:
    {{- include "aegisops.labels" . | nindent 4 }}
  annotations:
    {{- toYaml .Values.ingress.annotations | nindent 4 }}
spec:
  {{- if .Values.ingress.className }}
  ingressClassName: {{ .Values.ingress.className }}
  {{- end }}
  {{- if .Values.ingress.tls }}
  tls:
    {{- toYaml .Values.ingress.tls | nindent 4 }}
  {{- end }}
  rules:
    {{- range .Values.ingress.hosts }}
    - host: {{ .host | quote }}
      http:
        paths:
          {{- range .paths }}
          - path: {{ .path }}
            pathType: {{ .pathType }}
            backend:
              service:
                name: {{ include "aegisops.fullname" $ }}-{{ .service }}
                port:
                  number: {{ .port }}
          {{- end }}
    {{- end }}
{{- end }}
```

---

## 5.12 `networkpolicy.yaml`

路径：

```txt
deploy/helm/aegisops/templates/networkpolicy.yaml
```

```yaml
{{- if .Values.networkPolicy.enabled }}
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: {{ include "aegisops.fullname" . }}
  labels:
    {{- include "aegisops.labels" . | nindent 4 }}
spec:
  podSelector:
    matchLabels:
      {{- include "aegisops.selectorLabels" . | nindent 6 }}
  policyTypes:
    - Ingress
    {{- if .Values.networkPolicy.egressEnabled }}
    - Egress
    {{- end }}
  ingress:
    - from:
        - namespaceSelector:
            {{- toYaml .Values.networkPolicy.ingressNamespaceSelector | nindent 12 }}
      ports:
        - protocol: TCP
          port: 8080
        - protocol: TCP
          port: 8000
  {{- if .Values.networkPolicy.egressEnabled }}
  egress:
    - {}
  {{- end }}
{{- end }}
```

---

## 5.13 `NOTES.txt`

路径：

```txt
deploy/helm/aegisops/templates/NOTES.txt
```

```txt
AegisOps has been installed.

Release: {{ .Release.Name }}
Namespace: {{ .Release.Namespace }}

Services:
{{- range $appName, $app := .Values.apps }}
{{- if $app.enabled }}
  - {{ include "aegisops.fullname" $ }}-{{ $appName }}:{{ $app.port }}
{{- end }}
{{- end }}

Security:
  - X-Tenant-Id is required for /api/** and /internal/agent/**
  - X-AIOPS-INTERNAL-TOKEN is required for /internal/agent/**

Next:
  kubectl get pods -n {{ .Release.Namespace }}
  kubectl get svc -n {{ .Release.Namespace }}
```

---

# 6. 离线镜像清单

## 6.1 `deploy/offline/images.txt`

```txt
aegisops/aiops-server:0.1.0
aegisops/aiops-worker:0.1.0
aegisops/aiops-runner:0.1.0
aegisops/aiops-agent:0.1.0
```

---

## 6.2 `deploy/offline/README.md`

````md
# AegisOps Offline Package

## 内容

- images/
  - aegisops-images.tar
- chart/
  - aegisops-0.1.0.tgz
- values/
  - values-offline.yaml
- scripts/
  - load-offline-images.sh
  - render-helm.sh
  - verify-offline-package.sh

## 安装流程

1. 加载镜像

```bash
./scripts/load-offline-images.sh images/aegisops-images.tar registry.local/aegisops
```
````

2. 修改 values-offline.yaml 中的 Secret 和外部依赖地址。

3. 安装 Helm chart

```bash
helm upgrade --install aegisops ./chart/aegisops-0.1.0.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml
```

4. 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
```

````

---

# 7. 部署脚本

## 7.1 `scripts/deploy/build-images.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-server \
  --build-arg APP_NAME=aiops-server \
  -t "${REGISTRY}/aiops-server:${VERSION}" .

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-worker \
  --build-arg APP_NAME=aiops-worker \
  -t "${REGISTRY}/aiops-worker:${VERSION}" .

docker build \
  -f deploy/docker/java-app.Dockerfile \
  --build-arg APP_MODULE=apps/aiops-runner \
  --build-arg APP_NAME=aiops-runner \
  -t "${REGISTRY}/aiops-runner:${VERSION}" .

docker build \
  -f deploy/docker/aiops-agent.Dockerfile \
  -t "${REGISTRY}/aiops-agent:${VERSION}" .

echo "Built AegisOps images with version=${VERSION}, registry=${REGISTRY}"
````

---

## 7.2 `scripts/deploy/generate-secrets.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

OUT="${1:-deploy/helm/aegisops/generated-secrets.values.yaml}"

mkdir -p "$(dirname "$OUT")"

gen_secret() {
  openssl rand -base64 48 | tr -d '\n'
}

cat > "$OUT" <<EOF
security:
  internalAgentToken: "$(gen_secret)"
  jwtSecret: "$(gen_secret)"

external:
  postgres:
    password: "$(gen_secret)"
  redis:
    password: "$(gen_secret)"
  clickhouse:
    password: "$(gen_secret)"
  minio:
    accessKey: "aegisops"
    secretKey: "$(gen_secret)"
EOF

chmod 0600 "$OUT"

echo "Generated secrets values: $OUT"
echo "Keep this file private. Do not commit it."
```

---

## 7.3 `scripts/deploy/package-offline.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

VERSION="${VERSION:-0.1.0}"
REGISTRY="${REGISTRY:-aegisops}"
OUT_DIR="${OUT_DIR:-dist/offline/aegisops-${VERSION}}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR/images" "$OUT_DIR/chart" "$OUT_DIR/values" "$OUT_DIR/scripts"

IMAGE_LIST=(
  "${REGISTRY}/aiops-server:${VERSION}"
  "${REGISTRY}/aiops-worker:${VERSION}"
  "${REGISTRY}/aiops-runner:${VERSION}"
  "${REGISTRY}/aiops-agent:${VERSION}"
)

docker save "${IMAGE_LIST[@]}" -o "$OUT_DIR/images/aegisops-images.tar"

helm package deploy/helm/aegisops --destination "$OUT_DIR/chart"

cp deploy/helm/aegisops/values-offline.yaml "$OUT_DIR/values/values-offline.yaml"
cp deploy/offline/README.md "$OUT_DIR/README.md"
cp scripts/deploy/load-offline-images.sh "$OUT_DIR/scripts/load-offline-images.sh"
cp scripts/deploy/render-helm.sh "$OUT_DIR/scripts/render-helm.sh"
cp scripts/deploy/verify-offline-package.sh "$OUT_DIR/scripts/verify-offline-package.sh"

chmod +x "$OUT_DIR/scripts/"*.sh

tar -czf "dist/offline/aegisops-${VERSION}.tar.gz" -C "dist/offline" "aegisops-${VERSION}"

echo "Offline package created: dist/offline/aegisops-${VERSION}.tar.gz"
```

---

## 7.4 `scripts/deploy/load-offline-images.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAR="${1:-images/aegisops-images.tar}"
TARGET_REGISTRY="${2:-}"

if [[ ! -f "$IMAGE_TAR" ]]; then
  echo "Image tar not found: $IMAGE_TAR" >&2
  exit 1
fi

docker load -i "$IMAGE_TAR"

if [[ -n "$TARGET_REGISTRY" ]]; then
  VERSION="${VERSION:-0.1.0}"

  for image in aiops-server aiops-worker aiops-runner aiops-agent; do
    docker tag "aegisops/${image}:${VERSION}" "${TARGET_REGISTRY}/${image}:${VERSION}"
    docker push "${TARGET_REGISTRY}/${image}:${VERSION}"
  done
fi

echo "Offline images loaded."
```

---

## 7.5 `scripts/deploy/render-helm.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

RELEASE="${RELEASE:-aegisops}"
NAMESPACE="${NAMESPACE:-aegisops}"
VALUES="${1:-deploy/helm/aegisops/values.yaml}"

helm template "$RELEASE" deploy/helm/aegisops \
  --namespace "$NAMESPACE" \
  -f "$VALUES"
```

---

## 7.6 `scripts/deploy/verify-offline-package.sh`

```bash
#!/usr/bin/env bash
set -euo pipefail

PACKAGE_DIR="${1:-.}"

required_files=(
  "$PACKAGE_DIR/images/aegisops-images.tar"
  "$PACKAGE_DIR/values/values-offline.yaml"
  "$PACKAGE_DIR/scripts/load-offline-images.sh"
  "$PACKAGE_DIR/scripts/render-helm.sh"
)

for file in "${required_files[@]}"; do
  if [[ ! -f "$file" ]]; then
    echo "Missing required file: $file" >&2
    exit 1
  fi
done

chart_count="$(find "$PACKAGE_DIR/chart" -name 'aegisops-*.tgz' | wc -l | tr -d ' ')"
if [[ "$chart_count" == "0" ]]; then
  echo "Missing Helm chart package in $PACKAGE_DIR/chart" >&2
  exit 1
fi

echo "Offline package verification passed: $PACKAGE_DIR"
```

---

# 8. Helm 单测

## 8.1 `test_helm_values.py`

路径：

```txt
deploy/helm/aegisops/tests/test_helm_values.py
```

```python
from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_values_has_required_apps():
    values = load_yaml("values.yaml")

    apps = values["apps"]

    assert "server" in apps
    assert "worker" in apps
    assert "runner" in apps
    assert "agent" in apps

    for name in ["server", "worker", "runner", "agent"]:
      assert apps[name]["enabled"] is True
      assert apps[name]["image"]["repository"]
      assert apps[name]["image"]["tag"]
      assert apps[name]["port"] > 0


def test_security_defaults_do_not_contain_real_secret():
    values = load_yaml("values.yaml")

    assert values["security"]["internalAgentToken"] == ""
    assert values["security"]["jwtSecret"] == ""


def test_external_dependencies_have_hosts():
    values = load_yaml("values.yaml")
    external = values["external"]

    assert external["postgres"]["host"]
    assert external["redis"]["host"]
    assert external["clickhouse"]["host"]
    assert external["minio"]["endpoint"]
    assert external["victoriaMetrics"]["baseUrl"]
```

> 上面 Python 缩进里 `for` 下面要四空格，最终文件用下面修正版：

```python
from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_values_has_required_apps():
    values = load_yaml("values.yaml")

    apps = values["apps"]

    assert "server" in apps
    assert "worker" in apps
    assert "runner" in apps
    assert "agent" in apps

    for name in ["server", "worker", "runner", "agent"]:
        assert apps[name]["enabled"] is True
        assert apps[name]["image"]["repository"]
        assert apps[name]["image"]["tag"]
        assert apps[name]["port"] > 0


def test_security_defaults_do_not_contain_real_secret():
    values = load_yaml("values.yaml")

    assert values["security"]["internalAgentToken"] == ""
    assert values["security"]["jwtSecret"] == ""


def test_external_dependencies_have_hosts():
    values = load_yaml("values.yaml")
    external = values["external"]

    assert external["postgres"]["host"]
    assert external["redis"]["host"]
    assert external["clickhouse"]["host"]
    assert external["minio"]["endpoint"]
    assert external["victoriaMetrics"]["baseUrl"]
```

---

## 8.2 `test_offline_values.py`

路径：

```txt
deploy/helm/aegisops/tests/test_offline_values.py
```

```python
from __future__ import annotations

from pathlib import Path

import yaml


ROOT = Path(__file__).resolve().parents[1]


def load_yaml(name: str) -> dict:
    with (ROOT / name).open("r", encoding="utf-8") as file:
        return yaml.safe_load(file)


def test_offline_values_enables_offline_mode_and_local_registry():
    values = load_yaml("values-offline.yaml")

    assert values["offline"]["enabled"] is True
    assert values["global"]["imageRegistry"] == "registry.local/aegisops"


def test_offline_values_uses_local_image_repositories():
    values = load_yaml("values-offline.yaml")

    apps = values["apps"]
    assert apps["server"]["image"]["repository"] == "aiops-server"
    assert apps["worker"]["image"]["repository"] == "aiops-worker"
    assert apps["runner"]["image"]["repository"] == "aiops-runner"
    assert apps["agent"]["image"]["repository"] == "aiops-agent"
```

---

# 9. 脚本单测

## 9.1 `tests/test_phase8_2_deploy_scripts.py`

路径：

```txt
tests/test_phase8_2_deploy_scripts.py
```

```python
from __future__ import annotations

from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_deploy_scripts_exist_and_are_shell_safe():
    scripts = [
        "scripts/deploy/build-images.sh",
        "scripts/deploy/generate-secrets.sh",
        "scripts/deploy/package-offline.sh",
        "scripts/deploy/load-offline-images.sh",
        "scripts/deploy/render-helm.sh",
        "scripts/deploy/verify-offline-package.sh",
    ]

    for script in scripts:
        path = ROOT / script
        assert path.exists(), f"missing {script}"
        text = path.read_text(encoding="utf-8")
        assert "set -euo pipefail" in text


def test_offline_image_list_contains_required_images():
    image_list = ROOT / "deploy/offline/images.txt"
    assert image_list.exists()

    text = image_list.read_text(encoding="utf-8")
    assert "aegisops/aiops-server:0.1.0" in text
    assert "aegisops/aiops-worker:0.1.0" in text
    assert "aegisops/aiops-runner:0.1.0" in text
    assert "aegisops/aiops-agent:0.1.0" in text
```

---

# 10. 文档

## 10.1 `private-deployment.md`

路径：

```txt
docs/deployment/private-deployment.md
```

````md
# AegisOps Private Deployment

## 部署方式

推荐使用 Helm 部署：

```bash
helm upgrade --install aegisops deploy/helm/aegisops \
  -n aegisops --create-namespace \
  -f deploy/helm/aegisops/values-private.yaml \
  -f deploy/helm/aegisops/generated-secrets.values.yaml
```
````

## 外部依赖

生产环境建议使用客户已有中间件：

- PostgreSQL
- Redis
- ClickHouse
- MinIO
- VictoriaMetrics

## Secret

生成 Secret values：

```bash
scripts/deploy/generate-secrets.sh
```

生成的文件不要提交到 Git。

## 验证

```bash
kubectl get pods -n aegisops
kubectl get svc -n aegisops
kubectl logs -n aegisops deploy/aegisops-aegisops-server
```

````

---

## 10.2 `offline-package.md`

路径：

```txt
docs/deployment/offline-package.md
````

````md
# AegisOps Offline Package

## 制作离线包

```bash
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/build-images.sh
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/package-offline.sh
```
````

产物：

```txt
dist/offline/aegisops-0.1.0.tar.gz
```

## 离线环境安装

```bash
tar -xzf aegisops-0.1.0.tar.gz
cd aegisops-0.1.0
./scripts/verify-offline-package.sh .
./scripts/load-offline-images.sh images/aegisops-images.tar registry.local/aegisops
```

修改：

```txt
values/values-offline.yaml
```

安装：

```bash
helm upgrade --install aegisops chart/aegisops-0.1.0.tgz \
  -n aegisops --create-namespace \
  -f values/values-offline.yaml
```

````

---

## 10.3 `production-security-checklist.md`

路径：

```txt
docs/deployment/production-security-checklist.md
````

```md
# Production Security Checklist

## 必须修改

- [ ] security.internalAgentToken
- [ ] security.jwtSecret
- [ ] external.postgres.password
- [ ] external.redis.password
- [ ] external.clickhouse.password
- [ ] external.minio.accessKey
- [ ] external.minio.secretKey

## Kubernetes

- [ ] 开启 NetworkPolicy
- [ ] 开启 Ingress TLS
- [ ] 使用私有镜像仓库
- [ ] 禁止使用 latest tag
- [ ] 限制 Pod resource requests / limits
- [ ] 使用独立 namespace
- [ ] 使用最小权限 ServiceAccount

## AegisOps

- [ ] internal agent token required
- [ ] tenant required
- [ ] public API rate limit enabled
- [ ] internal agent rate limit enabled
- [ ] plugin tool policy 默认关闭或显式 allow
- [ ] Runner live 执行默认关闭
- [ ] Ansible / SSH adapter 默认关闭

## 备份

- [ ] PostgreSQL backup
- [ ] ClickHouse backup
- [ ] MinIO backup
- [ ] Helm values secret backup
```

---

# 11. CI 验证建议

在 `.github/workflows/ci.yml` 增加一个 job：

```yaml
helm-package:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v4
    - uses: azure/setup-helm@v4
      with:
        version: v3.15.4
    - uses: actions/setup-python@v5
      with:
        python-version: "3.12"
    - run: pip install pyyaml
    - run: pytest -q deploy/helm/aegisops/tests tests/test_phase8_2_deploy_scripts.py
    - run: helm lint deploy/helm/aegisops
    - run: |
        helm template aegisops deploy/helm/aegisops \
          -f deploy/helm/aegisops/values-private.yaml \
          --set security.internalAgentToken=test-token \
          --set security.jwtSecret=test-jwt \
          --set external.postgres.password=test-pg
```

---

# 12. 验证命令

## Helm

```bash
pip install pyyaml
pytest -q deploy/helm/aegisops/tests
helm lint deploy/helm/aegisops
helm template aegisops deploy/helm/aegisops \
  -f deploy/helm/aegisops/values-private.yaml \
  --set security.internalAgentToken=test-token \
  --set security.jwtSecret=test-jwt \
  --set external.postgres.password=test-pg
```

---

## Docker

```bash
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/build-images.sh
docker images | grep aiops
```

---

## Offline

```bash
VERSION=0.1.0 REGISTRY=aegisops scripts/deploy/package-offline.sh
tar -tzf dist/offline/aegisops-0.1.0.tar.gz | head
```

---

# 13. 验收标准

```txt
1. Java server image 可构建。
2. Java worker image 可构建。
3. Java runner image 可构建。
4. Python agent image 可构建。
5. Helm chart 可 lint。
6. Helm chart 可 template。
7. values.yaml 不包含真实 secret。
8. values-private.yaml 可覆盖私有化部署参数。
9. values-offline.yaml 使用本地 registry。
10. offline package 包含 images tar。
11. offline package 包含 Helm chart tgz。
12. offline package 包含 values-offline.yaml。
13. offline package 校验脚本通过。
14. generate-secrets.sh 可生成 secret values。
15. deployment docs 完整。
16. production security checklist 完整。
17. 不新增 execution。
18. 不新增 Runner adapter。
19. 不调 Webhook / Ansible / SSH。
20. 不动态加载插件代码。
```

---

# 14. 建议提交信息

```txt
feat(deploy): add private helm and offline package
```

---

# 15. 下一步 Phase8.3

Phase8.2 完成后建议进入：

```txt
Phase8.3 Observability & Production Operations
```

重点：

```txt
1. Prometheus metrics
2. structured logs
3. trace id propagation
4. readiness/liveness hardening
5. dashboard json
6. alert rules
7. runbook for platform operation
```
