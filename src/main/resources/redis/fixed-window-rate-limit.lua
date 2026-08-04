local count = redis.call('INCR', KEYS[1])
local windowMillis = tonumber(ARGV[1])

if count == 1 then
    redis.call('PEXPIRE', KEYS[1], windowMillis)
end

local ttl = redis.call('PTTL', KEYS[1])
if ttl == -1 then
    redis.call('PEXPIRE', KEYS[1], windowMillis)
    ttl = windowMillis
end

return {count, ttl}
