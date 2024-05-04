---
title: Redis学习6—Redis分布式锁
date: 2024-04-02 21:37:06
tags: 
    - 中间件
    - Redis
---
### 1. 引言
#### 1.1. 分布式锁
分布式锁（Distributed Lock）是一种用于分布式系统中实现互斥访问的机制，在分布式系统中，多个节点同时访问共享资源可能导致数据不一致或竞态条件的问题，分布式锁通过协调多个节点之间的访问，确保在同一时间只有一个节点能获得对共享资源的独占访问权限，从而解决并发访问问题。
#### 1.2. 分布式锁实现方式
常用的分布式锁实现方式有：
1）基于数据库的分布式锁（乐观锁)：使用数据库的事务特性和唯一约束来实现分布式锁。通过在数据库中创建一个特定的表或记录来表示锁的状态，节点可以通过获取或释放该记录来获取或释放锁。
2）基于缓存的分布式锁：使用分布式缓存系统（如Redis）的原子操作来实现分布式锁，节点可以通过在缓存中设置一个特定的键值对来获取锁，并利用缓存的原子性操作来保证锁的互斥性。
3）基于zookeeper的分布式锁：zookeeper是一个分布式协调服务，可以用于实现分布式锁，节点可以通过在zookeeper中创建一个临时有序节点来表示锁的占用状态，通过比较节点 的序号来确定锁的拥有权。

