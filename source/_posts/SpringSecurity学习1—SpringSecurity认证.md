---
title: SpringSecurity学习1—SpringSecurity认证
date: 2024-04-03 15:28:26
tags:
    - SpringBoot
    - SpringSecurity
---
### 1. 简介
Spring Security是一个用于包含应用程序安全性的Java框架，它提供了一套全面的安全解决方案，包括身份验证、授权、防止攻击等功能。它基于过滤器链的概念，可以轻松地集成到任何基于Spring的应用程序中，它支持多种身份验证选项和授权策略，此外，还提供一些附加功能，如集成第三方身份验证提供商和单点登录，以及会话管理和密码编码等。
### 2. SpringBoot整合SpringSecurity
#### 2.1. 引言
代码实现，基于前两章提到的登录、注册和鉴权项目，这里将使用SpringSecurity框架，逐步替代前两章自定义的认证授权实现。首先，注释掉WebMvcConfiguration类，去除该类对我们后续使用SpringSecurity的影响。
```java
//package com.yang.infrastructure.configuration;
//
//import com.yang.infrastructure.auth.interceptors.JwtTokenVerifyInterceptor;
//import com.yang.infrastructure.auth.interceptors.PermissionVerifyInterceptor;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
//
//@Configuration
//public class WebMvcConfiguration implements WebMvcConfigurer {
//
//    @Override
//    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(new JwtTokenVerifyInterceptor())
//                .addPathPatterns("/**") // 拦截所有请求
//                .excludePathPatterns("/user/login", "/user/register"); // 排除登录、注册接口
//
//        PermissionVerifyInterceptor permissionVerifyInterceptor = new PermissionVerifyInterceptor();
//        permissionVerifyInterceptor.addPermission("STUDENT", "/student/needPermission");
//        permissionVerifyInterceptor.addPermission("COUNSELOR", "/counselor/*");
//
//        registry.addInterceptor(permissionVerifyInterceptor)
//                .addPathPatterns("/**") // 拦截所有请求
//                .excludePathPatterns("/user/login", "/user/register"); // 排除登录、注册接口
//    }
//}
```
#### 2.2. SpringSecurity完整流程
SpringSecurity功能的实现主要是一系列过滤器链相互配合完成的
{% asset_img 1.png %}
SecurityContextPersistenceFilter：整个拦截过程的入口和出口（也就是第一个和最后一个拦截器），会在请求开始时从配置后的SecurityContextRepository中获取SecurityContext，然后把它设置给SecurityContextHolder，请求完成后将SecurityContextHolder持有的SecurityContext再保存到配置后的SecurityContextRepository，同时清除SecurityContextHolder所持有的SecurityContext；
UsernamePasswordAuthenticationFilter：用于处理来自表单提交的认证，该表单必须提供对应的用户名和密码，其内部还有登录成功或失败后进行处理的AuthenticationSuccessHandler和AuthenticationFailureHandler，这些都可以根据需求做相关改变；
Filter Security Interceptor：用于保护web资源，使用AccessDecisionManager对当前用户进行授权访问；
ExceptionTranslationFilter：捕获来着FilterChain所有的异常，并进行处理，但它只会处理两类异常：AuthenticationException和AccessDeniedException，其他异常会继续抛出。
#### 2.3. 认证流程
{% asset_img 2.png %}
AuthenticationManager：定义认证Authentication的方法
UserDetailsService：加载用户特定数据的核心接口，里面定义了一个根据用户名查询用户信息的方法
UserDetails接口：提供核心用户信息，通过UserDetailsService根据用户名获取处理的用户信息，要封装成UserDetails对象返回，然后将这些信息封装到Authentication对象中。
入门案例
引入依赖
首先，我们引入SpringSecurity的依赖
```java
 <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```
