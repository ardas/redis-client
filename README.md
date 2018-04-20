## Redis Client
This implementation extends `StringRedisClient` and allowing send requests like RestFul but uses Redis.

#### Description
Redis client uses Application ObjectMapper for Serialize and Deserialize DTO.
If application is not Web then will create default ObjectMapper. 

#### Properties
| Property  | Default | Note |
| ------------- | ------------- | ------------- | 
| redis.client.timeout  | 1 | Count seconds for waiting of response. |
| redis.client.thread-pool  | 5 | The number of threads in the pool  |
