local n = redis.call('INCR', KEYS[1])
if n == 1 then redis.call('EXPIRE', KEYS[1], 60) end
if n > 30 then return math.max(1, redis.call('TTL', KEYS[1])) end
return 0
