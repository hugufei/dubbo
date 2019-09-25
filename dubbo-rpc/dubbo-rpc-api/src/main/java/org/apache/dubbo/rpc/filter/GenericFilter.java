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
package org.apache.dubbo.rpc.filter;

import org.apache.dubbo.common.beanutil.JavaBeanAccessor;
import org.apache.dubbo.common.beanutil.JavaBeanDescriptor;
import org.apache.dubbo.common.beanutil.JavaBeanSerializeUtil;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.common.io.UnsafeByteArrayInputStream;
import org.apache.dubbo.common.io.UnsafeByteArrayOutputStream;
import org.apache.dubbo.common.serialize.Serialization;
import org.apache.dubbo.common.utils.PojoUtils;
import org.apache.dubbo.common.utils.ReflectUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ListenableFilter;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.RpcInvocation;
import org.apache.dubbo.rpc.service.GenericException;
import org.apache.dubbo.rpc.service.GenericService;
import org.apache.dubbo.rpc.support.ProtocolUtils;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.apache.dubbo.rpc.Constants.$INVOKE;
import static org.apache.dubbo.rpc.Constants.$INVOKE_ASYNC;
import static org.apache.dubbo.rpc.Constants.GENERIC_KEY;
import static org.apache.dubbo.rpc.Constants.GENERIC_SERIALIZATION_BEAN;
import static org.apache.dubbo.rpc.Constants.GENERIC_SERIALIZATION_NATIVE_JAVA;
import static org.apache.dubbo.rpc.Constants.GENERIC_SERIALIZATION_PROTOBUF;

/**
 * GenericInvokerFilter.
 *
 * 对于泛化调用的请求和结果进行反序列化和序列化的操作，它是服务提供者侧的。
 *
 * so， 什么是泛化调用？？
 *
 */
@Activate(group = CommonConstants.PROVIDER, order = -20000)
public class GenericFilter extends ListenableFilter {

    public GenericFilter() {
        super.listener = new GenericListener();
    }