然后定义一个测试接口，用于测试SpringSecurity：
```java
package com.yang.controller;

import com.yang.infrastructure.common.Response;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/security")
public class SecurityController {

    @GetMapping(value = "/hello")
    public Response hello() {
        return Response.success("hello");
    }
}
```
启动项目，访问/security/hello接口，此时会跳转到登录界面
{% asset_img 3.png %}
在控制台中，我们可以看到如下内容
{% asset_img 4.png %}
我们使用user用户名，以及控制台中的密码，在表单提交登录,此时便会跳转到登录成功页面。
{% asset_img 5.png %}
#### 2.4.2. UserDetailsService
在每次启动项目的时候，我们查看控制台时，总是能看到下面生成了一串UUID字符串
{% asset_img 6.png %}
这是因为在默认情况下，SpringSecurity自动化地帮我们完成以下三件事情：
1）开启FormLogin登录认证模式：假设我们还没有登录，然后访问/security/hello测试接口，那么请求会被重定向到页面/login，提示使用用户名和密码登录。
2）生成用于登录的用户名和密码：用户名是user，密码就是上面启动日志中随机生成的字符串
3）注册用于认证和鉴权的过滤器：SpringSecurity本质就是通过过滤器或过滤器（链）实现的，每一个接口请求都会按顺序经过这些过滤器的“过滤”，每个过滤器承担各自的职责，组合起来共同完成认证和鉴权，根据配置的不同，注册的过滤器有所不同。
使用默认用户名和随机密码的方式不够灵活，因此，我们可以实现SpringSecurity提供的UserDetailsService接口。这里先介绍SpringSecurity预置的两种常见的存储介质实现：
1）InMemoryUserDetailsManager：基于内存的实现
2）JdbcUserDetailsManager：基于数据库的实现
我们介绍以下InMemoryUserDetailsManager，我们创建用户实例和InMemoryUserDetailsManager实例，并使用@Bean将InMemoryUserDetailsManager实例注入到SpringSecurity中
```java
@Configuration
public class SecurityConfig {

    @Bean
    public UserDetailsManager users() {
        UserDetails user = User.builder()
                .username("cxy")
                .password("{bcrypt}$2a$10$CrPsv1X3hM" +
                        ".giwVZyNsrKuaRvpJZyGQycJg78xT7Dm68K4DWN/lxS") // 使用Bcrypt算法加密
                .roles("USER")
                .build();

        InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
        manager.createUser(user);
        return manager;
    }
}
```
重新启动项目，访问测试接口/security/hello，用户名输入cxy，密码输入123456
{% asset_img 7.png %}
{% asset_img 8.png %}
JdbcUserDetailsManager的实现与InMemoryUserDetailsManager类似，这里就不赘述了。
除了上面这两种内置实现，我们还可以自定义UserDetailsService的实现。但这里不进行介绍，因为UserDetailsService是基于表单认证这种模式的，而有时候，我们的登录方式、登录页面与它提供的又不一样，因此，我们下面会讲到，如何接入我们自定义的登录接口，登录方式。

