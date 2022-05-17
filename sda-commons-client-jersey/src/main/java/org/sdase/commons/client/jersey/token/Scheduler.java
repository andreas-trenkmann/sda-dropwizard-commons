package org.sdase.commons.client.jersey.token;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/** Wraps a scheduled executor service. Does not throw exceptions when called after shut down. */
final class Scheduler {

  private ThreadFactory threadFactory = NamedThreadFactory.get("OIDC_CLIENT", true);
  private ScheduledExecutorService executor =
      Executors.newSingleThreadScheduledExecutor(threadFactory);

  private final Runnable command;

  public Scheduler(Runnable command) {
    this.command = Objects.requireNonNull(command, "command cannot be null.");
  }

  public synchronized void start() {
    if (this.executor != null) {
      this.executor.execute(this.command);
    }
  }

  public synchronized void schedule(Duration delay) {
    Objects.requireNonNull(delay, "delay cannot be null.");
    if (this.executor != null) {
      this.executor.schedule(this.command, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  public synchronized void shutdown() {
    this.executor.shutdown();
    this.executor = null;
  }
}
