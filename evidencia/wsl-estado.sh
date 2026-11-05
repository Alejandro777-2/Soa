#!/usr/bin/env bash
# Vuelca el estado de las colas a evidencia/estado-colas.txt (ejecutar con cwd = raiz del proyecto).
{
  echo "=== estado de colas (post-test) $(date) ==="
  docker exec bikestore-rabbitmq rabbitmqctl list_queues -q name messages messages_ready
} > evidencia/estado-colas.txt
cat evidencia/estado-colas.txt
