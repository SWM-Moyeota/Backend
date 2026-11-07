#!/bin/bash
for i in $(seq 1 30); do
  if [ "$(docker inspect -f '{{.State.Running}}' moyeota-socket 2>/dev/null)" != "true" ]; then
    echo "container not running"
    docker logs --tail 50 moyeota-socket || true
    exit 1
  fi
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:9091/health || true)
  if [ "$code" = "200" ]; then
    echo "healthy after ${i}x5s"
    exit 0
  fi
  sleep 5
done
echo "health check failed (last code: $code)"
docker logs --tail 50 moyeota-socket
exit 1
