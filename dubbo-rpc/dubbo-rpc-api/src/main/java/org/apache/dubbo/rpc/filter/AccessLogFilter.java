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

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.ConcurrentHashSet;
import org.apache.dubbo.common.utils.ConfigUtils;
import org.apache.dubbo.common.utils.NamedThreadFactory;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcContext;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.support.AccessLogData;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.apache.dubbo.common.constants.CommonConstants.GROUP_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.PROVIDER;
import static org.apache.dubbo.common.constants.CommonConstants.VERSION_KEY;
import static org.apache.dubbo.rpc.Constants.ACCESS_LOG_KEY;

/**
 * Record access log for the service.
 * <p>
 * Logger key is <code><b>dubbo.accesslog</b></code>.
 * In order to configure access log appear in the specified appender only, additivity need to be configured in log4j's
 * config file, for example:
 * <code>
 * <pre>
 * &lt;logger name="<b>dubbo.accesslog</b>" <font color="red">additivity="false"</font>&gt;
 *    &lt;level value="info" /&gt;
 *    &lt;appender-ref ref="foo" /&gt;
 * &lt;/logger&gt;
 * </pre></code>
 */

// 对记录日志的过滤器,调用侧的过滤器 :
// 它所做的工作就是把引用服务或者暴露服务的调用链信息写入到文件中。

// 1) 先被放入日志集合，
// 2)然后加入到日志队列，
// 3)然后被放入到写入文件到任务中
// 4)最后进入文件。

@Activate(group = PROVIDER, value = ACCESS_LOG_KEY)
public class AccessLogFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(AccessLogFilter.class);

    // 日志访问名称，默认的日志访问名称
    private static final String ACCESS_LOG_KEY = "dubbo.accesslog";

    // 日志队列大小
    private static final int LOG_MAX_BUFFER = 5000;

    // 日志输出的频率
    private static final long LOG_OUTPUT_INTERVAL = 5000;

    // 日期格式
    private static final String FILE_DATE_FORMAT = "yyyyMMdd";

    // It's safe to declare it as singleton since it runs on single thread only
    private static final DateFormat FILE_NAME_FORMATTER = new SimpleDateFormat(FILE_DATE_FORMAT);

    // 日志队列 key为访问日志的名称，value为该日志名称对应的日志集合
    private static final Map<String, Set<AccessLogData>> LOG_ENTRIES = new ConcurrentHashMap<String, Set<AccessLogData>>();

    // 日志线程池
    private static final ScheduledExecutorService LOG_SCHEDULED = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("Dubbo-Access-Log", true));

    /**
     * Default constructor initialize demon thread for writing into access log file with names with access log key
     * defined in url <b>accesslog</b>
     */
    public AccessLogFilter() {
        LOG_SCHEDULED.scheduleWithFixedDelay(this::writeLogToFile, LOG_OUTPUT_INTERVAL, LOG_OUTPUT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    /**
     * This method logs the access log for service method invocation call.
     *
     * @param invoker service
     * @param inv     Invocation service method.
     * @return Result from service method.
     * @throws RpcException
     */
    // 1) 构造日志数据
    // 2) 把日志加入到集合
    // 3) 调用下一个调用链
    @Override
    public Result invoke(Invoker<?> invoker, Invocation inv) throws RpcException {
        try {
            // 获得日志名称
            String accessLogKey = invoker.getUrl().getParameter(ACCESS_LOG_KEY);
            if (ConfigUtils.isNotEmpty(accessLogKey)) {
                // 构建AccessLogData
                AccessLogData logData = buildAccessLogData(invoker, inv);
                // 记录日志
                log(accessLogKey, logData);
            }
        } catch (Throwable t) {
            logger.warn("Exception in AccessLogFilter of service(" + invoker + " -> " + inv + ")", t);
        }
        // 调用下一个调用链
        return invoker.invoke(inv);
    }

    // 增加日志信息到日志集合中。
    private void log(String accessLog, AccessLogData accessLogData) {
        Set<AccessLogData> logSet = LOG_ENTRIES.computeIfAbsent(accessLog, k -> new ConcurrentHashSet<>());

        if (logSet.size() < LOG_MAX_BUFFER) {
            logSet.add(accessLogData);
        } else {
            //TODO we needs use force writing to file so that buffer gets clear and new log can be written.
            logger.warn("AccessLog buffer is full skipping buffer ");
        }
    }

    // 把日志消息落地到文件
    private void writeLogToFile() {
        if (!LOG_ENTRIES.isEmpty()) {
            // 遍历日志队列
            for (Map.Entry<String, Set<AccessLogData>> entry : LOG_ENTRIES.entrySet()) {
                try {
                    // 获得日志名称
                    String accessLog = entry.getKey();
                    Set<AccessLogData> logSet = entry.getValue();
                    if (ConfigUtils.isDefault(accessLog)) {
                        processWithServiceLogger(logSet);
                    } else {
                        File file = new File(accessLog);
                        createIfLogDirAbsent(file);
                        if (logger.isDebugEnabled()) {
                            logger.debug("Append log to " + accessLog);
                        }
                        renameFile(file);
                        processWithAccessKeyLogger(logSet, file);
                    }

                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
        }
    }

    private void processWithAccessKeyLogger(Set<AccessLogData> logSet, File file) throws IOException {
        try (FileWriter writer = new FileWriter(file, true)) {
            for (Iterator<AccessLogData> iterator = logSet.iterator();
                 iterator.hasNext();
                 iterator.remove()) {
                writer.write(iterator.next().getLogMessage());
                writer.write("\r\n");
            }
            writer.flush();
        }
    }

    // 构造日志数据
    private AccessLogData buildAccessLogData(Invoker<?> invoker, Invocation inv) {
        // 获得rpc上下文
        RpcContext context = RpcContext.getContext();
        AccessLogData logData = AccessLogData.newLogData();
        // 获得调用的接口名称
        logData.setServiceName(invoker.getInterface().getName());
        logData.setMethodName(inv.getMethodName());
        // 获得版本号
        logData.setVersion(invoker.getUrl().getParameter(VERSION_KEY));
        // 获得组，是消费者侧还是生产者侧
        logData.setGroup(invoker.getUrl().getParameter(GROUP_KEY));
        logData.setInvocationTime(new Date());
        logData.setTypes(inv.getParameterTypes());
        logData.setArguments(inv.getArguments());
        return logData;
    }

    private void processWithServiceLogger(Set<AccessLogData> logSet) {
        for (Iterator<AccessLogData> iterator = logSet.iterator();
             iterator.hasNext();
             iterator.remove()) {
            AccessLogData logData = iterator.next();
            LoggerFactory.getLogger(ACCESS_LOG_KEY + "." + logData.getServiceName()).info(logData.getLogMessage());
        }
    }

    private void createIfLogDirAbsent(File file) {
        File dir = file.getParentFile();
        if (null != dir && !dir.exists()) {
            dir.mkdirs();
        }
    }

    private void renameFile(File file) {
        if (file.exists()) {
            String now = FILE_NAME_FORMATTER.format(new Date());
            String last = FILE_NAME_FORMATTER.format(new Date(file.lastModified()));
            if (!now.equals(last)) {
                File archive = new File(file.getAbsolutePath() + "." + last);
                file.renameTo(archive);
            }
        }
    }
}
