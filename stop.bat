@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
title MOEX Analytics - stop

set "ROOT=%~dp0"
cd /d "%ROOT%"

echo ============================================
echo   MOEX Analytics - остановка приложения
echo ============================================

rem --- 1. Убиваем процессы на портах backend (8090) и frontend (5180) ---
for %%P in (8090 5180) do (
    echo [*] Ищу процесс на порту %%P...
    set "FOUND="
    for /f "tokens=5" %%A in ('netstat -ano ^| findstr /r /c:":%%P .*LISTENING"') do (
        if not "%%A"=="0" (
            set "FOUND=1"
            echo     Останавливаю PID %%A ^(порт %%P^)
            taskkill /F /PID %%A >nul 2>&1
        )
    )
    if not defined FOUND echo     Порт %%P свободен.
)

rem --- 2. Останавливаем инфраструктуру ---
echo [*] Останавливаю Postgres и Redis (docker compose down)...
docker compose down
if errorlevel 1 (
    echo [!] docker compose down завершился с ошибкой (возможно, Docker не запущен).
)

echo.
echo ============================================
echo   Приложение остановлено.
echo ============================================
echo Данные Postgres сохранены в volume moex-pgdata.
echo Чтобы удалить и данные: docker compose down -v
echo.
pause
endlocal
