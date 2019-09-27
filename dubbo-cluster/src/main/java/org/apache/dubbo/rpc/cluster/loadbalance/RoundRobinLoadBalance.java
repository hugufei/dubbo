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
package org.apache.dubbo.rpc.cluster.loadbalance;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 *
 * 加权轮询算法的实现。
 *
 * 轮询：
 * 第一个请求分配给A服务器，第二个请求分配给B服务器，第三个请求分配给C服务器，第四个请求又分配给A服务器，这就是轮询。
 * 但是这只适合每台服务器性能相近的情况，这种是一种非常理想的情况。
 * 那更多的是每台服务器的性能都会有所差异，这个时候性能差的服务器被分到等额的请求就会需要承受压力大宕机的情况。
 *
 * 加权轮询：
 * 服务器 A、B、C 权重比为 6:3:1，那么在10次请求中，服务器 A 将收到其中的6次请求，服务器 B 会收到其中的3次请求，服务器 C 则收到其中的1次请求
 * 也就是说每台服务器能够收到的请求归结于它的权重。
 *
 *
 * 核心流程：
 * 初始值： current =0; total=sum(weight);
 *
 * 1） current = current + weight
 * 2） 选择current最大的那个
 * 3） 最大的那个 current = current - total
 *
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "roundrobin";

    // 回收间隔
    private static final int RECYCLE_PERIOD = 60000;

    // 加权轮询器
    // 记录了某一个服务提供者的一些数据，比如权重、比如当前已经有多少请求落在该服务提供者上等。
    protected static class WeightedRoundRobin {

        // 权重
        private int weight;

        // 当前已经有多少请求落在该服务提供者身上，也可以看成是一个动态的权重
        private AtomicLong current = new AtomicLong(0);

        // 最后一次更新时间
        private long lastUpdate;

        public int getWeight() {
            return weight;
        }
        public void setWeight(int weight) {
            this.weight = weight;
            current.set(0);
        }
        public long increaseCurrent() {
            return current.addAndGet(weight);
        }
        public void sel(int total) {
            current.addAndGet(-1 * total);
        }
        public long getLastUpdate() {
            return lastUpdate;
        }
        public void setLastUpdate(long lastUpdate) {
            this.lastUpdate = lastUpdate;
        }
    }

    private ConcurrentMap<String, ConcurrentMap<String, WeightedRoundRobin>> methodWeightMap = new ConcurrentHashMap<String, ConcurrentMap<String, WeightedRoundRobin>>();
    private AtomicBoolean updateLock = new AtomicBoolean();
    
    /**
     * get invoker addr list cached for specified invocation
     * <p>
     * <b>for unit test only</b>
     * 
     * @param invokers
     * @param invocation
     * @return
     */
    protected <T> Collection<String> getInvokerAddrList(List<Invoker<T>> invokers, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        Map<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map != null) {
            return map.keySet();
        }
        return null;
    }

    // 核心流程：
    // 初始值： current =0; total=sum(weight);
    // 1）current = current + weight
    // 2) 选择current最大的那个
    // 3) 最大的那个 current = current - total
    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // key = 全限定类名 + "." + 方法名，比如 com.xxx.DemoService.sayHello
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();

        ConcurrentMap<String, WeightedRoundRobin> map = methodWeightMap.get(key);
        if (map == null) {
            methodWeightMap.putIfAbsent(key, new ConcurrentHashMap<String, WeightedRoundRobin>());
            map = methodWeightMap.get(key);
        }
        // 权重总和
        int totalWeight = 0;
        // 获得现在的时间戳
        long now = System.currentTimeMillis();

        // 最大权重临时值，下面的循环选择时用的
        long maxCurrent = Long.MIN_VALUE;
        // 最大current的Invoker
        Invoker<T> selectedInvoker = null;
        // 最大current的Invoker对应的WeightedRoundRobin
        WeightedRoundRobin selectedWRR = null;

        // 下面这个循环主要做了这样几件事情：
        //   1. 遍历 Invoker 列表，检测当前 Invoker 是否有相应的 WeightedRoundRobin，没有则创建
        //   2. 检测 Invoker 权重是否发生了变化，若变化了，则更新 WeightedRoundRobin 的 weight 字段
        //   3. 让 current 字段加上自身权重，等价于 current += weight
        //   4. 设置 lastUpdate 字段，即 lastUpdate = now
        //   5. 寻找具有最大 current 的 Invoker，以及 Invoker 对应的 WeightedRoundRobin，暂存起来，留作后用
        //   6. 计算权重总和
        for (Invoker<T> invoker : invokers) {
            // 获得identify的值
            String identifyString = invoker.getUrl().toIdentityString();
            // 获得加权轮询器
            WeightedRoundRobin weightedRoundRobin = map.get(identifyString);
            // 计算权重
            int weight = getWeight(invoker, invocation);

            // 如果加权轮询器为空,则创建【map的key可以理解为服务提供者的地址】
            if (weightedRoundRobin == null) {
                weightedRoundRobin = new WeightedRoundRobin();
                weightedRoundRobin.setWeight(weight);
                map.putIfAbsent(identifyString, weightedRoundRobin);
            }

            // 如果权重跟之前的权重不一样，则重新设置权重
            if (weight != weightedRoundRobin.getWeight()) {
                //weight changed
                weightedRoundRobin.setWeight(weight);
            }

            // 计数器增加： current = weight + current
            long cur = weightedRoundRobin.increaseCurrent();
            // 更新最后一次更新时间
            weightedRoundRobin.setLastUpdate(now);
            // 当落在该服务提供者的统计数大于最大可承受的数
            if (cur > maxCurrent) {
                // 赋值
                maxCurrent = cur;
                // 被选择的selectedInvoker赋值
                selectedInvoker = invoker;
                // 被选择的加权轮询器赋值
                selectedWRR = weightedRoundRobin;
            }
            // 累加
            totalWeight += weight;
        }

        // 如果更新锁不能获得并且invokers的大小跟map大小不匹配
        // 对 <identifyString, WeightedRoundRobin> 进行检查，过滤掉长时间未被更新的节点。
        // 该节点可能挂了，invokers 中不包含该节点，所以该节点的 lastUpdate 长时间无法被更新。
        // 若未更新时长超过阈值后，就会被移除掉，默认阈值为60秒。
        if (!updateLock.get() && invokers.size() != map.size()) {
            if (updateLock.compareAndSet(false, true)) {
                try {
                    // copy -> modify -> update reference
                    // 复制
                    ConcurrentMap<String, WeightedRoundRobin> newMap = new ConcurrentHashMap<String, WeightedRoundRobin>();
                    newMap.putAll(map);
                    Iterator<Entry<String, WeightedRoundRobin>> it = newMap.entrySet().iterator();
                    // 轮询
                    while (it.hasNext()) {
                        Entry<String, WeightedRoundRobin> item = it.next();
                        // 如果大于回收时间，则进行回收
                        if (now - item.getValue().getLastUpdate() > RECYCLE_PERIOD) {
                            it.remove();
                        }
                    }
                    // 从集合中移除
                    methodWeightMap.put(key, newMap);
                } finally {
                    updateLock.set(false);
                }
            }
        }
        // 如果被选择的selectedInvoker不为空
        if (selectedInvoker != null) {
            // 选择的那个减去最大权重
            selectedWRR.sel(totalWeight);
            return selectedInvoker;
        }
        // should not happen here
        return invokers.get(0);
    }

}
