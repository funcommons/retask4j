-- Get prefix from KEYS parameter
local messagePrefix = KEYS[1]
local callbackPendingSetKey = KEYS[2]
-- Get from ARGV parameter
local timeout = tonumber(ARGV[1])
local items = ARGV
local count = 0
local pending_count = 0
local currentTime = tonumber(redis.call('TIME')[1])
-- Iterate each item, starting from the 2nd (the 1st is the timeout parameter)
for i = 2, #items do
    local map = cjson.decode(items[i])
    -- Expiration time (seconds)
    local id = map['id']
    local messageKey = messagePrefix .. id
    -- ACK: remove from pendingSet
    redis.call('ZREM',callbackPendingSetKey,id)
    -- Check if key exists
    if redis.call('EXISTS', messageKey) == 1 then
        for k, v in pairs(map) do
            redis.call('HSET', messageKey, k, v)
        end
        -- Refresh message hash expiration to cover retry window
        local resultExpire = tonumber(map['resultExpire'])
        if resultExpire and resultExpire > 0 then
            redis.call('EXPIRE', messageKey, resultExpire)
        else
            -- No result retention: clean up hash when callback is done
            if map['callbackStatus'] == 'SUCCESS' or map['callbackStatus'] == 'FAIL' then
                redis.call('DEL', messageKey)
            else
                -- Still retrying: refresh TTL to cover the next retry window
                redis.call('EXPIRE', messageKey, timeout + 60)
            end
        end
        if map['callbackStatus'] ~= 'SUCCESS' and map['callbackStatus'] ~= 'FAIL' then
            redis.call('ZADD', callbackPendingSetKey, currentTime + timeout, id)
            pending_count = pending_count + 1
        end
        count = count + 1
    else
        -- Hash expired before callback processing. Recreate from callback map data
        -- so the callback can still be retried or completed.
        if map['callbackStatus'] == 'SUCCESS' or map['callbackStatus'] == 'FAIL' then
            -- Terminal status: recreate hash so caller can read the result
            for k, v in pairs(map) do
                redis.call('HSET', messageKey, k, v)
            end
            local resultExpire = tonumber(map['resultExpire'])
            if resultExpire and resultExpire > 0 then
                redis.call('EXPIRE', messageKey, resultExpire)
            else
                redis.call('EXPIRE', messageKey, 300)
            end
            count = count + 1
        else
            -- Still retrying: recreate hash and re-queue for next retry
            for k, v in pairs(map) do
                redis.call('HSET', messageKey, k, v)
            end
            redis.call('EXPIRE', messageKey, timeout + 60)
            redis.call('ZADD', callbackPendingSetKey, currentTime + timeout, id)
            pending_count = pending_count + 1
            count = count + 1
        end
    end
end
--
if pending_count > 0 then
    -- Set callback retry queue 24-hour expiration
    redis.call('EXPIRE', callbackPendingSetKey, 24 * 60 * 60)
end
return count
