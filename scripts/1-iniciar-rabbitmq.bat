@echo off
REM En este equipo no hay Docker en Windows: el broker corre con Docker dentro de WSL.
pushd "%~dp0.."

REM Ruta del proyecto en formato WSL (/mnt/c/...), soportando espacios.
for /f "delims=" %%p in ('wsl -d Ubuntu wslpath "%CD%"') do set "WSLPATH=%%p"

echo Manteniendo viva la VM de WSL (no cierres la ventana "wsl-keepalive")...
start "wsl-keepalive" /min wsl -d Ubuntu -u root -e bash -lc "sleep 86400"

echo Iniciando RabbitMQ con Docker en WSL...
wsl -d Ubuntu -u root -e bash -lc "systemctl start docker && cd '%WSLPATH%' && docker compose -p bikestore up -d"

echo.
echo Consola de administracion: http://localhost:15672  (usuario/clave: guest/guest)
echo Para detener: scripts\4-detener.bat
popd
