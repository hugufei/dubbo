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
package org.apache.dubbo.remoting.transport;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.store.DataStore;
import org.apache.dubbo.common.utils.ExecutorUtil;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.remoting.Channel;
import org.apache.dubbo.remoting.ChannelHandler;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.RemotingException;
import org.apache.dubbo.remoting.Server;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.ANYHOST_VALUE;
import static org.apache.dubbo.common.constants.CommonConstants.THREADS_KEY;
import static org.apache.dubbo.remoting.Constants.IDLE_TIMEOUT_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_IDLE_TIMEOUT;
import static org.apache.dubbo.remoting.Constants.ACCEPTS_KEY;
import static org.apache.dubbo.remoting.Constants.DEFAULT_ACCEPTS;

/**
 * AbstractServer
 *
 * 服务器抽象类：
 *
 * 1）重点实现了服务器的公共逻辑，比如发送消息，关闭通道，连接通道，断开连接等。
 * 2）抽象了打开和关闭服务器两个方法。
 *
 */
public abstract class AbstractServer extends AbstractEndpoint implements Server {

    // 服务器线程名称
    protected static final String SERVER_THREAD_POOL_NAME = "DubboServerHandler";
    private static final Logger logger = LoggerFactory.getLogger(AbstractServer.class);

    // 线程池
    ExecutorService executor;

    // 服务地址，也就是本地地址
    private InetSocketAddress localAddress;

    // 绑定地址
    private InetSocketAddress bindAddress;

    // 最大可接受的连接数
    private int accepts;

    // 空闲超时时间，单位是s
    private int idleTimeout;

    // 大部分逻辑就是从url中取配置，存到缓存中，并且做了开启服务器的操作。
    public AbstractServer(URL url, ChannelHandler handler) throws RemotingException {
        super(url, handler);

        // 从url中获得本地地址
        localAddress = getUrl().toInetSocketAddress();

        // 从url配置中获得绑定的ip
        String bindIp = getUrl().getParameter(Constants.BIND_IP_KEY, getUrl().getHost());
        // 从url配置中获得绑定的端口号
        int bindPort = getUrl().getParameter(Constants.BIND_PORT_KEY, getUrl().getPort());
        //  判断url中配置anyhost是否为true或者判断host是否为不可用的本地Host
        if (url.getParameter(ANYHOST_KEY, false) || NetUtils.isInvalidLocalHost(bindIp)) {
            bindIp = ANYHOST_VALUE;
        }

        bindAddress = new InetSocketAddress(bindIp, bindPort);
        // 从url中获取accepts配置，默认值为0
        this.accepts = url.getParameter(ACCEPTS_KEY, DEFAULT_ACCEPTS);
        //  从url中获取idle.timeout配置，默认600s
        this.idleTimeout = url.getParameter(IDLE_TIMEOUT_KEY, DEFAULT_IDLE_TIMEOUT);
        try {
            // 开启服务器
            doOpen();
            if (logger.isInfoEnabled()) {
                logger.info("Start " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
            }
        } catch (Throwable t) {
            throw new RemotingException(url.toInetSocketAddress(), null, "Failed to bind " + getClass().getSimpleName()
                    + " on " + getLocalAddress() + ", cause: " + t.getMessage(), t);
        }
        // 获得线程池
        //fixme replace this with better method
        DataStore dataStore = ExtensionLoader.getExtensionLoader(DataStore.class).getDefaultExtension();
        executor = (ExecutorService) dataStore.get(Constants.EXECUTOR_SERVICE_COMPONENT_KEY, Integer.toString(url.getPort()));
    }

    protected abstract void doOpen() throws Throwable;

    protected abstract void doClose() throws Throwable;

    @Override
    public void reset(URL url) {
        if (url == null) {
            return;
        }
        try {
            // 重置accepts的值
            if (url.hasParameter(ACCEPTS_KEY)) {
                int a = url.getParameter(ACCEPTS_KEY, 0);
                if (a > 0) {
                    this.accepts = a;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            // 重置idle.timeout的值
            if (url.hasParameter(IDLE_TIMEOUT_KEY)) {
                int t = url.getParameter(IDLE_TIMEOUT_KEY, 0);
                if (t > 0) {
                    this.idleTimeout = t;
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        try {
            // 重置线程数配置
            if (url.hasParameter(THREADS_KEY)
                    && executor instanceof ThreadPoolExecutor && !executor.isShutdown()) {
                ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executor;
                // 获得url配置中的线程数
                int threads = url.getParameter(THREADS_KEY, 0);
                // 获得线程池允许的最大线程数
                int max = threadPoolExecutor.getMaximumPoolSize();
                // 返回核心线程数
                int core = threadPoolExecutor.getCorePoolSize();
                // 设置最大线程数和核心线程数
                if (threads > 0 && (threads != max || threads != core)) {
                    if (threads < core) {
                        // 如果设置的线程数比核心线程数少，则直接设置核心线程数
                        threadPoolExecutor.setCorePoolSize(threads);
                        if (core == max) {
                            // 当核心线程数和最大线程数相等的时候，把最大线程数也重置
                            threadPoolExecutor.setMaximumPoolSize(threads);
                        }
                    } else {
                        // 当大于核心线程数时，直接设置最大线程数
                        threadPoolExecutor.setMaximumPoolSize(threads);
                        // 只有当核心线程数和最大线程数相等的时候才设置核心线程数
                        if (core == max) {
                            threadPoolExecutor.setCorePoolSize(threads);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            logger.error(t.getMessage(), t);
        }
        // 重置url
        super.setUrl(getUrl().addParameters(url.getParameters()));
    }

    @Override
    public void send(Object message, boolean sent) throws RemotingException {
        Collection<Channel> channels = getChannels();
        for (Channel channel : channels) {
            if (channel.isConnected()) {
                channel.send(message, sent);
            }
        }
    }

    @Override
    public void close() {
        if (logger.isInfoEnabled()) {
            logger.info("Close " + getClass().getSimpleName() + " bind " + getBindAddress() + ", export " + getLocalAddress());
        }
        ExecutorUtil.shutdownNow(executor, 100);
        try {
            super.close();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
        try {
            doClose();
        } catch (Throwable e) {
            logger.warn(e.getMessage(), e);
        }
    }

    @Override
    public void close(int timeout) {
        ExecutorUtil.gracefulShutdown(executor, timeout);
        close();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return localAddress;
    }

    public InetSocketAddress getBindAddress() {
        return bindAddress;
    }

    public int getAccepts() {
        return accepts;
    }

    public int getIdleTimeout() {
        return idleTimeout;
    }

    @Override
    public void connected(Channel ch) throws RemotingException {
        // If the server has entered the shutdown process, reject any new connection
        if (this.isClosing() || this.isClosed()) {
            logger.warn("Close new channel " + ch + ", cause: server is closing or has been closed. For example, receive a new connect request while in shutdown process.");
            ch.close();
            return;
        }

        Collection<Channel> channels = getChannels();
        if (accepts > 0 && channels.size() > accepts) {
            logger.error("Close channel " + ch + ", cause: The server " + ch.getLocalAddress() + " connections greater than max config " + accepts);
            ch.close();
            return;
        }
        super.connected(ch);
    }

    @Override
    public void disconnected(Channel ch) throws RemotingException {
        Collection<Channel> channels = getChannels();
        if (channels.isEmpty()) {
            logger.warn("All clients has disconnected from " + ch.getLocalAddress() + ". You can graceful shutdown now.");
        }
        super.disconnected(ch);
    }

}
