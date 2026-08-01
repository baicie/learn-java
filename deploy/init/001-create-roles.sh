#!/bin/sh
set -eu

app_password="$(cat /run/secrets/app_db_password)"
runner_password="$(cat /run/secrets/runner_db_password)"

psql \
  --set=ON_ERROR_STOP=1 \
  --username "$POSTGRES_USER" \
  --dbname "$POSTGRES_DB" \
  --set=database_name="$POSTGRES_DB" \
  --set=app_password="$app_password" \
  --set=runner_password="$runner_password" \
  --file=/opt/aegisops/init/001-create-roles.sql
