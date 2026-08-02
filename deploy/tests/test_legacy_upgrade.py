from __future__ import annotations

import os
import shutil
import subprocess
import time
import uuid
from pathlib import Path

import pytest


ROOT = Path(__file__).resolve().parents[2]
INSTALL = ROOT / "deploy/install.sh"
MIGRATE = ROOT / "deploy/scripts/migrate-legacy-compose.sh"
FINALIZE_SQL = ROOT / "deploy/init/004-finalize-legacy-role.sql"
EXECUTION_GRANT_MIGRATION = (
    ROOT
    / "apps/aiops-server/src/main/resources/db/migration/V0051__init_execution_grant.sql"
)


def test_legacy_upgrade_preserves_rollback_until_explicit_healthy_finalization(
    tmp_path: Path,
):
    runtime = _runtime_material(tmp_path)
    fake_bin, docker_log, psql_log = _fake_docker(tmp_path)
    environment = {
        **os.environ,
        "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
        "AIOPS_RUNTIME_DIR": str(runtime),
        "AIOPS_LEGACY_POSTGRES_CONTAINER": "legacy-postgres",
        "AIOPS_LEGACY_COMPOSE_PROJECT": "legacy-test",
        "AIOPS_TEST_DOCKER_LOG": str(docker_log),
        "AIOPS_TEST_PSQL_LOG": str(psql_log),
        "AIOPS_TEST_LEGACY_CONTAINER": "legacy-postgres",
        "AIOPS_TEST_LEGACY_PROJECT": "legacy-test",
        "AIOPS_TEST_LEGACY_VOLUME": "legacy_postgres_data",
        "AIOPS_TEST_INCLUDE_COMPONENTS": "true",
    }

    prepared = subprocess.run(
        ["bash", str(MIGRATE)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        env=environment,
    )
    assert prepared.returncode == 0, prepared.stderr

    env_text = (runtime / ".env").read_text(encoding="utf-8")
    assert "AIOPS_POSTGRES_VOLUME_NAME=legacy_postgres_data" in env_text
    assert len(list((runtime / "backups").glob("pre-mtls-*.dump"))) == 1
    assert (runtime / ".legacy-migration.env").is_file()
    assert "nologin nosuperuser" not in psql_log.read_text(encoding="utf-8").lower()

    prepare_commands = docker_log.read_text(encoding="utf-8")
    for container_id in (
        "legacy-db-id",
        "legacy-server-id",
        "legacy-worker-id",
        "legacy-agent-id",
        "legacy-runner-id",
    ):
        assert f"stop --time 30 {container_id}" in prepare_commands
        assert f"rm {container_id}" not in prepare_commands

    rejected = subprocess.run(
        ["bash", str(MIGRATE), "--finalize"],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        env={
            **environment,
            "AIOPS_TARGET_POSTGRES_CONTAINER": "new-postgres",
        },
    )
    assert rejected.returncode != 0
    assert "health confirmation is required" in rejected.stderr

    subprocess.run(
        ["bash", str(MIGRATE), "--finalize"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        env={
            **environment,
            "AIOPS_TARGET_POSTGRES_CONTAINER": "new-postgres",
            "AIOPS_TARGET_STACK_HEALTHY": "true",
        },
    )

    commands = docker_log.read_text(encoding="utf-8")
    for container_id in (
        "legacy-db-id",
        "legacy-server-id",
        "legacy-worker-id",
        "legacy-agent-id",
        "legacy-runner-id",
    ):
        assert f"rm {container_id}" in commands
    assert not (runtime / ".legacy-migration.env").exists()
    assert (runtime / "legacy-migration.finalized").is_file()
    finalized_sql = psql_log.read_text(encoding="utf-8").lower()
    assert "nologin nosuperuser" in finalized_sql
    assert "password null" in finalized_sql


def test_discovers_generated_aegisops_core_postgres_name(tmp_path: Path):
    runtime = _runtime_material(tmp_path)
    fake_bin, docker_log, psql_log = _fake_docker(tmp_path)

    prepared = subprocess.run(
        ["bash", str(MIGRATE)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_RUNTIME_DIR": str(runtime),
            "AIOPS_TEST_DOCKER_LOG": str(docker_log),
            "AIOPS_TEST_PSQL_LOG": str(psql_log),
            "AIOPS_TEST_LEGACY_CONTAINER": "aegisops-core-postgres-1",
            "AIOPS_TEST_LEGACY_PROJECT": "aegisops-core",
            "AIOPS_TEST_LEGACY_VOLUME": "aegisops-core_core_postgres_data",
            "AIOPS_TEST_DISCOVER_CORE": "true",
        },
    )
    assert prepared.returncode == 0, prepared.stderr

    env_text = (runtime / ".env").read_text(encoding="utf-8")
    assert "AIOPS_POSTGRES_VOLUME_NAME=aegisops-core_core_postgres_data" in env_text
    commands = docker_log.read_text(encoding="utf-8")
    assert "label=com.docker.compose.project=aegisops-core" in commands
    assert "label=com.docker.compose.service=postgres" in commands


def test_empty_successful_docker_inspect_output_means_no_legacy_container(
    tmp_path: Path,
):
    fake_bin = tmp_path / "empty-bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
    fake_docker.chmod(0o755)

    result = subprocess.run(
        ["bash", str(MIGRATE)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_RUNTIME_DIR": str(tmp_path / "missing-runtime"),
        },
    )

    assert result.returncode == 0


def test_existing_target_postgres_is_not_treated_as_legacy(tmp_path: Path):
    fake_bin = tmp_path / "target-bin"
    fake_bin.mkdir()
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/bin/sh
last=''
for last do :; done
case "$*" in
  *'inspect --format {{.Id}} aegisops-postgres'*) printf '%s\\n' target-db-id ;;
  *'com.docker.compose.project'*) printf '%s\\n' aegisops ;;
  *'.Config.Env'*) printf '%s\\n' 'POSTGRES_DB=aegisops' 'POSTGRES_USER=aegisops_admin' ;;
