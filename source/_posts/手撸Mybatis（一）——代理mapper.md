---
title: 手撸Mybatis（一）——代理mapper
date: 2024-05-04 14:52:01
tags:
    - Mybatis
---
### 引言
最近刚写完毕设，闲来无事，看到网上有一个手撸Mybatis的教程，于是想自己实现一个简易版的Mybatis。
### 创建简单的映射器代理工厂
在使用mybatis的时候，我们一般只需要定义mapper的接口，并添加相应的@Mapper注解，然后实现对应的xml文件即可，而不需要对mapper接口进行具体的实现。其实本质上，这些mapper接口是有实现的，但不是我们手动通过implement来实现，而是通过代理的方式进行实现。因此，对于Mybatis的手撸，首先要关注的，就是如何对mapper进行代理。
首先我们定义一个MapperProxy，该类实现InvocationHandler接口，通过实现该接口，实现动态代理。这里有两个属性——sqlSession和mapperInterface，其中，sqlSession是用来模拟执行sql语句的。
```json
package com.yang.mybatis.proxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class MapperProxy<T> implements InvocationHandler {
private Map<String, Object> sqlSession ;

private Class<T> mapperInterface;

public MapperProxy(Map<String, Object> sqlSession, Class<T> mapperInterface) {
this.sqlSession = sqlSession;
this.mapperInterface = mapperInterface;
}

@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
if (Object.class.equals(method.getDeclaringClass())) {
return method.invoke(this, args);
}
String key = this.mapperInterface.getName() + "." + method.getName();
return sqlSession.get(key);
}
}

```
当我们在执行mapper的某个方法时，最终会进入到invoke方法，并通过sqlSession来获取模拟值。
接着我们定义MapperProxyFactory，每一个mapper都有一个MapperProxyFactory与之相对于，然后newInstance方法接收模拟的sql结果，并通过Proxy.newProxyInstance创建动态代理对象。
```json
package com.yang.mybatis.proxy;

import java.lang.reflect.Proxy;
import java.util.Map;

public class MapperProxyFactory <T> {
private final Class<T> mapperInterface;

public MapperProxyFactory(Class<T> mapperInterface) {
this.mapperInterface = mapperInterface;
}

public T newInstance(Map<String, Object> sqlSession) {
MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface);
return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(),
new Class[]{mapperInterface},
mapperProxy);
}
}

```
最后，我们先创建一个IUserMapper类
```json
package com.yang.mybatis.test;

public interface IUserMapper {
String queryUserName(Integer id);

Integer queryUserAge(Integer id);
}

```
然后创建对应的测试方法
```json
package com.yang.mybatis.test;

import com.yang.mybatis.proxy.MapperProxyFactory;

import java.util.HashMap;
import java.util.Map;

public class Main {
public static void main(String[] args) {
MapperProxyFactory<IUserMapper> userDaoMapperProxyFactory = new MapperProxyFactory<>(IUserMapper.class);
Map<String, Object> sqlSession = new HashMap<>();
sqlSession.put("com.yang.mybatis.test.IUserMapper.queryUserName", "模拟查询用户名");
sqlSession.put("com.yang.mybatis.test.IUserMapper.queryUserAge", 1);
IUserMapper iUserMapper = userDaoMapperProxyFactory.newInstance(sqlSession);
System.out.println(iUserMapper.queryUserAge(1));
System.out.println(iUserMapper.queryUserName(1));
}
}

```
运行结果如下：
{% asset_img 1.png %}
### 实现映射器的注册和使用
像上述的这种方法，我们每次要获取一个Mapper，就要new一个对应的MapperProxyFactory，这样不太方便。一般情况下，mapper是放在同一个包下的，那么我们可以通过扫描包，来初始化MapperProxyFactory。至于上一节的sqlSession，因为我们目前是模拟数据，所以在初始化过程中，把这些模拟数据要随便mock住就行。
因为要扫描包获取包下的Class类，我们先添加hutool-all，以便通过其提供的ClassScanner获取包
```json
<dependency>
<groupId>cn.hutool</groupId>
<artifactId>hutool-all</artifactId>
<version>5.8.12</version>
</dependency>
```
然后，我们修改MapperProxyFactory
```json
package com.yang.mybatis.proxy;

import cn.hutool.core.lang.ClassScanner;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MapperProxyFactory {
private Map<Class, MapperProxy> mapperProxyMap = new HashMap<>();

public MapperProxyFactory(String packageName) {
// 扫描包
ClassScanner scanner = new ClassScanner(packageName);
Set<Class<?>> mapperTypes = scanner.scan();
for (Class<?> mapperType : mapperTypes) {
if (!mapperType.isInterface()) {
// 只对接口进行处理
continue;
}

Map<String, String> mockSqlSession = mockSqlSession(mapperType);
MapperProxy mapperProxy = new MapperProxy(mockSqlSession, mapperType);
mapperProxyMap.put(mapperType, mapperProxy);
}
}

public Object newInstance(Class mapperType) {
MapperProxy mapperProxy = mapperProxyMap.get(mapperType);
return Proxy.newProxyInstance(mapperType.getClassLoader(),
new Class[]{mapperType},
mapperProxy);
}

private Map<String, String> mockSqlSession(Class mapperType) {
Map<String, String> sqlSession = new HashMap<>();
for (Method method : mapperType.getMethods()) {
String methodName = method.getName();
String key = mapperType.getName() + "." + methodName;
sqlSession.put(key, key);
}
return sqlSession;
}
}
```
最后进行测试：
```json
public static void main(String[] args) {
MapperProxyFactory mapperProxyFactory = new MapperProxyFactory("com.yang.mybatis.test");
IUserMapper iUserMapper = (IUserMapper) mapperProxyFactory.newInstance(IUserMapper.class);
System.out.println(iUserMapper.queryUserName(1));
}
```
测试结果如下：
{% asset_img 2.png %}


