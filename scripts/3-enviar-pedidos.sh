#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
N="${1:-20}"
URL="http://localhost:8080/api/orders"

echo "=== Enviando $N pedidos validos ==="
for i in $(seq 1 "$N"); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL" \
    -H "Content-Type: application/json" \
    -d "{\"cliente\":\"Cliente$i\",\"items\":[{\"nombre\":\"Bicicleta\",\"cantidad\":2,\"precio\":150.0},{\"nombre\":\"Casco\",\"cantidad\":1,\"precio\":45.5}]}")
  echo "Pedido $i -> HTTP $code"
done

echo
echo "=== Enviando 2 peticiones invalidas (se espera HTTP 400) ==="
c1=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL" \
  -H "Content-Type: application/json" -d '{"cliente":"","items":[]}')
echo "Invalido 1 (cliente vacio, items vacios) -> HTTP $c1"
c2=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL" \
  -H "Content-Type: application/json" -d '{"cliente":"Mala","items":[{"nombre":"X","cantidad":-1,"precio":10}]}')
echo "Invalido 2 (cantidad negativa) -> HTTP $c2"

echo
echo "=== Esperando procesamiento (cobros + reintentos, ~12s) ==="
sleep 12

echo
echo "=== Contenido de la DLQ (payment.dlq) ==="
java -jar target/bikestore-async-1.0.jar dlq
