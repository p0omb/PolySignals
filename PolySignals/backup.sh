#!/usr/bin/env bash
set -Eeuo pipefail

# -----------------------------
# Конфигурация
# -----------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose.prod.yaml}"
BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
LOCK_FILE="${LOCK_FILE:-/var/lock/polysignals_backup.lock}"

# Переменные для cleanup
TMP_FILE=""
TMP_RESTORE_DB=""

# -----------------------------
# Telegram helper
# -----------------------------
send_telegram() {
  local text="$1"
  local chat_id="${2:-}"

  # Если не настроено — молча пропускаем
  if [[ -z "${TELEGRAM_BOT_TOKEN:-}" || -z "${chat_id}" ]]; then
    return 0
  fi

  curl --silent --show-error --fail \
    -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
    --data-urlencode "chat_id=${chat_id}" \
    --data-urlencode "text=${text}" \
    >/dev/null || true
}

# -----------------------------
# Очистка временных ресурсов
# -----------------------------
cleanup() {
  # Удаляем временную БД восстановления (если была создана)
  if [[ -n "${TMP_RESTORE_DB}" ]]; then
    docker compose -f "$COMPOSE_FILE" exec -T \
      -e PGPASSWORD="${DB_PASSWORD:-}" \
      postgres \
      psql -v ON_ERROR_STOP=1 -U "${DB_USER:-}" -d postgres \
      -c "DROP DATABASE IF EXISTS \"${TMP_RESTORE_DB}\";" \
      >/dev/null 2>&1 || true
  fi

  # Удаляем временный файл
  if [[ -n "${TMP_FILE}" && -f "${TMP_FILE}" ]]; then
    rm -f "${TMP_FILE}" || true
  fi
}

on_error() {
  local exit_code="$1"
  local line="$2"
  local cmd="$3"
  local reason="line ${line}: ${cmd} (exit ${exit_code})"

  echo "ERROR: ${reason}" >&2

  # Критическое уведомление: сначала в админ-чат, иначе в обычный
  local target_chat="${TELEGRAM_ADMIN_CHAT_ID:-${TELEGRAM_CHAT_ID:-}}"
  send_telegram "❌ Backup failed: ${reason}" "${target_chat}"

  exit "${exit_code}"
}

trap 'on_error "$?" "${LINENO}" "${BASH_COMMAND}"' ERR
trap cleanup EXIT

# -----------------------------
# Защита от одновременного запуска
# -----------------------------
mkdir -p "$(dirname "$LOCK_FILE")"
exec 200>"$LOCK_FILE"
if ! flock -n 200; then
  echo "Backup is already running. Exit."
  exit 0
fi

# -----------------------------
# Проверки окружения
# -----------------------------
if [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: .env file not found: $ENV_FILE" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "ERROR: docker-compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

# Загружаем .env (DB_* и Telegram переменные)
set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

: "${DB_USER:?DB_USER is not set in .env}"
: "${DB_NAME:?DB_NAME is not set in .env}"
: "${DB_PASSWORD:?DB_PASSWORD is not set in .env}"

mkdir -p "$BACKUP_DIR"

TIMESTAMP="$(date -u +%F_%H-%M-%S)"
BACKUP_BASENAME="polysignals_${DB_NAME}_${TIMESTAMP}.sql.gz"
BACKUP_FILE="$BACKUP_DIR/$BACKUP_BASENAME"
TMP_FILE="${BACKUP_FILE}.tmp"

echo "Starting backup: $BACKUP_FILE"

# -----------------------------
# Создание дампа + gzip
# -----------------------------
docker compose -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$DB_PASSWORD" \
  postgres \
  pg_dump -U "$DB_USER" -d "$DB_NAME" --no-owner --no-privileges \
  | gzip -9 > "$TMP_FILE"

mv "$TMP_FILE" "$BACKUP_FILE"

# Базовая проверка gzip-архива
gzip -t "$BACKUP_FILE"

# -----------------------------
# Проверка целостности через тестовое восстановление
# -----------------------------
TMP_RESTORE_DB="test_restore_$(date +%s)"
echo "Verifying backup via temporary DB: $TMP_RESTORE_DB"

# 1) Создать временную БД
docker compose -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$DB_PASSWORD" \
  postgres \
  psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d postgres \
  -c "CREATE DATABASE \"$TMP_RESTORE_DB\";" \
  >/dev/null

# 2) Восстановить дамп в неё
gunzip -c "$BACKUP_FILE" | docker compose -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$DB_PASSWORD" \
  postgres \
  psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$TMP_RESTORE_DB" \
  >/dev/null

# 3) Минимальная проверка SQL
docker compose -f "$COMPOSE_FILE" exec -T \
  -e PGPASSWORD="$DB_PASSWORD" \
  postgres \
  psql -v ON_ERROR_STOP=1 -U "$DB_USER" -d "$TMP_RESTORE_DB" \
  -c "SELECT 1;" \
  >/dev/null

echo "Backup verification passed."

# -----------------------------
# Ротация (только после успешной проверки)
# -----------------------------
find "$BACKUP_DIR" -maxdepth 1 -type f -name "polysignals_*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "Backup completed successfully: $BACKUP_FILE"
send_telegram "✅ Backup completed: ${BACKUP_BASENAME}" "${TELEGRAM_CHAT_ID:-}"
