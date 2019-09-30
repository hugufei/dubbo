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
package org.apache.dubbo.rpc.protocol.dubbo.filter;

import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.ListenableFilter;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ConsumerMethodModel;
import org.apache.dubbo.rpc.model.ConsumerModel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.apache.dubbo.rpc.Constants.$INVOKE;

/**
 * EventFilter
 *
 * 消费端处理：处理异步和同步调用结果的过滤器。
 */
@Activate(group = CommonConstants.CONSUMER)
public class FutureFilter extends ListenableFilter {

    protected static final Logger logger = LoggerFactory.getLogger(FutureFilter.class);

    public FutureFilter() {
        super.listener = new FutureListener();
    }

    @Override
    public Result invoke(final Invoker<?> invoker, final Invocation invocation) throws RpcException {
        // 该方法是真正的调用方法的执行
        fireInvokeCallback(invoker, invocation);
        // need to configure if there's return value before the invocation in order to help invoker to judge if it's
        // necessary to return future.
        // 需要在调用之前配置是否有返回值，以帮助调用者判断是否有必要返回future对象。
        return invoker.invoke(invocation);
    }

    // 调用方法的执行
    private void fireInvokeCallback(final Invoker<?> invoker, final Invocation invocation) {
        final ConsumerMethodModel.AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        if (asyncMethodInfo == null) {
            return;
        }
        // 获得调用的方法
        final Method onInvokeMethod = asyncMethodInfo.getOninvokeMethod();
        // 获得调用的服务
        final Object onInvokeInst = asyncMethodInfo.getOninvokeInstance();

        if (onInvokeMethod == null && onInvokeInst == null) {
            return;
        }
        if (onInvokeMethod == null || onInvokeInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a oninvoke callback config , but no such " + (onInvokeMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        // 如果不可以访问，则设置为可访问
        if (!onInvokeMethod.isAccessible()) {
            onInvokeMethod.setAccessible(true);
        }

        // 获得参数数组
        Object[] params = invocation.getArguments();
        try {
            // 调用方法
            onInvokeMethod.invoke(onInvokeInst, params);
        } catch (InvocationTargetException e) {
            fireThrowCallback(invoker, invocation, e.getTargetException());
        } catch (Throwable e) {
            fireThrowCallback(invoker, invocation, e);
        }
    }

    // 监听器使用 - 正常的返回结果的处理
    private void fireReturnCallback(final Invoker<?> invoker, final Invocation invocation, final Object result) {
        final ConsumerMethodModel.AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        if (asyncMethodInfo == null) {
            return;
        }

        final Method onReturnMethod = asyncMethodInfo.getOnreturnMethod();
        final Object onReturnInst = asyncMethodInfo.getOnreturnInstance();

        //not set onreturn callback
        if (onReturnMethod == null && onReturnInst == null) {
            return;
        }

        if (onReturnMethod == null || onReturnInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a onreturn callback config , but no such " + (onReturnMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        if (!onReturnMethod.isAccessible()) {
            onReturnMethod.setAccessible(true);
        }

        Object[] args = invocation.getArguments();
        Object[] params;
        // 获得返回结果类型
        Class<?>[] rParaTypes = onReturnMethod.getParameterTypes();
        // 设置参数和返回结果
        if (rParaTypes.length > 1) {
            if (rParaTypes.length == 2 && rParaTypes[1].isAssignableFrom(Object[].class)) {
                params = new Object[2];
                params[0] = result;
                params[1] = args;
            } else {
                params = new Object[args.length + 1];
                params[0] = result;
                System.arraycopy(args, 0, params, 1, args.length);
            }
        } else {
            params = new Object[]{result};
        }
        try {
            // 调用方法
            onReturnMethod.invoke(onReturnInst, params);
        } catch (InvocationTargetException e) {
            fireThrowCallback(invoker, invocation, e.getTargetException());
        } catch (Throwable e) {
            fireThrowCallback(invoker, invocation, e);
        }
    }

    // 监听器使用 - 异常抛出时的结果处理。
    private void fireThrowCallback(final Invoker<?> invoker, final Invocation invocation, final Throwable exception) {
        final ConsumerMethodModel.AsyncMethodInfo asyncMethodInfo = getAsyncMethodInfo(invoker, invocation);
        if (asyncMethodInfo == null) {
            return;
        }

        final Method onthrowMethod = asyncMethodInfo.getOnthrowMethod();
        final Object onthrowInst = asyncMethodInfo.getOnthrowInstance();

        //onthrow callback not configured
        if (onthrowMethod == null && onthrowInst == null) {
            return;
        }
        if (onthrowMethod == null || onthrowInst == null) {
            throw new IllegalStateException("service:" + invoker.getUrl().getServiceKey() + " has a onthrow callback config , but no such " + (onthrowMethod == null ? "method" : "instance") + " found. url:" + invoker.getUrl());
        }
        if (!onthrowMethod.isAccessible()) {
            onthrowMethod.setAccessible(true);
        }
        Class<?>[] rParaTypes = onthrowMethod.getParameterTypes();
        if (rParaTypes[0].isAssignableFrom(exception.getClass())) {
            try {
                Object[] args = invocation.getArguments();
                Object[] params;

                if (rParaTypes.length > 1) {
                    if (rParaTypes.length == 2 && rParaTypes[1].isAssignableFrom(Object[].class)) {
                        params = new Object[2];
                        params[0] = exception;
                        params[1] = args;
                    } else {
                        params = new Object[args.length + 1];
                        params[0] = exception;
                        System.arraycopy(args, 0, params, 1, args.length);
                    }
                } else {
                    params = new Object[]{exception};
                }
                // 调用下一个调用连
                onthrowMethod.invoke(onthrowInst, params);
            } catch (Throwable e) {
                logger.error(invocation.getMethodName() + ".call back method invoke error . callback method :" + onthrowMethod + ", url:" + invoker.getUrl(), e);
            }
        } else {
            logger.error(invocation.getMethodName() + ".call back method invoke error . callback method :" + onthrowMethod + ", url:" + invoker.getUrl(), exception);
        }
    }

    //构造AsyncMethodInfo信息
    private ConsumerMethodModel.AsyncMethodInfo getAsyncMethodInfo(Invoker<?> invoker, Invocation invocation) {
        final ConsumerModel consumerModel = ApplicationModel.getConsumerModel(invoker.getUrl().getServiceKey());
        if (consumerModel == null) {
            return null;
        }

        String methodName = invocation.getMethodName();
        if (methodName.equals($INVOKE)) {
            methodName = (String) invocation.getArguments()[0];
        }

        ConsumerMethodModel methodModel = consumerModel.getMethodModel(methodName);
        if (methodModel == null) {
            return null;
        }

        final ConsumerMethodModel.AsyncMethodInfo asyncMethodInfo = methodModel.getAsyncInfo();
        if (asyncMethodInfo == null) {
            return null;
        }

        return asyncMethodInfo;
    }

    // 监听器
    class FutureListener implements Listener {

        // 处理正常结果
        @Override
        public void onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
            if (result.hasException()) {
                //触发异常
                fireThrowCallback(invoker, invocation, result.getException());
            } else {
                //触发return
                fireReturnCallback(invoker, invocation, result.getValue());
            }
        }

        // 处理异常结果
        @Override
        public void onError(Throwable t, Invoker<?> invoker, Invocation invocation) {

        }
    }
}
