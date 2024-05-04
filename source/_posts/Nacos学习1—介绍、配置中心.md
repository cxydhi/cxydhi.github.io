---
title: Nacos学习1—介绍、配置中心
date: 2024-04-02 20:17:20
tags: 
    - 中间件
    - Nacos
---
### 1. 背景
在之前的Spring Cloud Gateway学习中，我们了解了网关相关配置，包括断言、过滤器等内容。在之前的文章中，我们是将这些配置，写到application.yml上，而一般情况下，我们Spring Cloud Gateway的网关配置，肯定不会是一成不变的，如果配置信息是在application.yml上，那么当我们需要添加其他的路由配置时，就先修改application.yml配置，然后重启服务，这不利于用户使用和产品的稳定。我们希望通过一个配置中心，来方便我们对这些路由信息进行统一的维护、管理。
#### 1.1. 配置中心思路
配置中心的一般思路为：
1） 首先把项目中的各种配置全部放到一个集中的地方进行统一管理，并提供一套标准接口。
2）当各个服务需要获取配置的时候，就来配置中心的接口拉取自己的配置。
3）当配置中心中的各种参数有更新的时候，也能通知到各个服务实时同步最新的消息，使之动态更新。
当加入服务配置中心后，我们的系统架构图如下：
{% asset_img image.png %}
#### 1.2. 常用的服务配置中心
Spring Cloud Config:官方提供的分布式系统的外部配置中心。
Nacos:阿里开源的框架，致力于发现、配置和管理微服务。Nacos提供了一组简单易用的特性集，帮助您快速实现动态服务发现、服务配置、服务元数据及流量管理。
Apollo：携程框架部门研发的开源配置管理中心，能够集中化管理应用不同环境、不同集群的配置，配置修改后能够实时推送到应用端，并且具备规范的权限、流程治理等特性。
{% asset_img image1.png %}
### 2. Nacos介绍和环境搭建
#### 2.1. Nacos介绍
Nacos是阿里巴巴推出的一个更易于构建云原生应用的动态服务发现、配置管理和服务管理平台。
Nacos的关键特性有以下几点：
1）服务发现和服务健康检测
Nacos 提供对服务的实时的健康检查，阻止向不健康的主机或服务实例发送请求。Nacos 支持传输层 (PING 或 TCP)和应用层 (如 HTTP、MySQL、用户自定义）的健康检查。 对于复杂的云环境和网络拓扑环境中（如 VPC、边缘网络等）服务的健康检查，Nacos 提供了 agent 上报模式和服务端主动检测2种健康检查模式。
2）动态配置服务
动态配置服务可以让您以中心化、外部化和动态化的方式管理所有环境的应用配置和服务配置。
动态配置消除了配置变更时重新部署应用和服务的需要，让配置管理变得更加高效和敏捷。
配置中心化管理让实现无状态服务变得更简单，让服务按需弹性扩展变得更容易。
3）动态DNS服务
动态 DNS 服务支持权重路由，从而更方便地实现中间层负载均衡、更灵活的路由策略、流量控制以及数据中心内网的简单DNS解析服务。
4）服务及其元数据管理
Nacos 能让您从微服务平台建设的视角管理数据中心的所有服务及元数据，包括管理服务的描述、生命周期、服务的静态依赖分析、服务的健康状态、服务的流量管理、路由及安全策略、服务的 SLA 以及最首要的 metrics 统计数据。
#### 2.2. Nacos环境搭建
进入Nacos官网：https://nacos.io
{% asset_img image2.png %}
点击前往github，跳转至github下载页面，然后点击tags选择要下载的版本
{% asset_img image3.png %}
下载完毕后解压到需要安装的目录
{% asset_img image5.png %}
解压完毕后，进入bin目录，修改启动文件startup.cmd，将mode由"cluster"改为standalone
{% asset_img image6.png %}
修改完毕后，双击startup.cmd启动Nacos服务
{% asset_img image7.png %}
访问http://localhost:8848/nacos，默认的用户名和密码为"nacos"。
{% asset_img image8.png %}
### 3. Nacos Config配置中心
#### 3.1. 基本使用
添加Nacos config的依赖
{% asset_img image9.png %}
注意，如果是在springboot2.4.x的版本之后，对于bootstrap.properties和bootstrap.yaml配置文件，需要在pom中加入依赖：
{% asset_img image10.png %}
然后添加配置文件，注意，不能使用application.yml，要新建一个bootstrap.yml作为配置文件
配置文件有优先级为：
bootstrap.properties  > bootstrap.yml > application.properties > application.yml
假设我们当前的配置如下：
{% asset_img image11.png %}
然后我们添加一个controller用于测试，下面这个controller的hello方法，会返回helloWorld的值，这里我先设置了一个默认值为“defalut value”
{% asset_img image12.png %}
当我们的hello.world在nacos上没有进行配置时，那么我们访问http://localhost:80/test/hello，结果如下图所示：
{% asset_img image13.png %}
现在我进入nacos官网，新建对应的配置，如下图所示，点击左边的创建配置
{% asset_img image14.png %}
{% asset_img image15.png %}
然后填写dataId,group,配置类型和配置的内容，注意，这里的data id要和我们的bootstrap.yml中配置的spring.cloud.nacos.config.name保持一致(如果不配置也可以，不配置的话，默认使用application.name的名称对应的配置）
{% asset_img image16.png %}
配置完毕后点击发布
{% asset_img image17.png %}
发布完成后，我们再次访问http://localhost:80/test/hello
{% asset_img image18.png %}
#### 3.2. dataId
之所以配置spring.application.name或spring.cloud.nacos.config.name，是因为它是构成Nacos配置管理dataId字段的一部分。在Nacos 中，dataId完整格式如下：
${prefix}-${spring.profiles.active}.${file-extension}
prefix: 默认为spring.application.name的值，也可以通过配置项spring.cloud.nacos.config.name来配置
spring.profiles.active:当前环境对应的profile，当spring.profiles.active为空时，对应的连接符-也不存在
file-extension：配置内容的数据格式，可以通过spring.cloud.nacos.config.file-extension来配置，目前只支持properties和yaml类型
我们修改刚才的bootstrap.yaml配置文件，修改结果如下：
{% asset_img image19.png %}
然后在nacos添加一个配置
{% asset_img image20.png %}
然后重启应用，访问http://localhost:8080/test/hello，结果如下：
{% asset_img image21.png %}

#### 4. 参考文章
使用nacos作为配置中心：https://blog.csdn.net/weixin_65211978/article/details/128102799
