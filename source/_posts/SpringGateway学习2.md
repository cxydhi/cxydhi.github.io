---
title: SpringGateway学习2
date: 2024-04-02 20:08:47
tags: 
    - SpringBoot
    - SpringGateway
---
### 1. 断言
在上一节中，我们的gateway应用，它的配置文件为：
```yml
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
这里面使用了Path断言，Path断言会根据请求的路径进行匹配，除了Path断言外，常用的断言如下图所示：
{% asset_img image.png %}
这里再简单介绍几个常用的断言：
#### 1.1. After
After用于匹配在指定日期时间之后发生的请求，也就是说，只有在指定日期之后的请求，才能被转发，假设我们的配置如下：
```yml 
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
            - After=2025-01-20T09:08:01.000+08:00[Asia/Shanghai]
            - Path=/app1/** #配置规则Path，如果是app1开头的请求，会转发到目标URL
        - id: app-service2
          uri: http://localhost:9001
          predicates:
            - Path=/app2/**
```
在1月20号9点8分之前：
{% asset_img image1.png %}
在1月20号9点8分之后：
{% asset_img image2.png %}

#### 1.2. Before
Before用于匹配在指定日期之前的请求，也就是说，只有在指定日期之前，该请求才会被匹配并转发
假设我们的配置如下：
```yml
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
            - After=2024-01-20T09:08:01.000+08:00[Asia/Shanghai]
            - Path=/app1/** #配置规则Path，如果是app1开头的请求，会转发到目标URL
        - id: app-service2
          uri: http://localhost:9001
          predicates:
            - Before=2024-01-20T09:17:01.000+08:00[Asia/Shanghai]
            - Path=/app2/**
```
在1月20号9点17分之前：
{% asset_img image3.png %}
在1月20号9点17分之后：
{% asset_img image4.png %}
### 2. 网关过滤器
#### 2.1. Gateway Filter
SpringCloud Gateway的Filter分为两种类型：Gateway Filter和Global Filter，过滤器会对请求和响应进行处理，比如添加参数，URL重写等，常用的网关过滤器如下：
{% asset_img image5.png %}
##### 2.1.1. AddRequestHeader
AddRequestHeader需要name和value参数
假设我们修改gateway应用的配置如下：
```yml 
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
          filters:
            - AddRequestHeader=X-Request-red,blue
        - id: app-service2
          uri: http://localhost:9001
          predicates:
            - Path=/app2/**
          filters:
            - AddRequestHeader=X-Request-red,blue
```
这里会将blue消息头添加到所有匹配请求的下游请求消息头中
然后修改app-service1应用的controller
```java
@RestController
@RequestMapping(value = "/app1")
public class App1Controller {

    @GetMapping(value = "/test")
    public Object test(HttpServletRequest request) {
        System.out.println(request.getHeader("X-Request-red"));
        return "app1";
    }
}
```
修改app-service2应用的controller
```java
@RestController
@RequestMapping(value = "/app2")
public class App2Controller {

    @GetMapping(value = "/test")
    public Object test(HttpServletRequest request) {
        System.out.println(request.getHeader("X-Request-red"));
        return "app2";
    }
}
```
分别访问http://localhost/app1/test和http://localhost/app2/test，然后查看控制台，发现确实有添加上blue请求头
{% asset_img image6.png %}
{% asset_img image7.png %}

##### 2.1.2. AddRequestParameter
AddRequestParamter需要name和value参数
我们修改gateway的配置如下：
```yml
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
          filters:
            - AddRequestHeader=X-Request-red,blue
            - AddRequestParameter=red,blue
        - id: app-service2
          uri: http://localhost:9001
          predicates:
            - Path=/app2/**
          filters:
            - AddRequestHeader=X-Request-red,blue
            - AddRequestParameter=red,blue
```
这里表示将red=blue添加到下游请求参数中
我们修改app-service1和app-service2的controller代码，方便查看结果：
```java
@RestController
@RequestMapping(value = "/app1")
public class App1Controller {

    @GetMapping(value = "/test")
    public Object test(HttpServletRequest request) {
        System.out.println(request.getHeader("X-Request-red"));
        System.out.println(request.getParameter("red"));
        return "app1";
    }
}


@RestController
@RequestMapping(value = "/app2")
public class App2Controller {

    @GetMapping(value = "/test")
    public Object test(HttpServletRequest request) {
        System.out.println(request.getHeader("X-Request-red"));
        System.out.println(request.getParameter("red"));
        return "app2";
    }
}
```
分别访问http://localhost/app1/test和http://localhost/app2/test，结果如下：
{% asset_img image8.png %}
{% asset_img image9.png %}

#### 2.2. GlobalFilter
GlobalFilter是应用于所有路由的特殊过滤器
{% asset_img image10.png %}
通过全局网关过滤器，我们可以很方便的实现统一鉴权，下面我们自定义一个全局过滤器，通过token判断用户是否登录，从而实现一个统一的鉴权。
我们在gateway项目中，添加一个鉴权的全局网关过滤器：
```java
package com.young.filter;

import com.alibaba.fastjson.JSONObject;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class AuthorizeFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        final ServerHttpResponse servletResponse = exchange.getResponse();
        final ServerHttpRequest servletRequest = exchange.getRequest();
        // 获取token参数
        final String token = servletRequest.getHeaders().getFirst("token");
        if (StringUtils.isEmpty(token)) {
            servletResponse.setStatusCode(HttpStatus.UNAUTHORIZED);
            servletResponse.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("code", "403");
            jsonObject.put("message", "token is empty");
            final DataBuffer dataBuffer = servletResponse.bufferFactory().wrap(jsonObject.toJSONString().getBytes());
            return servletResponse.writeWith(Flux.just(dataBuffer));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```
重新启动项目，然后访问http://localhost/app1/test
{% asset_img image11.png %}
由于没有携带token，因此被拦截住，假设我们现在在请求头上添加token，再次访问，结果如下：
{% asset_img image12.png %}
此时能够访问成功，这里只是为了演示，因此鉴权逻辑写的比较简单，真实情况下的鉴权，可以基于此进行扩展和补充。
### 3. 参考文档
https://blog.csdn.net/zouliping123456/article/details/116128179