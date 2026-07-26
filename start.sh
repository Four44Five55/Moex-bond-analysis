#!/usr/bin/env bash
# MOEX Analytics — запуск всего приложения (инфраструктура + backend + frontend)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

echo "============================================"
echo "  MOEX Analytics — запуск приложения"
echo "============================================"

# --- 1. Инфраструктура: Postgres 5445 + Redis 6380 ---
echo "[1/3] Поднимаю Postgres и Redis (docker compose)..."
if ! docker compose up -d; then
    echo
    echo "[ОШИБКА] Не удалось запустить docker compose."
    echo "Убедись, что Docker запущен, и попробуй снова."
    exit 1
fi

echo "Жду готовности БД..."
sleep 5

# Каталог для PID-файлов, чтобы stop.sh мог остановить процессы
PIDDIR="$ROOT/.run"
mkdir -p "$PIDDIR"

# --- 2. Backend: Spring Boot на 8090 ---
echo "[2/3] Запускаю backend (http://localhost:8090)..."
( cd "$ROOT/backend" && ./gradlew bootRun ) >"$ROOT/backend.log" 2>&1 &
echo $! > "$PIDDIR/backend.pid"

# --- 3. Frontend: Vite dev на 5180 ---
echo "[3/3] Запускаю frontend (http://localhost:5180)..."
( cd "$ROOT/frontend" && npm install && npm run dev ) >"$ROOT/frontend.log" 2>&1 &
echo $! > "$PIDDIR/frontend.pid"

echo
echo "============================================"
echo "  Backend:  http://localhost:8090  (Swagger: /swagger-ui.html)"
echo "  Frontend: http://localhost:5180"
echo "============================================"
echo "Backend и frontend запущены в фоне."
echo "Логи: backend.log / frontend.log (tail -f backend.log)"
echo "Первый старт backend делает синхронизацию с MOEX (~несколько секунд)."
echo "Остановка: ./stop.sh"
