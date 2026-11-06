#!/usr/bin/env bash
# UC-AUTH-11: tạo một archive gồm pg_dump custom và toàn bộ uploads.
set -euo pipefail
umask 077

: "${PGHOST:?Thiếu PGHOST}"
: "${PGDATABASE:?Thiếu PGDATABASE}"
: "${PGUSER:?Thiếu PGUSER}"
backup_dir="${BACKUP_STORAGE_PATH:-/var/lib/familyconnect/backups}"
media_dir="${MEDIA_STORAGE_PATH:-/var/lib/familyconnect/media}"
backup_id="${1:-familyconnect-$(date -u +%Y%m%d-%H%M%S)}"

if [[ ! "$backup_id" =~ ^familyconnect-[0-9]{8}-[0-9]{6}$ ]]; then
  echo 'Mã backup không hợp lệ' >&2
  exit 2
fi
[[ -d "$media_dir" ]] || { echo 'Không tìm thấy volume uploads' >&2; exit 1; }
mkdir -p -- "$backup_dir"
exec 9>"$backup_dir/.operation.lock"
flock -n 9 || { echo 'Đang có tác vụ backup/restore khác' >&2; exit 75; }

target="$backup_dir/$backup_id.tar.gz"
[[ ! -e "$target" ]] || { echo 'Bản sao lưu đã tồn tại' >&2; exit 1; }
staging="$(mktemp -d "$backup_dir/.backup.XXXXXXXX")"
archive="$(mktemp "$backup_dir/.archive.XXXXXXXX")"
trap 'rm -rf -- "$staging"; rm -f -- "$archive"' EXIT

pg_dump --format=custom --no-owner --no-acl --file="$staging/database.dump"
mkdir -p -- "$staging/media"
# Bỏ thư mục tạm .restore-* nếu một lần khôi phục trước bị ngắt giữa chừng.
find "$media_dir" -mindepth 1 -maxdepth 1 ! -name '.restore-*' -exec cp -a -t "$staging/media/" -- {} +
tar -czf "$archive" -C "$staging" database.dump media
mv -- "$archive" "$target"
echo "$target"
