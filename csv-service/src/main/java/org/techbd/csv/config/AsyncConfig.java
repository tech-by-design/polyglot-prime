package org.techbd.csv.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "asyncTaskExecutor")
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int corePoolSize = Integer.parseInt(
                System.getenv().getOrDefault("TECHBD_CSV_ASYNC_EXECUTOR_CORE_POOL_SIZE", "20"));
        int maxPoolSize = Integer.parseInt(
                System.getenv().getOrDefault("TECHBD_CSV_ASYNC_EXECUTOR_MAX_POOL_SIZE", "50"));
        int queueCapacity = Integer.parseInt(
                System.getenv().getOrDefault("TECHBD_CSV_ASYNC_EXECUTOR_QUEUE_CAPACITY", "200"));
        int awaitTermination = Integer.parseInt(
                System.getenv().getOrDefault("TECHBD_CSV_ASYNC_EXECUTOR_AWAIT_TERMINATION_SECONDS", "30"));
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setAwaitTerminationSeconds(awaitTermination);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setThreadNamePrefix("async-exec-");
        executor.initialize();
        return executor;
    }
}