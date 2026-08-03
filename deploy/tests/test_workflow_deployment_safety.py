from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
from pathlib import Path

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
COMPONENT_WORKFLOW = ROOT / ".github/workflows/deploy-component.yml"
RELEASE_WORKFLOW = ROOT / ".github/workflows/release-verify.yml"
PROMOTE_SCRIPT = ROOT / "deploy/scripts/promote-deployment-candidate.sh"
DEPLOY_APP_SCRIPT = ROOT / "deploy/scripts/deploy-app.sh"
SCP_ACTION = "appleboy/scp-action@ff85246acaad7bdce478db94a363cd2bf7c90345"
SSH_ACTION = "appleboy/ssh-action@823bd89e131d8d508129f9443cad5855e9ba96f0"
PRODUCTION_HOST_FINGERPRINT = (
    "SHA256:t42JX0HGVD6m/KDVHYjoudZQGMv+8B4hkrfGJdZ8axY"
)
PRODUCTION_GO_SSH_FINGERPRINT = (
    "SHA256:TtfGZDilKBdm05HX3b1i4yqG/mG0Ooas43ZBrFKyj2w"
)


def test_manual_production_workflows_require_mvp_sha_with_successful_ci():
    component = yaml.safe_load(COMPONENT_WORKFLOW.read_text(encoding="utf-8"))
    release = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    component_text = COMPONENT_WORKFLOW.read_text(encoding="utf-8")
    release_text = RELEASE_WORKFLOW.read_text(encoding="utf-8")

    assert component["permissions"]["actions"] == "read"
    assert release["permissions"]["actions"] == "read"
    assert component["jobs"]["deploy"]["needs"] == "authorize"
    assert "environment" not in component["jobs"]["authorize"]

    for workflow_text in (component_text, release_text):
        assert "refs/heads/mvp" in workflow_text
        assert "actions/workflows/ci.yml/runs" in workflow_text
        assert "head_sha=" in workflow_text
        assert "No successful CI run found" in workflow_text


def test_production_remote_actions_are_immutable_and_pin_host_identity():
    for workflow_path in (COMPONENT_WORKFLOW, RELEASE_WORKFLOW):
        workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
        remote_steps = [
            step
            for step in workflow["jobs"]["deploy"]["steps"]
            if str(step.get("uses", "")).startswith("appleboy/")
        ]

        assert {step["uses"] for step in remote_steps} == {
            SCP_ACTION,
            SSH_ACTION,
        }
        for step in remote_steps:
            assert step["with"]["fingerprint"] == PRODUCTION_GO_SSH_FINGERPRINT


def test_production_workflows_pin_ed25519_identity_before_go_ssh_handshake():
    for workflow_path in (COMPONENT_WORKFLOW, RELEASE_WORKFLOW):
        workflow_text = workflow_path.read_text(encoding="utf-8")
        assert "ssh-keyscan" in workflow_text
        assert "-t ed25519" in workflow_text
        assert "ssh-keygen -lf" in workflow_text
        assert PRODUCTION_HOST_FINGERPRINT in workflow_text

        workflow = yaml.safe_load(workflow_text)
        remote_steps = [
            step
            for step in workflow["jobs"]["deploy"]["steps"]
            if str(step.get("uses", "")).startswith("appleboy/")
        ]
        for step in remote_steps:
            assert step["with"]["fingerprint"] == PRODUCTION_GO_SSH_FINGERPRINT