    @Override
    public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
        // 如果是泛化调用
        if ((inv.getMethodName().equals($INVOKE) || inv.getMethodName().equals($INVOKE_ASYNC))
                && inv.getArguments() != null
                && inv.getArguments().length == 3
                && !GenericService.class.isAssignableFrom(invoker.getInterface())) {
            // 获得请求名字
            String name = ((String) inv.getArguments()[0]).trim();
            // 获得请求参数类型
            String[] types = (String[]) inv.getArguments()[1];
            // 获得请求参数
            Object[] args = (Object[]) inv.getArguments()[2];
            try {
                // 获得方法
                Method method = ReflectUtils.findMethodByMethodSignature(invoker.getInterface(), name, types);
                // 获得该方法的参数类型
                Class<?>[] params = method.getParameterTypes();
                if (args == null) {
                    args = new Object[params.length];
                }
                // 获得generic的附加值
                String generic = inv.getAttachment(GENERIC_KEY);

                // 如果generic的附加值为空，在用上下文携带的附加值
                if (StringUtils.isBlank(generic)) {
                    generic = RpcContext.getContext().getAttachment(GENERIC_KEY);
                }

                // 如果附加值还是为空或者是默认的泛化序列化类型
                if (StringUtils.isEmpty(generic)
                        || ProtocolUtils.isDefaultGenericSerialization(generic)) {
                    // 直接进行类型转化
                    args = PojoUtils.realize(args, params, method.getGenericParameterTypes());
                } else if (ProtocolUtils.isJavaGenericSerialization(generic)) {
                    for (int i = 0; i < args.length; i++) {
                        if (byte[].class == args[i].getClass()) {
                            try (UnsafeByteArrayInputStream is = new UnsafeByteArrayInputStream((byte[]) args[i])) {
                                // 使用nativejava方式反序列化
                                args[i] = ExtensionLoader.getExtensionLoader(Serialization.class)
                                        .getExtension(GENERIC_SERIALIZATION_NATIVE_JAVA)
                                        .deserialize(null, is).readObject();
                            } catch (Exception e) {
                                throw new RpcException("Deserialize argument [" + (i + 1) + "] failed.", e);
                            }
                        } else {
                            throw new RpcException(
                                    "Generic serialization [" +
                                            GENERIC_SERIALIZATION_NATIVE_JAVA +
                                            "] only support message type " +
                                            byte[].class +
                                            " and your message type is " +
                                            args[i].getClass());
                        }
                    }
                } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] instanceof JavaBeanDescriptor) {
                            // 用JavaBean方式反序列化
                            args[i] = JavaBeanSerializeUtil.deserialize((JavaBeanDescriptor) args[i]);
                        } else {
                            throw new RpcException(
                                    "Generic serialization [" +
                                            GENERIC_SERIALIZATION_BEAN +
                                            "] only support message type " +
                                            JavaBeanDescriptor.class.getName() +
                                            " and your message type is " +
                                            args[i].getClass().getName());
                        }
                    }
                } else if (ProtocolUtils.isProtobufGenericSerialization(generic)) {
                    // as proto3 only accept one protobuf parameter
                    if (args.length == 1 && args[0] instanceof String) {
                        try (UnsafeByteArrayInputStream is =
                                     new UnsafeByteArrayInputStream(((String) args[0]).getBytes())) {
                            args[0] = ExtensionLoader.getExtensionLoader(Serialization.class)
                                    .getExtension("" + GENERIC_SERIALIZATION_PROTOBUF)
                                    .deserialize(null, is).readObject(method.getParameterTypes()[0]);
                        } catch (Exception e) {
                            throw new RpcException("Deserialize argument failed.", e);
                        }
                    } else {
                        throw new RpcException(
                                "Generic serialization [" +
                                        GENERIC_SERIALIZATION_PROTOBUF +
                                        "] only support one" + String.class.getName() +
                                        " argument and your message size is " +
                                        args.length + " and type is" +
                                        args[0].getClass().getName());
                    }
                }
                return invoker.invoke(new RpcInvocation(method, args, inv.getAttachments()));
            } catch (NoSuchMethodException e) {
                throw new RpcException(e.getMessage(), e);
            } catch (ClassNotFoundException e) {
                throw new RpcException(e.getMessage(), e);
            }
        }
        // 调用下一个调用链
        return invoker.invoke(inv);
    }

    static class GenericListener implements Listener {

        @Override
        public void onResponse(Result appResponse, Invoker<?> invoker, Invocation inv) {
            // 如果是泛化调用
            if ((inv.getMethodName().equals($INVOKE) || inv.getMethodName().equals($INVOKE_ASYNC))
                    && inv.getArguments() != null
                    && inv.getArguments().length == 3
                    && !GenericService.class.isAssignableFrom(invoker.getInterface())) {

                String generic = inv.getAttachment(GENERIC_KEY);
                if (StringUtils.isBlank(generic)) {
                    generic = RpcContext.getContext().getAttachment(GENERIC_KEY);
                }

                if (appResponse.hasException() && !(appResponse.getException() instanceof GenericException)) {
                    appResponse.setException(new GenericException(appResponse.getException()));
                }
                if (ProtocolUtils.isJavaGenericSerialization(generic)) {
                    try {
                        // 用nativejava方式序列化
                        UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(512);
                        ExtensionLoader.getExtensionLoader(Serialization.class).getExtension(GENERIC_SERIALIZATION_NATIVE_JAVA).serialize(null, os).writeObject(appResponse.getValue());
                        appResponse.setValue(os.toByteArray());
                    } catch (IOException e) {
                        throw new RpcException(
                                "Generic serialization [" +
                                        GENERIC_SERIALIZATION_NATIVE_JAVA +
                                        "] serialize result failed.", e);
                    }
                } else if (ProtocolUtils.isBeanGenericSerialization(generic)) {
                    // 使用JavaBean方式序列化返回结果
                    appResponse.setValue(JavaBeanSerializeUtil.serialize(appResponse.getValue(), JavaBeanAccessor.METHOD));
                } else if (ProtocolUtils.isProtobufGenericSerialization(generic)) {
                    try {
                        // 使用protobuf-json方式序列化返回结果
                        UnsafeByteArrayOutputStream os = new UnsafeByteArrayOutputStream(512);
                        ExtensionLoader.getExtensionLoader(Serialization.class)
                                .getExtension(GENERIC_SERIALIZATION_PROTOBUF)
                                .serialize(null, os).writeObject(appResponse.getValue());
                        appResponse.setValue(os.toString());
                    } catch (IOException e) {
                        throw new RpcException("Generic serialization [" +
                                GENERIC_SERIALIZATION_PROTOBUF +
                                "] serialize result failed.", e);
                    }
                } else {
                    // 直接转化为pojo类型然后返回
                    appResponse.setValue(PojoUtils.generalize(appResponse.getValue()));
                }
            }
        }

        @Override
        public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {

        }
    }
}
