/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.rpc.support;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.utils.ArrayUtils;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.PojoUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.AsyncRpcResult;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;

import com.alibaba.fastjson.JSON;

import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.apache.dubbo.rpc.Constants.MOCK_KEY;
import static org.apache.dubbo.rpc.Constants.RETURN_PREFIX;
import static org.apache.dubbo.rpc.Constants.THROW_PREFIX;
import static org.apache.dubbo.rpc.Constants.FAIL_PREFIX;
import static org.apache.dubbo.rpc.Constants.FORCE_PREFIX;
import static org.apache.dubbo.rpc.Constants.RETURN_KEY;

// 本地伪装的invoker实现类。
final public class MockInvoker<T> implements Invoker<T> {

    // 代理工厂
    private final static ProxyFactory proxyFactory = ExtensionLoader.getExtensionLoader(ProxyFactory.class).getAdaptiveExtension();

    // mock 与 Invoker 对象的映射缓存
    private final static Map<String, Invoker<?>> mocks = new ConcurrentHashMap<String, Invoker<?>>();

    // 异常集合
    private final static Map<String, Throwable> throwables = new ConcurrentHashMap<String, Throwable>();

    // url对象
    private final URL url;

    private final Class<T> type;

    public MockInvoker(URL url, Class<T> type) {
        this.url = url;
        this.type = type;
    }

    // 解析mock值
    public static Object parseMockValue(String mock) throws Exception {
        return parseMockValue(mock, null);
    }

    // 解析mock值
    public static Object parseMockValue(String mock, Type[] returnTypes) throws Exception {
        // 如果mock为empty，则
        Object value = null;
        if ("empty".equals(mock)) {
            // 获得空的对象
            value = ReflectUtils.getEmptyObject(returnTypes != null && returnTypes.length > 0 ? (Class<?>) returnTypes[0] : null);
        } else if ("null".equals(mock)) {
            // 返回null
            value = null;
        } else if ("true".equals(mock)) {
            // 返回true
            value = true;
        } else if ("false".equals(mock)) {
            // 返回false
            value = false;
        } else if (mock.length() >= 2 && (mock.startsWith("\"") && mock.endsWith("\"")
                || mock.startsWith("\'") && mock.endsWith("\'"))) {
            // // 使用 '' 或 "" 的字符串，截取掉头尾
            value = mock.subSequence(1, mock.length() - 1);
        } else if (returnTypes != null && returnTypes.length > 0 && returnTypes[0] == String.class) {
            // 字符串
            value = mock;
        } else if (StringUtils.isNumeric(mock, false)) {
            // 是数字
            value = JSON.parse(mock);
        } else if (mock.startsWith("{")) {
            // 是map类型的
            value = JSON.parseObject(mock, Map.class);
        } else if (mock.startsWith("[")) {
            // 数组类型
            value = JSON.parseObject(mock, List.class);
        } else {
            value = mock;
        }
        if (ArrayUtils.isNotEmpty(returnTypes)) {
            value = PojoUtils.realize(value, (Class<?>) returnTypes[0], returnTypes.length > 1 ? returnTypes[1] : null);
        }
        return value;
    }

    // 本地伪装的核心实现
    // mock分三种，分别是 return、throw、自定义的mock类。
    @Override
    public Result invoke(Invocation invocation) throws RpcException {
        // 获得 `"mock"` 配置项，方法级 > 类级
        String mock = getUrl().getParameter(invocation.getMethodName() + "." + MOCK_KEY);
        if (invocation instanceof RpcInvocation) {
            ((RpcInvocation) invocation).setInvoker(this);
        }
        // 如果mock为空
        if (StringUtils.isBlank(mock)) {
            // 获取类级的mock
            mock = getUrl().getParameter(MOCK_KEY);
        }
        // 如果还是为空。则抛出异常
        if (StringUtils.isBlank(mock)) {
            throw new RpcException(new IllegalAccessException("mock can not be null. url :" + url));
        }
        // 标准化 "mock" 配置项
        mock = normalizeMock(URL.decode(mock));

        // 等于 "return " ，返回值为空的 RpcResult 对象
        if (mock.startsWith(RETURN_PREFIX)) {
            // 分割
            mock = mock.substring(RETURN_PREFIX.length()).trim();
            try {
                // 获得返回类型
                Type[] returnTypes = RpcUtils.getReturnTypes(invocation);
                // 解析mock值
                Object value = parseMockValue(mock, returnTypes);
                return AsyncRpcResult.newDefaultAsyncResult(value, invocation);
            } catch (Exception ew) {
                throw new RpcException("mock return invoke error. method :" + invocation.getMethodName()
                        + ", mock:" + mock + ", url: " + url, ew);
            }
        }
        // 如果是throw
        else if (mock.startsWith(THROW_PREFIX)) {
            // 根据throw分割
            mock = mock.substring(THROW_PREFIX.length()).trim();
            // 如果为空，则抛出异常
            if (StringUtils.isBlank(mock)) {
                throw new RpcException("mocked exception for service degradation.");
            } else { // user customized class
                // 创建自定义异常
                Throwable t = getThrowable(mock);
                throw new RpcException(RpcException.BIZ_EXCEPTION, t);
            }
        } else { //impl mock
            try {
                // 否则直接获得invoke
                Invoker<T> invoker = getInvoker(mock);
                // 调用
                return invoker.invoke(invocation);
            } catch (Throwable t) {
                throw new RpcException("Failed to create mock implementation class " + mock, t);
            }
        }
    }