def test_core_deploy_uses_smoke_verified_registry_digests():
    workflow = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    runtime = workflow["jobs"]["runtime-smoke"]
    assert runtime["outputs"] == {
        "app_image": "${{ steps.publish.outputs.app_image }}",
        "agent_image": "${{ steps.publish.outputs.agent_image }}",
        "runner_image": "${{ steps.publish.outputs.runner_image }}",
    }

    publish = next(step for step in runtime["steps"] if step.get("id") == "publish")
    publish_script = publish["run"]
    assert "push_with_digest" in publish_script
    assert "docker push" in publish_script
    assert '"${IMAGE_PREFIX}@${digest}"' in publish_script
    assert "docker buildx imagetools inspect" in publish_script
    for component in ("app", "agent", "runner"):
        assert f'printf \'{component}_image=%s@%s\\n\'' in publish_script

    deploy = workflow["jobs"]["deploy"]
    remote = next(
        step for step in deploy["steps"] if step.get("uses") == SSH_ACTION
    )
    assert remote["env"]["AIOPS_APP_IMAGE"] == (
        "${{ needs.runtime-smoke.outputs.app_image }}"
    )
    assert remote["env"]["AIOPS_AGENT_IMAGE"] == (
        "${{ needs.runtime-smoke.outputs.agent_image }}"
    )
    assert remote["env"]["AIOPS_RUNNER_IMAGE"] == (
        "${{ needs.runtime-smoke.outputs.runner_image }}"
    )
    assert "AIOPS_APP_IMAGE,AIOPS_AGENT_IMAGE,AIOPS_RUNNER_IMAGE" in remote["with"][
        "envs"
    ]
    assert "IMAGE_PREFIX" not in remote["with"]["envs"]
    assert "IMAGE_TAG" not in remote["with"]["envs"]

    deploy_script = DEPLOY_APP_SCRIPT.read_text(encoding="utf-8")
    assert "validate_digest_image" in deploy_script
    assert 'export AIOPS_APP_IMAGE="${IMAGE_PREFIX}:${IMAGE_TAG}-app"' not in deploy_script


def test_release_workflow_run_requires_internal_mvp_push_before_checkout():
    workflow = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    steps = workflow["jobs"]["preflight"]["steps"]
    authorize_index = next(
        index
        for index, step in enumerate(steps)
        if step.get("name") == "Authorize release ref and CI"
    )
    checkout_index = next(
        index for index, step in enumerate(steps) if step.get("name") == "Checkout"
    )
    authorize = steps[authorize_index]
    script = authorize["run"]

    assert authorize_index < checkout_index
    assert authorize["env"]["WORKFLOW_HEAD_REPOSITORY"] == (
        "${{ github.event.workflow_run.head_repository.full_name }}"
    )
    assert authorize["env"]["WORKFLOW_EVENT"] == (
        "${{ github.event.workflow_run.event }}"
    )
    assert authorize["env"]["WORKFLOW_HEAD_BRANCH"] == (
        "${{ github.event.workflow_run.head_branch }}"
    )
    assert authorize["env"]["WORKFLOW_CONCLUSION"] == (
        "${{ github.event.workflow_run.conclusion }}"
    )
    assert '"$WORKFLOW_HEAD_REPOSITORY" != "$GITHUB_REPOSITORY"' in script
    assert '"$WORKFLOW_EVENT" != "push"' in script
    assert '"$WORKFLOW_HEAD_BRANCH" != "mvp"' in script
    assert '"$WORKFLOW_CONCLUSION" != "success"' in script

    for workflow_path in (COMPONENT_WORKFLOW, RELEASE_WORKFLOW):
        workflow_text = workflow_path.read_text(encoding="utf-8")
        assert '-f branch=mvp' in workflow_text
        assert '-f event=push' in workflow_text


def test_production_workflows_upload_only_to_staging_then_promote():
    component = yaml.safe_load(COMPONENT_WORKFLOW.read_text(encoding="utf-8"))
    release = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))

    for workflow, deploy_step_name in (
        (component, "Deploy staged Zabbix candidate"),
        (release, "Apply staged immutable release"),
    ):
        deploy_job = workflow["jobs"]["deploy"]
        scp_step = next(
            step for step in deploy_job["steps"] if step.get("uses") == SCP_ACTION
        )
        assert "/deploy/staging/" in scp_step["with"]["target"]
        assert "deployment-manifest.sha256" in scp_step["with"]["source"]
        assert "promote-deployment-candidate.sh" in scp_step["with"]["source"]

        deploy_step = next(
            step for step in deploy_job["steps"] if step.get("name") == deploy_step_name
        )
        script = deploy_step["with"]["script"]
        checksum_index = script.index("sha256sum -c deployment-manifest.sha256")
        network_index = script.index('bash "$NETWORK_SCRIPT"')
        recovery_index = script.index("--recover-only")
        promote_index = script.rindex('bash "$PROMOTE_SCRIPT"')
        mirror_index = script.index("configure-docker-mirror.sh")
        assert checksum_index < recovery_index < network_index < promote_index < mirror_index


