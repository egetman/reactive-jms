package com.github.egetman.etc;

import java.text.MessageFormat;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nonnull;

import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unused")
public class CustomizableThreadFactory implements ThreadFactory {

    private static final String DEFAULT_THREAD_NAME = "worker";
    private final AtomicLong workerNumber = new AtomicLong();
    private final TracePrinter tracePrinter = new TracePrinter();
    private final boolean isDaemon;
    private final String threadName;

    CustomizableThreadFactory(boolean isDaemon) {
        this(DEFAULT_THREAD_NAME, isDaemon);
    }

    public CustomizableThreadFactory(String threadName) {
        this(threadName, false);
    }

    public CustomizableThreadFactory(String threadName, boolean isDaemon) {
        this.threadName = threadName;
        this.isDaemon = isDaemon;
    }

    @Override
    public Thread newThread(@Nonnull Runnable runnable) {
        String name = MessageFormat.format("{0} [{1}]", threadName, workerNumber.getAndIncrement());
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(isDaemon);
        thread.setUncaughtExceptionHandler(tracePrinter);
        return thread;
    }

    @Slf4j
    private static class TracePrinter implements Thread.UncaughtExceptionHandler {

        @Override
        public void uncaughtException(@Nonnull Thread thread, @Nonnull Throwable ex) {
            log.error("Uncaught exception for {}:", thread.getName(), ex);
        }
    }

}