### 2. 基于数据库的分布式锁（乐观锁）
基于数据库的分布式锁实现方案，一般是在表中加一个字段，用于表示版本号，当读取数据时，会读取对应的版本号，在更新数据的时候，也会相应的更新版本号（比如版本号递增），且在更新数据的时候，会判断当前版本号是否正确，以账户余额修改为例，具体流程如下：
1）查询账户信息（此时从数据库中查出的版本号为version1)
2）根据请求对账户对象进行操作
3）更新数据库(update t_account set 字段=新值, version = version + 1 where id = #{accountId} and version = version1的值)
在这个过程中，最重要的就是更新sql的语句，也就是在更新的时候，判断版本号是否被修改过，只有没有被修改过，我们才能更新成功。
示例如下：
首先，我们创建一个账户表：
{% asset_img 1.png %}
然后在账户表上插入一条数据，假设账户中有1000元
{% asset_img 2.png %}
对应的实体类和mapper：
```java
@TableName(value = "t_account")
@Data
public class Account implements Serializable {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer userId;

    private Integer balance;

    private Date createTime;

    private Date updateTime;

    private Integer version;
}



@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
```
然后我们创建一个AccountService，先演示没有乐观锁时，会造成的问题
```java
package org.example.service;

import org.example.mapper.AccountMapper;
import org.example.pojo.Account;
import org.example.request.account.TakeOutMoneyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AccountService {
    @Autowired
    private AccountMapper accountMapper;

    public boolean takeOutMoneyWithoutOpLock(TakeOutMoneyRequest request) throws InterruptedException {
        Integer accountId = request.getAccountId();
        Account account = accountMapper.selectById(accountId);
        if (account.getBalance() - request.getMoney() < 0) {
            System.out.println("余额不足==============");
            return false;
        }
        Thread.sleep(1000);

        account.setBalance(account.getBalance() - request.getMoney());
        account.setUpdateTime(new Date());
        return accountMapper.updateById(account) > 0;
    }
}
```
添加一个测试类，用于演示并发情况下，账户余额的减少
```java
package org.example.service;

import org.example.mapper.AccountMapper;
import org.example.pojo.Account;
import org.example.request.account.TakeOutMoneyRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class AccountService {
    @Autowired
    private AccountMapper accountMapper;

    public boolean takeOutMoneyWithoutOpLock(TakeOutMoneyRequest request) throws InterruptedException {
        Integer accountId = request.getAccountId();
        Account account = accountMapper.selectById(accountId);
        if (account.getBalance() - request.getMoney() < 0) {
            System.out.println("余额不足==============");
            return false;
        }
        Thread.sleep(1000);

        account.setBalance(account.getBalance() - request.getMoney());
        account.setUpdateTime(new Date());
        return accountMapper.updateById(account) > 0;
    }
}
```
运行测试方法，结果如下图所示，说明都更新成功了
{% asset_img 3.png %}
然后查看数据库：
{% asset_img 4.png %}
如上图所示，原先我们的账户余额是1000元，每次扣除100元，经过10次扣减后，账户余额应该为0，但是因为并发问题，导致查询的时候，有多个请求查询到同一个值，最后导致数据不一致。
我们修改Account，添加使用乐观锁进行扣减余额的方法：
```java
   public boolean takeOutMoneyWithOpLock(TakeOutMoneyRequest request) throws InterruptedException {
        Integer accountId = request.getAccountId();
        Account account = accountMapper.selectById(accountId);
        if (account.getBalance() - request.getMoney() < 0) {
            System.out.println("余额不足==============");
            return false;
        }
        Thread.sleep(1000);

        LambdaUpdateWrapper<Account> lambdaUpdateWrapper = new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.set(Account::getVersion, account.getVersion() + 1)
                .set(Account::getUpdateTime, new Date())
                        .set(Account::getBalance, account.getBalance() - request.getMoney())
                                .eq(Account::getVersion, account.getVersion())
                                        .eq(Account::getId, request.getAccountId());
        return accountMapper.update(account, lambdaUpdateWrapper) > 0;
    }
```
我们把金额修改回1000，然后添加测试方法：
```java
 @Test
    public void testTakeoutMoneyWithOpLock() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Callable<Boolean> takeoutTask = () -> {
            TakeOutMoneyRequest request = new TakeOutMoneyRequest();
            request.setAccountId(1);
            request.setMoney(100);
            try {
                return accountService.takeOutMoneyWithOpLock(request);
            } catch (InterruptedException e) {
                return false;
            }
        };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executorService.submit(takeoutTask);
            futureList.add(future);
        }

        for (Future<Boolean> future : futureList) {
            System.out.println(future.get());
        }
    }
```
运行测试方法，结果如下，说明只有两次更新成功了，其余的更新，都以为乐观锁被修改了，导致更新失败
{% asset_img 5.png %}
然后我们查看数据库，结果如下，因为扣减了两次，所有余额为800，这个数据对的上。
{% asset_img 6.png %}
乐观锁的实现思路，是基于对并发更新的乐观假设，也就是认为冲突的概率较低，因此在读取和提交数据时进行版本号或时间戳的比较，而不是在数据访问阶段进行加锁操作，避免了显示的锁竞争，提高了并发性能。但乐观锁并不能完全消除并发冲突，只是在提交数据时进行冲突检测和处理，如果系统中的并发冲突非常频繁，乐观锁的效率可能会下降。
### 3. 基于Redis的分布式锁
#### 3.1. 基于Redis的SETNX实现分布式锁
SETNX指的是set if not exist，也就是当key不存在的时候，设置key的值，存在的话，什么都不做, 其语法为:
```
set key value nx
```
如果我们要设置过期时间的话，可以使用
```
set key value ex 时间 nx
```
如下图所示，在使用nx指令的时候，只有在该key不存在的时候，才能设置成功
{% asset_img 7.png %}
我们修改之前的RedisUtils工具类，添加上和这两条指令相关的方法：
```java
 public boolean setIfAbsent(String key, Object value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    /**
     * 不存在时设置值，适用与分布式锁的场景
     * @param key
     * @param value
     * @param time
     * @return
     */
    public boolean setIfAbsent(String key, Object value, long time) {
        return setIfAbsent(key, value, time, TimeUnit.SECONDS);
    }

    public boolean setIfAbsent(String key, Object value, long time, TimeUnit timeUnit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, time, timeUnit);
    }
```
##### 3.1.1. 通过setnx实现分布式锁
具体流程如下：
{% asset_img 24.png %}
我们修改AccountService，添加和该指令相关的方法：
```java
  @Autowired
    private RedisUtils redisUtils;

    public boolean takeOutMoneyWithSetnx(TakeOutMoneyRequest request) {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;

        boolean lock = redisUtils.setIfAbsent(key, request.getAccountId());
        if (!lock) {
            // 加锁失败，返回
            return false;
        }
        // 加锁成功
        try {
            Account account = accountMapper.selectById(accountId);
            account.setBalance(account.getBalance() - request.getMoney());
            account.setUpdateTime(new Date());
            return accountMapper.updateById(account) > 0;
        } finally {
            // 释放锁
            redisUtils.removeKey(key);
        }
    }
```
添加测试方法：
```java
@Test
    public void testTakeoutMoneyWithSetnx() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Callable<Boolean> takeoutTask = () -> {
            TakeOutMoneyRequest request = new TakeOutMoneyRequest();
            request.setAccountId(1);
            request.setMoney(100);
            return accountService.takeOutMoneyWithSetnx(request);
        };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executorService.submit(takeoutTask);
            futureList.add(future);
        }

        for (Future<Boolean> future : futureList) {
            System.out.println(future.get());
        }
    }
```
测试结果如下：
{% asset_img 8.png %}
我们查看数据库，确实只扣减了100
{% asset_img 9.png %}
这里冲突次数比较多，因此更新的效率有点低，我们可以将对应的方法修改一下，加上重试，修改代码如下：
```java
  @Autowired
    private RedisUtils redisUtils;

    public boolean takeOutMoneyWithSetnx(TakeOutMoneyRequest request) {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;

        boolean lock = redisUtils.setIfAbsent(key, request.getAccountId());
        if (lock) {
            // 加锁成功
            try {
                Account account = accountMapper.selectById(accountId);
                account.setBalance(account.getBalance() - request.getMoney());
                account.setUpdateTime(new Date());
                return accountMapper.updateById(account) > 0;
            } finally {
                // 释放锁
                redisUtils.removeKey(key);
            }
        }
        // 加锁失败，进行重试
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return takeOutMoneyWithSetnx(request);
    }
```
这里休眠了一段时间，是因为要涉及到递归调用，可能会导致栈空间溢出，我们再次执行测试代码，结果如下，经过重试后，执行成功率变高。
{% asset_img 10.png %}
查看数据库，确实扣减了10次。
{% asset_img 11.png %}
但是，使用set key value nx存在一个问题，如果setnx占锁成功，但是服务器宕机了，没有执行删除锁的逻辑，那么就会造成这个锁一直没有被释放，最终导致死锁。
##### 3.1.2. setnx with expire
为解决setnx造成的死锁问题，我们在setnx的基础上，加上过期时间，来解决上述问题。我们给AccountService添加上对应的方法如下：
```java
 public boolean takeOutMoneyWithSetnxExpire(TakeOutMoneyRequest request) {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;
        
        // 占有锁并设置过期时间
        boolean lock = redisUtils.setIfAbsent(key, request.getAccountId(), TTL);
        if (lock) {
            // 加锁成功
            try {
                Account account = accountMapper.selectById(accountId);
                account.setBalance(account.getBalance() - request.getMoney());
                account.setUpdateTime(new Date());
                return accountMapper.updateById(account) > 0;
            } finally {
                // 释放锁
                redisUtils.removeKey(key);
            }
        }
        // 加锁失败，进行重试
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return takeOutMoneyWithSetnx(request);
    }
```
添加上对应的测试方法：
```java
@Test
    public void testTakeoutMoneyWithSetnxExpire() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Callable<Boolean> takeoutTask = () -> {
            TakeOutMoneyRequest request = new TakeOutMoneyRequest();
            request.setAccountId(1);
            request.setMoney(100);
            return accountService.takeOutMoneyWithSetnxExpire(request);
        };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executorService.submit(takeoutTask);
            futureList.add(future);
        }

        for (Future<Boolean> future : futureList) {
            System.out.println(future.get());
        }
    }
```
测试结果如下：
{% asset_img 12.png %}
我们 查看数据库，数据库也确实扣减了10次
{% asset_img 13.png %}
但是，这个方案还是有一定缺陷，因为我们设置的这个过期时间，是根据我们的经验设置的，而业务代码的执行时长，是不确定的，那么可能存在这种情况，假设我们现在有三个请求过来，我们设置的过期时间是100ms
1）请求A占锁成功，执行业务代码
2）请求A执行100ms后，锁过期，但此时请求A的业务代码还未执行完毕
3）请求B占锁成功，执行业务代码
4）请求A执行完毕，执行释放锁的逻辑，导致把B占有的锁打开了
5）请求C占锁成功，执行业务代码
6）请求B执行完毕，执行释放锁的逻辑，导致把C占有的锁打开了
这里是因为，这三个请求占有的锁的key都是相同的，而我们在释放锁的时候，只是执行删除key的命令，并不在意这个锁是谁占有的。
这种情况，我们可以通过lua脚本来解决，思路如下：
1）占锁的时候，设置value值为用户标识
2）释放锁的时候，通过lua脚本，判断此时key对应的value值，与传入值是否相同，只有相同的时候，我们才执行删除key的逻辑。
我们修改刚才的方法，如下，在占锁的时候，我们设置value值为当前的线程id（这里是为了演示，实际业务场景中，应该是多个用户抢占同一个资源，因此可以将vlaue值设置为用户的标识，比如用户id），然后在释放资源的时候，执行lua脚本，判断value值是否相同，相同则执行删除操作。
```java
  @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
   public boolean takeOutMoneyWithSetnxExpire(TakeOutMoneyRequest request) {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;

        long threadId = Thread.currentThread().getId();
        // 占有锁并设置过期时间
        boolean lock = redisUtils.setIfAbsent(key, threadId, TTL);
        if (lock) {
            // 加锁成功
            try {
                Account account = accountMapper.selectById(accountId);
                account.setBalance(account.getBalance() - request.getMoney());
                account.setUpdateTime(new Date());
                return accountMapper.updateById(account) > 0;
            } finally {
                // 释放锁
                // lua脚本
                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class), Arrays.asList(key), threadId);
            }
        }
        // 加锁失败，进行重试
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return takeOutMoneyWithSetnx(request);
    }
```
再次执行测试代码：
{% asset_img 14.png %}
查看数据库，确实减少10次
{% asset_img 15.png %}
但这里还有一个问题没有解决，因为我们设置的TTL，是我们的经验值，不准确，所以还是会存在，某个请求占有锁后，还没执行完毕，锁过期了，被另外一个请求占有，此时会出现两个请求都认为自己占有锁的情况。
#### 3.2. Redisson
##### 3.2.1. 简介
Redisson是一个在Redis基础上实现的Java驻内存数据网络，它不仅提供一系列的分布式java常用对象，还提供许多分布式服务，其宗旨是促进使用者对Redis的关注分离（Separation of Concern），从而让使用者能将精力更多集中在处理业务逻辑上。
{% asset_img 16.png %}
##### 3.2.2. SpringBoot 整合Redisson
引入redisson的maven依赖：
```xml
<dependency>
            <groupId>org.redisson</groupId>
            <artifactId>redisson</artifactId>
            <version>3.15.5</version>
        </dependency>
```
然后自定义配置类（这里使用的是单节点Redis配置）
```java
package org.example.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfiguration {
    
    @Bean
    public RedissonClient redisson() {
        // 1. 创建配置
        Config config = new Config();
        // 集群模式
//        config.useClusterServers().addNodeAddress("集群ip1", "集群id2");
        // 2. 根据Config创建出RedissonClient示例
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        return Redisson.create(config);
    }
}
```
我们添加测试方法，来测试redisson的一些基本操作：
```java
package org.example;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import org.redisson.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedissonTest {
    @Autowired
    private RedissonClient redissonClient;

    @Test
    public void testRedisson() {
        // 字符串操作
        RBucket<Object> rBuck = redissonClient.getBucket("name");
        rBuck.set("cxy", 30, TimeUnit.SECONDS);
        System.out.println(redissonClient.getBucket("name").get());

        // 哈希操作
        RMap<Object, Object> student = redissonClient.getMap("student");
        student.put("id", 1);
        student.put("name", "cxy");
        student.put("age", 20);
        student.expire(30, TimeUnit.SECONDS);

        System.out.println(redissonClient.getMap("student").get("name"));

        // 列表操作
        RList<Object> schools = redissonClient.getList("schools");
        schools.add("华南理工大学");
        schools.add("中山大学");
        schools.add("暨南大学");
        System.out.println(JSONObject.toJSONString(redissonClient.getList("schools")));

        // 集合操作
        RSet<Object> schoolSet = redissonClient.getSet("schoolSet");
        schoolSet.add("华南理工大学");
        schoolSet.add("中山大学");
        schoolSet.add("暨南大学");
        System.out.println(JSONObject.toJSONString(redissonClient.getSet("schoolSet")));

        // ZSet操作
        RScoredSortedSet<Object> schoolScoreSet = redissonClient.getScoredSortedSet("schoolScoreSet");
        schoolScoreSet.add(100d, "华南理工大学");
        schoolScoreSet.add(90d, "中山大学");
        schoolScoreSet.add(80d, "暨南大学");
        System.out.println(JSONObject.toJSONString(redissonClient.getScoredSortedSet("schoolScoreSet")));
    }
}
```
结果如下：
{% asset_img 17.png %}
##### 3.2.3. Redisson分布式锁
redisson加锁，可以使用lock方法，注意，在加锁的时候，处理完业务逻辑后要记得释放锁，测试代码如下：
```java
  @Test
    public void testLock() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        Runnable lockTask = () -> {
            try {
                lock();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        List< Future> futureList = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futureList.add(executorService.submit(lockTask));
        }
        for (Future future : futureList) {
            future.get();
        }
    }

    private void lock() throws InterruptedException {
        RLock myLock = redissonClient.getLock("myLock");
        myLock.lock();
        try {
            System.out.println("currentTime:" + System.currentTimeMillis());
            Thread.sleep(2000);
            System.out.println("执行业务代码");
        } finally {
            myLock.unlock();
        }
    }
```
测试结果如下，从执行结果可以看出，当多个线程抢占锁时，后面的锁，需要等待，即这个锁是阻塞的。
{% asset_img 18.png %}
如果不想阻塞的话，我们可以使用tryLock来上锁，结合刚才的accountService，我们先修改accountService，加上对应的方法
```java
 @Autowired
    private RedissonClient redissonClient;

    public boolean takeoutMoneyWithRedissonTryLock(TakeOutMoneyRequest request) throws InterruptedException {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;

        RLock lock = redissonClient.getLock(key);
        if (lock.tryLock(2, 4, TimeUnit.SECONDS)) { // 过期时间为2秒，最长存活时间为4秒
            // 上锁成功
            try {
                Thread.sleep(1000);
                Account account = accountMapper.selectById(accountId);
                account.setBalance(account.getBalance() - request.getMoney());
                account.setUpdateTime(new Date());
                return accountMapper.updateById(account) > 0;
            } finally {
                // 释放锁
                lock.unlock();
            }
        }
        return false;
    }
```
添加测试方法：
```java
   @Test
    public void testTakeoutMoneyWithRedissonTryLock() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Callable<Boolean> takeoutTask = () -> {
            TakeOutMoneyRequest request = new TakeOutMoneyRequest();
            request.setAccountId(1);
            request.setMoney(100);
            return accountService.takeoutMoneyWithRedissonTryLock(request);
        };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executorService.submit(takeoutTask);
            futureList.add(future);
        }

        for (Future<Boolean> future : futureList) {
            System.out.println(future.get());
        }
    }
```
测试结果如下:
{% asset_img 19.png %}
查看数据库，减少的次数与上面的次数一致
{% asset_img 20.png %}
不过上面这种，成功率比较低，因此我们可以将tryLock改为lock方法，来上锁，我们修改accountService，添加相关方法：
```java
  public boolean takeoutMoneyWithRedissonLock(TakeOutMoneyRequest request) throws InterruptedException {
        Integer accountId = request.getAccountId();
        String key = "lock::" + accountId;

        RLock lock = redissonClient.getLock(key);
        lock.lock(2, TimeUnit.SECONDS);
        // 上锁成功
        try {
            Thread.sleep(1000);
            Account account = accountMapper.selectById(accountId);
            account.setBalance(account.getBalance() - request.getMoney());
            account.setUpdateTime(new Date());
            return accountMapper.updateById(account) > 0;
        } finally {
            // 释放锁
            lock.unlock();
        }
    }
```
添加测试方法：
```java
 @Test
    public void testTakeoutMoneyWithRedissonLock() throws ExecutionException, InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(5);
        Callable<Boolean> takeoutTask = () -> {
            TakeOutMoneyRequest request = new TakeOutMoneyRequest();
            request.setAccountId(1);
            request.setMoney(100);
            return accountService.takeoutMoneyWithRedissonLock(request);
        };
        List<Future<Boolean>> futureList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Future<Boolean> future = executorService.submit(takeoutTask);
            futureList.add(future);
        }

        for (Future<Boolean> future : futureList) {
            System.out.println(future.get());
        }
    }
```
测试结果如下：
{% asset_img 21.png %}
查看数据库，扣减次数确实为10次。
{% asset_img 22.png %}
##### 3.2.4. watch dog 看门狗机制
{% asset_img 23.png %}
Redisson中的分布式锁自带自动续期机制，其提供了一个专门用来监控和续期锁的Watch Dog（看门狗），如果操作共享资源的线程还没有执行完成的话，Watch Dog会不断延长锁的过期时间，从而保证锁不会因为超时而被释放。
### 4. 参考文章
https://zhuanlan.zhihu.com/p/374306005
https://my.oschina.net/u/4499317/blog/5039486
https://blog.csdn.net/qq_15071263/article/details/101277474
https://www.cnblogs.com/jelly12345/p/14699492.html