def test_core_candidate_manifest_includes_every_runtime_migration_file():
    workflow = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))
    manifest_step = next(
        step
        for step in workflow["jobs"]["deploy"]["steps"]
        if step.get("name") == "Generate deployment manifest"
    )

    for migration_file in sorted((ROOT / "deploy/init").iterdir()):
        if migration_file.is_file():
            relative_path = migration_file.relative_to(ROOT).as_posix()
            assert relative_path in manifest_step["run"]


def test_production_workflows_recover_interrupted_promotion_before_active_reads():
    component = yaml.safe_load(COMPONENT_WORKFLOW.read_text(encoding="utf-8"))
    release = yaml.safe_load(RELEASE_WORKFLOW.read_text(encoding="utf-8"))

    for workflow, deploy_step_name in (
        (component, "Deploy staged Zabbix candidate"),
        (release, "Apply staged immutable release"),
    ):
        deploy_step = next(
            step
            for step in workflow["jobs"]["deploy"]["steps"]
            if step.get("name") == deploy_step_name
        )
        script = deploy_step["with"]["script"]
        recovery_index = script.index("--recover-only")
        network_index = script.index('bash "$NETWORK_SCRIPT"')
        promotion_index = script.rindex('bash "$PROMOTE_SCRIPT"')

        assert recovery_index < network_index < promotion_index

    component_script = next(
        step
        for step in component["jobs"]["deploy"]["steps"]
        if step.get("name") == "Deploy staged Zabbix candidate"
    )["with"]["script"]
    assert component_script.index("--recover-only") < component_script.index(
        'COMPOSE_FILE="$APP_DIR/deploy/docker-compose.zabbix.yml"'
    )


