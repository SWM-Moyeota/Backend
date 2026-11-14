if redis.call('EXISTS', KEYS[1]) == 0 then return {'FAILED'} end
local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts') or '0')
if attempts >= 5 then return {'LIMITED', tostring(math.max(1, redis.call('TTL', KEYS[1])))} end
redis.call('HINCRBY', KEYS[1], 'attempts', 1)
local state = redis.call('HGET', KEYS[1], 'state')
local digest = redis.call('HGET', KEYS[1], 'digest')
if state ~= 'READY' or not digest or digest ~= ARGV[1] then return {'FAILED'} end
redis.call('HSET', KEYS[1], 'state', 'VERIFIED')
redis.call('HDEL', KEYS[1], 'digest')
return {'OK', redis.call('HGET', KEYS[1], 'userId'), redis.call('HGET', KEYS[1], 'phoneHash'), redis.call('HGET', KEYS[1], 'generation')}
