local scoredSetKey = KEYS[1]
local workListKey = KEYS[2]
local messagePrefix = KEYS[3]
local currentTime = tonumber(redis.call('TIME')[1])
local maxCount = tonumber(ARGV[1])
-- Defense-in-depth: cap maxCount to prevent Lua scripts from running too long
if maxCount == nil or maxCount < 1 then maxCount = 1 end
if maxCount > 500 then maxCount = 500 end
local resetStatus = tonumber(ARGV[2]) == 1
-- Return message IDs that have been consumed (past their scheduled time)
local entries = redis.call('ZRANGEBYSCORE', scoredSetKey, '-inf', currentTime, 'LIMIT', 0, maxCount)
-- Create a table to store message IDs
local messageIds = {}
local redeliverIds = {}
-- ZRANGEBYSCORE returns a list of member values (not value-score pairs)
for i = 1, #entries do
    table.insert(messageIds, entries[i])
end
-- If there are values to move, process them
if #messageIds > 0 then
    -- Check current status before re-delivering to avoid duplicate execution
    if resetStatus then
        for i = 1, #messageIds do
            local messageKey = messagePrefix .. messageIds[i]
            if redis.call('EXISTS', messageKey) == 1 then
                local currentStatus = redis.call('HGET', messageKey, 'status')
                if currentStatus == 'SUCCESS' or currentStatus == 'FAIL' then
                    -- Task already completed; just remove from scored set, do NOT re-deliver
                else
                    redis.call('HSET', messageKey, 'status', 'WAITING')
                    table.insert(redeliverIds, messageIds[i])
                end
            end
        end
    else
        -- No status reset needed (e.g., callback pending) — re-deliver all
        for i = 1, #messageIds do
            table.insert(redeliverIds, messageIds[i])
        end
    end
    -- Chunk RPUSH/ZREM to avoid Lua stack overflow with unpack()
    local CHUNK = 500
    if #redeliverIds > 0 then
        for j = 1, #redeliverIds, CHUNK do
            local chunk = {}
            for k = j, math.min(j + CHUNK - 1, #redeliverIds) do
                table.insert(chunk, redeliverIds[k])
            end
            redis.call('RPUSH', workListKey, unpack(chunk))
        end
        -- Set task queue 24-hour expiration
        redis.call('EXPIRE', workListKey, 24 * 60 * 60)
    end
    -- Remove these values from the sorted set (always remove, even if task was completed)
    for j = 1, #messageIds, CHUNK do
        local chunk = {}
        for k = j, math.min(j + CHUNK - 1, #messageIds) do
            table.insert(chunk, messageIds[k])
        end
        redis.call('ZREM', scoredSetKey, unpack(chunk))
    end
end
-- Return the number of moved elements
return #redeliverIds
