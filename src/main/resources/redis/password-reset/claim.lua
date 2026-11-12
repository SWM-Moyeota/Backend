-- 점유 중 프로세스가 죽으면 30초 뒤 인증 불가로 정리한다. 불명확한 문자 발송은 재시도하지 않는다.
local now = tonumber(redis.call('TIME')[1])
for i = 1, 100 do
    local ids = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', now, 'LIMIT', 0, 1)
    if #ids == 0 then return {} end
    local id = ids[1]
    local key = ARGV[1] .. id
    local state = redis.call('HGET', key, 'state')
    if state == 'PENDING' then
        redis.call('HSET', key, 'state', 'PROCESSING')
        redis.call('ZADD', KEYS[1], now + 30, id)
        return {id, redis.call('HGET', key, 'loginId'), redis.call('HGET', key, 'phoneHash'), redis.call('HGET', key, 'expires')}
    end
    if state == 'PROCESSING' or state == 'SENDING' then
        redis.call('HSET', key, 'state', 'INVALID')
        redis.call('HDEL', key, 'digest')
    end
    redis.call('ZREM', KEYS[1], id)
end
return {}
