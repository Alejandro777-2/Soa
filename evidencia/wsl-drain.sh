#!/usr/bin/env bash
# Espera a que payment.queue y payment.retry.queue queden en 0.
for i in $(seq 1 40); do
  lst=$(docker exec bikestore-rabbitmq rabbitmqctl list_queues -q name messages)
  pq=$(echo "$lst" | grep -P '^payment\.queue\t' | cut -f2)
  rq=$(echo "$lst" | grep -P '^payment\.retry\.queue\t' | cut -f2)
  echo "t=$i payment.queue=${pq:-?} retry=${rq:-?}"
  if [ "${pq:-1}" = "0" ] && [ "${rq:-1}" = "0" ]; then echo DRAINED; break; fi
  sleep 2
done
echo "=== estado final de colas ==="
docker exec bikestore-rabbitmq rabbitmqctl list_queues -q name messages
