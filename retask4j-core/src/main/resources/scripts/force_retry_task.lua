-- Force retry a task: push it to the work deque immediately, ignoring retryDelay
-- KEYS[1] = fu-task-{topic}-message:{id}   (hash)
-- KEYS[2] = fu-task-{topic}-blocking      (work deque)
-- KEYS[3] = fu-task-{topic}-pending       (pending set, to remove from)
-- ARGV[1] = task id

local taskId = ARGV[1]

-- Remove from pending set (it may be there)
redis.call('ZREM', KEYS[3], taskId)

-- Push to work deque
redis.call('RPUSH', KEYS[2], taskId)

return 1
