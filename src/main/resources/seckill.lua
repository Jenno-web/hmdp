--优惠卷ID
local voucherId=ARGV[1]
--用户ID
local userId=ARGV[2]
--库存value的Key
local stockKey='seckill:stock:'..voucherId
--订单sat的Key
local orderId='seckill:order:'..voucherId

--判断库存是否充足
if(tonumber(redis.call('get',stockKey))<=0)then
	--不足，不能买
	return 1
	end

--判断是否下过单
if(redis.call('sismember',orderId,userId)==1)then
	--下过，不准买
	return 2
	end

--可以买，库存减1，下过单
redis.call('incrby',stockKey,-1)
redis.call('sadd',orderId,userId)
return 0