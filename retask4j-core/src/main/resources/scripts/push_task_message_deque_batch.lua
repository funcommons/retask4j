-- Get prefix from KEYS parameter
local messagePrefix = KEYS[1]
local workListKey = KEYS[2]
local timingSetKey = KEYS[3]
-- Get from ARGV parameter
local items = ARGV
local count = 0
local currentTime = tonumber(redis.call('TIME')[1])
-- Iterate each item
for i, item in ipairs(items) do
    local map = cjson.decode(item)
    -- Expiration time (seconds), with buffer for retry lifecycle
    local delay = tonumber(map['delayTime']) or 0
    local executeExpire = tonumber(map['executeExpire']) or 3600
    -- Base TTL covers delay + one execution; retry_batch.lua refreshes TTL on each retry
    local expire = delay + executeExpire
    -- Add buffer for queue wait + potential pending timeout
    local ttlBuffer = tonumber(map['ttlBuffer']) or 0
    if ttlBuffer > 0 then
        expire = expire + ttlBuffer
    end
    local id = map['id']
    local messageKey = messagePrefix .. id
    for k, v in pairs(map) do
        redis.call('HSET', messageKey, k, v)
    end
    -- Set task message expiration time
    if expire > 2592000 then expire = 2592000 end
    redis.call('EXPIRE', messageKey, expire)
    if delay > 0 then
        -- If there is a delay, push ID to timing queue
        redis.call('ZADD', timingSetKey, currentTime + delay, id)
    else
        -- Push ID to work list
        redis.call('RPUSH', workListKey, id)
    end
    count = count + 1
end
if count > 0 then
    -- Set task queue and timing queue 24-hour expiration
    redis.call('EXPIRE', workListKey, 24 * 60 * 60)
    redis.call('EXPIRE', timingSetKey, 24 * 60 * 60)
end
return count
