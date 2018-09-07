package com.github.egetman;

import java.text.MessageFormat;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.egetman.barrier.Barrier;
import com.github.egetman.barrier.OpenBarrier;
import com.github.egetman.etc.CustomizableThreadFactory;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.Executors.newScheduledThreadPool;

/**
 * {@link BalancingSubscriber} is {@link Subscriber} implementation with dynamic throughput.
 * The main idea of {@link BalancingSubscriber} is you never ask the given {@literal Subscription} for an unbounded
 * sequence of elements (usually through {@literal Long.MAX_VALUE}).
 * Instead, you say how much elements you want to process for a concrete time interval. {@link BalancingSubscriber}
 * will demand {@literal NOT MORE} elements from it's subscription for that time.
 * In a case when the application throughput rises too high, you can obtain additional control through {@link Barrier}.
 *
 * <p>The simplest way to create a subscriber:
 * {@code
 * Subscriber<T> subscriber = new BalancingSubscriber<T>(System.out::println);
 * }
 *
 * @param <T> type of elements, that {@link BalancingSubscriber} handle.
 */
@Slf4j
public class BalancingSubscriber<T> implements Subscriber<T>, AutoCloseable {

    private static final int BATCH_SIZE = 10;
    private static final int POLL_INTERVAL = 3000;

    private final int batchSize;
    private final int pollInterval;
    private final Barrier barrier;
    private final Consumer<T> onNext;
    private final Runnable onComplete;
    private final Consumer<Throwable> onError;

    private Subscription subscription;
    private final AtomicLong consumed = new AtomicLong();
    private final AtomicBoolean completed = new AtomicBoolean();
    private final ThreadFactory threadFactory = new CustomizableThreadFactory("bs-worker", true);
    private final ScheduledExecutorService executor = newScheduledThreadPool(1, threadFactory);

    public BalancingSubscriber(@Nonnull Consumer<T> onNext) {
        this(onNext, null, null, new OpenBarrier(), BATCH_SIZE, POLL_INTERVAL);
    }

    public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nonnull Barrier barrier) {
        this(onNext, null, null, barrier, BATCH_SIZE, POLL_INTERVAL);
    }

    public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nonnull Barrier barrier, int batchSize, int pollInterval) {
        this(onNext, null, null, barrier, batchSize, pollInterval);
    }

    public BalancingSubscriber(@Nonnull Consumer<T> onNext, @Nullable Consumer<Throwable> onError,
                               @Nullable Runnable onComplete, @Nonnull Barrier barrier, int batchSize,
                               int pollInterval) {

        this.onNext = Objects.requireNonNull(onNext, "OnNext action must not be null");
        this.onError = onError;
        this.onComplete = onComplete;

        this.barrier = Objects.requireNonNull(barrier, "Barrier must not be null");
        this.batchSize = batchSize;
        this.pollInterval = pollInterval;
        log.debug("{}: initialized with {} batch size and {} barrier", this, batchSize, barrier);
    }

    @Override
    public void onSubscribe(@Nullable Subscription subscription) {
        Objects.requireNonNull(subscription, "Subscription must not be null");
        if (this.subscription != null) {
            log.debug("{}: already subscribed: cancelling new subscription {}", this, subscription);
            subscription.cancel();
        } else {
            log.debug("{}: subscribed with {}", this, subscription);
            this.subscription = subscription;
            if (barrier.isOpen() && !completed.get()) {
                this.subscription.request(batchSize);
            } else {
                log.debug("{}: can't request any more elements", this);
            }
            executor.scheduleAtFixedRate(this::tryToRequest, 0, pollInterval, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onNext(@Nullable T next) {
        log.debug("{}: received element: {}", this, next);
        Objects.requireNonNull(next, "Provided element must not be null");

        onNext.accept(next);
        consumed.incrementAndGet();
    }

    @Override
    public void onError(@Nullable Throwable throwable) {
        log.warn("{}: received throwable:", this, throwable);
        Objects.requireNonNull(throwable, "Provided throwable must not be null");

        if (onError != null) {
            onError.accept(throwable);
        }
        completed.set(true);
        close();
    }

    @Override
    public void onComplete() {
        log.debug("{}: complete", this);

        if (onComplete != null) {
            onComplete.run();
        }
        completed.set(true);
        close();
    }

    private void tryToRequest() {
        if (consumed.compareAndSet(batchSize, 0) && barrier.isOpen() && !completed.get()) {
            log.debug("{}: additional {} elements requested", this, batchSize);
            subscription.request(batchSize);
        }
    }

    @Override
    public void close() {
        if (!completed.get()) {
            onComplete();
        }
        if (!executor.isShutdown()) {
            executor.shutdown();
            log.debug("{}: shutdown complete", this);
        }
    }

    @Override
    public String toString() {
        return MessageFormat.format("{0} [batch {1}]", getClass().getSimpleName(), batchSize);
    }

}
