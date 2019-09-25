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
package org.apache.dubbo.rpc;

import java.util.Map;

/**
 * Invocation. (API, Prototype, NonThreadSafe)
 *
 * @serial Don't change the class name and package name.
 * @see org.apache.dubbo.rpc.Invoker#invoke(Invocation)
 * @see org.apache.dubbo.rpc.RpcInvocation
 */
//调用对象，它持有调用过程中的变量，比如方法名，参数等。
public interface Invocation {

    //获得方法名称
    String getMethodName();

    //获得参数类型
    Class<?>[] getParameterTypes();

    //获得参数
    Object[] getArguments();

    //获得附加值集合
    Map<String, String> getAttachments();

    // 设置附加值
    void setAttachment(String key, String value);

    // 设置附加值
    void setAttachmentIfAbsent(String key, String value);

    // 获得附加值
    String getAttachment(String key);

    // 获得附加值
    String getAttachment(String key, String defaultValue);

    // 获得当前上下文的invoker
    Invoker<?> getInvoker();

}