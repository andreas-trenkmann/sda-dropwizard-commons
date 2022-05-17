package org.sdase.commons.client.jersey.token;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** A thread factory that produces threads with a given name prefix. */
public final class NamedThreadFactory implements ThreadFactory {

  private static String amendNamePrefix(String namePrefix, boolean daemon) {
    final StringBuilder builder = new StringBuilder(64).append(namePrefix);
    if (daemon) {
      builder.append("-daemon");
    }
    builder.append("-thread-");
    return builder.toString();
  }

  private static final Map<String, NamedThreadFactory> threadFactories = new HashMap<>();

  private static final Map<String, NamedThreadFactory> daemonThreadFactories = new HashMap<>();

  public static NamedThreadFactory get(String namePrefix, boolean daemon) {
    Objects.requireNonNull(namePrefix, "namePrefix cannot be null.");
    if (namePrefix.isEmpty()) {
      throw new IllegalArgumentException("namePrefix cannot be blank.");
    }
    final String key = amendNamePrefix(namePrefix, daemon);
    final Map<String, NamedThreadFactory> instances =
        daemon ? daemonThreadFactories : threadFactories;
    return instances.computeIfAbsent(key, k -> new NamedThreadFactory(key, daemon));
  }

  private final String namePrefix;

  private final ThreadGroup threadGroup;

  private final AtomicInteger threadNumber = new AtomicInteger(1);

  private final boolean daemon;

  private NamedThreadFactory(String namePrefix, boolean daemon) {
    this.namePrefix = namePrefix;
    final SecurityManager securityManager = System.getSecurityManager();
    this.threadGroup =
        securityManager != null
            ? securityManager.getThreadGroup()
            : Thread.currentThread().getThreadGroup();
    this.daemon = daemon;
  }

  @Override
  public Thread newThread(Runnable runnable) {
    final String name = this.namePrefix + this.threadNumber.getAndIncrement();
    final Thread thread = new Thread(this.threadGroup, runnable, name, 0);
    if (thread.isDaemon() != this.daemon) {
      thread.setDaemon(this.daemon);
    }
    if (thread.getPriority() != Thread.NORM_PRIORITY) {
      thread.setPriority(Thread.NORM_PRIORITY);
    }
    return thread;
  }
}
