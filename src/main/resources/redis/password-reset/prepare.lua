if redis.call('HGET', KEYS[1], 'state') ~= 'PROCESSING' then return 0 end
redis.call('HSET', KEYS[1], 'state', 'SENDING', 'userId', ARGV[1], 'generation', ARGV[2], 'digest', ARGV[3])
return 1