#### 2.4.3. 登录和注册放行
上面这种整合方式，会对所有的请求进行拦截，但是一般情况下，我们是不拦截用户登录和用户注册接口的，而且现在的项目一般是前后端分离，没必要跳转到专门的登录页面。
这里使用的springboot版本是2.7.0，在Spring Boot 2.7.0之前的版本中，我们需要写个配置类继承WebSecurityConfigurerAdapter，然后重写Adapter中的三个方法进行配置，如下所示：
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class OldSecurityConfig extends WebSecurityConfigurerAdapter {
    @Autowired
    private UmsAdminService adminService;

    @Override
    protected void configure(HttpSecurity httpSecurity) throws Exception {
        //省略HttpSecurity的配置
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService())
                .passwordEncoder(passwordEncoder());
    }
    
    @Bean
    @Override
    public AuthenticationManager authenticationManagerBean() throws Exception {
        return super.authenticationManagerBean();
    }

}
```
但这种方式在SpringBoot 2.7.0中，WebSecurityConfigurerAdapter已经被弃用了，新用法中，无需继承WebSecurityConfigurerAdapter，只需要直接声明一个配置类，再配置一个生成SecurityFilterChainBean方法，配置信息如下，我们对/user/login和/user/register接口，进行放行，而其他接口，都需要进行验证。
```java
package com.yang.infrastructure.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 登录和注册接口，放行
        return httpSecurity.authorizeRequests()
                .antMatchers("/user/login").permitAll()
                .antMatchers("/user/register").permitAll()
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 设置无状态连接，即不创建session
                .and()
                .cors().disable() // 解决跨域问题
                .build();
    }
}
```
http.authorizeHttpRequests(): 指定哪些请求需要什么样的认证或授权，这里使用anyRequest()和authenticated()表示所有请求均需要认证。
http.authorizeHttpRequests()：表示我们使用HttpBasic认证。
在上面的配置中，我们配置了登录接口和注册接口允许放行，其他接口进行拦截，然后我们访问登录接口，可以看出登录接口放行通过，能顺利访问。
{% asset_img 9.png %}
再访问/security/hello接口，结果是403，说明无权访问。
{% asset_img 10.png %}
##### 2.4.4. 异常处理
在最开始的时候，我们没有配置任何东西时，没有权限便会默认跳转到用户登录界面，现在因为我们没有配置登录表单路径，所以会直接提示403，我们可以在配置类中，配置异常处理方式，方便返回一些格式化的数据供前端做出决策。
我们创建一个JwtAuthenticationExceptionHandler，该类实现自AuthenticationEntryPoint接口
```java
public class JwtAuthenticationExceptionHandler implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        sendErrorResponse(response);
    }

    private void sendErrorResponse(HttpServletResponse response) throws IOException {
        Response<Object> errorResponse = Response.fail(ResultCode.AUTHENTICATION_FAIL);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        PrintWriter writer = response.getWriter();
        writer.write(JSONObject.toJSONString(errorResponse));
        writer.flush();
    }
}
```
然后我们修改SpringSecurity的配置类，添加上和异常处理相关的配置：
```java
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 登录和注册接口，放行
        return httpSecurity.authorizeRequests()
                .antMatchers("/user/login").permitAll()
                .antMatchers("/user/register").permitAll()
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 设置无状态连接，即不创建session
                .and()
                .cors().disable() // 解决跨域问题
                .exceptionHandling()
                .authenticationEntryPoint(new JwtAuthenticationExceptionHandler()) // 未认证异常处理
                .and()
                .build();
    }
}
```
接着再次访问/security/hello接口，结果如下：
{% asset_img 11.png %}
同理，在权限不足的情况下，我们也可以实现相关的异常处理类
```java
public class JwtAccessDeniedExceptionHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        sendErrorResponse(response);
    }

    private void sendErrorResponse(HttpServletResponse response) throws IOException {
        Response<Object> errorResponse = Response.fail(ResultCode.ACCESS_DENIED);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        PrintWriter writer = response.getWriter();
        writer.write(JSONObject.toJSONString(errorResponse));
        writer.flush();
    }
}
```
修改配置类：
```java
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 登录和注册接口，放行
        return httpSecurity.authorizeRequests()
                .antMatchers("/user/login").permitAll()
                .antMatchers("/user/register").permitAll()
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 设置无状态连接，即不创建session
                .and()
                .cors().disable() // 解决跨域问题
                .exceptionHandling()
                .authenticationEntryPoint(new JwtAuthenticationExceptionHandler()) // 未认证异常处理
                .accessDeniedHandler(new JwtAccessDeniedExceptionHandler()) // 权限不足异常处理
                .and()
                .build();
    }
}
```
##### 2.4.5. 认证过滤器
当我们登录成功后，一般会返回一个token，然后前端后续将这个token，携带于请求头，想后端发起访问，后端解析这个token，来判断该请求是否认证通过，通过，则放行。在SpringSecurity中，我们可以通过addFilterBefore()将我们自定义过滤器添加上去，然后再我们的自定义过滤器中，实现相关的token解析逻辑。
首先我们定义一个MyUserDetails类，实现UserDetails接口，UserDetails提供用户的核心信息，在前两篇文章中，用户的核心信息，存储于UserContextDetails类中，这里我们沿用该类，当然，也可以自己重新定义一个类用于保存用户的核心信息，甚至直接把用户实体类作为核心信息，这个依据项目的需要。目前的实现中，除了获取用户名之外，其他都是空实现，因为其他的接口，暂时还不需要。
```java
package com.yang.infrastructure.security;

import com.yang.infrastructure.auth.UserContextDetails;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

@Data
public class MyUserDetails implements UserDetails {
    private UserContextDetails userContextDetails;

    private String password;

    public MyUserDetails(UserContextDetails userContextDetails) {
        this.userContextDetails = userContextDetails;
    }

