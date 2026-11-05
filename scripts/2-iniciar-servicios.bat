@echo off
pushd "%~dp0.."
REM El broker corre en WSL; forzamos IPv4 (localhost resolveria a ::1 y fallaria).
set RABBITMQ_HOST=127.0.0.1
set JAR=target\bikestore-async-1.0.jar
if not exist "%JAR%" (
  echo No se encontro %JAR%. Ejecuta primero: mvn package
  popd
  exit /b 1
)
echo Iniciando PaymentWorker, EmailWorker y OrderProducer en ventanas separadas...
start "PaymentWorker" cmd /k java -jar %JAR% payment
start "EmailWorker"   cmd /k java -jar %JAR% email
start "OrderProducer" cmd /k java -jar %JAR% producer
echo Servicios iniciados. Cada uno escribe tambien en logs\.
popd
