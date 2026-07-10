-- Get prefix from KEYS parameter
local retrySetKey = KEYS[1]
local workListKey = KEYS[2]
local messagePrefix = KEYS[3]
local pendingSetKey = KEYS[4]
-- Get from ARGV parameter
local items = ARGV
local count = 0
local currentTime = tonumber(redis.call('TIME')[1])
-- Iterate each item
for i, item in ipairs(items) do
    local map = cjson.decode(item)
    -- Retry time (seconds)
    local scheduleTime = currentTime + (tonumber(map['retryDelay']) or 0)
    map['scheduleTime'] = scheduleTime
    local id = map['id']
    local messageKey = messagePrefix .. id
    -- ACK: remove from pendingSet (always, even if hash expired, to avoid stuck pending entries)
    redis.call('ZREM',pendingSetKey,id)
    -- Check if key exists
    if redis.call('EXISTS', messageKey) == 1 then
        -- If exists, update key value
        for k, v in pairs(map) do
            redis.call('HSET', messageKey, k, v)
        end
        -- Refresh hash TTL to cover the retry window
        local retryDelay = tonumber(map['retryDelay']) or 0
        local executeExpire = tonumber(map['executeExpire']) or 3600
        local ttl = retryDelay + executeExpire
        if ttl > 2592000 then ttl = 2592000 end
        if ttl > 0 then
            redis.call('EXPIRE', messageKey, ttl)
        end
        if scheduleTime > currentTime then
            -- If there is a delay, push ID to retry queue
            redis.call('ZADD', retrySetKey, scheduleTime , id)
        else
            -- Push ID to work list
            redis.call('RPUSH', workListKey, id)
        end
        count = count + 1
    else
        -- Hash expired before retry. For FUNCTION/CALLBACK mode, recreate the hash
        -- so the task can still be retried and eventually deliver its result.
        -- For NORMAL mode, the task is fire-and-forget — dropping is acceptable.
        if map['mode'] == 'FUNCTION' or map['mode'] == 'CALLBACK' then
            for k, v in pairs(map) do
                redis.call('HSET', messageKey, k, v)
            end
            local retryDelay = tonumber(map['retryDelay']) or 0
            local executeExpire = tonumber(map['executeExpire']) or 3600
            local ttl = retryDelay + executeExpire
            if ttl > 2592000 then ttl = 2592000 end
            if ttl > 0 then
                redis.call('EXPIRE', messageKey, ttl)
            end
            if scheduleTime > currentTime then
                redis.call('ZADD', retrySetKey, scheduleTime, id)
            else
                redis.call('RPUSH', workListKey, id)
            end
            count = count + 1
        end
    end
end
if count > 0 then
    -- Set task queue and retry queue 24-hour expiration
    redis.call('EXPIRE', workListKey, 24 * 60 * 60)
    redis.call('EXPIRE', retrySetKey, 24 * 60 * 60)
end
return count
