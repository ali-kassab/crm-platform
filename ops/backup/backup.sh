#!/usr/bin/env bash
# Backup of the compose stack (R13): PostgreSQL logical dump + object-storage volume archive.
#
#   ops/backup/backup.sh [--project NAME] [--env-file FILE] [--out DIR] [--keep DAYS]
#
# Produces <out>/<UTC timestamp>/{db.dump, storage.tar.gz, SHA256SUMS}. Read-only on the running
# stack: pg_dump runs in a consistent snapshot and the storage volume is mounted read-only.
# Production on AWS: prefer RDS automated backups/PITR + S3 versioning; this script is for
# self-hosted compose installs and for taking a copy before upgrades.
set -euo pipefail

project=crm-platform
env_file=.env
out=./backups
keep_days=14
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) project=$2; shift 2 ;;
    --env-file) env_file=$2; shift 2 ;;
    --out) out=$2; shift 2 ;;
    --keep) keep_days=$2; shift 2 ;;
    -h|--help) sed -n '2,10p' "$0"; exit 0 ;;
    *) echo "unknown option: $1" >&2; exit 2 ;;
  esac
done

compose=(docker compose -p "$project" --env-file "$env_file")
db_name=$(grep -E '^DB_NAME=' "$env_file" | cut -d= -f2- || true); db_name=${db_name:-crm}
db_user=$(grep -E '^DB_USERNAME=' "$env_file" | cut -d= -f2- || true); db_user=${db_user:-crm}

stamp=$(date -u +%Y%m%dT%H%M%SZ)
dir="$out/$stamp"
mkdir -p "$dir"

echo "→ database $db_name"
"${compose[@]}" exec -T postgres pg_dump -U "$db_user" -d "$db_name" --format=custom --compress=6 --no-owner > "$dir/db.dump"
# a dump that pg_restore cannot list is not a backup
"${compose[@]}" exec -T postgres pg_restore --list < "$dir/db.dump" > /dev/null

echo "→ object storage volume ${project}_storage-data"
MSYS_NO_PATHCONV=1 docker run --rm -v "${project}_storage-data:/data:ro" alpine:3.22 tar -czf - -C /data . > "$dir/storage.tar.gz"

(cd "$dir" && sha256sum db.dump storage.tar.gz > SHA256SUMS)
echo "✓ backup written to $dir"; cat "$dir/SHA256SUMS"

if [[ "$keep_days" =~ ^[0-9]+$ ]] && (( keep_days > 0 )); then
  # only removes timestamped folders this script created, never anything else
  find "$out" -mindepth 1 -maxdepth 1 -type d -name '20*T*Z' -mtime +"$keep_days" -print -exec rm -r {} + || true
fi
