#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
JAR=target/bikestore-async-1.0.jar
if [ ! -f "$JAR" ]; then
  echo "No se encontro $JAR. Ejecuta primero: mvn package"
  exit 1
fi
echo "Iniciando PaymentWorker, EmailWorker y OrderProducer como procesos separados..."
java -jar "$JAR" payment  & echo "  PaymentWorker PID $!"
java -jar "$JAR" email    & echo "  EmailWorker   PID $!"
java -jar "$JAR" producer & echo "  OrderProducer PID $!"
echo "Servicios iniciados. Cada uno escribe tambien en logs/. Ctrl+C para detener."
wait
