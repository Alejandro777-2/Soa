@echo off
REM Detiene el broker (y su volumen) y la ventana keepalive de WSL.
pushd "%~dp0.."
for /f "delims=" %%p in ('wsl -d Ubuntu wslpath "%CD%"') do set "WSLPATH=%%p"

echo Deteniendo RabbitMQ y borrando volumenes...
wsl -d Ubuntu -u root -e bash -lc "cd '%WSLPATH%' && docker compose -p bikestore down -v"

echo Cerrando ventana keepalive de WSL (si sigue abierta)...
taskkill /FI "WINDOWTITLE eq wsl-keepalive" /T /F >nul 2>&1

echo Hecho.
popd
