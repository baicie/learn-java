from __future__ import annotations

import json
import os
import shutil
import stat
import subprocess
import tarfile
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]


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


def test_generate_secrets_includes_random_zabbix_webhook_signing_secret(tmp_path):
    if os.name == "nt":
        git = shutil.which("git")
        git_root = Path(git).resolve().parents[1] if git else None
        bash = git_root / "bin" / "bash.exe" if git_root else None
    else:
        found = shutil.which("bash")
        bash = Path(found) if found else None

    if bash is None or not bash.exists():
        pytest.skip("bash is required to exercise generate-secrets.sh")

    output = tmp_path / "generated-secrets.values.yaml"
    subprocess.run(
        [
            str(bash),
            (ROOT / "scripts/deploy/generate-secrets.sh").as_posix(),
            output.as_posix(),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    values = yaml.safe_load(output.read_text(encoding="utf-8"))
    signing_secret = values["security"]["zabbixWebhookSigningSecret"]
    assert signing_secret
    assert not signing_secret.startswith("CHANGE_ME_")
    assert signing_secret not in {
        values["security"]["diagnosisGrantSecret"],
        values["security"]["jwtSecret"],
    }
    service_clients = values["security"]["serviceAuth"]["clients"]
    assert service_clients["server"]["clientSecret"]
    assert service_clients["worker"]["clientSecret"]
    assert service_clients["agent"]["clientSecret"]


def test_generate_secrets_default_output_is_outside_chart():
    script = (ROOT / "scripts/deploy/generate-secrets.sh").read_text(encoding="utf-8")

    assert 'OUT="${1:-deploy/generated-secrets.values.yaml}"' in script


def test_generate_secrets_replaces_output_atomically_with_private_permissions(tmp_path):
    found = shutil.which("bash")
    if found is None:
        pytest.skip("bash is required to exercise generate-secrets.sh")

    output = tmp_path / "generated-secrets.values.yaml"
    output.write_text("old-content\n", encoding="utf-8")
    original_inode = output.stat().st_ino

    subprocess.run(
        [found, str(ROOT / "scripts/deploy/generate-secrets.sh"), str(output)],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )

    assert output.stat().st_ino != original_inode
    assert stat.S_IMODE(output.stat().st_mode) == 0o600
    assert not list(tmp_path.glob("generated-secrets.values.yaml.tmp.*"))

    script = (ROOT / "scripts/deploy/generate-secrets.sh").read_text(encoding="utf-8")
    assert "umask 077" in script
    assert 'cat > "$TEMP_FILE"' in script
    assert 'chmod 0600 "$TEMP_FILE"' in script
    assert 'mv -f "$TEMP_FILE" "$OUT"' in script
    assert 'cat > "$OUT"' not in script


@pytest.mark.parametrize(
    "relative_path",
    [
        "deploy/generated-secrets.values.yaml",
        "deploy/helm/aegisops/generated-secrets.values.yaml",
    ],
)
def test_generated_secret_values_are_ignored_by_git(relative_path):
    subprocess.run(
        ["git", "check-ignore", "--quiet", relative_path],
        cwd=ROOT,
        check=True,
    )


def test_helm_package_excludes_generated_secret_values(tmp_path):
    helm = shutil.which("helm")
    if helm is None:
        pytest.skip("helm is required to exercise chart packaging")

    chart = tmp_path / "aegisops"
    shutil.copytree(ROOT / "deploy/helm/aegisops", chart)
    generated_values = chart / "generated-secrets.values.yaml"
    generated_values.write_text(
        'security:\n  diagnosisGrantSecret: "must-not-enter-package"\n',
        encoding="utf-8",
    )

    subprocess.run(
        [helm, "package", str(chart), "--destination", str(tmp_path)],
        check=True,
        capture_output=True,
        text=True,
    )
    packages = list(tmp_path.glob("aegisops-*.tgz"))
    assert len(packages) == 1

    with tarfile.open(packages[0], mode="r:gz") as archive:
        packaged_files = archive.getnames()

    assert not any(
        Path(name).name == generated_values.name for name in packaged_files
    )


def test_offline_image_list_contains_required_images():
    image_list = ROOT / "deploy/offline/images.txt"
    assert image_list.exists()

    text = image_list.read_text(encoding="utf-8")
    assert "aegisops/aiops-server:0.1.0" in text
    assert "aegisops/aiops-worker:0.1.0" in text
    assert "aegisops/aiops-runner:0.1.0" in text
    assert "aegisops/aiops-agent:0.1.0" in text


def test_java_dockerfile_declares_app_module_in_build_stage():
    dockerfile = ROOT / "deploy/docker/java-app.Dockerfile"
    text = dockerfile.read_text(encoding="utf-8")

    assert "ARG APP_MODULE=apps/aiops-server" in text
    assert "FROM ${MAVEN_IMAGE} AS build" in text
    assert "ARG APP_MODULE" in text
    assert 'mvn -pl "${APP_MODULE}"' in text


def test_build_images_passes_app_ports():
    # server 用专用 Dockerfile（含 portal-build 阶段）
    # runner  用专用 Dockerfile（要装 ansible / sshpass / tini）
    # worker  走 deploy/docker/java-app.Dockerfile + ARG 通用模板
    script = ROOT / "scripts/deploy/build-images.sh"
    text = script.read_text(encoding="utf-8")

    assert "apps/aiops-server/Dockerfile" in text
    assert '--build-arg BUILD_VERSION="${VERSION}"' in text
    assert "apps/aiops-runner/Dockerfile" in text
    assert "--build-arg APP_PORT=8081" in text  # worker 走通用模板，必须显式传 port


def test_server_image_exposes_build_version_to_portal():
    dockerfile = ROOT / "apps/aiops-server/Dockerfile"
    text = dockerfile.read_text(encoding="utf-8")

    assert "ARG BUILD_VERSION=development" in text
    assert "ENV VITE_BUILD_VERSION=${BUILD_VERSION}" in text


def test_runner_image_retries_apt_over_https():
    dockerfile = ROOT / "apps/aiops-runner/Dockerfile"
    text = dockerfile.read_text(encoding="utf-8")

    assert "sed -i 's|http://|https://|g' /etc/apt/sources.list" in text
    assert text.count("Acquire::Retries=5") == 2


def test_worker_uses_compose_redis_service():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    worker = compose["services"]["aiops-worker"]

    assert worker["environment"]["SPRING_DATA_REDIS_HOST"] == "redis"
    assert worker["environment"]["SPRING_DATA_REDIS_PORT"] == 6379
    assert worker["depends_on"]["redis"]["condition"] == "service_healthy"


def test_worker_uses_agent_for_ai_generation():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    worker = compose["services"]["aiops-worker"]

    assert worker["environment"]["AIOPS_AGENT_BASE_URL"] == "http://aiops-agent:9008"
    assert worker["environment"]["AIOPS_AGENT_OAUTH2_TOKEN_URI"] == (
        "${AIOPS_SERVICE_AUTH_TOKEN_URI:?AIOPS_SERVICE_AUTH_TOKEN_URI is required}"
    )
    assert worker["environment"]["AIOPS_AGENT_OAUTH2_CLIENT_ID"] == "aiops-worker"
    assert worker["environment"]["AIOPS_AGENT_OAUTH2_CLIENT_SECRET"] == (
        "${AIOPS_WORKER_OAUTH2_CLIENT_SECRET:?"
        "AIOPS_WORKER_OAUTH2_CLIENT_SECRET is required}"
    )
    assert set(worker["environment"]["AIOPS_AGENT_OAUTH2_SCOPE"].split()) == {
        "agent:diagnose",
        "agent:work-record",
    }
    assert worker["environment"]["AIOPS_INTERNAL_AGENT_JWT_ISSUER_URI"] == (
        "${AIOPS_SERVICE_AUTH_ISSUER_URI:?AIOPS_SERVICE_AUTH_ISSUER_URI is required}"
    )
    assert worker["environment"]["AIOPS_INTERNAL_AGENT_JWT_JWK_SET_URI"] == (
        "${AIOPS_SERVICE_AUTH_JWK_SET_URI:?"
        "AIOPS_SERVICE_AUTH_JWK_SET_URI is required}"
    )
    assert worker["environment"]["AIOPS_DIAGNOSIS_GRANT_SECRET"] == (
        "${AIOPS_DIAGNOSIS_GRANT_SECRET:?AIOPS_DIAGNOSIS_GRANT_SECRET is required}"
    )
    assert worker["depends_on"]["aiops-agent"]["condition"] == "service_healthy"


def test_service_authentication_is_oauth2_only_and_local_compose_bootstraps_keycloak():
    forbidden_names = {
        "AIOPS_JAVA_TO_AGENT_TOKEN",
        "AIOPS_AGENT_TO_JAVA_TOKEN",
        "AIOPS_AGENT_INTERNAL_TOKEN",
        "AIOPS_INTERNAL_AGENT_TOKEN",
        "AIOPS_AGENT_INTERNAL_AGENT_TOKEN",
        "AIOPS_AGENT_OUTBOUND_STATIC_TOKEN",
        "AIOPS_DIAGNOSIS_GRANT_REQUIRED",
        "AIOPS_AGENT_DIAGNOSIS_GRANT_REQUIRED",
    }

    for relative_path in ("deploy/docker-compose.app.yml", "infra/docker-compose.yml"):
        compose = yaml.safe_load((ROOT / relative_path).read_text(encoding="utf-8"))
        for service in compose["services"].values():
            assert forbidden_names.isdisjoint(service.get("environment", {}))

    local_compose = yaml.safe_load(
        (ROOT / "infra/docker-compose.yml").read_text(encoding="utf-8")
    )
    assert "keycloak" in local_compose["services"]
    assert local_compose["services"]["keycloak"]["command"] == [
        "start-dev",
        "--import-realm",
    ]


def test_production_compose_marks_the_rollback_compatible_auth_contract():
    compose = yaml.safe_load(
        (ROOT / "deploy/docker-compose.app.yml").read_text(encoding="utf-8")
    )

    for service_name in ("aiops-server", "aiops-worker", "aiops-agent"):
        assert (
            compose["services"][service_name]["environment"][
                "AIOPS_SERVICE_AUTH_CONTRACT"
            ]
            == "oauth2-v1"
        )


def test_release_pipeline_uploads_a_candidate_without_overwriting_the_active_descriptor():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )

    prepare = workflow.index("name: Prepare candidate deployment descriptor")
    upload = workflow.index("name: Copy candidate and deployment tooling")
    configure = workflow.index("name: Configure Tencent Cloud Docker mirror")
    upload_step = workflow[upload:configure]

    assert prepare < upload
    assert "name: Snapshot current deployment descriptor" not in workflow
    assert (
        "cp deploy/docker-compose.app.yml "
        "deploy/docker-compose.app.candidate.yml" in workflow
    )
    assert "deploy/docker-compose.app.candidate.yml" in upload_step
    assert 'source: "deploy/docker-compose.app.yml,' not in upload_step
    deploy_job = workflow[workflow.index("  deploy:") :]
    assert "group: aegisops-prod-deploy" in deploy_job
    assert "cancel-in-progress: false" in deploy_job


def test_deployment_rollback_uses_only_a_previous_oauth2_descriptor():
    script = (ROOT / "deploy/scripts/deploy-app.sh").read_text(encoding="utf-8")

    assert 'ACTIVE_COMPOSE_FILE="${ACTIVE_COMPOSE_FILE:-' in script
    assert 'CANDIDATE_COMPOSE_FILE="${CANDIDATE_COMPOSE_FILE:-' in script
    assert 'PREVIOUS_COMPOSE_FILE="${PREVIOUS_COMPOSE_FILE:-' in script
    assert 'SELECTED_COMPOSE_FILE="$CANDIDATE_COMPOSE_FILE"' in script
    assert 'SELECTED_COMPOSE_FILE="$PREVIOUS_COMPOSE_FILE"' in script
    assert "snapshot_active_descriptor" in script
    assert "promote_candidate_descriptor" in script
    assert "container_env_value" in script
    assert (
        "SERVICE_AUTH_CONTAINERS=(aegisops-server aegisops-agent aegisops-worker)"
        in script
    )
    assert "previous_service_auth_is_compatible" in script
    assert 'for container_name in "${SERVICE_AUTH_CONTAINERS[@]}"; do' in script
    assert (
        'contract="$(container_env_value "$container_name" '
        'AIOPS_SERVICE_AUTH_CONTRACT)"' in script
    )
    assert '[ "$contract" != "oauth2-v1" ]' in script
    assert "AIOPS_JAVA_TO_AGENT_TOKEN" not in script
    assert "AIOPS_AGENT_TO_JAVA_TOKEN" not in script


def test_production_deploy_runs_non_destructive_service_auth_probe_before_success():
    deploy_script = (ROOT / "deploy/scripts/deploy-app.sh").read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )
    probe = ROOT / "deploy/scripts/verify-service-auth-oauth.py"
    redactor = ROOT / "deploy/scripts/redact-runtime-output.py"

    assert probe.exists()
    assert redactor.exists()
    assert 'DEPLOY_STAGE="service-auth-probe"' in deploy_script
    assert 'python3 "$SERVICE_AUTH_PROBE_SCRIPT"' in deploy_script
    assert "RUNTIME_REDACTOR_SCRIPT" in deploy_script
    assert "redact_runtime_output" in deploy_script
    assert deploy_script.index('DEPLOY_STAGE="service-auth-probe"') < deploy_script.index(
        'DEPLOY_STAGE="complete"'
    )
    assert "deploy/scripts/verify-service-auth-oauth.py" in workflow
    assert "deploy/scripts/redact-runtime-output.py" in workflow


