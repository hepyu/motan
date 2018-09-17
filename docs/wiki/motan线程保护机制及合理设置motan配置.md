# motan线程保护机制及合理设置motan配置



## 背景

线上ne-account服务由于调用量及qps都较高，在上线期间，motan日志打出如下错误：

```java
2018-09-14 12:18:19 [ERROR] ThreadProtectedRequestRouter reject request: request_method=XXXXXX request_counter=76 =76 max_thread=100
```

github上作者的回复： <https://github.com/weibocom/motan/issues/551>

## 重现

配置motan服务MotanDemoService，提供4个方法，其中hello1的处理逻辑为sleep 1s

```java
2018-09-14 13:58:27 [INFO] add method sign:hell817ff3733269, methodinfo:MethodInfo [group=motan-demo-rpc, interfaceName=com.weibo.motan.demo.service.MotanDemoService, methodName=hello, paramtersDesc=java.lang.String, version=1.0]
2018-09-14 13:58:27 [INFO] add method sign:helld1f0f2c9182d, methodinfo:MethodInfo [group=motan-demo-rpc, interfaceName=com.weibo.motan.demo.service.MotanDemoService, methodName=hello2, paramtersDesc=java.lang.String, version=1.0]
2018-09-14 13:58:27 [INFO] add method sign:hell52228017a74a, methodinfo:MethodInfo [group=motan-demo-rpc, interfaceName=com.weibo.motan.demo.service.MotanDemoService, methodName=hello4, paramtersDesc=java.lang.String, version=1.0]
2018-09-14 13:58:27 [INFO] add method sign:hellaea7504ac806, methodinfo:MethodInfo [group=motan-demo-rpc, interfaceName=com.weibo.motan.demo.service.MotanDemoService, methodName=hello3, paramtersDesc=java.lang.String, version=1.0]
```

服务配置：

```xml
<motan:protocol id="demoMotan" default="true" name="motan"
                maxServerConnection="80000" maxContentLength="1048576"
                maxWorkerThread="100" minWorkerThread="100" threads="100" />
```

motan服务暴露在8002端口，启动一个客户端，请求服务器1000次

汇总结果如下： 

- **测试场景1**

  75并发访问服务端，请求1000次，结果如下：
  motan demo is finish. success: 1000 error: 0

- **测试场景2**

  80并发访问服务端，请求1000次，结果如下：
  motan demo is finish. success: 150 **error: 850**

  **服务端错误信息：**

  ```java
  2018-09-14 14:58:03 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=76 =76 max_thread=100
  2018-09-14 14:58:03 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=76 =76 max_thread=100
  2018-09-14 14:58:03 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=76 =76 max_thread=100
  2018-09-14 14:58:04 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=76 =76 max_thread=100
  2018-09-14 14:58:04 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=78 =78 max_thread=100
  2018-09-14 14:58:04 [ERROR] ThreadProtectedRequestRouter reject request: request_method=com.weibo.motan.demo.service.MotanDemoService.hello request_counter=77 =77 max_thread=100
  ```

  客户端将请求指向MotanDemoSingleMethodService的hello方法

  **期望接口能够承受100并发**，测试结果如下：

  motan demo is finish. **success: 1000** error: 0

  **修改并发数为150，期望服务器触发熔断，拒绝部分请求**，测试结果如下：

  motan demo is finish. **success: 1000** error: 0

  **实际与预期不符，推测motan在这种情况下，可能有服务端排队机制，或者客户端阻塞等待请求了**，为了避免在服务端处理能力达到极限时hang死客户端，客户端应该设置合理的超时退出时间，另外也需考虑熔断机制避免引发雪崩

  ## 综述

  - 综上所述，motan默认的保护机制确实带来了一定的困扰，但是motan的这种机制也默认为大家的服务进行了一种保护，在某个接口并发压力大的情况下依然可以预留25%的线程处理其余的接口方法，所以大家在设置motan的线程数时要额外注意，是否接口中各个方法的访问压力严重不均衡，如果发生了上述的错误，则说明这个接口需要单独抽离出来了
  - TODO：深入研究各种极端条件下，motan的排队，拒绝行为，找到设置合理motan配置的最佳实践