# DEPLOY.md

## PolySignals: деплой на VPS (Ubuntu + Docker Compose)

## 1) Минимальные требования к VPS

### Минимум (для старта)
- CPU: **2 ядра vCPU**
- RAM: **4 GB**
- Диск: **30 GB SSD/NVMe**
- OS: **Ubuntu 22.04 LTS** или **Ubuntu 24.04 LTS**
- Сеть: публичный IP + открытый SSH (порт 22)

### Рекомендуется (стабильнее под нагрузкой)
- CPU: **4 vCPU**
- RAM: **4–8 GB**
- Диск: **40+ GB NVMe**

---

## 2) Установка Docker и Docker Compose (Ubuntu 22.04/24.04)

> Выполняйте под пользователем с `sudo`.

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker

docker --version
docker compose version
docker run --rm hello-world
```

---

## 3) Настройка `.env` файла

Создайте файл `.env` в корне проекта с обязательными переменными:

```bash
# Database (PostgreSQL)
DB_USER=polysignals_user
DB_NAME=polysignals_db
DB_PASSWORD=<СГЕНЕРИРОВАТЬ_СТОЙКИЙ_ПАРОЛЬ>  # минимум 16 символов


# Spring Boot
SPRING_PROFILES_ACTIVE=prod


# Actuator Security (basic auth)
ADMIN_NAME=admin
ADMIN_PASSWORD=<СГЕНЕРИРОВАТЬ_СТОЙКИЙ_ПАРОЛЬ>  # минимум 12 символов

# Telegram (для уведомлений из приложения)
TELEGRAM_BOT_TOKEN=<ПОЛУЧИТЬ_ОТ_BOTFATHER>
TELEGRAM_CHAT_ID=<ПОЛУЧИТЬ_ID_ЧАТА>
TELEGRAM_ADMIN_CHAT_ID=<ОПЦИОНАЛЬНО_ADMIN_ID>

# Backup (для Telegram-уведомлений о бэкапе)
TELEGRAM_BOT_TOKEN=<ТОЖЕ_САМОЕ>
TELEGRAM_CHAT_ID=<ТОЖЕ_САМОЕ>

# Логирование (опционально)
LOG_LEVEL=INFO
LOG_FORMAT=json
```

### Генерация стойких паролей:

```bash
# Вариант 1: openssl
openssl rand -base64 32

# Вариант 2: python
python3 -c "import secrets; print(secrets.token_urlsafe(32))"

# Вариант 3: на VPS без доп. инструментов
head -c 32 /dev/urandom | base64
```

---

## 4) Размещение файлов на VPS

```bash
# На локальной машине: скопируйте проект
scp -r ./PolySignals root@<YOUR_VPS_IP>:/opt/

# Или через git:
cd /opt
git clone <YOUR_REPO_URL>
cd PolySignals

# Установите права
sudo chown -R $(whoami):$(whoami) /opt/PolySignals
chmod +x backup.sh
```

---

## 5) Запуск приложения

```bash
cd /opt/PolySignals

# Проверка переменных окружения
cat .env

# Сборка образов (если используется локальный Dockerfile)
docker compose -f docker-compose.prod.yaml build

# Запуск контейнеров в фоне
docker compose -f docker-compose.prod.yaml up -d

# Проверка статуса
docker compose -f docker-compose.prod.yaml ps
```

Ожидаемый вывод:
```
NAME                   STATUS              PORTS
polysignals-app      Up 10 seconds       0.0.0.0:8080->8080/tcp, 0.0.0.0:9091->9091/tcp
polysignals-postgres Up 12 seconds       5432/tcp
```

---

## 6) Проверка работоспособности

### 6.1 Проверка статуса контейнеров

```bash
docker compose -f docker-compose.prod.yaml ps
docker compose -f docker-compose.prod.yaml logs -f app
```

### 6.2 Проверка healthcheck

```bash
# Главный API (port 8080)
# actuator health на management порту
curl -u admin:$ADMIN_PASSWORD http://localhost:9091/actuator/health
# или если открыли /actuator/health без пароля по IP (как у вас в ActuatorSecurityConfig)
curl http://localhost:9091/actuator/health

# Management (port 9091) — требует basic auth
curl -u admin:<ADMIN_PASSWORD> -i http://localhost:9091/actuator/health

# Ожидаемый ответ (200 OK):
# {"status":"UP"}
```

### 6.3 Проверка доступности снаружи

```bash
# С локальной машины
curl -i http://<YOUR_VPS_IP>:8080/actuator/health

# Или через SSH:
ssh root@<YOUR_VPS_IP> "curl -i http://localhost:8080/actuator/health"
```

### 6.4 Логи приложения

```bash
# Последние 100 строк логов
docker compose -f docker-compose.prod.yaml logs --tail=100 app

# В реальном времени
docker compose -f docker-compose.prod.yaml logs -f app

# Логи БД
docker compose -f docker-compose.prod.yaml logs -f postgres
```

---

## 7) Настройка Firewall (UFW)

### 7.1 Включение UFW

```bash
sudo ufw enable
sudo ufw status
```

### 7.2 Открытие портов

```bash
# SSH (обязательно!)
sudo ufw allow 22/tcp

# HTTP API
sudo ufw allow 8080/tcp

# Management Actuator (опционально, для удаленного мониторинга)
sudo ufw allow 9091/tcp