    // 从异常集合中取出异常
    public static Throwable getThrowable(String throwstr) {
        // 从异常集合中取出异常
        Throwable throwable = throwables.get(throwstr);
        // 如果不为空，则抛出异常
        if (throwable != null) {
            return throwable;
        }

        try {
            Throwable t;
            // 获得异常类
            Class<?> bizException = ReflectUtils.forName(throwstr);
            Constructor<?> constructor;
            // 获得参数为string类型的构造方法
            constructor = ReflectUtils.findConstructor(bizException, String.class);
            // 创建 Throwable 对象
            t = (Throwable) constructor.newInstance(new Object[]{"mocked exception for service degradation."});
            // 添加到缓存中
            if (throwables.size() < 1000) {
                throwables.put(throwstr, t);
            }
            return t;
        } catch (Exception e) {
            throw new RpcException("mock throw error :" + throwstr + " argument error.", e);
        }
    }

    // 创建mockService的代理对象
    @SuppressWarnings("unchecked")
    private Invoker<T> getInvoker(String mockService) {
        // 从缓存中，获得 Invoker 对象，如果有，直接缓存。
        Invoker<T> invoker = (Invoker<T>) mocks.get(mockService);
        if (invoker != null) {
            return invoker;
        }
        // 获得服务类型
        Class<T> serviceType = (Class<T>) ReflectUtils.forName(url.getServiceInterface());
        // 获得MockObject实例对象
        T mockObject = (T) getMockObject(mockService, serviceType);
        // 创建invoker
        invoker = proxyFactory.getInvoker(mockObject, serviceType, url);
        if (mocks.size() < 10000) {
            // 加入集合
            mocks.put(mockService, invoker);
        }
        return invoker;
    }

    @SuppressWarnings("unchecked")
    public static Object getMockObject(String mockService, Class serviceType) {
        if (ConfigUtils.isDefault(mockService)) {
            mockService = serviceType.getName() + "Mock";
        }

        // // 获得类型
        Class<?> mockClass = ReflectUtils.forName(mockService);
        if (!serviceType.isAssignableFrom(mockClass)) {
            throw new IllegalStateException("The mock class " + mockClass.getName() +
                    " not implement interface " + serviceType.getName());
        }

        try {
            // 初始化
            return mockClass.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException("No default constructor from mock class " + mockClass.getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }


    /**
     * Normalize mock string:
     *
     * <ol>
     * <li>return => return null</li>
     * <li>fail => default</li>
     * <li>force => default</li>
     * <li>fail:throw/return foo => throw/return foo</li>
     * <li>force:throw/return foo => throw/return foo</li>
     * </ol>
     *
     * @param mock mock string
     * @return normalized mock string
     */
    // 规范化mock值。
    public static String normalizeMock(String mock) {
        if (mock == null) {
            return mock;
        }

        mock = mock.trim();

        if (mock.length() == 0) {
            return mock;
        }

        if (RETURN_KEY.equalsIgnoreCase(mock)) {
            return RETURN_PREFIX + "null";
        }

        // 若果为 "true" "default" "fail" "force" 四种字符串，返回default
        if (ConfigUtils.isDefault(mock) || "fail".equalsIgnoreCase(mock) || "force".equalsIgnoreCase(mock)) {
            return "default";
        }

        // fail:throw/return foo => throw/return
        if (mock.startsWith(FAIL_PREFIX)) {
            mock = mock.substring(FAIL_PREFIX.length()).trim();
        }

        // force:throw/return foo => throw/return
        if (mock.startsWith(FORCE_PREFIX)) {
            mock = mock.substring(FORCE_PREFIX.length()).trim();
        }

        // 如果是return或者throw，替换`为"
        if (mock.startsWith(RETURN_PREFIX) || mock.startsWith(THROW_PREFIX)) {
            mock = mock.replace('`', '"');
        }

        return mock;
    }

    @Override
    public URL getUrl() {
        return this.url;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public void destroy() {
        //do nothing
    }

    @Override
    public Class<T> getInterface() {
        return type;
    }
}
