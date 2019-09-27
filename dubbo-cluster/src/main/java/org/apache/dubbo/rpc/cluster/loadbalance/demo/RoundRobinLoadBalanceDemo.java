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
package org.apache.dubbo.rpc.cluster.loadbalance.demo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round robin load balance.
 *
 * 加权轮询：
 * 服务器 A、B、C 权重比为 6:3:1，那么在10次请求中，服务器 A 将收到其中的6次请求，服务器 B 会收到其中的3次请求，服务器 C 则收到其中的1次请求
 * 也就是说每台服务器能够收到的请求归结于它的权重。
 *
 *  初始值 current=0， total = 10
 *
 * 1) current = current + weight
 * 2) 选current最大的那个
 * 3） 最大的那个 current = current - total
 *  1
 *(A,B,C)   (A,B,C)  选择  (A,B,C)
 *(0,0,0)   (6,3,1)  A    (-4,3,1)
 *(-4,3,1)  (2,6,2)  B    (2,-4,2)
 *(2,-4,2)  (8,-1,3) A    (-2,-4,2)
 *(-2,-4,2)
 *
 */
public class RoundRobinLoadBalanceDemo {

    private Map<String, WeightedRoundRobin> weightMap = new ConcurrentHashMap();

    private List<Server> serverList;

    private int totalWeight;

    public RoundRobinLoadBalanceDemo(List<Server> serverList) {
        this.serverList = serverList;
        this.weightMap = new HashMap<>();
        for(Server server: serverList){
            WeightedRoundRobin  weightedRoundRobin = new WeightedRoundRobin();
            weightedRoundRobin.setWeight(server.weight);
            weightMap.put(server.id,weightedRoundRobin);
            totalWeight = totalWeight + server.weight;
        }
    }

    public static class Server {
        String id;
        int weight;

        public Server(String id, int weight) {
            this.id = id;
            this.weight = weight;
        }

        @Override
        public String toString() {
            return  "id=" + id +  "-weight=" + weight ;
        }
    }




    // 加权轮询器
    // 记录了某一个服务提供者的一些数据，比如权重、比如当前已经有多少请求落在该服务提供者上等。
    protected static class WeightedRoundRobin {

        // 权重
        private int weight;

        // 当前已经有多少请求落在该服务提供者身上，也可以看成是一个动态的权重
        private AtomicLong current = new AtomicLong(0);

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
    }

    public Server doSelect() {

        // 最大权重临时值，下面的循环选择时用的
        long maxCurrent = Long.MIN_VALUE;
        // 最大current的Server
        Server selectedServer = null;
        // 最大current的Invoker对应的WeightedRoundRobin
        WeightedRoundRobin selectedWRR = null;

        for (Server server : serverList) {
            WeightedRoundRobin weightedRoundRobin = weightMap.get(server.id);
            // 计数器增加： current = weight + current
            long cur = weightedRoundRobin.increaseCurrent();
            // 当落在该服务提供者的统计数大于最大可承受的数
            if (cur > maxCurrent) {
                // 赋值
                maxCurrent = cur;
                // 被选择的selectedInvoker赋值
                selectedServer = server;
                // 被选择的加权轮询器赋值
                selectedWRR = weightedRoundRobin;
            }
        }

        // 如果被选择的selectedInvoker不为空
        if (selectedServer != null) {
            // 选择的那个减去最大权重
            selectedWRR.sel(totalWeight);
            return selectedServer;
        }
        // should not happen here
        return serverList.get(0);
    }

    private void printCurrent(){
        System.out.print("(");
        for (Server server1 : serverList) {
            System.out.print(weightMap.get(server1.id).current+ ",");
        }
        System.out.print(")");
    }


    public static void main(String[] args) {
        List<Server> serverList = new ArrayList<>();
        serverList.add(new Server("A",6));
        serverList.add(new Server("B",3));
        serverList.add(new Server("C",1));
        RoundRobinLoadBalanceDemo demo = new RoundRobinLoadBalanceDemo(serverList);
        for(int i =0;i<10;i++){
            demo.printCurrent();
            Server server = demo.doSelect();
            System.out.print("-" + server.id + "-");
            demo.printCurrent();
            System.out.println();
        }
    }

}
