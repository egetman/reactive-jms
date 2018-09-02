package com.github.egetman.etc;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import javax.annotation.Nonnull;

import lombok.extern.slf4j.Slf4j;

import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newCachedThreadPool;

/**
 * {@inheritDoc}
 * This class is {@literal Unconditionally thread safe}.
 * Thread safety guarantees by internal sync with used {@link BlockingQueue}.
 *
 * @param <T> is pool elements type.
 */
@Slf4j
final class BoundedBlockingPool<T> extends AbstractPool<T> implements BlockingPool<T> {

    private BlockingQueue<T> objects;
    private final Supplier<T> factory;
    private final Consumer<T> cleaner;
    private final Predicate<T> validator;

    private static final String SHUTDOWN_CAUSE = "Object pool is already shutdown";

    private volatile boolean shutdownCalled = false;
    private final ExecutorService executor = newCachedThreadPool(new CustomizableThreadFactory(true));

    // cleaner is optional
    BoundedBlockingPool(int size, @Nonnull Predicate<T> validator, @Nonnull Supplier<T> factory, Consumer<T> cleaner) {
        this.cleaner = cleaner;
        this.factory = Objects.requireNonNull(factory);
        this.validator = Objects.requireNonNull(validator);

        objects = new ArrayBlockingQueue<>(size);
        IntStream.range(0, size).forEach(ignored -> objects.add(factory.get()));
    }

    @Override
    public T get(long timeOut, TimeUnit unit) {
        if (!shutdownCalled) {
            try {
                return objects.poll(timeOut, unit);
            } catch (InterruptedException ie) {
                currentThread().interrupt();
            }
            return null;
        }
        throw new IllegalStateException(SHUTDOWN_CAUSE);
    }

    @Override
    public T get() {
        if (!shutdownCalled) {
            try {
                return objects.take();
            } catch (InterruptedException ie) {
                currentThread().interrupt();
            }
            return null;
        }
        throw new IllegalStateException(SHUTDOWN_CAUSE);
    }

    /**
     * Shutdowns pool and release all acquired resources.
     */
    @Override
    public void shutdown() {
        log.info("Pool shutdown requested");
        shutdownCalled = true;
        executor.shutdownNow();
        if (cleaner != null) {
            objects.forEach(cleaner);
        }
    }

    @Override
    protected void returnToPool(T object) {
        if (shutdownCalled) {
            throw new IllegalStateException(SHUTDOWN_CAUSE);
        }
        execute(() -> put(object));
    }

    @Override
    protected void handleInvalidReturn(T object) {
        if (shutdownCalled) {
            throw new IllegalStateException(SHUTDOWN_CAUSE);
        }
        execute(() -> put(factory.get()));
    }

    private void execute(Runnable runnable) {
        if (executor.isShutdown() || executor.isTerminated()) {
            log.warn("Can't execute task: executor is shutdown");
        } else {
            executor.execute(runnable);
        }
    }

    @Override
    protected boolean isValid(T object) {
        return validator.test(object);
    }

    private void put(T object) {
        while (true) {
            try {
                objects.put(object);
                break;
            } catch (InterruptedException ie) {
                currentThread().interrupt();
            }
        }
    }

}