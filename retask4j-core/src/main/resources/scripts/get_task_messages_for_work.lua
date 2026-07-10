local workListKey = KEYS[1]
local pendingSetKey = KEYS[2]
local messagePrefix = KEYS[3]
local maxCount = tonumber(ARGV[1])
-- Defense-in-depth: cap maxCount to prevent Lua scripts from running too long
if maxCount == nil or maxCount < 1 then maxCount = 1 end
if maxCount > 500 then maxCount = 500 end
local timeout = tonumber(ARGV[2])
local setPendingStatus = tonumber(ARGV[3]) == 1
local currentTime = tonumber(redis.call('TIME')[1])
-- Fields
local fields = cjson.decode(ARGV[4])
-- IMPORTANT: This script is NOT atomic across the LPOP + ZADD operations.
-- If Redis partitions (network split) between LPOP and ZADD, a task could be
-- lost from both the work list and the pending set, leading to duplicate execution
-- on pending timeout re-delivery. This is an inherent limitation of the current
-- deque + sorted-set architecture. Migration to Redis Streams would provide
-- XAUTOCLAIM-style atomic handoff.
local results = {};
local pendingItems = {}
for i = 1, maxCount, 1 do
    local id = redis.call('LPOP', workListKey);
    if id ~= false then
        -- Build task message hash key
        local messageKey = messagePrefix .. id
        -- Check if key exists
        if redis.call('EXISTS', messageKey) == 1 then
            -- Create a table to store the specified key-value pairs of the current hash
            local hashTable = {}
            -- Iterate each field
            for j, field in ipairs(fields) do
                -- Use HGET command to get the value of the specified key
                local value = redis.call('HGET', messageKey, field)
                if value then
                 -- If value exists, add it to the current hash table
                 hashTable[field] = value
                end
            end
            -- Set PENDING status only for work queue items (not callback queue items)
            -- Only set PENDING if task is currently WAITING, to avoid overwriting a completed status
            -- in the rare race between pending timeout re-delivery and complete_batch completion
            if setPendingStatus then
                local currentStatus = redis.call('HGET', messageKey, 'status')
                if currentStatus == 'WAITING' then
                    redis.call('HSET', messageKey, 'status', 'PENDING')
                end
            end
            -- Refresh TTL to ensure hash survives until worker completes or pending timeout fires
            local currentTtl = redis.call('TTL', messageKey)
            -- -1 = no expiry (orphaned key), -2 = key gone (shouldn't happen after EXISTS)
            -- Only extend TTL, never reduce it (pendingTimeout may be less than executeExpire)
            if currentTtl >= 0 and currentTtl < timeout then
                redis.call('EXPIRE', messageKey, timeout)
            elseif currentTtl == -1 then
                redis.call('EXPIRE', messageKey, timeout)
            end
            table.insert(pendingItems, currentTime + timeout)
            table.insert(pendingItems, id)
            -- Add the specified key-value pairs of the current hash to the results table
            table.insert(results, cjson.encode(hashTable))
        else
            -- Hash expired: skip entirely, no need to ACK a non-existent task
        end
    else
        -- If the list is empty or no more elements to pop, break
        break
    end
end
-- If task messages were retrieved
if #results > 0 then
    -- Chunk ZADD to avoid Lua stack overflow with unpack()
    local CHUNK = 250
    for j = 1, #pendingItems, CHUNK * 2 do
        local chunk = {}
        for k = j, math.min(j + CHUNK * 2 - 1, #pendingItems) do
            table.insert(chunk, pendingItems[k])
        end
        redis.call('ZADD', pendingSetKey, unpack(chunk))
    end
    -- Set pending set 24-hour expiration
    redis.call('EXPIRE', pendingSetKey, 24 * 60 * 60)
end
-- Return multiple task messages
return results