def test_candidate_promotion_is_transactional_and_preserves_backup(tmp_path: Path):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")
    assert PROMOTE_SCRIPT.is_file()

    active = tmp_path / "active"
    candidate = tmp_path / "candidate"
    backup = active / "deploy/backups/test-promotion"
    relative_files = [
        "deploy/docker-compose.core.yml",
        "deploy/scripts/deploy-app.sh",
    ]
    for relative_path in relative_files:
        (active / relative_path).parent.mkdir(parents=True, exist_ok=True)
        (candidate / relative_path).parent.mkdir(parents=True, exist_ok=True)
        (active / relative_path).write_text(f"old:{relative_path}\n", encoding="utf-8")
        (candidate / relative_path).write_text(f"new:{relative_path}\n", encoding="utf-8")

    manifest = candidate / "deployment-manifest.sha256"
    manifest.write_text(
        "".join(
            f"{hashlib.sha256((candidate / path).read_bytes()).hexdigest()}  {path}\n"
            for path in relative_files
        ),
        encoding="utf-8",
    )

    subprocess.run(
        [
            bash,
            str(PROMOTE_SCRIPT),
            "--candidate-root",
            str(candidate),
            "--active-root",
            str(active),
            "--manifest",
            str(manifest),
            "--backup-dir",
            str(backup),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
    )

    for relative_path in relative_files:
        assert (active / relative_path).read_text(encoding="utf-8") == (
            f"new:{relative_path}\n"
        )
        assert (backup / relative_path).read_text(encoding="utf-8") == (
            f"old:{relative_path}\n"
        )
    assert (backup / "PROMOTION_COMPLETE").is_file()


def test_candidate_promotion_rejects_tampering_before_active_write(tmp_path: Path):
    bash = shutil.which("bash")
    if bash is None:
        pytest.skip("bash is required")
    assert PROMOTE_SCRIPT.is_file()

    active = tmp_path / "active"
    candidate = tmp_path / "candidate"
    backup = active / "deploy/backups/test-tamper"
    relative_path = "deploy/docker-compose.core.yml"
    (active / relative_path).parent.mkdir(parents=True, exist_ok=True)
    (candidate / relative_path).parent.mkdir(parents=True, exist_ok=True)
    (active / relative_path).write_text("old\n", encoding="utf-8")
    candidate_file = candidate / relative_path
    candidate_file.write_text("candidate\n", encoding="utf-8")
    digest = hashlib.sha256(candidate_file.read_bytes()).hexdigest()
    manifest = candidate / "deployment-manifest.sha256"
    manifest.write_text(f"{digest}  {relative_path}\n", encoding="utf-8")
    candidate_file.write_text("tampered\n", encoding="utf-8")

    result = subprocess.run(
        [
            bash,
            str(PROMOTE_SCRIPT),
            "--candidate-root",
            str(candidate),
            "--active-root",
            str(active),
            "--manifest",
            str(manifest),
            "--backup-dir",
            str(backup),
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
        check=False,
    )

    assert result.returncode != 0
    assert "FAILED" in f"{result.stdout}\n{result.stderr}"
    assert (active / relative_path).read_text(encoding="utf-8") == "old\n"
    assert not backup.exists()


def test_candidate_promotion_recovers_after_forced_kill(tmp_path: Path):
    bash = shutil.which("bash")
    real_mv = shutil.which("mv")
    if bash is None or real_mv is None:
        pytest.skip("bash and mv are required")

    active = tmp_path / "active"
    candidate = tmp_path / "candidate"
    backup = active / "deploy/backups/test-killed-promotion"
    relative_files = [
        "deploy/docker-compose.core.yml",
        "deploy/scripts/deploy-app.sh",
    ]
    for relative_path in relative_files:
        (active / relative_path).parent.mkdir(parents=True, exist_ok=True)
        (candidate / relative_path).parent.mkdir(parents=True, exist_ok=True)
        (active / relative_path).write_text(f"old:{relative_path}\n", encoding="utf-8")
        (candidate / relative_path).write_text(f"new:{relative_path}\n", encoding="utf-8")

    manifest = candidate / "deployment-manifest.sha256"
    manifest.write_text(
        "".join(
            f"{hashlib.sha256((candidate / path).read_bytes()).hexdigest()}  {path}\n"
            for path in relative_files
        ),
        encoding="utf-8",
    )

    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    fake_mv = fake_bin / "mv"
    fake_mv.write_text(
        """#!/usr/bin/env bash
set -Eeuo pipefail
"$REAL_MV" "$@"
destination="${!#}"
case "$destination" in
  "$TEST_ACTIVE_ROOT"/deploy/backups/*) ;;
  "$TEST_ACTIVE_ROOT"/deploy/*)
    count=0
    if [ -f "$MOVE_COUNTER" ]; then
      count="$(cat "$MOVE_COUNTER")"
    fi
    count="$((count + 1))"
    printf '%s\n' "$count" > "$MOVE_COUNTER"
    if [ "$count" -eq 1 ]; then
      kill -KILL "$PPID"
    fi
    ;;
esac
""",
        encoding="utf-8",
    )
    fake_mv.chmod(0o755)

    killed = subprocess.run(
        [
            bash,
            str(PROMOTE_SCRIPT),
            "--candidate-root",
            str(candidate),
            "--active-root",
            str(active),
            "--manifest",
            str(manifest),
            "--backup-dir",
            str(backup),
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "REAL_MV": real_mv,
            "TEST_ACTIVE_ROOT": str(active),
            "MOVE_COUNTER": str(tmp_path / "move-counter"),
        },
        check=False,
    )

    assert killed.returncode < 0
    assert (active / "deploy/backups/.promotion-in-progress").is_file()
    assert {
        (active / path).read_text(encoding="utf-8") for path in relative_files
    } == {
        f"new:{relative_files[0]}\n",
        f"old:{relative_files[1]}\n",
    }

    recovered = subprocess.run(
        [
            bash,
            str(PROMOTE_SCRIPT),
            "--active-root",
            str(active),
            "--recover-only",
        ],
        cwd=ROOT,
        capture_output=True,
        text=True,
        env=os.environ.copy(),
        check=False,
    )

    assert recovered.returncode == 0, recovered.stderr
    for relative_path in relative_files:
        assert (active / relative_path).read_text(encoding="utf-8") == (
            f"old:{relative_path}\n"
        )
    assert not (active / "deploy/backups/.promotion-in-progress").exists()
    assert (backup / "PROMOTION_ROLLED_BACK").is_file()


def test_production_promotion_requires_kernel_file_lock():
    promotion = PROMOTE_SCRIPT.read_text(encoding="utf-8")
    assert "flock -n 9" in promotion
    assert 'PROMOTION_REQUIRE_FLOCK:-0' in promotion

    for workflow_path in (COMPONENT_WORKFLOW, RELEASE_WORKFLOW):
        workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
        deploy_step = next(
            step
            for step in workflow["jobs"]["deploy"]["steps"]
            if "script" in step.get("with", {})
        )
        script = deploy_step["with"]["script"]
        assert script.count("PROMOTION_REQUIRE_FLOCK=1") == 2
