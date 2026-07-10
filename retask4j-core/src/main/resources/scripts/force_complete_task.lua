-- Force complete a task: mark as SUCCESS or FAIL, write output/error, remove from all queues
-- KEYS[1] = fu-task-{topic}-message:{id}   (hash)
-- KEYS[2] = fu-task-{topic}-blocking      (work deque)
-- KEYS[3] = fu-task-{topic}-pending       (pending set)
-- KEYS[4] = fu-task-{topic}-retry         (retry set)
-- ARGV[1] = task id
-- ARGV[2] = "SUCCESS" or "FAIL"
-- ARGV[3] = output JSON (may be empty)
-- ARGV[4] = error string (may be empty)
-- ARGV[5] = current timestamp (millis)
-- ARGV[6] = callerId (for FUNCTION mode, may be empty)
-- ARGV[7] = return deque key (for FUNCTION mode, may be empty)
-- ARGV[8] = callback deque key (for CALLBACK mode, may be empty)

local taskId = ARGV[1]
local status = ARGV[2]
local output = ARGV[3]
local errorMsg = ARGV[4]
local now = tonumber(ARGV[5])
local callerId = ARGV[6]
local returnDequeKey = ARGV[7]
local callbackDequeKey = ARGV[8]

-- Remove from all working queues
redis.call('LREM', KEYS[2], 0, taskId)
redis.call('ZREM', KEYS[3], taskId)
redis.call('ZREM', KEYS[4], taskId)

-- Update message hash
redis.call('HSET', KEYS[1],
    'status', status,
    'output', output,
    'error', errorMsg,
    'completeTime', tostring(now))

-- Refresh message hash TTL (24h)
redis.call('EXPIRE', KEYS[1], 86400)

-- Route result to FUNCTION mode return deque if applicable
if returnDequeKey ~= '' and callerId ~= '' then
    local resultKey = 'fu-task-result-' .. callerId
    redis.call('HSET', resultKey, taskId, output)
    redis.call('EXPIRE', resultKey, 86400)
    redis.call('RPUSH', returnDequeKey, taskId)
    redis.call('EXPIRE', returnDequeKey, 86400)
end

-- Route to callback deque if applicable
if callbackDequeKey ~= '' then
    redis.call('RPUSH', callbackDequeKey, taskId)
    redis.call('EXPIRE', callbackDequeKey, 86400)
end

return 1
