#!/usr/bin/env bash
# Restore a backup made by backup.sh into a database (R13).
#
#   ops/backup/restore.sh --from DIR --target-db NAME [--project NAME] [--env-file FILE] [--storage] --confirm
#
# The target database is CREATED and must not exist yet: restoring never overwrites live data.
# To switch the application to it, point DB_URL at the new database (or rename databases while the
# backend is stopped). --storage restores the storage archive into the storage volume
# (only into an EMPTY volume; refuses otherwise).
set -euo pipefail

project=crm-platform
env_file=.env
from=
target=
storage=false
confirm=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --from) from=$2; shift 2 ;;
    --target-db) target=$2; shift 2 ;;
    --project) project=$2; shift 2 ;;
    --env-file) env_file=$2; shift 2 ;;
    --storage) storage=true; shift ;;
    --confirm) confirm=true; shift ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done
[[ -n "$from" && -n "$target" ]] || { echo "--from and --target-db are required" >&2; exit 2; }
[[ "$target" =~ ^[a-z_][a-z0-9_]{0,62}$ ]] || { echo "invalid database name: $target" >&2; exit 2; }
$confirm || { echo "add --confirm to proceed" >&2; exit 2; }
(cd "$from" && sha256sum -c SHA256SUMS)

compose=(docker compose -p "$project" --env-file "$env_file")
db_user=$(grep -E '^DB_USERNAME=' "$env_file" | cut -d= -f2- || true); db_user=${db_user:-crm}

exists=$("${compose[@]}" exec -T postgres psql -U "$db_user" -d postgres -tAc "select 1 from pg_database where datname = '$target'")
[[ -z "$exists" ]] || { echo "database $target already exists — refusing to overwrite" >&2; exit 1; }

"${compose[@]}" exec -T postgres createdb -U "$db_user" "$target"
"${compose[@]}" exec -T postgres pg_restore -U "$db_user" -d "$target" --no-owner --exit-on-error < "$from/db.dump"
echo "✓ database restored into $target"

if $storage; then
  vol="${project}_storage-data"
  MSYS_NO_PATHCONV=1 docker run --rm -v "$vol:/data" -v "$(cd "$from" && pwd -W 2>/dev/null || pwd):/backup:ro" alpine:3.22 \
    sh -c '[ -z "$(ls -A /data)" ] || { echo "storage volume is not empty — refusing" >&2; exit 1; }; tar -xzf /backup/storage.tar.gz -C /data'
  echo "✓ storage restored into $vol"
fi
