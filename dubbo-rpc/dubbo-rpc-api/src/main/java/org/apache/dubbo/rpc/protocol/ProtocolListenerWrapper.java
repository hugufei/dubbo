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
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.rpc.Exporter;
import org.apache.dubbo.rpc.ExporterListener;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.InvokerListener;
import org.apache.dubbo.rpc.Protocol;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.listener.ListenerExporterWrapper;
import org.apache.dubbo.rpc.listener.ListenerInvokerWrapper;

import java.util.Collections;

import static org.apache.dubbo.common.constants.RegistryConstants.REGISTRY_PROTOCOL;

import static org.apache.dubbo.rpc.Constants.INVOKER_LISTENER_KEY;
import static org.apache.dubbo.rpc.Constants.EXPORTER_LISTENER_KEY;

/**
 * ListenerProtocol
 *
 * 该类也实现了Protocol，也是装饰了Protocol接口，但是它是在服务引用和暴露过程中加上了监听器的功能。
 */
public class ProtocolListenerWrapper implements Protocol {

    private final Protocol protocol;

    // Protocol 的监听器功能的 AOP
    public ProtocolListenerWrapper(Protocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol == null");
        }
        this.protocol = protocol;
    }

    @Override
    public int getDefaultPort() {
        return protocol.getDefaultPort();
    }

    // 在服务暴露上做了监听器功能的增强，也就是加上了监听器。
    @Override
    public <T> Exporter<T> export(Invoker<T> invoker) throws RpcException {
        // 如果是注册中心，则暴露该invoker
        if (REGISTRY_PROTOCOL.equals(invoker.getUrl().getProtocol())) {
            return protocol.export(invoker);
        }
        // 创建一个暴露者监听器包装类对象
        return new ListenerExporterWrapper<T>(protocol.export(invoker),
                Collections.unmodifiableList(ExtensionLoader.getExtensionLoader(ExporterListener.class)
                        .getActivateExtension(invoker.getUrl(), EXPORTER_LISTENER_KEY)));
    }

    // 在服务引用上做了监听器功能的增强，也就是加上了监听器。
    @Override
    public <T> Invoker<T> refer(Class<T> type, URL url) throws RpcException {
        // 如果是注册中心。则直接引用服务
        if (REGISTRY_PROTOCOL.equals(url.getProtocol())) {
            return protocol.refer(type, url);
        }
        // 创建引用服务监听器包装类对象
        return new ListenerInvokerWrapper<T>(protocol.refer(type, url),
                Collections.unmodifiableList(
                        ExtensionLoader.getExtensionLoader(InvokerListener.class)
                                .getActivateExtension(url, INVOKER_LISTENER_KEY)));
    }

    @Override
    public void destroy() {
        protocol.destroy();
    }

}
