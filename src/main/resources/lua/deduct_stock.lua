# Lua 脚本：原子扣减库存（防止超卖）
-- KEYS[1]: 库存 Key
-- ARGV[1]: 扣减数量
-- ARGV[2]: 版本号（乐观锁）

local stock = redis.call('get', KEYS[1])
if not stock then
    return -1  -- 库存不存在
end

local current = tonumber(stock)
local deduct = tonumber(ARGV[1])

if current < deduct then
    return -2  -- 库存不足
end

redis.call('decrby', KEYS[1], deduct)
return current - deduct
