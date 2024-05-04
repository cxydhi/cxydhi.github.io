---
title: SpringGateway学习1
date: 2024-04-02 19:56:52
tags: 
    - SpringBoot
    - SpringGateway
---
### 1. 背景
API网关是一个服务器，是系统对外的唯一入口，对于服务数量众多、复杂度比较高、规模比较大的业务来说，引入API网关有以下好处：
1） 聚合接口使得服务对调用者透明，客户端与后端耦合度降低
2） 聚合后台服务，节省流量，提高性能和用户体验
3）提供安全、流控、过滤、缓存、计费、监控等API管理功能
SpringCloud Gateway是基于Spring生态系统之上搭建的API网关，包括：Spring5， SpringBoot2和Project Reactor。Spring Cloud Gateway旨在提供一种简单有效的方法来路由到API，并为它们提供领域的关注点，例如安全性、监控/指标、限流等。
### 2. 核心概念和原理
#### 2.1. 核心概念
路由（Route）：路由信息由ID、目标URL、一组断言和一组过滤器组成，如果断言路由为真，说明请求的URI和配置匹配
断言（Predicate）：Java8中的断言函数，SpringCloud Gateway中的断言函数，允许开发者去定义匹配来自Http Request中的任何信息，比如请求头和参数等
过滤器（Filter）：在SpringCloud Gateway中，有两种过滤器：Gateway Filter 和 Global Filter，过滤器负责对请求和响应进行处理。
#### 2.2. 工作原理
{% asset_img image1.png %}
客户端向Spring Cloud Gateway发出请求，由网关处理程序Gateway Handler Mapping映射确定与请求相匹配的路由（route），将其发送到网关web处理程序Gateway Web Handler，该程序通过指定的过滤器将请求发送到实际的服务，执行业务逻辑，然后返回。
过滤器由虚线分隔的原因是，过滤器可以在发送代理请求之前和之后运行逻辑。所有 pre过滤器逻辑均被执行。然后发出代理清求。发出代理清求后，将运行post过滤器逻辑。

### 3. 入门案例
#### 3.1. 创建普通的web项目，并测试相关接口
创建一个maven项目，在该maven项目下，创建三个子模块，假设分别命名为gateway、app-service1、app-service2
{% asset_img image2.png %}
在父模块的pom.xml中，添加相关的版本管理
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>org.example</groupId>
    <artifactId>springcloudgateway1</artifactId>
    <packaging>pom</packaging>
    <version>1.0-SNAPSHOT</version>
    <modules>
        <module>gateway</module>
        <module>app-service1</module>
        <module>app-service2</module>
    </modules>

    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <spring.boot.version>2.5.2</spring.boot.version>
        <spring.cloud.version>2020.0.3</spring.cloud.version>
        <spring.cloud.alibaba.version>2.2.6.RELEASE</spring.cloud.alibaba.version>
    </properties>

    <!--Spring版本-->
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.alibaba.cloud</groupId>
                <artifactId>spring-cloud-alibaba-dependencies</artifactId>
                <version>${spring.cloud.alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring.boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring.cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>

</project>
```
在gateway模块，添加下列依赖：
``` xml
<dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
    </dependencies>
```
app-service1和app-service2模块，添加下列依赖：
``` xml
<dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
    </dependencies>
```
在app-service1模块，添加启动类：
``` java
package com.young;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Service1App {
    public static void main(String[] args) {
        SpringApplication.run(Service1App.class, args);
    }
}
```
添加一个controller类用于测试：
``` java
@RestController
@RequestMapping(value = "/app1")
public class App1Controller {

    @GetMapping(value = "/test")
    public Object test() {
        return "app1";
    }
}
```
application.yml配置信息如下：
``` yml
server:
  port: 9000
spring:
  application:
    name: app-service1
```
在app-service2中，添加启动类
```
@SpringBootApplication
public class Service2App {
    public static void main(String[] args) {
        SpringApplication.run(Service2App.class, args);
    }
}
```
对外controller
``` java
@RestController
@RequestMapping(value = "/app2")
public class App2Controller {

    @GetMapping(value = "/test")
    public Object test() {
        return "app2";
    }
}
```
配置文件：
``` yml
server:
  port: 9001
spring:
  application:
    name: app-service2
```
启动app-service1和app-service2，然后访问http://localhost:9000/app1/test和http://localhost:9001/app2/test，结果如下：
{% asset_img image3.png %}
{% asset_img image4.png %}
#### 3.2. 配置网关转发
在gateway模块，添加启动类：
```java
@SpringBootApplication
public class GatewayApp {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApp.class, args);
    }
}
```
修改配置文件，配置路由转发规则
``` yml
server:
  port: 80
spring:
  cloud:
    gateway:
      enabled: true
      routes:
        - id: app-service1
          uri: http://localhost:9000
          predicates: #断言，为真则匹配成功
            - Path=/app1/** #配置规则Path，如果是app1开头的请求，会转发到目标URL
        - id: app-service2
          uri: http://localhost:9001
          predicates:
            - Path=/app2/**
```
启动gateway项目，使用gateway的端口，进行测试
{% asset_img image5.png %}
{% asset_img image6.png %}