def test_local_keycloak_realm_has_separate_clients_audiences_and_scopes():
    realm = json.loads(
        (ROOT / "infra/keycloak/aegisops-realm.json").read_text(encoding="utf-8")
    )
    clients = {client["clientId"]: client for client in realm["clients"]}

    assert realm["sslRequired"] == "none"
    assert set(clients) == {"aiops-server", "aiops-worker", "aiops-agent"}
    assert len({client["secret"] for client in clients.values()}) == 3
    assert set(clients["aiops-server"]["optionalClientScopes"]) == {
        "agent:diagnose",
        "agent:resume",
        "agent:work-record",
    }
    assert set(clients["aiops-worker"]["optionalClientScopes"]) == {
        "agent:diagnose",
        "agent:work-record",
    }
    assert set(clients["aiops-agent"]["optionalClientScopes"]) == {
        "evidence:read",
        "cases:read",
        "plugin:authorize",
        "memory:read",
        "memory:write",
        "checkpoint:read",
        "checkpoint:write",
    }
    assert (
        clients["aiops-server"]["protocolMappers"][0]["config"][
            "included.custom.audience"
        ]
        == "aiops-agent-api"
    )
    assert (
        clients["aiops-agent"]["protocolMappers"][0]["config"][
            "included.custom.audience"
        ]
        == "aegisops-internal-api"
    )