    public MyUserDetails(UserContextDetails userContextDetails, String password) {
        this.userContextDetails = userContextDetails;
        this.password = password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return null;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getUsername() {
        return userContextDetails.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
```
我们我们添加一个JwtTokenVerifyFilter，这个类继承于OncePerRequestFilter，其具体实现如下：
```java
package com.yang.infrastructure.security.filter;

import com.alibaba.fastjson.JSONObject;
import com.yang.domain.data.Role;
import com.yang.infrastructure.auth.PermissionDetails;
import com.yang.infrastructure.auth.UserContextDetails;
import com.yang.infrastructure.auth.config.JwtTokenProperty;
import com.yang.infrastructure.auth.request.JwtTokenVerifyRequest;
import com.yang.infrastructure.auth.response.JwtTokenVerifyDTO;
import com.yang.infrastructure.auth.service.JwtTokenService;
import com.yang.infrastructure.security.MyUserDetails;
import com.yang.infrastructure.utils.RedisUtils;
import com.yang.infrastructure.utils.SpringContextUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class JwtTokenVerifyFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        UserContextDetails userContextDetails = null;
        Object userDetailsFromRedis = getUserDetailsFromRedis(token);
        if (userDetailsFromRedis != null) {
            userContextDetails = (UserContextDetails) userDetailsFromRedis;
        }

        if (userContextDetails == null) {
            JwtTokenService jwtTokenService = SpringContextUtils.getBeanOfType(JwtTokenService.class);
            JwtTokenProperty jwtTokenProperty = SpringContextUtils.getBeanOfType(JwtTokenProperty.class);

            JwtTokenVerifyRequest jwtTokenVerifyRequest = new JwtTokenVerifyRequest();
            jwtTokenVerifyRequest.setToken(token);
            jwtTokenVerifyRequest.setSecret(jwtTokenProperty.getSecret());

            JwtTokenVerifyDTO verify = jwtTokenService.verify(jwtTokenVerifyRequest);
            if (verify == null) {
                filterChain.doFilter(request, response);
                return;
            }

            userContextDetails = new UserContextDetails();
            userContextDetails.setId(Integer.valueOf(verify.getSubject()));
            userContextDetails.setToken(token);
            userContextDetails.setUsername(verify.getPayLoads().get("username"));
            userContextDetails.setExtendMap(verify.getPayLoads());
            List<Role> roles = JSONObject.parseArray(verify.getPayLoads().get("roles"), Role.class);
            userContextDetails.setPermissionDetails(roles.stream().map(role -> {
                PermissionDetails permissionDetails = new PermissionDetails();
                permissionDetails.setName(role.getCode());
                return permissionDetails;
            }).collect(Collectors.toList()));
        }

        if (userContextDetails == null) {
            filterChain.doFilter(request, response);
            return;
        }
        UserDetails userDetails = new MyUserDetails(userContextDetails);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(userDetails, null, null);
        SecurityContextHolder.getContext().setAuthentication(authenticationToken);
        filterChain.doFilter(request, response);
    }

    private Object getUserDetailsFromRedis(String token) {
        RedisUtils redisUtils = SpringContextUtils.getBeanOfType(RedisUtils.class);
        return redisUtils.getKey("token:" + token);
    }
}
```
其实从上面的实现中，会发现，与我们之前的JwtTokenVerifyInterceptor很像，不过我们当时只是简单地通过线程上下文来传递用户核心信息，而SpringSecurity对此进一步作出封装:
1）UsernamePasswordAuthenticationToken：SpringSecurity用于表示基于用户名和密码地身份验证对象，继承自AbstractAuthenticationToken类，包含了用户名和密码等凭据信息，在身份验证过程中，UsernamePasswordAuthenticationToken用于封装用户提交地身份验证凭据，并在后续身份验证过程中进行传递和处理。
2）SecurityContextHolder：SpringSecurity用于管理安全上下文地持有者，提供一个静态方法getContext()用于获取当前线程中地安全上下文。
最后，我们将这个过滤器，添加到SpringSecurity的配置类中
```java
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // 登录和注册接口，放行
        return httpSecurity.authorizeRequests()
                .antMatchers("/user/login").permitAll()
                .antMatchers("/user/register").permitAll()
                .anyRequest().authenticated()
                .and()
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // 设置无状态连接，即不创建session
                .and()
                .cors().disable() // 解决跨域问题
                .exceptionHandling()
                .authenticationEntryPoint(new JwtAuthenticationExceptionHandler()) // 未认证异常处理
                .accessDeniedHandler(new JwtAccessDeniedExceptionHandler()) // 权限不足异常处理
                .and()
                .addFilterBefore(new JwtTokenVerifyFilter(), UsernamePasswordAuthenticationFilter.class) // 添加自定义过滤器
                .build();
    }
}
```
然后我们启动项目，先进行登录
{% asset_img 12.png %}
登录成功后，携带该token，作为请求头，访问/security/hello测试接口,访问成功
{% asset_img 13.png %}
然后我们携带一个无效的token，作为请求头，再次访问/security/hello测试接口
{% asset_img 14.png %}

### 3. 参考文章
https://segmentfault.com/a/1190000041947192
https://blog.csdn.net/m0_37989980/article/details/107519382
https://zhuanlan.zhihu.com/p/455858001