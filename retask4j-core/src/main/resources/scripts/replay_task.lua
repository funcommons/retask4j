-- Replay a task: reset retry counter, set status to WAITING, push to work deque
-- KEYS[1] = fu-task-{topic}-message:{id}   (hash)
-- KEYS[2] = fu-task-{topic}-blocking      (work deque)
-- ARGV[1] = task id
-- ARGV[2] = current timestamp (millis)

local taskId = ARGV[1]
local now = tonumber(ARGV[2])

-- Update retry counter and status in the message hash
redis.call('HSET', KEYS[1], 'retryTimes', '0', 'status', 'WAITING', 'error', '', 'completeTime', '0')

-- Push to work deque
redis.call('RPUSH', KEYS[2], taskId)

-- Refresh message hash TTL (24h)
redis.call('EXPIRE', KEYS[1], 86400)

return 1
