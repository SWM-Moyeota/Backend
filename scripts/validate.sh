#!/bin/bash
for i in $(seq 1 30); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/health || true)
  if [ "$code" = "200" ]; then
    echo "healthy after ${i}x5s"
    exit 0
  fi
  sleep 5
done
echo "health check failed (last code: $code)"
journalctl -u moyeota -n 30 --no-pager
exit 1