esac
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)

    result = subprocess.run(
        ["bash", str(MIGRATE)],
        cwd=ROOT,
        check=False,
        capture_output=True,
        text=True,
        env={
            **os.environ,
            "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}",
            "AIOPS_RUNTIME_DIR": str(tmp_path / "missing-runtime"),
        },
    )

    assert result.returncode == 0, result.stderr


def test_legacy_role_is_only_disabled_by_post_health_finalize_sql():
    prepare_sql = (ROOT / "deploy/init/003-migrate-legacy-owner.sql").read_text(
        encoding="utf-8"
    ).lower()
    finalize_sql = FINALIZE_SQL.read_text(encoding="utf-8").lower()

    assert "pg_namespace" in prepare_sql
    assert "pg_class" in prepare_sql
    assert "pg_proc" in prepare_sql
    assert "pg_type" in prepare_sql
    assert "information_schema" in prepare_sql
    assert "dependency.deptype = 'e'" in prepare_sql
    assert "reassign owned by" not in prepare_sql
    assert "nologin" not in prepare_sql
    assert "nosuperuser" not in prepare_sql
    assert "drop owned by" not in finalize_sql
    assert "aegisops_bootstrap_disabled" in finalize_sql
    assert "create role aegisops with nologin nosuperuser" in finalize_sql
    assert "password null" in finalize_sql