def test_runtime_keycloak_healthcheck_uses_bash_for_dev_tcp():
    compose = yaml.safe_load(
        (ROOT / "deploy/docker-compose.idp.yml").read_text(encoding="utf-8")
    )

    healthcheck = compose["services"]["keycloak"]["healthcheck"]["test"]
    assert healthcheck[:3] == ["CMD", "bash", "-ec"]
    assert "/dev/tcp/127.0.0.1/9000" in healthcheck[3]


def test_zabbix_webhook_signing_secret_is_required_by_compose_and_has_no_public_default():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    server_environment = compose["services"]["aiops-server"]["environment"]

    assert server_environment["AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN"] == (
        "${AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN:?"
        "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN is required}"
    )

    application = (
        ROOT / "apps/aiops-server/src/main/resources/application.yml"
    ).read_text(encoding="utf-8")
    assert "dev-zabbix-webhook-token" not in application


def test_release_pipeline_propagates_required_zabbix_webhook_signing_secret():
    workflow = (ROOT / ".github/workflows/release-verify.yml").read_text(
        encoding="utf-8"
    )
    preflight = (ROOT / "scripts/ci/release-preflight.sh").read_text(encoding="utf-8")

    assert "name: Generate masked runtime credentials" in workflow
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN" in workflow
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN: runtime-smoke-only" not in workflow
    assert (
        "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN: "
        "${{ secrets.AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN }}"
    ) in workflow
    assert (
        "envs: IMAGE_PREFIX,IMAGE_TAG,AIOPS_SERVICE_AUTH_ISSUER_URI,"
        "AIOPS_SERVICE_AUTH_JWK_SET_URI,AIOPS_SERVICE_AUTH_TOKEN_URI,"
        "AIOPS_SERVER_OAUTH2_CLIENT_SECRET,AIOPS_WORKER_OAUTH2_CLIENT_SECRET,"
        "AIOPS_AGENT_OAUTH2_CLIENT_SECRET,AIOPS_DIAGNOSIS_GRANT_SECRET,"
        "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN"
    ) in workflow
    assert "AIOPS_INTEGRATIONS_ZABBIX_WEBHOOK_TOKEN=preflight-only" in preflight


