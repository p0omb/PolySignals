PostgreSQL backup/restore (prod)

1) Подготовка
- chmod +x /opt/PolySignals/backup.sh
- mkdir -p /opt/PolySignals/backups

2) Ручной тест
- cd /opt/PolySignals
- ./backup.sh
- ls -lh backups

3) Ежедневный запуск через cron (VPS)
- crontab -e
- добавить строку:
  30 3 * * * cd /opt/PolySignals && /opt/PolySignals/backup.sh >> /var/log/polysignals-backup.log 2>&1

Проверка:
- crontab -l
- tail -n 50 /var/log/polysignals-backup.log

4) Восстановление из дампа
- cd /opt/PolySignals
- set -a; source .env; set +a
- gunzip -c backups/<dump_file>.sql.gz | docker compose -f docker-compose.prod.yaml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres psql -U "$DB_USER" -d "$DB_NAME"

(Опционально) Очистка схемы перед восстановлением:
- docker compose -f docker-compose.prod.yaml exec -T -e PGPASSWORD="$DB_PASSWORD" postgres psql -U "$DB_USER" -d "$DB_NAME" -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"

