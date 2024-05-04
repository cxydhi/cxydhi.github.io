---
title: Nacos学习2—Nacos配置中心
date: 2024-04-02 20:28:45
tags: 
    - 中间件
    - Nacos
---
在上一节中，介绍了Nacos配置中心的入门使用，在这一节，会讲解关于nacos配置中心的其他概念，比如命名空间、共享配置、以及如何在服务端更新nacos配置。
### 1. nacos的基础概念
如下图所示，在nacos配置中，namespace、group、dataId为最基础的、最重要的三个概念。
namespace: 命名空间可用于进行不同环境的配置隔离。一般一个环境划分到一个命名空间
group： 配置分组用于将不同的服务可以归类到同一分组。一般将一个项目的配置分到一组
dataId： 在系统中，一个配置文件通常就是一个配置集（dataId）。一般微服务的配置就是一个配置集
{% asset_img image.png %}
当我们没有配置namespace时，其默认值就是public；没有配置group时，默认值为DEFAULT_GROUP，dataId默认是当前应用的application.name。
进入nacos，点击命名空间，选择新建命名空间
{% asset_img image1.png %}
假设我们现在是在开发环境，然后我们创建一个dev命名空间，用于开发环境的相关配置,这里的命名空间id不用填，系统会自动生成
{% asset_img image2.png %}
创建成功后，我们复制命名空间id
{% asset_img image3.png %}
将这个命名空间id，粘贴到之前bootstrap.yml配置文件的namespace中
{% asset_img image4.png %}
然后我们进入dev命名空间，新建一个配置
{% asset_img image5.png %}
{% asset_img image6.png %}
配置完毕后，启动项目，再次访问http://localhost:8080/test/hello，结果如下：
{% asset_img image7.png %}
可见，此时读取到的，是dev命名空间下的配置。

### 2. 共享配置
当我们项目中的服务数量增加后，配置文件也会相应的增加，而多个配置文件中，可能存在相同的配置，因此我们可以将这些相同的配置独立出来，作为该项目各个服务的共享配置文件。
假设我们现在有两个服务，这两个服务都共享同一个redis数据源和同一个mysql数据源，因此我们可以把这两个数据源的配置，提取成共享配置。
service1和service2的bootstrap.yml配置分别如下：
{% asset_img image8.png %}
{% asset_img image9.png %}
这两个服务的bootstrap配置文件内容，基本一样（除了端口号和服务名），除此之外，这里共享配置中，common-redis共享配置的refresh值设置为true，而common-mysql没有设置，也就是说，这两个服务能监听到common-redis配置的变化，而不会关注到common-mysql配置的变化。
配置完成后，这两个应用，我们都添加下面这个controller类，用于进行测试：
{% asset_img image10.png %}
然后，我们在nacos上添加相关的共享配置
{% asset_img image11.png %}
{% asset_img image12.png %}
启动服务1和服务2，然后分别访问获取mysql配置和redis配置的接口
{% asset_img image13.png %}
然后我们在分别修改mysql和redis的配置
{% asset_img image14.png %}
{% asset_img image15.png %}
然后再次访问相关的接口，结果如下：
{% asset_img image16.png %}
由上图可知，如果公共配置想要获取实时数据，需要加上refresh属性的配置。
### 3. 扩展配置
一般情况下，我们的配置文件一个就可以了，但有时候，如果我们的配置分散在多个配置文件时，就需要使用到扩展配置了。
假设我们现在有两个扩展配置，一个是配置日志打印的，一个是配置消息队列，假设配置内容如下：
ext-log.yaml
{% asset_img image17.png %}
{% asset_img image18.png %}
bootstrap.yml内容如下：
{% asset_img image19.png %}
添加一个controller用于测试
{% asset_img image20.png %}
启动应用，结果如下：
{% asset_img image21.png %}
然后我们修改扩展配置
{% asset_img image22.png %}
{% asset_img image23.png %}
再次访问相关的接口
{% asset_img image24.png %}
可见，对于扩展配置，如果要获取实时数据，那么也需要加上refresh
### 4. 更新数据到nacos
假设我们现在有一个数据迁移的任务，每隔一段时间，会触发这个任务一次，该任务会读取nacos配置中的起始时间和结束时间，然后查询数据库并将查询的数据进行迁移，最后会更新起始时间，然后修改nacos中的配置。
首先我们在nacos上添加一个和数据迁移相关的配置：
{% asset_img image25.png %}
添加一个config，用于构造configService
{% asset_img image26.png %}
添加一个定时任务，用于查询nacos配置，并根据nacos配置迁移数据
{% asset_img image27.png %}
执行结果如下图所示，每隔十秒执行一次任务，并且将起始时间进行更新。
{% asset_img image28.png %}

### 5. 参考文档
（超详细）关于Nacos的共享配置( shared-configs)和拓展配置(extension-config)：https://blog.csdn.net/weixin_42329623/article/details/131018680