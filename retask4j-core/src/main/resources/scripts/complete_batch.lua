-- Get prefix from KEYS parameter
local messagePrefix = KEYS[1]
local pendingSetKey = KEYS[2]
local funcPrefix =  KEYS[3]
local callbackListKey =  KEYS[4]
-- Get from ARGV parameter
local items = ARGV
local count = 0
local callback_count = 0
local currentTime = tonumber(redis.call('TIME')[1])
-- Iterate each item
for i, item in ipairs(items) do
    local map = cjson.decode(item)
    -- Expiration time (seconds)
    local expire = tonumber(map['resultExpire']) or 0
    if expire > 2592000 then expire = 2592000 end
    local id = map['id']
    local messageKey = messagePrefix .. id
    -- Clear stale fields that may persist from a previous retry cycle.
    -- toCompleteMap() omits null-valued fields (error, output), so if a task
    -- previously failed and now succeeds, the old error would remain in the hash.
    if map['error'] == nil then
        redis.call('HDEL', messageKey, 'error')
    end
    if map['output'] == nil then
        redis.call('HDEL', messageKey, 'output')
    end
    -- ACK: remove from pendingSet BEFORE any DEL to prevent task loss on failover
    redis.call('ZREM',pendingSetKey,id)
    -- Whether to retain task result
    if expire > 0 then
        -- Check if key exists
        if redis.call('EXISTS', messageKey) == 1 then
            -- Guard: skip overwrite if task already completed (prevents stale compete from clobbering)
            local currentStatus = redis.call('HGET', messageKey, 'status')
            if currentStatus == 'SUCCESS' or currentStatus == 'FAIL' then
                count = count + 1
            else
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                -- Set hash expiration time
                redis.call('EXPIRE', messageKey, expire)
                count = count + 1
                -- Only route result when message exists
                if map['mode'] == 'FUNCTION' and map['callerId'] then
                    local funcListKey = funcPrefix .. map['callerId']
                    redis.call('RPUSH', funcListKey,id)
                    -- Set return queue 24-hour expiration
                    redis.call('EXPIRE', funcListKey, 24 * 60 * 60)
                elseif map['mode'] == 'CALLBACK' then
                    redis.call('RPUSH', callbackListKey,id)
                    callback_count = callback_count + 1
                end
            end
        else
            -- Hash expired before completion with resultExpire > 0.
            -- NORMAL mode: ACK is sufficient. FUNCTION/CALLBACK: recreate hash to deliver result.
            if map['mode'] == 'FUNCTION' and map['callerId'] then
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                redis.call('EXPIRE', messageKey, expire)
                local funcListKey = funcPrefix .. map['callerId']
                redis.call('RPUSH', funcListKey, id)
                redis.call('EXPIRE', funcListKey, 24 * 60 * 60)
                count = count + 1
            elseif map['mode'] == 'CALLBACK' then
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                redis.call('EXPIRE', messageKey, expire)
                redis.call('RPUSH', callbackListKey, id)
                callback_count = callback_count + 1
                count = count + 1
            else
                count = count + 1
            end
        end
    else
        if redis.call('EXISTS', messageKey) == 1 then
            -- Guard: skip overwrite if task already completed
            local currentStatus = redis.call('HGET', messageKey, 'status')
            if currentStatus == 'SUCCESS' or currentStatus == 'FAIL' then
                count = count + 1
            elseif map['mode'] == 'FUNCTION' and map['callerId'] then
                -- Write completion data before routing result so caller reads correct state
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                local funcListKey = funcPrefix .. map['callerId']
                redis.call('RPUSH', funcListKey, id)
                redis.call('EXPIRE', funcListKey, 24 * 60 * 60)
                -- Keep hash alive for func result retrieval; caller invalidates after reading
                redis.call('EXPIRE', messageKey, 24 * 60 * 60)
                count = count + 1
            elseif map['mode'] == 'CALLBACK' then
                -- Write completion data before routing callback
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                redis.call('RPUSH', callbackListKey, id)
                callback_count = callback_count + 1
                -- Keep hash alive for callback consumer; set_callback_batch.lua will clean up
                redis.call('EXPIRE', messageKey, 24 * 60 * 60)
                count = count + 1
            else
                -- NORMAL mode with no result retention: delete immediately
                redis.call('DEL', messageKey)
                count = count + 1
            end
        else
            -- Hash expired before completion. For NORMAL mode, ACK is sufficient (fire-and-forget).
            -- For FUNCTION/CALLBACK mode, recreate a minimal hash with completion data so the
            -- caller can at least read the terminal status and error instead of hanging until timeout.
            if map['mode'] == 'FUNCTION' and map['callerId'] then
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                redis.call('EXPIRE', messageKey, 300)
                local funcListKey = funcPrefix .. map['callerId']
                redis.call('RPUSH', funcListKey, id)
                redis.call('EXPIRE', funcListKey, 24 * 60 * 60)
                count = count + 1
            elseif map['mode'] == 'CALLBACK' then
                for k, v in pairs(map) do
                    redis.call('HSET', messageKey, k, v)
                end
                redis.call('EXPIRE', messageKey, 300)
                redis.call('RPUSH', callbackListKey, id)
                callback_count = callback_count + 1
                count = count + 1
            else
                count = count + 1
            end
        end
    end
end
if callback_count > 0 then
    -- Set callback queue 24-hour expiration
    redis.call('EXPIRE', callbackListKey, 24 * 60 * 60)
end
return count
