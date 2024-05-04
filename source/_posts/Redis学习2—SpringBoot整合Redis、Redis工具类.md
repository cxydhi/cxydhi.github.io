---
title: Redis学习2—SpringBoot整合Redis、Redis工具类
date: 2024-04-02 21:08:13
tags: 
    - 中间件
    - Redis
---
### 1. 依赖和配置
#### 1.1. pom.xml
SpringBoot整合Redis，需要引入spring-boot-starter-data-redis依赖
```xml
  <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
```
当我们需要配置redis的连接池时，还需要引入commons-pool2依赖
```xml
 <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>
```
application.yml配置
```yml
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    database: 0
    jedis:
      pool:
        max-idle: 8
        max-active: 8
        min-idle: 0
```
这里的jedis指的是使用jedis客户端，这里也可以将jedis改为lettuce，表示使用lettuce客户端，客户端下面的pool表示的是连接池的相关配置。
上述的配置针对的是单机redis配置，如果使用的是集群，配置如下：
```yml
spring:
  redis:
    password: 123456
    cluster:
      nodes: 10.255.144.115:7001,10.255.144.115:7002,10.255.144.115:7003,10.255.144.115:7004,10.255.144.115:7005,10.255.144.115:7006
      max-redirects: 3
```
### 2. RedisTemplate
上述的配置完毕后，我们就可以在项目中操作redis了，操作的时候，可以直接使用spring-boot-starter-data-redis为我们提供的RedisTemplate这个类，也可以使用StringRedisTemplate，两者的方法基本一致，后者可以看作RedisTemplate<String, String> 。
在上一节中，提到，Redis有多种数据类型，比如string、list、hash、set、zset等，这些类型的操作在RedisTemplate分别对应opsForValue(), opsForList(), opsForHash(), opsForSet(), opsForZSet()。
具体的操作演示如下：
```java
@SpringBootTest
public class RedisAppTest {
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Test
    public void testGet() {
        System.out.println("字符串操作=================");
        redisTemplate.opsForValue().set("name", "cxy");
        System.out.println(redisTemplate.opsForValue().get("name"));

        System.out.println("列表操作");
        redisTemplate.opsForList().leftPush("school", "华南理工大学");
        redisTemplate.opsForList().leftPush("school", "中山大学");
        redisTemplate.opsForList().leftPush("school", "华南农业大学");
        List<String> school = redisTemplate.opsForList().range("school", 0, 5);
        for (String s : school) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("hash操作");
        redisTemplate.opsForHash().put("student", "name", "cxy");
        redisTemplate.opsForHash().put("student", "age", "21");
        redisTemplate.opsForHash().put("student", "sex", "male");
        System.out.println(redisTemplate.opsForHash().get("student", "name"));
        System.out.println(redisTemplate.opsForHash().get("student", "age"));
        System.out.println(redisTemplate.opsForHash().get("student", "sex"));

        System.out.println("set操作");
        redisTemplate.opsForSet().add("project", "软件工程", "计算机技术", "通信工程", "软件工程");
        Set<String> project = redisTemplate.opsForSet().members("project");
        for (String s : project) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("zset操作");
        redisTemplate.opsForZSet().add("movie", "热辣滚烫", 2.7d);
        redisTemplate.opsForZSet().add("movie", "飞驰人生", 9.1d);
        redisTemplate.opsForZSet().add("movie", "熊出没之逆转时空", 9.3d);
        redisTemplate.opsForZSet().add("movie", "二十一条", 8.2d);
        Set<String> movie = redisTemplate.opsForZSet().rangeByScore("movie", 3.0d, 9.3d);
        for (String s : movie) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("基数操作");
        redisTemplate.opsForHyperLogLog().add("accessIp", "127.0.0.1");
        redisTemplate.opsForHyperLogLog().add("accessIp", "192.168.10.1");
        redisTemplate.opsForHyperLogLog().add("accessIp", "192.168.0.1");
        redisTemplate.opsForHyperLogLog().add("accessIp", "33.45.23.1");
        System.out.println(redisTemplate.opsForHyperLogLog().size("accessIp"));

    }

}
```
运行结果如下：
{% asset_img 1.png %}
### 3. RedisUtils工具类
虽然RedisTemplate提供的redis操作很全面，但对于不了解redis的开发同学来说，直接看RedisTemplate的方法不够见名知义，因此一般情况下，我们都会单独封装一个工具类，将常用的一些方法进行抽象，以方便后续使用。
创建Redis配置类，配置RedisTemplate
```java
@Configuration
public class RedisConfiguration {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        return redisTemplate;
    }
}
```
工具类代码如下：
```java
package org.example.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
public class RedisUtils {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public boolean removeKey(String key) {
        return redisTemplate.delete(key);
    }

    public boolean hasKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return false;
        }
        return redisTemplate.hasKey(key);
    }

    public boolean expire(String key, long time) {
        return expire(key, time, TimeUnit.SECONDS);
    }

    public boolean expire(String key, long time, TimeUnit timeUnit) {
        if (StringUtils.isEmpty(key)) {
            return false;
        }
        return redisTemplate.expire(key, time, timeUnit);
    }

    public void addKey(String key, Object value, long time) {
        addKey(key, value, time, TimeUnit.SECONDS);
    }

    public void addKey(String key, Object value, long time, TimeUnit timeUnit) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForValue().set(key, value, time, timeUnit);
    }

    public void addKey(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public Object getKey(String key) {
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        return redisTemplate.opsForValue().get(key);
    }

    public void increment(String key) {
        increment(key, 1L);
    }

    public void increment(String key, long delta) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForValue().increment(key, delta);
    }

    public void decrement(String key) {
        decrement(key, 1L);
    }

    public void decrement(String key, long delta) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForValue().decrement(key, delta);
    }

    public void leftPush(String key, List<Object> values) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForList().leftPushAll(key, values);
    }

    public void leftPush(String key, Object... value) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForList().leftPushAll(key, value);
    }

    public Object leftPop(String key) {
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        return redisTemplate.opsForList().leftPop(key);
    }

    public List<Object> range(String key, long start, long end) {
        if (StringUtils.isEmpty(key)) {
            return new ArrayList<>();
        }
        return redisTemplate.opsForList().range(key, start, end);
    }

    public Object indexOfList(String key, int index) {
        if (StringUtils.isEmpty(key)) {
            return null;
        }
        return redisTemplate.opsForList().index(key, index);
    }

    public void addHash(String key, Map<String, Object> map) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        if (map == null || map.isEmpty()) {
            return;
        }
        map.forEach((field, value) -> {
            redisTemplate.opsForHash().put(key, field, value);
        });
    }

    public void addField(String key, String field, Object value) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(field)) {
            return;
        }
        redisTemplate.opsForHash().put(key, field, value);
    }

     public void removeField(String key, String field) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(field)) {
            return;
        }
        redisTemplate.opsForHash().delete(key, field);
    }

    public Map<String, Object> getEntries(String key) {
        if (StringUtils.isEmpty(key)) {
            return new HashMap<>();
        }
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        Map<String, Object> result = new HashMap<>();
        entries.forEach((k, v) -> {
            result.put(k.toString(), v);
        });
        return result;
    }

    public Object getField(String key, String field) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(field)) {
            return null;
        }
        return redisTemplate.opsForHash().get(key, field);
    }

    public Boolean hasField(String key, String field) {
        if (StringUtils.isEmpty(key) || StringUtils.isEmpty(field)) {
            return false;
        }
        return redisTemplate.opsForHash().hasKey(key, field);
    }

    public void addSet(String key, String... value) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForSet().add(key, value);
    }

    public Set<Object> members(String key) {
        if (StringUtils.isEmpty(key)) {
            return new HashSet<>();
        }
        return redisTemplate.opsForSet().members(key);
    }

    public Boolean isMember(String key, Object value) {
        if (StringUtils.isEmpty(key)) {
            return false;
        }
        return redisTemplate.opsForSet().isMember(key, value);
    }

    public void addZSet(String key, Object value, Double score) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForZSet().add(key, value, score);
    }

    public Set<Object> rangeByScore(String key, Double min, Double max) {
        if (StringUtils.isEmpty(key)) {
            return new HashSet<>();
        }
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    public Set<ZSetOperations.TypedTuple<Object>> rangeByScoreWithScore(String key, Double min, Double max) {
        if (StringUtils.isEmpty(key)) {
            return new HashSet<>();
        }
       return redisTemplate.opsForZSet().rangeByScoreWithScores(key, min, max);
    }

    public void addHyperLogLog(String key, Object... value) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForHyperLogLog().add(key, value);
    }

    public void addHyperLogLog(String key, List value) {
        if (StringUtils.isEmpty(key)) {
            return;
        }
        redisTemplate.opsForHyperLogLog().add(key, value);
    }

    public Long countHyperLogLog(String key) {
        if (StringUtils.isEmpty(key)) {
            return 0L;
        }
        return redisTemplate.opsForHyperLogLog().size(key);
    }
}
```
修改之前的测试代码，将redisTemplate，改为我们的工具类
```java
@Autowired
    private RedisUtils redisUtils;

    @Test
    public void testRedisUtils() {
        System.out.println("字符串操作=================");
       redisUtils.addKey("name", "cxy");
        System.out.println(redisUtils.getKey("name"));

        System.out.println("列表操作");
        redisUtils.leftPush("school", "华南理工大学");
        redisUtils.leftPush("school", "中山大学");
        redisUtils.leftPush("school", "华南农业大学");
        List<Object> school = redisUtils.range("school", 0, 5);
        for (Object s : school) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("hash操作");
        redisUtils.addField("student", "name", "cxy");
        redisUtils.addField("student", "age", "21");
        redisUtils.addField("student", "sex", "male");
        System.out.println(redisUtils.getField("student", "name"));
        System.out.println(redisUtils.getField("student", "age"));
        System.out.println(redisUtils.getField("student", "sex"));

        System.out.println("set操作");
        redisUtils.addSet("project", "软件工程", "计算机技术", "通信工程", "软件工程");
        Set<Object> project = redisUtils.members("project");
        for (Object s : project) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("zset操作");
        redisUtils.addZSet("movie", "热辣滚烫", 2.7d);
        redisUtils.addZSet("movie", "飞驰人生", 9.1d);
        redisUtils.addZSet("movie", "熊出没之逆转时空", 9.3d);
        redisUtils.addZSet("movie", "二十一条", 8.2d);
        Set<Object> movie = redisUtils.rangeByScore("movie", 3.0d, 9.3d);
        for (Object s : movie) {
            System.out.print(s + " ");
        }
        System.out.println();

        System.out.println("基数操作");
        redisUtils.addHyperLogLog("accessIp", "127.0.0.1");
        redisUtils.addHyperLogLog("accessIp", "192.168.10.1");
        redisUtils.addHyperLogLog("accessIp", "192.168.0.1");
        redisUtils.addHyperLogLog("accessIp", "33.45.23.1");
        System.out.println(redisUtils.countHyperLogLog("accessIp"));
    }
```
在测试之前，先删除之前添加的数据
{% asset_img 2.png %}
测试结果如下：
{% asset_img 3.png %}