# PostgreSQL (ТОЛЬКО для внутренней сети, не для интернета!)
# sudo ufw allow from 192.168.0.0/16 to any port 5432

# Проверка правил
sudo ufw status verbose
```

### 7.3 Ограничение SSH по IP (опционально, если известен IP)

```bash
# Сначала добавьте default allow для своего IP:
sudo ufw allow from <YOUR_IP>/32 to any port 22

# Затем limit на остальных:
sudo ufw limit 22/tcp
```

---

## 8) Рекомендации по fail2ban

### 8.1 Установка

```bash
sudo apt install -y fail2ban

# Запуск
sudo systemctl enable fail2ban
sudo systemctl start fail2ban

# Проверка статуса
sudo systemctl status fail2ban
```

### 8.2 Конфигурация SSH защиты

```bash
sudo tee /etc/fail2ban/jail.local > /dev/null <<EOF
[DEFAULT]
bantime = 3600
findtime = 600
maxretry = 5

[sshd]
enabled = true
port = ssh
logpath = /var/log/auth.log
maxretry = 3
bantime = 7200
EOF

# Перезагрузка
sudo systemctl restart fail2ban

# Проверка активных блокировок
sudo fail2ban-client status sshd
```

### 8.3 Мониторинг

```bash
# Просмотр заблокированных IP
sudo fail2ban-client status sshd | grep "Banned IP list"

# Разблокировка IP (если нужно)
sudo fail2ban-client set sshd unbanip <IP>
```

---

## 9) Процедура обновления образа

### 9.1 Проверка текущей версии

```bash
docker compose -f docker-compose.prod.yaml images
```

### 9.2 Обновление

```bash
cd /opt/PolySignals

# 1. Остановка контейнеров (БД не удаляется благодаря volume)
docker compose -f docker-compose.prod.yaml down

# 2. Обновление кода
git pull origin main

# 3. Пересборка образа
docker compose -f docker-compose.prod.yaml pull app
docker compose -f docker-compose.prod.yaml up -d --no-deps app

# 4. Запуск новой версии
docker compose -f docker-compose.prod.yaml up -d

# 5. Проверка статуса и логов
docker compose -f docker-compose.prod.yaml ps
docker compose -f docker-compose.prod.yaml logs -f app
```

### 9.3 Откат (если что-то сломалось)

```bash
# Остановка текущей версии
docker compose -f docker-compose.prod.yaml down

# Возврат на предыдущий commit
git revert HEAD

# Пересборка и запуск
docker compose -f docker-compose.prod.yaml pull app
docker compose -f docker-compose.prod.yaml up -d --no-deps app
```

---

## 10) Резервное копирование БД

### 10.1 Ручной бэкап

```bash
cd /opt/PolySignals
bash backup.sh
```

### 10.2 Автоматизация (cron)

```bash
# Редактируем crontab
crontab -e

# Добавляем строку для ежедневного бэкапа в 03:00
0 3 * * * cd /opt/PolySignals && bash backup.sh >> /var/log/polysignals_backup.log 2>&1
```

### 10.3 Восстановление из бэкапа

```bash
# Список доступных бэкапов
ls -lh backups/

# Распаковка и восстановление
cd /opt/PolySignals
gunzip -c backups/polysignals_db_2024-01-15_03-00-00.sql.gz | docker compose -f docker-compose.prod.yaml exec -T postgres psql -U polysignals_user -d polysignals_db
```

---

## 11) Полезные команды

```bash
# Проверка использования ресурсов
docker stats

# Просмотр всех контейнеров (включая остановленные)
docker ps -a

# Удаление старых образов и неиспользуемых томов
docker system prune -a --volumes

# Проверка размера томов
docker volume ls -q | xargs -r docker volume inspect

# SSH на VPS в корень проекта
ssh root@<YOUR_VPS_IP> "cd /opt/PolySignals && bash"
```

---

## 12) Troubleshooting

### Контейнер постоянно перезагружается

```bash
# Проверка логов
docker compose -f docker-compose.prod.yaml logs app

# Типичные причины:
# - Wrong .env variables
# - PostgreSQL not ready
# - Port already in use
```

### БД недоступна

```bash
# Проверка статуса PostgreSQL
docker compose -f docker-compose.prod.yaml logs postgres

# Проверка прав на volume
docker volume inspect polysignals_postgres_data

# Пересоздание volume (внимание: потеря данных!)
docker compose -f docker-compose.prod.yaml down -v
docker compose -f docker-compose.prod.yaml up -d
```

### Нехватка диска

```bash
# Проверка
df -h

# Очистка старых образов
docker image prune -a --force

# Очистка неиспользуемых томов
docker volume prune --force
```

---

## 13) Мониторинг и логирование

### Просмотр метрик через Prometheus

```bash
# Доступ с basic auth
curl -u admin:<ADMIN_PASSWORD> http://localhost:9091/actuator/prometheus
```

### Централизованное логирование (опционально)

```bash
# Все логи контейнеров в одно место
mkdir -p /var/log/polysignals
docker compose -f docker-compose.prod.yaml logs > /var/log/polysignals/all.log
```

---

## 14) Контакты и поддержка

- **Issues**: GitHub Issues
- **Documentation**: /README.md
- **Backup guide**: /backup.sh комментарии
- **VPS provider**: Selectel, DigitalOcean, Hetzner, etc.

---

**Дата обновления**: 2024-01-15  
**Версия**: 1.0
