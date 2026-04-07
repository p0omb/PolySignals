package ru.gbp.PolySignals.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@Configuration
@EnableScheduling
@EnableAsync
public class SchedulerConfig {

    @Bean(name = "whaleUpdateSchedulerExecutor")
    public TaskExecutor whaleUpdateSchedulerExecutor() {
        return buildExecutor("whale-update-", 1, 2, 200);
    }

    @Bean(name = "betFetchSchedulerExecutor")
    public TaskExecutor betFetchSchedulerExecutor() {
        return buildExecutor("bet-fetch-", 2, 4, 500);
    }

    @Bean(name = "signalDetectionSchedulerExecutor")
    public TaskExecutor signalDetectionSchedulerExecutor() {
        return buildExecutor("signal-backup-", 1, 2, 200);
    }

    @Bean(name = "cleanupSchedulerExecutor")
    public TaskExecutor cleanupSchedulerExecutor() {
        return buildExecutor("cleanup-", 1, 1, 50);
    }

    @Bean(name = "marketCacheSchedulerExecutor")
    public TaskExecutor marketCacheSchedulerExecutor() {
        return buildExecutor("market-cache-", 1, 2, 300);
    }

    private TaskExecutor buildExecutor(String prefix, int core, int max, int queue) {
        ThreadPoolTaskExecutor e = new ThreadPoolTaskExecutor();
        e.setThreadNamePrefix(prefix);
        e.setCorePoolSize(core);
        e.setMaxPoolSize(max);
        e.setQueueCapacity(queue);
        e.setWaitForTasksToCompleteOnShutdown(true);
        e.setAwaitTerminationSeconds(20);
        e.initialize();
        return e;
    }
}

