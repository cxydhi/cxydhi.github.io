---
title: MongoDB学习2—SpringBoot集成MongoDB
date: 2024-04-03 14:05:33
tags:
    - 中间件
    - MongoDB
---
### 1. 引言
SpringBoot要集成MongoDB，可以直接使用spring-data-mongodb提供的MongoTemplate和MongoRepository这两种方式，前者操作比较灵活，后者比较简单。
引入依赖如下：
```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    <dependency>
        <groupId>joda-time</groupId>
        <artifactId>joda-time</artifactId>
        <version>2.10.1</version>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
        <exclusions>
            <exclusion>
                <groupId>org.junit.vintage</groupId>
                <artifactId>junit-vintage-engine</artifactId>
            </exclusion>
        </exclusions>
    </dependency>
</dependencies>
```
application.yml配置信息如下：
``` yml
spring:
  data:
    mongodb:
      uri: mongodb://127.0.0.1:27017/test
```
### 2. 代码示例
首先，我们创建一个test数据库，在这个数据库中，创建一个user集合，用于演示：
{% asset_img 1.png %}
#### 2.1. MongoTemplate使用
##### 2.1.1. 插入和简单查询
我们添加测试方法，首先测试插入和一些简单查询：
```java
 @Test
    public void testInsertAndSimpleFind() {
        // 测试添加
        User createUser = new User();
        createUser.setAge(20);
        createUser.setName("cxy");
        createUser.setEmail("123@qq.com");
        createUser.setCreateTime(new Date());
        createUser = mongoTemplate.insert(createUser);
        System.out.println(JSONObject.toJSONString(createUser));

        // 根据id查询
        User userById = mongoTemplate.findById(createUser.getId(), User.class);
        System.out.println(JSONObject.toJSONString(userById));

        // 查询全部
        List<User> allUsers = mongoTemplate.findAll(User.class);
        System.out.println(JSONObject.toJSONString(allUsers));
    }
```
执行结果如下：
{% asset_img 2.png %}
查看数据库，确实多了一条记录
{% asset_img 3.png %}
##### 2.1.2. 更新
接着我们测试mongoTemplate的修改
```java
  @Test
    public void testUpdateMongoTemplate() {
        String id = "65ea70bf090d176df859e5fa";
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update();
        update.set("name", "cxy123");
        update.set("age", 20);
        UpdateResult upsert = mongoTemplate.upsert(query, update, User.class);
        long modifiedCount = upsert.getModifiedCount();
        System.out.println("受影响的条数：" + modifiedCount);
    }
```
执行上述代码，查看数据库，数据库确实被修改了。
{% asset_img 4.png %}
##### 2.1.3. 删除
```java
 @Test
    public void testDeleteMongoTemplate() {
        String id = "65ea70bf090d176df859e5fa";
        Query query = new Query(Criteria.where("_id").is(id));
        DeleteResult deleteResult = mongoTemplate.remove(query, User.class);
        System.out.println("删除个数：" + deleteResult.getDeletedCount());
    }
```
执行完毕后，查看数控库，相关的集合被成功删除。
{% asset_img 5.png %}
##### 2.1.4. 条件查询
我们先插入一些测试数据，方便后续测试条件查询
```java
   @Test
    public void insertTestUser() {
        User user1 = new User();
        user1.setName("张三");
        user1.setAge(10);
        user1.setEmail("zhangsna@qq.com");
        user1.setCreateTime(new Date());
        
        User user2 = new User();
        user2.setName("李四");
        user2.setAge(20);
        user2.setEmail("lisi@163.com");
        user2.setCreateTime(new Date());
        
        User user3 = new User();
        user3.setName("王五");
        user3.setAge(30);
        user3.setCreateTime(new Date());
        
        List<User> userList = new ArrayList<>();
        userList.add(user1);
        userList.add(user2);
        userList.add(user3);
        for (int i = 0; i < 10; i++) {
            User user = new User();
            user.setName("name:" + i);
            user.setAge(20 + i);
            user.setEmail("template" + i + "@qq.com");
            user.setCreateTime(new Date());
            userList.add(user);
        }
        mongoTemplate.insert(userList, User.class);
    }
```
假设我们现在查询姓名为张三的用户：
```java
@Test
    public void testFindByName() {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is("张三"));
        User zhangsan = mongoTemplate.findOne(query, User.class);
        System.out.println("张三：" + JSONObject.toJSONString(zhangsan));
    }
```
执行结果如下：
{% asset_img 6.png %}
查询年龄在20到30之间的用户：
```java
@Test
    public void testFindByAge() {
        Query query = new Query();
        query.addCriteria(Criteria.where("age").gte(20).lte(30));
        List<User> users = mongoTemplate.find(query, User.class);
        for (User user : users) {
            System.out.println("age:" + user.getAge() + ":" + JSONObject.toJSONString(user));
        }
    }
```
结果如下：
{% asset_img 7.png %}
当我们要进行查询的时候，可以使用where来查询，上述演示的，只是对一个field进行条件查询，当我们需要对多个field进行条件查询时，可以使用and来连接，假设我们要查询姓名为王五并且年龄为30岁的人，测试方法如下：
```java
  @Test
    public void testFindByNameAndAge() {
        Query query = new Query();
        query.addCriteria(Criteria.where("name").is("王五")
                .and("age").is(30));

        List<User> users = mongoTemplate.find(query, User.class);
        for (User user : users) {
            System.out.println("age:" + user.getAge() + ":" + JSONObject.toJSONString(user));
        }
    }
```
测试结果如下：
{% asset_img 8.png %}
##### 2.1.5. 分页查询
```java
 @Test
    public void testPage() {
        int pageNo = 1;
        int pageSize = 3;

        Query query = new Query();
        query.skip((pageNo - 1) * pageSize).limit(pageSize);
        List<User> users = mongoTemplate.find(query, User.class);
        for (User user : users) {
            System.out.println("user:" + JSONObject.toJSONString(user));
        }
    }
```
测试结果如下：
{% asset_img 9.png %}
#### 2.2. MongoRepository
Spring Data提供对mongodb数据访问的支持，我们只需要继承MongoRepository类，按照SpringData规范就可以了。
首先，我们添加UserRepository接口，继承MongoRepository<User, String>
```java
package org.example.repository;

import org.example.pojo.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
}

然后创建UserService，用于对user进行增删改查
package org.example.service;

import org.example.pojo.User;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    public User saveUser(User user) {
        user.setCreateTime(new Date());
        return userRepository.save(user);
    }
    
    public User updateUser(User user) {
        // 插入和更新都是使用save
        return userRepository.save(user);
    }
    
    public void removeUserById(String id) {
        userRepository.deleteById(id);
    }
    
    public User findById(String id) {
        return userRepository.findById(id).get();
    }
    
    public List<User> findAll() {
        return userRepository.findAll();
    }
    
    public List<User> findByNameAndAge(String name, int age) {
        User conditionUser = new User();
        conditionUser.setName(name);
        conditionUser.setAge(age);
        Example<User> example = Example.of(conditionUser);
        return userRepository.findAll(example);
    }
    
    public List<User> findPage(int pageNo, int pageSize) {
        Pageable pageable = PageRequest.of(pageNo, pageSize);
        Page<User> page = userRepository.findAll(pageable);
        return page.getContent();
    }
    
   
}
```
添加测试方法，测试查询是否生效：
```java
 @Autowired
    private UserService userService;

    @Test
    public void testFind() {
        // 根据id查询
        User findById = userService.findById("65ea7586b4c1f3160f791b06");
        System.out.println(JSONObject.toJSONString(findById));

        // 根据姓名和年龄查询
        List<User> zhangsan = userService.findByNameAndAge("张三", 10);
        System.out.println(JSONObject.toJSONString(zhangsan));

        // 分页查询
        List<User> page = userService.findPage(1, 3);
        System.out.println(JSONObject.toJSONString(page));

        // 查询全部
        List<User> all = userService.findAll();
        System.out.println(JSONObject.toJSONString(all));

    }
```
查询结果如下：
{% asset_img 10.png %}