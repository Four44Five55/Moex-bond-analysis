#!/usr/bin/env bash
# MOEX Analytics — остановка приложения (backend + frontend + инфраструктура)
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

echo "============================================"
echo "  MOEX Analytics — остановка приложения"
echo "============================================"

PIDDIR="$ROOT/.run"

# --- 1. Останавливаем backend/frontend по PID-файлам, затем по портам ---
stop_pidfile() {
    local name="$1" file="$PIDDIR/$1.pid"
    if [[ -f "$file" ]]; then
        local pid; pid="$(cat "$file")"
        if kill -0 "$pid" 2>/dev/null; then
            echo "[*] Останавливаю $name (PID $pid)..."
            # Убиваем всю группу процессов (gradle/npm порождают дочерние)
            kill -TERM -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
        fi
        rm -f "$file"
    fi
}

kill_port() {
    local port="$1" pids=""
    if command -v lsof >/dev/null 2>&1; then
        pids="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
    elif command -v fuser >/dev/null 2>&1; then
        fuser -k "${port}/tcp" 2>/dev/null && { echo "    Порт $port освобождён."; return; }
    fi
    if [[ -n "$pids" ]]; then
        echo "    Останавливаю процессы на порту $port: $pids"
        kill $pids 2>/dev/null || true
    else
        echo "    Порт $port свободен."
    fi
}

stop_pidfile backend
stop_pidfile frontend

for port in 8090 5180; do
    echo "[*] Проверяю порт $port..."
    kill_port "$port"
done

# --- 2. Останавливаем инфраструктуру ---
echo "[*] Останавливаю Postgres и Redis (docker compose down)..."
docker compose down || echo "[!] docker compose down завершился с ошибкой (возможно, Docker не запущен)."

echo
echo "============================================"
echo "  Приложение остановлено."
echo "============================================"
echo "Данные Postgres сохранены в volume moex-pgdata."
echo "Чтобы удалить и данные: docker compose down -v"
