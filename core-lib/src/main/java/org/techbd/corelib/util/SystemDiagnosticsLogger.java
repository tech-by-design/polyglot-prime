package org.techbd.corelib.util;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.zaxxer.hikari.HikariDataSource;

public class SystemDiagnosticsLogger {

    private static final Logger LOG = LoggerFactory.getLogger(SystemDiagnosticsLogger.class);

    public static void logResourceStats(String interactionId, DataSource dataSource, TaskExecutor taskExecutor,String techBDVersion) {
        doLogDiagnostics(interactionId != null ? interactionId : "unknown", dataSource, taskExecutor,techBDVersion);
    }

    public static void logBasicSystemStats() {
        doLogDiagnostics("unknown", null, null,null);
    }

    private static void doLogDiagnostics(String interactionId, DataSource dataSource, TaskExecutor taskExecutor,String techBDVersion) {
        try {
            // Thread Stats
            ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
            LOG.error("[{}] JVM Threads - Live: {}, Peak: {}, Daemon: {}, TechBD Version : {}",
                    interactionId,
                    threadMXBean.getThreadCount(),
                    threadMXBean.getPeakThreadCount(),
                    threadMXBean.getDaemonThreadCount(),
                    techBDVersion);

            // Memory Stats
            MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
            MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
            MemoryUsage nonHeap = memoryMXBean.getNonHeapMemoryUsage();
            LOG.error("[{}] Memory - Heap Used: {} MB, Max: {} MB, Committed: {} MB, TechBD Version : {}",
                    interactionId,
                    toMB(heap.getUsed()),
                    toMB(heap.getMax()),
                    toMB(heap.getCommitted()),
                    techBDVersion);
            LOG.error("[{}] Memory - Non-Heap Used: {} MB, Committed: {} MB, TechBD Version : {}",
                    interactionId,
                    toMB(nonHeap.getUsed()),
                    toMB(nonHeap.getCommitted()),
                    techBDVersion);

            // CPU Stats
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean extendedOs) {
                LOG.error("[{}] CPU - Process Load: {}%, System Load: {}%, Available CPUs: {}, TechBD Version : {}",
                        interactionId,
                        percent(extendedOs.getProcessCpuLoad()),
                        percent(extendedOs.getSystemCpuLoad()),
                        extendedOs.getAvailableProcessors(),
                        techBDVersion);
            } else {
                LOG.warn("[{}] OS MXBean does not support detailed CPU metrics, TechBD Version : {}", interactionId, techBDVersion);
            }

            // TaskExecutor Stats
            if (taskExecutor instanceof ThreadPoolTaskExecutor executor) {
                LOG.error("[{}] AsyncExecutor - Core: {}, Max: {}, Active: {}, QueueSize: {}, TechBD Version : {}",
                        interactionId,
                        executor.getCorePoolSize(),
                        executor.getMaxPoolSize(),
                        executor.getActiveCount(),
                        executor.getThreadPoolExecutor().getQueue().size(),
                        techBDVersion);
            } else if (taskExecutor == null) {
                LOG.warn("[{}] AsyncExecutor is null. Skipping thread pool diagnostics, TechBD Version : {}", interactionId, techBDVersion);
            } else {
                LOG.warn("[{}] AsyncExecutor is not a ThreadPoolTaskExecutor. Skipping diagnostics, TechBD Version : {}", interactionId, techBDVersion);
            }

            // HikariCP Pool Stats
            if (dataSource instanceof HikariDataSource hikariDataSource) {
                Object poolProxy = hikariDataSource.getHikariPoolMXBean();
                if (poolProxy != null) {
                    try {
                        int active = (int) poolProxy.getClass().getMethod("getActiveConnections").invoke(poolProxy);
                        int idle = (int) poolProxy.getClass().getMethod("getIdleConnections").invoke(poolProxy);
                        int total = (int) poolProxy.getClass().getMethod("getTotalConnections").invoke(poolProxy);
                        int waiting = (int) poolProxy.getClass().getMethod("getThreadsAwaitingConnection")
                                .invoke(poolProxy);
                        LOG.error("[{}] HikariCP Pool - Active: {}, Idle: {}, Total: {}, Waiting: {}, TechBD Version : {}",
                                interactionId, active, idle, total, waiting, techBDVersion);
                    } catch (Exception e) {
                        LOG.warn("[{}] Failed to reflectively access HikariCP pool metrics, TechBD Version : {}", interactionId, techBDVersion, e);
                    }
                } else {
                    LOG.warn("[{}] HikariCP MXBean is null, TechBD Version : {}", interactionId, techBDVersion);
                }
            } else if (dataSource != null) {
                LOG.warn("[{}] DataSource is not a HikariDataSource, TechBD Version : {}", interactionId, techBDVersion);
            }

        } catch (Exception ex) {
            LOG.error("[{}] Failed to log system diagnostics, TechBD Version : {}", interactionId, techBDVersion, ex);
        }
    }

    private static long toMB(long bytes) {
        return bytes / (1024 * 1024);
    }

    private static String percent(double value) {
        return value < 0 ? "N/A" : String.format("%.2f", value * 100);
    }
}

