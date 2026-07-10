local messagePrefix = KEYS[1]
local ids = ARGV
local results = {};
for i, id in ipairs(ids) do
    if id ~= '' then
        -- Build task message hash key
        local messageKey = messagePrefix .. id
        -- Check if key exists
        if redis.call('EXISTS', messageKey) == 1 then
            -- Create a table to store the specified key-value pairs of the current hash
            local hashTable = {}
            -- Get all fields from hash
            local fields = redis.call("HGETALL", messageKey)
            -- Iterate each field
            for j = 1, #fields, 2 do
                local field = fields[j]
                local value = fields[j + 1]
                hashTable[field] = value
            end
            -- Add the specified key-value pairs of the current hash to the results table
            table.insert(results, cjson.encode(hashTable))
        end
    end
end
-- Return multiple task messages
return results
