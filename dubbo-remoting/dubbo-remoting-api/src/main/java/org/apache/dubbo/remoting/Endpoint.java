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
package org.apache.dubbo.remoting;

import org.apache.dubbo.common.URL;

import java.net.InetSocketAddress;

/**
 * Endpoint. (API/SPI, Prototype, ThreadSafe)
 *
 *
 * @see org.apache.dubbo.remoting.Channel
 * @see org.apache.dubbo.remoting.Client
 * @see org.apache.dubbo.remoting.Server
 */

// 端的抽象概念
// 可以理解为客户端和服务端共同拥有的方法
public interface Endpoint {

    /**
     * get url.
     *
     * @return url
     */
    URL getUrl();

    /**
     * get channel handler.
     *
     * @return channel handler
     */
    // 获得该端的通道处理器
    ChannelHandler getChannelHandler();

    /**
     * get local address.
     *
     * @return local address.
     */
    // 获得该端的本地地址
    InetSocketAddress getLocalAddress();

    /**
     * send message.
     *
     * @param message
     * @throws RemotingException
     */
    // 发送消息
    void send(Object message) throws RemotingException;

    /**
     * send message.
     *
     * @param message
     * @param sent    already sent to socket?
     */
    // 发送消息，sent是是否已经发送的标记
    // 1) sent值为true，等待消息发出，消息发送失败将抛出异常。
    // 2) sent值为false，不等待消息发出，将消息放入 IO 队列，即刻返回。
    void send(Object message, boolean sent) throws RemotingException;

    /**
     * close the channel.
     */
    // 关闭
    void close();

    /**
     * Graceful close the channel.
     */
    // 优雅的关闭，也就是加入了等待时间
    void close(int timeout);

    // 开始关闭
    void startClose();

    /**
     * is closed.
     *
     * @return closed
     */
    // 判断是否已经关闭
    boolean isClosed();

}