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
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.LoadBalance;

import java.util.List;

import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WARMUP;
import static org.apache.dubbo.rpc.cluster.Constants.DEFAULT_WEIGHT;
import static org.apache.dubbo.rpc.cluster.Constants.REMOTE_TIMESTAMP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WARMUP_KEY;
import static org.apache.dubbo.rpc.cluster.Constants.WEIGHT_KEY;

/**
 * AbstractLoadBalance
 *
 * 负载均衡的抽象类，提供了权重计算的功能
 *
 */
public abstract class AbstractLoadBalance implements LoadBalance {

    /**
     * Calculate the weight according to the uptime proportion of warmup time
     * the new weight will be within 1(inclusive) to weight(inclusive)
     *
     * @param uptime the uptime in milliseconds 启动总时长
     * @param warmup the warmup time in milliseconds 预热需要总时长
     * @param weight the weight of an invoker  服务权重。默认为 100
     * @return weight which takes warmup into account
     */

    // 计算权重的方法，其中计算公式是(uptime / warmup) weight，含义就是进度百分比 权重值。
    static int calculateWarmupWeight(int uptime, int warmup, int weight) {
        // 计算权重 (uptime / warmup) * weight，进度百分比 * 权重
        int ww = (int) ((float) uptime / ((float) warmup / (float) weight));
        // 权重范围为 [1, weight] 之间
        return ww < 1 ? 1 : (ww > weight ? weight : ww);
    }

    @Override
    public <T> Invoker<T> select(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        // 如果invokers为空则返回空
        if (CollectionUtils.isEmpty(invokers)) {
            return null;
        }
        //  如果invokers只有一个服务提供者，则返回一个
        if (invokers.size() == 1) {
            return invokers.get(0);
        }
        // 调用doSelect进行选择
        return doSelect(invokers, url, invocation);
    }

    protected abstract <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation);


    /**
     * Get the weight of the invoker's invocation which takes warmup time into account
     * if the uptime is within the warmup time, the weight will be reduce proportionally
     *
     * @param invoker    the invoker
     * @param invocation the invocation of this invoker
     * @return weight
     */
    // 获得权重的方法，计算权重在calculateWarmupWeight方法中实现，该方法考虑到了jvm预热的过程。
    protected int getWeight(Invoker<?> invoker, Invocation invocation) {
        // 获得 weight 配置，即服务权重。默认为 100
        int weight = invoker.getUrl().getMethodParameter(invocation.getMethodName(), WEIGHT_KEY, DEFAULT_WEIGHT);
        if (weight > 0) {
            // 获得启动时间戳
            long timestamp = invoker.getUrl().getParameter(REMOTE_TIMESTAMP_KEY, 0L);
            if (timestamp > 0L) {
                // 获得启动总时长[当前时间-参数时间]
                int uptime = (int) (System.currentTimeMillis() - timestamp);
                // 获得预热需要总时长。默认为10分钟
                int warmup = invoker.getUrl().getParameter(WARMUP_KEY, DEFAULT_WARMUP);
                // 如果服务运行时间小于预热时间，则重新计算服务权重，即降权
                if (uptime > 0 && uptime < warmup) {
                    // 计算权重
                    weight = calculateWarmupWeight(uptime, warmup, weight);
                }
            }
        }
        return weight >= 0 ? weight : 0;
    }

}
