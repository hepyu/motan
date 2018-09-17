# motan源码分析二（服务引用流程源码分析）

## RefererConfigBean服务引用流程源码分析

## 初始化

主体流程：

```java
private void checkAndConfigBasicConfig() //检查并配置basicConfig
private void checkAndConfigProtocols() //检查是否已经装配protocols，否则按basicConfig--->default路径查找
public void checkAndConfigRegistry() //检查并配置registry

//引用生成bean的时候会调用
public T getObject()
```

下面展开看getObject()方法：

```java
	@Override
    public T getObject() throws Exception {
        return getRef();
    }

    public T getRef() {
        if (ref == null) {
            initRef();
        }
        return ref;
    }

    public synchronized void initRef() {
        if (initialized.get()) {
            return;
        }

        try {
            interfaceClass = (Class) Class.forName(interfaceClass.getName(), true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new MotanFrameworkException("ReferereConfig initRef Error: Class not found " + interfaceClass.getName(), e,
                    MotanErrorMsgConstant.FRAMEWORK_INIT_ERROR);
        }

        if (CollectionUtil.isEmpty(protocols)) {
            throw new MotanFrameworkException(String.format("%s RefererConfig is malformed, for protocol not set correctly!",
                    interfaceClass.getName()));
        }

        checkInterfaceAndMethods(interfaceClass, methods);

        clusterSupports = new ArrayList<ClusterSupport<T>>(protocols.size());
        List<Cluster<T>> clusters = new ArrayList<Cluster<T>>(protocols.size());
        String proxy = null;

        ConfigHandler configHandler = ExtensionLoader.getExtensionLoader(ConfigHandler.class).getExtension(MotanConstants.DEFAULT_VALUE);

        List<URL> registryUrls = loadRegistryUrls();
        String localIp = getLocalHostAddress(registryUrls);
        for (ProtocolConfig protocol : protocols) {
            Map<String, String> params = new HashMap<String, String>();
            params.put(URLParamType.nodeType.getName(), MotanConstants.NODE_TYPE_REFERER);
            params.put(URLParamType.version.getName(), URLParamType.version.getValue());
            params.put(URLParamType.refreshTimestamp.getName(), String.valueOf(System.currentTimeMillis()));

            collectConfigParams(params, protocol, basicReferer, extConfig, this);
            collectMethodConfigParams(params, this.getMethods());

            URL refUrl = new URL(protocol.getName(), localIp, MotanConstants.DEFAULT_INT_VALUE, interfaceClass.getName(), params);
            ClusterSupport<T> clusterSupport = createClusterSupport(refUrl, configHandler, registryUrls);

            clusterSupports.add(clusterSupport);
            clusters.add(clusterSupport.getCluster());

            proxy = (proxy == null) ? refUrl.getParameter(URLParamType.proxy.getName(), URLParamType.proxy.getValue()) : proxy;

        }

        ref = configHandler.refer(interfaceClass, clusters, proxy);

        initialized.set(true);
    }
```



