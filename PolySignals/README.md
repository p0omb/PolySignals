# PolySignals

PolySignals — сервис для отслеживания ставок крупных пользователей на Polymarket, поиска совпадений ставок нескольких китов и отправки сигналов в Telegram.

## Технологии

- Java 21
- Spring Boot 4
- PostgreSQL
- Docker
- Telegram Bot

## Документация

- [DEPLOY.md](DEPLOY.md) — развёртывание на VPS
- [BACKUP.md](BACKUP.md) — резервное копирование PostgreSQL

## Локальный запуск

### 1. Клонировать репозиторий

```bash
git clone <URL_REПОЗИТОРИЯ>
cd PolySignals
```

### 2. Установить Java 17+ и Maven

Проверьте доступность инструментов:

```bash
java -version
mvn -version
```

### 3. Запустить PostgreSQL (через Docker)

```bash
docker run --name polysignals-postgres -e POSTGRES_DB=polysignals_db -e POSTGRES_USER=polysignals_user -e POSTGRES_PASSWORD=yourpassword -p 5432:5432 -d postgres:16-alpine
```

### 4. Настроить `.env`

Создайте файл `.env` в корне проекта и укажите ключевые переменные:

```dotenv
DB_USER=...
DB_NAME=...
DB_PASSWORD=...
TELEGRAM_BOT_TOKEN=...
TELEGRAM_CHAT_ID=...
ADMIN_NAME=...
ADMIN_PASSWORD=...
```

### 5. Собрать проект

```bash
mvn clean package
```

### 6. Запустить приложение

```bash
java -jar target/*.jar
```