def test_compose_dify_secrets_are_only_exposed_to_agent():
    for relative_path in ("deploy/docker-compose.app.yml", "infra/docker-compose.yml"):
        compose_file = ROOT / relative_path
        compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
        services = compose["services"]

        agent_environment = services["aiops-agent"]["environment"]
        assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" in agent_environment
        assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" in agent_environment

        for service_name in ("aiops-server", "aiops-worker", "aiops-runner"):
            if service_name not in services:
                continue
            environment = services[service_name]["environment"]
            assert "AIOPS_AGENT_DIFY_WORK_RECORD_API_KEY" not in environment
            assert "AIOPS_AGENT_DIFY_USER_HMAC_SECRET" not in environment


def test_vm_compose_bounds_core_service_memory():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    expected_limits = {
        "postgres": "${AIOPS_POSTGRES_MEMORY_LIMIT:-384m}",
        "redis": "${AIOPS_REDIS_MEMORY_LIMIT:-128m}",
        "aiops-server": "${AIOPS_SERVER_MEMORY_LIMIT:-640m}",
        "aiops-agent": "${AIOPS_AGENT_MEMORY_LIMIT:-256m}",
        "aiops-worker": "${AIOPS_WORKER_MEMORY_LIMIT:-576m}",
        "aiops-runner": "${AIOPS_RUNNER_MEMORY_LIMIT:-512m}",
    }

    for service_name, expected_limit in expected_limits.items():
        assert services[service_name]["mem_limit"] == expected_limit