@pytest.mark.skipif(
    os.environ.get("AIOPS_RUN_DOCKER_MIGRATION_TEST") != "1",
    reason="set AIOPS_RUN_DOCKER_MIGRATION_TEST=1 for isolated Docker/PostgreSQL upgrade",
)
def test_real_postgres_volume_upgrade_is_atomic_and_removes_legacy_privilege(
    tmp_path: Path,
):
    if shutil.which("docker") is None:
        pytest.skip("docker is required")
    if subprocess.run(
        ["docker", "info"], capture_output=True, text=True, check=False
    ).returncode:
        pytest.skip("docker daemon is unavailable")

    suffix = uuid.uuid4().hex[:12]
    project = f"aegisops-migration-test-{suffix}"
    old_container = f"{project}-old-postgres"
    new_container = f"{project}-new-postgres"
    volume = f"{project}-data"
    runtime = tmp_path / "runtime"

    subprocess.run(
        [
            "bash",
            str(INSTALL),
            "--no-start",
            "--runtime-dir",
            str(runtime),
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    subprocess.run(
        ["docker", "volume", "create", volume],
        check=True,
        capture_output=True,
        text=True,
    )

    try:
        subprocess.run(
            [
                "docker",
                "run",
                "-d",
                "--name",
                old_container,
                "--label",
                f"com.docker.compose.project={project}",
                "--label",
                "com.docker.compose.service=postgres",
                "--env",
                "POSTGRES_DB=aegisops",
                "--env",
                "POSTGRES_USER=aegisops",
                "--env",
                "POSTGRES_PASSWORD=legacy-password",
                "--volume",
                f"{volume}:/var/lib/postgresql/data",
                "postgres:16-alpine",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        _wait_for_postgres(old_container, "aegisops")
        for migration in sorted(
            (ROOT / "apps/aiops-server/src/main/resources/db/migration").glob(
                "V*.sql"
            )
        ):
            if migration == EXECUTION_GRANT_MIGRATION:
                continue
            _psql_file(
                old_container,
                "aegisops",
                "legacy-password",
                migration,
            )
        _psql(
            old_container,
            "aegisops",
            "legacy-password",
            "create table migration_probe(id integer primary key, value text); "
            "insert into migration_probe values (1, 'retained'); "
            "create role legacy_membership; grant legacy_membership to aegisops;",
        )

        migration_env = {
            **os.environ,
            "AIOPS_RUNTIME_DIR": str(runtime),
            "AIOPS_LEGACY_POSTGRES_CONTAINER": old_container,
            "AIOPS_LEGACY_COMPOSE_PROJECT": project,
        }
        prepared = subprocess.run(
            ["bash", str(MIGRATE)],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
            env=migration_env,
        )
        assert prepared.returncode == 0, prepared.stderr
        old_state = subprocess.run(
            ["docker", "inspect", "--format", "{{.State.Running}}", old_container],
            check=True,
            capture_output=True,
            text=True,
        )
        assert old_state.stdout.strip() == "false"
        assert (runtime / ".legacy-migration.env").is_file()

        admin_password = (runtime / "secrets/postgres_admin_password").read_text(
            encoding="utf-8"
        ).strip()
        app_password = (runtime / "secrets/app_db_password").read_text(
            encoding="utf-8"
        ).strip()
        runner_password = (runtime / "secrets/runner_db_password").read_text(
            encoding="utf-8"
        ).strip()
        subprocess.run(
            [
                "docker",
                "run",
                "-d",
                "--name",
                new_container,
                "--label",
                f"com.docker.compose.project={project}-target",
                "--label",
                "com.docker.compose.service=postgres",
                "--env",
                "POSTGRES_DB=aegisops",
                "--env",
                "POSTGRES_USER=aegisops_admin",
                "--env",
                f"POSTGRES_PASSWORD={admin_password}",
                "--health-cmd",
                "pg_isready -U aegisops_admin -d aegisops",
                "--health-interval",
                "1s",
                "--health-timeout",
                "3s",
                "--health-retries",
                "30",
                "--volume",
                f"{volume}:/var/lib/postgresql/data",
                "postgres:16-alpine",
            ],
            check=True,
            capture_output=True,
            text=True,
        )
        _wait_for_postgres(new_container, "aegisops_admin", require_healthy=True)

        _psql_file(
            new_container,
            "aegisops_app",
            app_password,
            EXECUTION_GRANT_MIGRATION,
        )
        execution_grant_columns = _psql(
            new_container,
            "aegisops_app",
            app_password,
            "select count(*) from information_schema.columns "
            "where table_schema='public' and table_name='execution_run' "
            "and column_name in ('execution_grant','execution_snapshot_sha256',"
            "'execution_grant_expires_at');",
        )
        assert execution_grant_columns.stdout.strip() == "3"

        _psql_file(
            new_container,
            "aegisops_admin",
            admin_password,
            ROOT / "deploy/init/002-grant-runner.sql",
        )

        before_finalize = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select rolcanlogin || ',' || rolsuper from pg_roles where rolname='aegisops';",
        )
        assert before_finalize.stdout.strip() == "true,true"
        _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "grant aegisops to aegisops_runner;",
        )

        finalized = subprocess.run(
            ["bash", str(MIGRATE), "--finalize"],
            cwd=ROOT,
            check=False,
            capture_output=True,
            text=True,
            env={
                **migration_env,
                "AIOPS_TARGET_POSTGRES_CONTAINER": new_container,
                "AIOPS_TARGET_STACK_HEALTHY": "true",
            },
        )
        assert finalized.returncode == 0, finalized.stderr

        retained = _psql(
            new_container,
            "aegisops_app",
            app_password,
            "select value from migration_probe where id=1;",
        )
        assert retained.stdout.strip() == "retained"
        ownership = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select tableowner from pg_tables where tablename='migration_probe';",
        )
        assert ownership.stdout.strip() == "aegisops_app"
        remaining_legacy_relations = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select count(*) from pg_class c join pg_namespace n on n.oid=c.relnamespace "
            "join pg_roles r on r.oid=c.relowner where r.rolname='aegisops' "
            "and n.nspname <> 'information_schema' and n.nspname !~ '^pg_' "
            "and c.relkind in ('r','p','S','v','m','f','c');",
        )
        assert remaining_legacy_relations.stdout.strip() == "0"
        legacy_flags = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select rolcanlogin || ',' || rolsuper || ',' || (rolpassword is null) "
            "from pg_authid where rolname='aegisops';",
        )
        assert legacy_flags.stdout.strip() == "false,false,true"
        membership = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select count(*) from pg_auth_members m join pg_roles r on r.oid=m.member "
            "where r.rolname='aegisops';",
        )
        assert membership.stdout.strip() == "0"
        bootstrap_flags = _psql(
            new_container,
            "aegisops_admin",
            admin_password,
            "select rolcanlogin || ',' || rolsuper || ',' || (rolpassword is null) "
            "from pg_authid where rolname='aegisops_bootstrap_disabled';",
        )
        assert bootstrap_flags.stdout.strip() == "false,true,true"
        assert _psql(
            new_container,
            "aegisops",
            "legacy-password",
            "select 1;",
            check=False,
        ).returncode != 0
        assert _psql(
            new_container,
            "aegisops_runner",
            runner_password,
            "set role aegisops_bootstrap_disabled; select current_user;",
            check=False,
        ).returncode != 0
        assert _psql(
            new_container,
            "aegisops_runner",
            runner_password,
            "select count(*) from execution_run;",
        ).returncode == 0
        assert _psql(
            new_container,
            "aegisops_runner",
            runner_password,
            "update execution_run set status=status where false;",
        ).returncode == 0
        assert _psql(
            new_container,
            "aegisops_runner",
            runner_password,
            "select * from migration_probe;",
            check=False,
        ).returncode != 0
        assert len(list((runtime / "backups").glob("pre-mtls-*.dump"))) == 1
        assert (runtime / "legacy-migration.finalized").is_file()
    finally:
        for container in (new_container, old_container):
            subprocess.run(
                ["docker", "rm", "-f", container],
                check=False,
                capture_output=True,
                text=True,
            )
        subprocess.run(
            ["docker", "volume", "rm", volume],
            check=False,
            capture_output=True,
            text=True,
        )


