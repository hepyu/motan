# motan源码分析三（服务端服务被调用流程）

# 1. EventHandler及ProviderMessageRouter 

## 1.1 注册netty的eventHandler

前面讲过，在暴露服务的时候会初始化DefaultRpcExporter对象作为暴露的exporter，在DefaultRpcExporter的构造方法中初始化了默认的：

ProviderMessageRouter requestRouter = initRequestRouter(url);

```java
protected ProviderMessageRouter initRequestRouter(URL url) {
        String ipPort = url.getServerPortStr();
        ProviderMessageRouter requestRouter = ipPort2RequestRouter.get(ipPort);

        if (requestRouter == null) {
            ipPort2RequestRouter.putIfAbsent(ipPort, new ProviderProtectedMessageRouter());
            requestRouter = ipPort2RequestRouter.get(ipPort);
        }
        requestRouter.addProvider(provider);

        return requestRouter;
}
```

## 1.2 ProviderProtectedMessageRouter类

下面我们来看下这个ProviderProtectedMessageRouter类

ProviderProtectedMessageRouter extends > ProviderMessageRouter extends > MessageHandler

所以能够看得出，这个类就是motan rpc的，tcp包处理类，ProviderMessageRouter通过实现MessageHandler的handle方法处理RPC的TCP包

下面我们先看这个handle方法：

```java
@Override
    public Object handle(Channel channel, Object message) {
        // 参数检查，忽略

        Request request = (Request) message;
		// 根据请求获取service的标识serviceKey
        String serviceKey = MotanFrameworkUtil.getServiceKey(request);
		// 从已注册的provider中获取provider代理类的实例
        Provider<?> provider = providers.get(serviceKey);

        if (provider == null) {
            // ... 这里判断没有provider的时候封装错误并返回响应，代码这里省略掉
            return response;
        }
        // 通过反射找到本地方法引用，并设置相关参数到request中，反序列化请求包到具体的参数列表
        Method method = provider.lookupMethod(request.getMethodName(), request.getParamtersDesc());
        fillParamDesc(request, method);
        processLazyDeserialize(request, method);
        // 调用代理类的call方法执行本地bean
        return call(request, provider);
    }
```

call方法（ProviderProtectedMessageRouter类）：

```java
  @Override
    protected Response call(Request request, Provider<?> provider) {
        // 支持的最大worker thread数
        int maxThread =
                provider.getUrl().getIntParameter(URLParamType.maxWorkerThread.getName(), URLParamType.maxWorkerThread.getIntValue());

        String requestKey = MotanFrameworkUtil.getFullMethodString(request);

        try {
            int requestCounter = 0, totalCounter = 0;
            requestCounter = incrRequestCounter(requestKey);
            totalCounter = incrTotalCounter();
            if (isAllowRequest(requestCounter, totalCounter, maxThread, request)) {
                return super.call(request, provider);
            } else {
                // reject request
                return reject(request.getInterfaceName() + "." + request.getMethodName(), requestCounter, totalCounter, maxThread);
            }

        } finally {
            decrTotalCounter();
            decrRequestCounter(requestKey);
        }
    }
```

这块可以看到默认motan是会有一个基本的熔断策略的，就是isAllowRequest这个方法里面的规则，这里不展开，大家感兴趣可以参考这个链接, 

[motan线程保护机制及合理设置motan配置](https://github.com/zrbcool/motan/blob/thread-issue-demo/docs/wiki/motan%E7%BA%BF%E7%A8%8B%E4%BF%9D%E6%8A%A4%E6%9C%BA%E5%88%B6%E5%8F%8A%E5%90%88%E7%90%86%E8%AE%BE%E7%BD%AEmotan%E9%85%8D%E7%BD%AE.md)

## 1.3 ProviderMessageRouter 类

上面的流程调用完后，会调用父类的call方法也就是ProviderMessageRouter类中的call方法，内容如下

```java
protected Response call(Request request, Provider<?> provider) {
        try {
            return provider.call(request);
        } catch (Exception e) {
            DefaultResponse response = new DefaultResponse();
            response.setException(new MotanBizException("provider call process error", e));
            return response;
        }
}
```

可以看到到这个地方后，就开始调用具体provider的call方法了，下面我们来介绍Provider

# 2. Provider类

我们前面介绍过provider注册的时候调用这段代码初始化provider：

```java
    protected <T> Provider<T> getProvider(Protocol protocol, T proxyImpl, URL url, Class<T> clz){
        if (protocol instanceof ProviderFactory){
            return ((ProviderFactory)protocol).newProvider(proxyImpl, url, clz);
        } else{
            return new DefaultProvider<T>(proxyImpl, url, clz);
        }
    }
```

因为motan还没有实现ProviderFactory动态代理，所以均创建的DefaultProvider代理类，我们就看这个类：

由DefaultProvider继承自AbstractProvider，call方法如下：

```java
 @Override
    public Response call(Request request) {
        Response response = invoke(request);

        return response;
    }
```

然后我们再看DefaultProvider的invoke方法：

```java
	@Override
    public Response invoke(Request request) {
        DefaultResponse response = new DefaultResponse();
		// 通过反射得到本地方法的引用
        Method method = lookupMethod(request.getMethodName(), request.getParamtersDesc());

        if (method == null) {
            // 如果找不到方法，封装错误并返回，这里省略
            return response;
        }

        try {
            // 调用具体实例proxyImpl的方法method，并传入运行时参数request.getArguments() （参数在前面已经经过了反序列化）
            Object value = method.invoke(proxyImpl, request.getArguments());
            // 封装响应
            response.setValue(value);
        } catch (Exception e) {
            // 错误处理，省略
            // response.setException .......
        } catch (Throwable t) {{
            // 错误处理，省略
            // response.setException .......
        }

        if (response.getException() != null) {
            //是否传输业务异常栈
            boolean transExceptionStack = this.url.getBooleanParameter(URLParamType.transExceptionStack.getName(), URLParamType.transExceptionStack.getBooleanValue());
            if (!transExceptionStack) {//不传输业务异常栈
                ExceptionUtil.setMockStackTrace(response.getException().getCause());
            }
        }
        // 传递rpc版本和attachment信息方便不同rpc版本的codec使用。
        response.setRpcProtocolVersion(request.getRpcProtocolVersion());
        response.setAttachments(request.getAttachments());
        return response;
    }
```



