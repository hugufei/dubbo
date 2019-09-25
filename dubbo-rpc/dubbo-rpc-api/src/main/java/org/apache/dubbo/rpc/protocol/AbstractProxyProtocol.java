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
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ProxyFactory;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;

/**
 * AbstractProxyProtocol
 *
 * 利用了代理工厂对AbstractProtocol中的两个集合进行了填充，并且对异常做了处理。
 */
public abstract class AbstractProxyProtocol extends AbstractProtocol {

    // rpc的异常类集合
    private final List<Class<?>> rpcExceptions = new CopyOnWriteArrayList<Class<?>>();

    // 代理工厂
    private ProxyFactory proxyFactory;

    public AbstractProxyProtocol() {
    }

    public AbstractProxyProtocol(Class<?>... exceptions) {
        for (Class<?> exception : exceptions) {
            addRpcException(exception);
        }
    }

    public void addRpcException(Class<?> exception) {
        this.rpcExceptions.add(exception);
    }

    public ProxyFactory getProxyFactory() {
        return proxyFactory;
    }

    public void setProxyFactory(ProxyFactory proxyFactory) {
        this.proxyFactory = proxyFactory;
    }

    // 服务暴露
    @Override
    @SuppressWarnings("unchecked")
    public <T> Exporter<T> export(final Invoker<T> invoker) throws RpcException {
        // org.apache.dubbo.demo.DemoService:8080
        // 获得uri
        final String uri = serviceKey(invoker.getUrl());
        // 获得服务暴露者
        Exporter<T> exporter = (Exporter<T>) exporterMap.get(uri);
        if (exporter != null) {
            // When modifying the configuration through override, you need to re-expose the newly modified service.
            if (Objects.equals(exporter.getInvoker().getUrl(), invoker.getUrl())) {
                return exporter;
            }
        }

        final Runnable runnable = doExport(proxyFactory.getProxy(invoker, true), invoker.getInterface(), invoker.getUrl());
        exporter = new AbstractExporter<T>(invoker) {
            // 对unexport方法进行了重写
            @Override
            public void unexport() {
                //首先取消暴露
                super.unexport();
                // 移除uri的暴露
                exporterMap.remove(uri);
                // 执行上面定义的doExport方法
                if (runnable != null) {
                    try {
                        runnable.run();
                    } catch (Throwable t) {
                        logger.warn(t.getMessage(), t);
                    }
                }
            }
        };

        // org.apache.dubbo.demo.DemoService:8080 --> CallbackRegistrationInvoker
        exporterMap.put(uri, exporter);
        return exporter;
    }

    @Override
    protected <T> Invoker<T> protocolBindingRefer(final Class<T> type, final URL url) throws RpcException {
        // 1、先从代理工厂中获得Invoker对象target
        final Invoker<T> target = proxyFactory.getInvoker(doRefer(type, url), type, url);
        // 2、创建了真实的invoker，在重写方法中调用代理的方法
        Invoker<T> invoker = new AbstractInvoker<T>(type, url) {
            @Override
            protected Result doInvoke(Invocation invocation) throws Throwable {
                try {
                    // 获得调用结果
                    Result result = target.invoke(invocation);
                    // FIXME result is an AsyncRpcResult instance.
                    Throwable e = result.getException();
                    // 如果有异常，则抛出相应异常
                    if (e != null) {
                        for (Class<?> rpcException : rpcExceptions) {
                            if (rpcException.isAssignableFrom(e.getClass())) {
                                throw getRpcException(type, url, invocation, e);
                            }
                        }
                    }
                    return result;
                } catch (RpcException e) {
                    // 抛出未知异常
                    if (e.getCode() == RpcException.UNKNOWN_EXCEPTION) {
                        e.setCode(getErrorCode(e.getCause()));
                    }
                    throw e;
                } catch (Throwable e) {
                    throw getRpcException(type, url, invocation, e);
                }
            }
        };
        // 3、加入集合
        invokers.add(invoker);
        return invoker;
    }

    protected RpcException getRpcException(Class<?> type, URL url, Invocation invocation, Throwable e) {
        RpcException re = new RpcException("Failed to invoke remote service: " + type + ", method: "
                + invocation.getMethodName() + ", cause: " + e.getMessage(), e);
        re.setCode(getErrorCode(e));
        return re;
    }

    protected String getAddr(URL url) {
        String bindIp = url.getParameter(Constants.BIND_IP_KEY, url.getHost());
        if (url.getParameter(ANYHOST_KEY, false)) {
            bindIp = ANYHOST_VALUE;
        }
        return NetUtils.getIpByHost(bindIp) + ":" + url.getParameter(Constants.BIND_PORT_KEY, url.getPort());
    }

    protected int getErrorCode(Throwable e) {
        return RpcException.UNKNOWN_EXCEPTION;
    }

    // 抽象了服务暴露的方法，让各类协议各自实现。
    protected abstract <T> Runnable doExport(T impl, Class<T> type, URL url) throws RpcException;

    // 抽象了服务引用的方法，让各类协议各自实现。
    protected abstract <T> T doRefer(Class<T> type, URL url) throws RpcException;

}
