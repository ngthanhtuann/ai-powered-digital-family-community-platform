#!/usr/bin/env bash
# CẢNH BÁO: ghi đè database và uploads. Chỉ gọi sau khi xác nhận RESTORE.
set -euo pipefail
umask 077

: "${PGHOST:?Thiếu PGHOST}"
: "${PGDATABASE:?Thiếu PGDATABASE}"
: "${PGUSER:?Thiếu PGUSER}"
backup_dir="${BACKUP_STORAGE_PATH:-/var/lib/familyconnect/backups}"
media_dir="${MEDIA_STORAGE_PATH:-/var/lib/familyconnect/media}"
backup_id="${1:-}"
confirmation="${2:-}"

[[ "$confirmation" == 'RESTORE' ]] || { echo 'Phải xác nhận RESTORE' >&2; exit 2; }
[[ "$backup_id" =~ ^familyconnect-[0-9]{8}-[0-9]{6}$ ]] || { echo 'Mã backup không hợp lệ' >&2; exit 2; }
[[ -d "$backup_dir" && -d "$media_dir" ]] || { echo 'Thiếu volume backups/uploads' >&2; exit 1; }
# Chặn MEDIA_STORAGE_PATH bị trỏ sang /, thư mục cha hoặc symbolic link.
[[ ! -L "$media_dir" && "$media_dir" == /* && "$media_dir" == "$(realpath -- "$media_dir")" ]] || {
  echo 'Đường dẫn uploads không an toàn' >&2; exit 1;
}
case "$media_dir" in
  /|/tmp|/var|/var/lib|/var/lib/familyconnect) echo 'Không được khôi phục vào thư mục gốc/cha' >&2; exit 1 ;;
esac
exec 9>"$backup_dir/.operation.lock"
flock -n 9 || { echo 'Đang có tác vụ backup/restore khác' >&2; exit 75; }

archive="$backup_dir/$backup_id.tar.gz"
[[ -f "$archive" && ! -L "$archive" ]] || { echo 'Không tìm thấy file backup' >&2; exit 1; }
staging="$(mktemp -d "$backup_dir/.restore.XXXXXXXX")"
trap 'rm -rf -- "$staging"' EXIT

# Chỉ archive nội bộ gồm database.dump và media/; không chấp nhận đường dẫn thoát thư mục.
tar -tzf "$archive" > "$staging/entries.txt"
while IFS= read -r entry; do
  case "$entry" in
    database.dump|media|media/|media/*) ;;
    *) echo 'Archive chứa đường dẫn ngoài phạm vi cho phép' >&2; exit 1 ;;
  esac
  case "/$entry" in
    */../*|*/./*|*/..|*/.|*//*) echo 'Archive chứa đường dẫn không an toàn' >&2; exit 1 ;;
  esac
done < "$staging/entries.txt"
tar -xzf "$archive" -C "$staging" --no-same-owner
[[ -s "$staging/database.dump" && ! -L "$staging/database.dump" && -d "$staging/media" ]] || {
  echo 'Archive thiếu database.dump hoặc media' >&2; exit 1;
}
if [[ -n "$(find "$staging/media" -type l -print -quit)" ]]; then
  echo 'Archive chứa symbolic link trong uploads' >&2
  exit 1
fi

# Chỉ chạy sau xác nhận; dữ liệu DB và volume hiện tại sẽ bị thay thế.
pg_restore --clean --if-exists --no-owner --no-acl --single-transaction --exit-on-error \
  --dbname="$PGDATABASE" "$staging/database.dump"
find "$media_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
cp -a -- "$staging/media/." "$media_dir/"
echo "Đã khôi phục $backup_id"