def _runtime_material(tmp_path: Path) -> Path:
    runtime = tmp_path / "runtime"
    secrets = runtime / "secrets"
    secrets.mkdir(parents=True)
    (secrets / "postgres_admin_password").write_text(
        "admin-secret\n", encoding="utf-8"
    )
    (secrets / "app_db_password").write_text("app-secret\n", encoding="utf-8")
    (secrets / "runner_db_password").write_text(
        "runner-secret\n", encoding="utf-8"
    )
    (runtime / ".env").write_text(
        "AIOPS_POSTGRES_VOLUME_NAME=aegisops_postgres_data\n",
        encoding="utf-8",
    )
    return runtime


def _fake_docker(tmp_path: Path) -> tuple[Path, Path, Path]:
    fake_bin = tmp_path / "bin"
    fake_bin.mkdir()
    docker_log = tmp_path / "docker.log"
    psql_log = tmp_path / "psql.log"
    fake_docker = fake_bin / "docker"
    fake_docker.write_text(
        """#!/bin/sh
set -eu
printf '%s\\n' "$*" >> "$AIOPS_TEST_DOCKER_LOG"
last=''
for last do :; done

service_for_id() {
  case "$1" in
    legacy-db-id) printf '%s\\n' postgres ;;
    legacy-server-id) printf '%s\\n' aiops-server ;;
    legacy-worker-id) printf '%s\\n' aiops-worker ;;
    legacy-agent-id) printf '%s\\n' aiops-agent ;;
    legacy-runner-id) printf '%s\\n' aiops-runner ;;
    new-db-id) printf '%s\\n' postgres ;;
  esac
}

case "$*" in
  *'ps -a '*'label=com.docker.compose.project=aegisops-core'*'--format {{.Names}}'*)
    if [ "${AIOPS_TEST_DISCOVER_CORE:-}" = true ]; then
      printf '%s\\n' "$AIOPS_TEST_LEGACY_CONTAINER"
    fi
    ;;
  *'ps -aq '*'label=com.docker.compose.service=postgres'*) printf '%s\\n' legacy-db-id ;;
  *'ps -aq '*'label=com.docker.compose.service=aiops-server'*)
    if [ "${AIOPS_TEST_INCLUDE_COMPONENTS:-}" = true ]; then printf '%s\\n' legacy-server-id; fi
    ;;
  *'ps -aq '*'label=com.docker.compose.service=aiops-worker'*)
    if [ "${AIOPS_TEST_INCLUDE_COMPONENTS:-}" = true ]; then printf '%s\\n' legacy-worker-id; fi
    ;;
  *'ps -aq '*'label=com.docker.compose.service=aiops-agent'*)
    if [ "${AIOPS_TEST_INCLUDE_COMPONENTS:-}" = true ]; then printf '%s\\n' legacy-agent-id; fi
    ;;
  *'ps -aq '*'label=com.docker.compose.service=aiops-runner'*)
    if [ "${AIOPS_TEST_INCLUDE_COMPONENTS:-}" = true ]; then printf '%s\\n' legacy-runner-id; fi
    ;;
  *'inspect --format {{.Id}}'*)
    case "$last" in
      "$AIOPS_TEST_LEGACY_CONTAINER") printf '%s\\n' legacy-db-id ;;
      legacy-db-id|legacy-server-id|legacy-worker-id|legacy-agent-id|legacy-runner-id) printf '%s\\n' "$last" ;;
      new-postgres) printf '%s\\n' new-db-id ;;
    esac
    ;;
  *'.Config.Env'*)
    case "$last" in
      new-postgres) printf '%s\\n' 'POSTGRES_DB=aegisops' 'POSTGRES_USER=aegisops_admin' ;;
      *) printf '%s\\n' 'POSTGRES_DB=aegisops' 'POSTGRES_USER=aegisops' ;;
    esac
    ;;
  *'com.docker.compose.project'*)
    case "$last" in
      new-postgres) printf '%s\\n' target-test ;;
      *) printf '%s\\n' "$AIOPS_TEST_LEGACY_PROJECT" ;;
    esac
    ;;
  *'com.docker.compose.service'*)
    case "$last" in
      "$AIOPS_TEST_LEGACY_CONTAINER") printf '%s\\n' postgres ;;
      new-postgres) printf '%s\\n' postgres ;;
      *) service_for_id "$last" ;;
    esac
    ;;
  *'.Mounts'*) printf '%s\\n' "$AIOPS_TEST_LEGACY_VOLUME" ;;
  *'{{.State.Running}}'*) printf '%s\\n' true ;;
  *'{{if .State.Health}}'*) printf '%s\\n' healthy ;;
  *'{{.Name}}'*)
    case "$last" in
      legacy-agent-id) printf '%s\\n' /aegisops-agent ;;
      legacy-runner-id) printf '%s\\n' /aegisops-runner ;;
      legacy-db-id) printf '%s\\n' "/$AIOPS_TEST_LEGACY_CONTAINER" ;;
      *) printf '/%s\\n' "$last" ;;
    esac
    ;;
  *' pg_dump '*) printf '%s\\n' legacy-database-backup ;;
  *' psql '*) cat >> "$AIOPS_TEST_PSQL_LOG" ;;
esac
""",
        encoding="utf-8",
    )
    fake_docker.chmod(0o755)
    return fake_bin, docker_log, psql_log


