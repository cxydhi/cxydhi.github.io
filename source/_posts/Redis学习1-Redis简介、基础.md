---
title: Redis学习1-Redis简介、基础
date: 2024-04-02 20:55:26
tags: 
    - 中间件
    - Redis
---
### 1. 介绍
#### 1.1. redis简介
Redis(Remote Dictonary Server) 是由Salvatore Sanfilippo开发的key-value缓存数据库，基于C语言开发。目前市面上，Redis和MongoDB是当前使用最广泛的NoSQL，而就Redis技术而言，它的性能十分优越，可以支持每秒十几万此的读/写操作，其性能远超数据库，并且还支持集群、分布式、主从同步等配置，原则上可以无限扩展，让更多的数据存储在内存中，更让人欣慰的是它还支持一定的事务能力，这保证了高并发的场景下数据的安全和一致性。
#### 1.2. redis特点
与其他key/value缓存产品相比，redis有以下特点：
1、Redis支持数据的持久化，可以将内存中的数据保存在磁盘中，重启的时候可以再次加载进行使用；
2、 Redis不仅支持key-value类型的数据，还提供list，set，zset，hash等数据结构的存储；
3、 Redis支持数据的备份，即master-slave模式的数据备份；
### 2. Redis安装
#### 2.1. window
Redis Windows 下载地址：https://github.com/MicrosoftArchive/redis/releases
#### 2.2. linux
```
$ wget http://download.redis.io/releases/redis-4.0.2.tar.gz
$ tar xzf redis-4.0.2.tar.gz
$ cd redis-4.0.2
$ make
```
执行上述命令后，进入redis目录下的src目录
启动redis服务
```
./redis-server
```
{% asset_img 1.png %}
进入redis命令行
```
./redis-cli
```
#### 2.3. 配置项
进入redis.conf，可以修改一些配置项，主要的配置项如下：
|port|	端口号|
|---|---|
|dir	|本地数据库存放目录|
|dbfilename	|本地数据库文件名|
|bind	|绑定的主机地址|
|timeout	|设置客户端闲置多长时间后关闭连接|
|loglevel	|日志记录级别，有：debug，verbose，notice，warning，默认为verbose|
|logfile	|日志记录方式，默认为标准输出|

### 3. Redis数据类型
#### 3.1. String字符串
最基本的数据类型，一个key对应一个value，redis的string可包含任何数据，比如jpg图片或者序列化的对象，一个键最大能存储512MB的对象
示例如下：
{% asset_img 2.png %}

|命令	|说明|
|------|----|
|SET	|设置指定 key 的值|
|GET	|获取指定 key 的值|
|MSET	|同时设置一个或多个 key-value 对|
|MGET	|获取所有(一个或多个)给定 key 的值|
|INCR	|将 key 中储存的数字值增一|
|INCRBY	|将 key 所储存的值加上给定的增量值 ( increment )|
|DECR	|将 key 中储存的数字值减一|
|DECRBY	|将 key 所储存的值减去给定的减量值 ( decrement )|

#### 3.2. Hash哈希
Redis Hash是一个string类型的field和value的映射表，特别适合用于存储对象。
常用的命令为HMSET KEY FIELD VALUE FIELD VALUE，即同时将多个field-value（域-值）对设置到哈希表key中。示例如下：
{% asset_img 3.png %}
其中，user:1为键
|命令	|说明|
|---|---|
|HEXISTS	|查看哈希表 key 中，指定的字段是否存在|
|HGET	|获取存储在哈希表中指定字段的值|
|HSET	|将哈希表 key 中的字段 field 的值设为 value|
|HMSET	|同时将多个 field-value (域-值)对设置到哈希表 key 中|
|HMGET	|获取所有给定字段的值|
|HDEL	|删除一个或多个哈希表字段|

#### 3.3. List列表
Redis List为最简单的字符串列表，按照插入顺序排序，可以添加一个元素到列表的头部（左边）或尾部（右边）。
常用的命令有lpush，rpush，lrange，示例如下：
{% asset_img 4.png %}
|命令	|说明|
|--|--|
|LINDEX	|通过索引获取列表中的元素|
|LINSERT	|在列表的元素前或者后插入元素|
|LLEN	|获取列表长度|
|LPOP	|移出并获取列表的第一个元素|
|LPUSH	|将一个或多个值插入到列表头部|
|RPOP	|移除并获取列表最后一个元素|
|RPUSH	|在列表中添加一个或多个值|
|LSET	|通过索引设置列表元素的值|
|LRANGE	|获取列表指定范围内的元素|

#### 3.4. set集合
Redis是string类型的无序集合，通过哈希表实现，元素具有唯一性。
常用命令有sadd，smembers
{% asset_img 5.png %}

|命令	|说明|
|--|--|
|SADD   |向集合添加一个或多个成员|
|SCARD	|获取集合的成员数|
|SDIFF	|返回给定所有集合的差集|
|SINTER	|返回给定所有集合的交集|
|SISMEMBER	|判断 member 元素是否是集合 key 的成员|
|SMEMBERS	|返回集合中的所有成员|
|SUNION	|返回所有给定集合的并集|
|SREM	|移除集合中一个或多个成员|

#### 3.5. zset（sorted set）有序集合
和set一样是string类型元素的集合，不同的是每个元素会关联一个double类型的分数，通过分数为集合中的成员进行从小到大的排序，zset的成员是唯一的，但分数可以重复。
常用命令有：zadd，zrangeByscore
{% asset_img 6.png %}

|命令	|说明|
|--|--|
|ZADD	|向有序集合添加一个或多个成员，或者更新已存在成员的分数|
|ZCARD	|获取有序集合的成员数|
|ZCOUNT	|计算在有序集合中指定区间分数的成员数|
|ZRANGE	|通过索引区间返回有序集合成指定区间内的成员|
|ZRANGEBYSCORE	|通过分数返回有序集合指定区间内的成员|
|ZRANK	|返回有序集合中指定成员的索引|
|ZSCORE	|返回有序集中，成员的分数值|

#### 3.6. Redis Bitmap 位图
通过类似map结构存放0或1（bit位）作为值，可以用来统计状态，如日活，是否浏览过某个东西
{% asset_img 7.png %}

#### 3.7. HyperLogLogs基数统计
可以接受多个元素作为输入，并给出输入元素的基数估算值
● 基数：集合中不同元素的数量，比如 {’apple’, ‘banana’, ‘cherry’, ‘banana’, ‘apple’} 的基数就是 3
● 估算值：算法给出的基数并不是精确的，可能会比实际稍微多一些或者稍微少一些，但会控制在合 理的范围之内
HyperLogLog 的优点是即使输入元素的数量或体积非常大，计算基数所需空间总是固定的，并且是很小的。
在Redis 里面，每个 HyperLogLog 键只需要花费 12 KB 内存，就可以计算接近 2^64 个不同元素的基数
这和计算基数时，元素越多耗费内存就越多的集合形成鲜明对比
因为HyperLogLog 只会根据输入元素来计算基数，而不会储存输入元素本身，所以 HyperLogLog 不能像集合那样，返回输入的各个元素
{% asset_img 8.png %}
|命令	|说明|
|--|--|
|PFADD	|添加指定元素到 HyperLogLog 中|
|PFCOUNT	|返回给定 HyperLogLog 的基数估算值|
|PFMERGE	|将多个 HyperLogLog 合并为一个 HyperLogLog|
