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
package org.apache.dubbo.rpc.protocol;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ListenableFilter;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;

import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;
import static org.apache.dubbo.rpc.Constants.REFERENCE_FILTER_KEY;
import static org.apache.dubbo.rpc.Constants.SERVICE_FILTER_KEY;

/**
 * ListenerProtocol
 *
 * 该类实现了Protocol接口，其中也用到了装饰模式，是对Protocol的装饰，
 *
 * 是在服务引用和暴露的方法上加上了过滤器功能。
 *
 */
public class ProtocolFilterWrapper implements Protocol {

    private final Protocol protocol;

    // Protocol 的filter功能的 AOP
    public ProtocolFilterWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    // 该方法就是创建带 Filter 链的 Invoker 对象。倒序的把每一个过滤器串连起来，形成一个invoker。
    private static <T> Invoker<T> buildInvokerChain(final Invoker<T> invoker, String key, String group) {
        // 原来的invoker对象：ListenerInvokerWrapper
        Invoker<T> last = invoker;

        // 获得过滤器的所有扩展实现类实例集合
        List<Filter> filters = ExtensionLoader.getExtensionLoader(Filter.class).getActivateExtension(invoker.getUrl(), key, group);

        if (!filters.isEmpty()) {
            // 从最后一个过滤器开始循环，创建一个带有过滤器链的invoker对象
            for (int i = filters.size() - 1; i >= 0; i--) {
                final Filter filter = filters.get(i);
                final Invoker<T> next = last;
                // 生成一个新的带有filter功能的Invoker
                last = new Invoker<T>() {

                    @Override
                    public Class<T> getInterface() {
                        return invoker.getInterface();
                    }

                    @Override
                    public URL getUrl() {
                        return invoker.getUrl();
                    }

                    @Override
                    public boolean isAvailable() {
                        return invoker.isAvailable();
                    }

                    //  关键在这里，调用下一个filter代表的invoker，把每一个过滤器串起来
                    @Override
                    public Result invoke(Invocation invocation) throws RpcException {
                        Result asyncResult;
                        try {
                            // 依次调用各个过滤器，获得最终的返回结果
                            asyncResult = filter.invoke(next, invocation);
                        } catch (Exception e) {
                            // onError callback
                            // 捕获异常，如果该过滤器是ListenableFilter类型的
                            if (filter instanceof ListenableFilter) {
                                Filter.Listener listener = ((ListenableFilter) filter).listener();
                                if (listener != null) {
                                    //调用onError，回调错误信息
                                    listener.onError(e, invoker, invocation);
                                }
                            }
                            throw e;
                        }
                        return asyncResult;
                    }

                    @Override
                    public void destroy() {
                        invoker.destroy();
                    }

                    @Override
                    public String toString() {
                        return invoker.toString();
                    }
                };
            }
        }

        return new CallbackRegistrationInvoker<>(last, filters);
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // 如果是注册中心，则直接暴露服务
        if (REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        // 服务提供侧 暴露服务,注意buildInvokerChain返回的是CallbackRegistrationInvoker对象
        // 在服务暴露上做了过滤器链的增强，也就是加上了过滤器。
        return protocol.export(buildInvokerChain(invoker, SERVICE_FILTER_KEY, CommonConstants.PROVIDER));
    }

    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        // 如果是注册中心，则直接引用
        if (REGISTRY_PROTOCOL.equals(url.getProtocol())) {
            return protocol.refer(type, url);
        }
        // 消费者侧引用服务
        // 在服务引用上做了过滤器链的增强，也就是加上了过滤器。
        // 参数为reference.filter ， consumer
        return buildInvokerChain(protocol.refer(type, url), REFERENCE_FILTER_KEY, CommonConstants.CONSUMER);
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }

    // 带有 过滤器链 的 Invoker
    static class CallbackRegistrationInvoker<T> implements Invoker<T> {

        private final Invoker<T> filterInvoker;
        private final List<Filter> filters;

        public CallbackRegistrationInvoker(Invoker<T> filterInvoker, List<Filter> filters) {
            this.filterInvoker = filterInvoker;
            this.filters = filters;
        }

        @Override
        public Result invoke(Invocation invocation) throws RpcException {
            // 调用拦截器链的invoke
            Result asyncResult = filterInvoker.invoke(invocation);

            // 把异步返回的结果加入到上下文中
            asyncResult.thenApplyWithContext(r -> {
                for (int i = filters.size() - 1; i >= 0; i--) {
                    Filter filter = filters.get(i);
                    // 如果该过滤器是ListenableFilter类型的
                    if (filter instanceof ListenableFilter) {
                        // 强制类型转化
                        Filter.Listener listener = ((ListenableFilter) filter).listener();
                        if (listener != null) {
                            // 如果内部类listener不为空，则调用回调方法onResponse
                            listener.onResponse(r, filterInvoker, invocation);
                        }
                    } else {
                        // 否则，直接调用filter的onResponse，做兼容。
                        filter.onResponse(r, filterInvoker, invocation);
                    }
                }
                return r;
            });
            // 返回异步结果
            return asyncResult;
        }

        @Override
        public Class<T> getInterface() {
            return filterInvoker.getInterface();
        }

        @Override
        public URL getUrl() {
            return filterInvoker.getUrl();
        }

        @Override
        public boolean isAvailable() {
            return filterInvoker.isAvailable();
        }

        @Override
        public void destroy() {
            filterInvoker.destroy();
        }
    }
}
