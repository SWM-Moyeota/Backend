local state = redis.call('HGET', KEYS[1], 'state')
if state then
    if ARGV[2] == 'INVALID' then
        redis.call('HSET', KEYS[1], 'state', 'INVALID')
        redis.call('HDEL', KEYS[1], 'digest')
    elseif state == 'SENDING' then
        redis.call('HSET', KEYS[1], 'state', 'READY')
    end
end
redis.call('ZREM', KEYS[2], ARGV[1])
return 1
