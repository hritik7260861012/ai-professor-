package com.professor.assistant.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded executor for AI generation jobs.
 *
 * <p>Deliberately a plain thread pool rather than Redis or a message
 * broker: the project has no broker today and adding one purely for this
 * phase would be disproportionate. The pool bounds how many provider calls
 * can be in flight at once, which is the property that actually controls
 * cost.
 *
 * <p>Queue capacity is small on purpose. When it fills, submissions are
 * rejected rather than buffered indefinitely, so a burst cannot silently
 * become a backlog of paid requests.
 */
@Configuration
public class AiExecutorConfig {

  /**
   * @param properties bounds pulled from configuration so an operator can
   *     tune concurrency without a rebuild
   */
  @Bean(name = "aiGenerationExecutor")
  public Executor aiGenerationExecutor(AiGenerationProperties properties) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(Math.max(1, properties.getMaxConcurrentJobs()));
    executor.setMaxPoolSize(Math.max(1, properties.getMaxConcurrentJobs()));
    executor.setQueueCapacity(Math.max(1, properties.getMaxConcurrentJobs()) * 2);
    executor.setThreadNamePrefix("ai-gen-");
    // A full queue must surface as a rejection, not an unbounded wait.
    executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.initialize();
    return executor;
  }
}