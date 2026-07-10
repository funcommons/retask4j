-- Delete a task from all queues atomically
-- KEYS[1] = fu-task-{topic}-message:{id}   (hash)
-- KEYS[2] = fu-task-{topic}-blocking      (work deque)
-- KEYS[3] = fu-task-{topic}-timing        (timing set, score = execute timestamp)
-- KEYS[4] = fu-task-{topic}-pending       (pending set, score = timeout)
-- KEYS[5] = fu-task-{topic}-retry         (retry set, score = retry timestamp)
-- KEYS[6] = fu-task-{topic}-callback      (callback deque)
-- KEYS[7] = fu-task-{topic}-callback-pending (callback pending set)
-- ARGV[1] = task id

local taskId = ARGV[1]
local removed = {}

-- Remove from work deque (LREM removes 0 = all matching)
local r1 = redis.call('LREM', KEYS[2], 0, taskId)
table.insert(removed, r1)

-- Remove from timing set
local r2 = redis.call('ZREM', KEYS[3], taskId)
table.insert(removed, r2)

-- Remove from pending set
local r3 = redis.call('ZREM', KEYS[4], taskId)
table.insert(removed, r3)

-- Remove from retry set
local r4 = redis.call('ZREM', KEYS[5], taskId)
table.insert(removed, r4)

-- Remove from callback deque
local r5 = redis.call('LREM', KEYS[6], 0, taskId)
table.insert(removed, r5)

-- Remove from callback pending set
local r6 = redis.call('ZREM', KEYS[7], taskId)
table.insert(removed, r6)

-- Delete the message hash
local r7 = redis.call('DEL', KEYS[1])
table.insert(removed, r7)

-- Return: total removed count (sum of all removals + 1 if hash deleted)
local total = 0
for i, v in ipairs(removed) do
    total = total + tonumber(v)
end

return total
