@echo off
chcp 65001 >nul
setlocal
title MOEX Analytics - launcher

rem Каталог, где лежит этот батник (корень проекта)
set "ROOT=%~dp0"
cd /d "%ROOT%"

echo ============================================
echo   MOEX Analytics - запуск приложения
echo ============================================

rem --- 1. Инфраструктура: Postgres 5445 + Redis 6380 ---
echo [1/3] Поднимаю Postgres и Redis (docker compose)...
docker compose up -d
if errorlevel 1 (
    echo.
    echo [ОШИБКА] Не удалось запустить docker compose.
    echo Убедись, что Docker Desktop запущен, и попробуй снова.
    pause
    exit /b 1
)

rem Даём БД пару секунд на прогрев healthcheck
echo Жду готовности БД...
timeout /t 5 /nobreak >nul

rem --- 2. Backend: Spring Boot на 8090 ---
echo [2/3] Запускаю backend (http://localhost:8090)...
start "MOEX backend" /D "%ROOT%backend" cmd /k gradlew.bat bootRun

rem --- 3. Frontend: Vite dev на 5180 ---
echo [3/3] Запускаю frontend (http://localhost:5180)...
start "MOEX frontend" /D "%ROOT%frontend" cmd /k "npm install && npm run dev"

echo.
echo ============================================
echo   Backend:  http://localhost:8090  (Swagger: /swagger-ui.html)
echo   Frontend: http://localhost:5180
echo ============================================
echo Backend и frontend открылись в отдельных окнах.
echo Первый старт backend делает синхронизацию с MOEX (~несколько секунд).
echo.
echo Это окно можно закрыть. Для остановки инфраструктуры: docker compose down
pause
endlocal
