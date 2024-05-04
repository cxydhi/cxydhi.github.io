---
title: Redis学习5-Redis应用之签到
date: 2024-04-02 21:34:00
tags: 
    - 中间件
    - Redis
---
### 1. Redis位图bitMap
位图由一系列二进制位组成，每个位可以被设置为1或0，当我们在处理需要高效存储和操作大量二进制位数据的适合，位图是一个非常有用的工具。
位图操作命令有：
1. SETBIT：设置位图中指定位置的位的值。可以将位设置为 0 或 1。
2. GETBIT：获取位图中指定位置的位的值。
3. BITCOUNT：计算位图中置为 1 的位的数量。
4. BITOP：对多个位图执行逻辑运算（AND、OR、XOR、NOT）。
5. BITFIELD：执行复杂的位字段操作，允许你在位图上进行位级别的读写操作。
其中，用的最多的是前三个操作，示例如下：
{% asset_img 1.png %}
位图的应用十分广泛，包括但不限于以下几方面：
● 统计用户活跃度：可以使用位图追踪用户的登录活动，每个用户对应一个位图，每天的登录状态可以用一个二进制位表示，通过 BITOP 命令可以计算多个用户的交集，从而得到活跃用户的统计信息。
● 数据压缩：位图可以高效地存储大量的二进制数据，比如布隆过滤器（Bloom Filter）就是基于位图实现的一种数据结构，用于快速判断元素是否存在。
● 事件计数：可以使用位图记录每天不同时间段的事件发生情况，比如网站的访问量，每个时间段对应一个位图，每次事件发生时将对应的位设置为 1，通过 BITCOUNT 命令可以计算出每个时间段的事件数量。
● 权限管理：可以使用位图来管理用户的权限，每个用户对应一个位图，每个权限对应一个二进制位，通过 BITOP 命令可以进行权限的并集、交集等操作。
### 2. RedisTemplate操作位图
在之前的几篇文章中，我们总结了一个Redis工具类，但是那个工具类中，并没有和位图相关的操作，这里添加和位图操作相关的方法：
```java
   // value: true为1， false为0
    public boolean setBit(String key, int offset, boolean value) {
        return redisTemplate.opsForValue().setBit(key, offset, value);
    }

    public boolean getBit(String key, int offset) {
        return redisTemplate.opsForValue().getBit(key, offset);
    }

    /**
     * 统计对应值为1 的数量
     * @param key
     * @return
     */
    public long bitCount(String key) {
        if (StringUtils.isEmpty(key)) {
            return 0L;
        }
        return redisTemplate.execute((RedisCallback<Long>) con -> con.bitCount(key.getBytes()));
    }

    /**
     * 统计在字节范围内，对应值为1的数量
     * @param key
     * @param start
     * @param end
     * @return
     */
    public Long bitCount(String key, long start, long end) {
        return redisTemplate.execute((RedisCallback<Long>) con -> con.bitCount(key.getBytes(), start, end));
    }
```
添加测试类，用于测试位图操作：
```java
package org.example;

import org.example.util.RedisUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisBitMapTest {
    @Autowired
    private RedisUtils redisUtils;

    @Test
    public void testBitMap() {
        redisUtils.setBit("bit", 0, true);
        redisUtils.setBit("bit", 1, true);
        redisUtils.setBit("bit", 3, true);
        redisUtils.setBit("bit", 7, true);
        System.out.println(redisUtils.bitCount("bit"));

    }
}
```
执行结果如下：
{% asset_img 2.png %}
我们通过Redis可视化工具，查看bit的值，可以看出其二进制值与我们操作的一致
{% asset_img 3.png %}
### 3. 位图应用之签到
在很多时候，我们遇到用户签到的场景，用户进入应用时，获取用户当天的签到情况，如果没有签到，用户可以签到，一般这种功能，可以通过set数据结构或bitMap来实现，但bitMap和set相比，其占用的空间更小，因此我们选择使用bitMap来实现签到的功能。
SignService：
```java
package org.example.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class SignService {
    @Autowired
    private RedisUtils redisUtils;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 签到
     * @param id
     */
    public void sign(Integer id) {
        LocalDate now = LocalDate.now();
        String key = buildCacheKey(id, now);
        int dayOfMonth = now.getDayOfMonth();

        // 签到
        redisUtils.setBit(key, dayOfMonth, true);
    }

    /**
     * 判断是否签到
     */
    public boolean isSign(Integer id) {
        LocalDate now = LocalDate.now();
        String key = buildCacheKey(id, now);
        int dayOfMonth = now.getDayOfMonth();
        return redisUtils.getBit(key, dayOfMonth);
    }

    /**
     * 获取当月的签到次数
     * @param id
     * @return
     */
    public Long getSignCountOfThisMonth(Integer id) {
        LocalDate now = LocalDate.now();
        String key = buildCacheKey(id, now);
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(1));
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return 0L;
        }

        String binaryStr = Long.toString(num, 2);

        long count = 0;
        for (int i = 0; i < binaryStr.length(); i++) {
            char ch = binaryStr.charAt(i);
            if (ch == '1') {
                count ++;
            }
        }
        return count;
    }

    /**
     * 获取本月连续签到次数
     * @param id
     * @return
     */
    public Long getContinuousSignCountOfThisMonth(Integer id) {
        LocalDate now = LocalDate.now();
        String key = buildCacheKey(id, now);
        int dayOfMonth = now.getDayOfMonth();
        List<Long> result = redisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(1));
        if (result == null || result.isEmpty()) {
            return 0L;
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return 0L;
        }

        long count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else {
                count ++;
            }
            num >>>= 1;
        }
        return count;
    }

    private String buildCacheKey(Integer id, LocalDate localDate) {
        int year = localDate.getYear();
        int monthValue = localDate.getMonthValue();
        String key = "sign:" + year + ":" + monthValue + ":" + id;
        return key;
    }
}
```
测试代码如下：
```java
@Autowired
    private SignService signService;

    @Test
    public void testSign() {
        // 签到
        signService.sign(1);

        // 判断是否签到
        System.out.println("是否签到：" + signService.isSign(1));

        // 获取当月的签到次数
        System.out.println("当月的签到次数：" + signService.getSignCountOfThisMonth(1));

        // 获取当月的连续签到次数
        System.out.println("当月连续签到次数：" + signService.getContinuousSignCountOfThisMonth(1));
    }
```
运行结果如下：
{% asset_img 4.png %}