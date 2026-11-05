@echo off
setlocal
pushd "%~dp0.."
REM Para que DlqInspector (al final) conecte al broker en WSL por IPv4.
set RABBITMQ_HOST=127.0.0.1
set N=%1
if "%N%"=="" set N=20
set URL=http://localhost:8080/api/orders

echo === Enviando %N% pedidos validos ===
for /L %%i in (1,1,%N%) do (
  curl -s -o nul -w "Pedido %%i -> HTTP %%{http_code}\n" -X POST %URL% ^
    -H "Content-Type: application/json" ^
    -d "{\"cliente\":\"Cliente%%i\",\"items\":[{\"nombre\":\"Bicicleta\",\"cantidad\":2,\"precio\":150.0},{\"nombre\":\"Casco\",\"cantidad\":1,\"precio\":45.5}]}"
)

echo.
echo === Enviando 2 peticiones invalidas (se espera HTTP 400) ===
curl -s -o nul -w "Invalido 1 (cliente vacio, items vacios) -> HTTP %%{http_code}\n" -X POST %URL% ^
  -H "Content-Type: application/json" -d "{\"cliente\":\"\",\"items\":[]}"
curl -s -o nul -w "Invalido 2 (cantidad negativa) -> HTTP %%{http_code}\n" -X POST %URL% ^
  -H "Content-Type: application/json" -d "{\"cliente\":\"Mala\",\"items\":[{\"nombre\":\"X\",\"cantidad\":-1,\"precio\":10}]}"

echo.
echo === Esperando procesamiento (cobros + reintentos, ~12s) ===
timeout /t 12 /nobreak >nul

echo.
echo === Contenido de la DLQ (payment.dlq) ===
java -jar target\bikestore-async-1.0.jar dlq
popd
endlocal
