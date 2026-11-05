#!/usr/bin/env bash
set -e
cd "$(dirname "$0")/.."
echo "Iniciando RabbitMQ con Docker..."
docker compose up -d
echo
echo "Consola de administracion: http://localhost:15672  (usuario/clave: guest/guest)"
