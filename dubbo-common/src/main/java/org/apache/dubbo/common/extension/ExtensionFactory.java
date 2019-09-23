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
package org.apache.dubbo.common.extension;

/**
 * ExtensionFactory类
 *
 * 表示扩展机制的工厂，在Dubbo里面有SPI扩展机制，也有Spring扩展机制
 * 对于一个接口，比如Car接口，有两种实现类：
 * 1）一种就是我们自定义的实现类，比如RedCar
 * 2）还有一种就是代理类，对于代理类，可以由我们自己实现，也可以让Dubbo帮我们实现，而代理类主要就是依赖注入时使用
 */
@SPI
public interface ExtensionFactory {

    /**
     * Get extension.
     *
     * @param type object type. objectFactory
     * @param name object name.
     * @return object instance.
     */
    // 这个接口的唯一代理类为 AdaptiveExtensionFactory
    //AdaptiveExtensionFactory里会调用SpiExtensionFactory或SpringExtensionFactory的getExtension方法。
    // getExtension方法实际上就是获取某一个接口的实现类实例（代理类也是接口的实例）
    // 1）SpiExtensionFactory会调用getAdaptiveExtension()返回代理对象
    // 2）SpringExtensionFactory会给我们返回spring容器中已经存在的名字为property的bean。如果按名称找不到，则会按类型查找
    // 3) 如果spring中不存在，则继续会从SpiExtensionFactory中获取代理对象
    <T> T getExtension(Class<T> type, String name);

}