def test_vm_compose_uses_per_app_bounded_java_options():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    expected_heap_caps = {
        "aiops-server": "-Xmx320m",
        "aiops-worker": "-Xmx256m",
        "aiops-runner": "-Xmx192m",
    }

    for service_name, heap_cap in expected_heap_caps.items():
        java_opts = services[service_name]["environment"]["JAVA_OPTS"]
        assert heap_cap in java_opts
        assert "MaxRAMPercentage" not in java_opts
        assert "MaxMetaspaceSize" in java_opts
        assert "MaxDirectMemorySize" in java_opts


def test_vm_compose_bounds_java_database_pools():
    compose_file = ROOT / "deploy/docker-compose.app.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    for service_name in ("aiops-server", "aiops-worker", "aiops-runner"):
        environment = services[service_name]["environment"]
        assert environment["SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE"] == 6
        assert environment["SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE"] == 2


def test_optional_zabbix_compose_has_low_memory_defaults():
    compose_file = ROOT / "deploy/docker-compose.zabbix.yml"
    compose = yaml.safe_load(compose_file.read_text(encoding="utf-8"))
    services = compose["services"]

    for service_name in (
        "zabbix-postgres",
        "zabbix-server",
        "zabbix-web",
        "zabbix-agent2",
    ):
        assert "mem_limit" in services[service_name]

    assert services["zabbix-server"]["environment"]["ZBX_CACHESIZE"] == "${ZABBIX_CACHESIZE:-64M}"


def test_package_offline_uses_split_for_volume_packaging():
    script = ROOT / "scripts/deploy/package-offline.sh"
    text = script.read_text(encoding="utf-8")

    assert "split -b" in text, "缺少 split 分卷命令"
    assert "manifest.txt" in text, "缺少 manifest.txt 生成"
    assert "NUM_VOLUMES=" in text, "缺少 NUM_VOLUMES 变量记录"
    assert "merge-volumes.sh" in text, "缺少 merge-volumes.sh 脚本生成"


def test_load_offline_supports_volumes_and_single_tar():
    script = ROOT / "scripts/deploy/load-offline-images.sh"
    text = script.read_text(encoding="utf-8")

    assert "manifest.txt" in text, "load 脚本缺少 manifest 读取逻辑"
    assert "tar.vol" in text, "load 脚本缺少分卷扩展名检测"
    assert "docker load -i" in text, "load 脚本缺少 docker load"
