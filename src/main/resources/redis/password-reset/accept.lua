-- 원자적으로 제한 확인, 이전 요청 무효화, 접수 및 복구 가능한 큐 등록.
-- 현재 단일 Redis/Sentinel 배포용. Cluster에서는 다중 키 배치를 다시 설계해야 한다.
local queue, latest, challenge, cooldown, daily, account = unpack(KEYS)
local retry = redis.call('TTL', cooldown)
if retry > 0 then return retry end
if tonumber(redis.call('GET', daily) or '0') >= 5 then return math.max(1, redis.call('TTL', daily)) end
if tonumber(redis.call('GET', account) or '0') >= 10 then return math.max(1, redis.call('TTL', account)) end
if redis.call('ZCARD', queue) >= 10000 then return -1 end
local previous = redis.call('GET', latest)
if previous and redis.call('EXISTS', ARGV[4] .. previous) == 1 then
    redis.call('HSET', ARGV[4] .. previous, 'state', 'INVALID')
    redis.call('HDEL', ARGV[4] .. previous, 'digest')
    redis.call('ZREM', queue, previous)
end
redis.call('SET', cooldown, '1', 'EX', 60)
if redis.call('INCR', daily) == 1 then redis.call('EXPIRE', daily, 86400) end
if redis.call('INCR', account) == 1 then redis.call('EXPIRE', account, 3600) end
local now = tonumber(redis.call('TIME')[1])
redis.call('HSET', challenge, 'state', 'PENDING', 'loginId', ARGV[2], 'phoneHash', ARGV[3], 'attempts', 0, 'expires', now + 180)
redis.call('EXPIRE', challenge, 180)
redis.call('SET', latest, ARGV[1], 'EX', 180)
redis.call('ZADD', queue, now, ARGV[1])
return 0