def _wait_for_postgres(
    container: str, user: str, *, require_healthy: bool = False
) -> None:
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if require_healthy:
            result = subprocess.run(
                [
                    "docker",
                    "inspect",
                    "--format",
                    "{{if .State.Health}}{{.State.Health.Status}}{{end}}",
                    container,
                ],
                check=False,
                capture_output=True,
                text=True,
            )
            if result.stdout.strip() == "healthy":
                return
        else:
            result = subprocess.run(
                ["docker", "exec", container, "pg_isready", "-U", user, "-d", "aegisops"],
                check=False,
                capture_output=True,
                text=True,
            )
            if result.returncode == 0:
                return
        time.sleep(1)
    raise AssertionError(f"PostgreSQL did not become ready: {container}")


def _psql(
    container: str,
    user: str,
    password: str,
    sql: str,
    *,
    check: bool = True,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "docker",
            "exec",
            "--env",
            f"PGPASSWORD={password}",
            container,
            "psql",
            "-h",
            "127.0.0.1",
            "--username",
            user,
            "--dbname",
            "aegisops",
            "--tuples-only",
            "--no-align",
            "--set",
            "ON_ERROR_STOP=1",
            "--command",
            sql,
        ],
        check=check,
        capture_output=True,
        text=True,
    )


def _psql_file(
    container: str,
    user: str,
    password: str,
    sql_file: Path,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [
            "docker",
            "exec",
            "--interactive",
            "--env",
            f"PGPASSWORD={password}",
            container,
            "psql",
            "-h",
            "127.0.0.1",
            "--username",
            user,
            "--dbname",
            "aegisops",
            "--set",
            "ON_ERROR_STOP=1",
        ],
        input=sql_file.read_text(encoding="utf-8"),
        check=True,
        capture_output=True,
        text=True,
    )
