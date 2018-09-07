package com.github.egetman;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

import com.github.egetman.etc.CustomizableThreadFactory;
import com.github.egetman.source.Source;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Slf4j
public class ColdPublisher<T> implements Publisher<T>, AutoCloseable {

    private final Source<T> source;
    private final AtomicInteger demandKey = new AtomicInteger();
    private final Map<Integer, Demand> demands = new ConcurrentHashMap<>();

    private final int poolSize = Runtime.getRuntime().availableProcessors();
    private final ThreadFactory threadFactory = new CustomizableThreadFactory("cp-worker", true);
    private final ExecutorService executor = newScheduledThreadPool(poolSize, threadFactory);

    public ColdPublisher(@Nonnull Source<T> source) {
        this.source = Objects.requireNonNull(source, "Source must not be null");
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void subscribe(@Nonnull Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        try {
            final Demand demand = new Demand(subscriber, demandKey.getAndIncrement());
            subscriber.onSubscribe(demand);
            log.debug("{}: subscribed with {}", demand, subscriber);
            demands.put(demand.key, demand);
            log.debug("Total subscriptions count: {}", demands.size());
            executor.execute(() -> sendNext(demand));
        } catch (Exception e) {
            log.error("Exception occurred during subscription: " + e, e);
            subscriber.onError(e);
        }
    }

    private void sendNext(@Nonnull Demand demand) {
        // if cancel was requested, skip execution.
        if (demand.isCancelled()) {
            return;
        }

        try {
            // we could add new requested elements from different threads, but process from one
            if (demand.tryLock()) {
                log.debug("{}: processing Next for total pool of: {} requests(s)", demand, demand.size());
                final Iterator<T> iterator = source.iterator(demand.key);

                completeIfNoMoreElements(demand);
                while (!demand.isCancelled() && demand.size() > 0 && iterator.hasNext()) {
                    final T element = Objects.requireNonNull(iterator.next());
                    log.debug("Publishing next element with type {}", element.getClass().getSimpleName());
                    demand.onNext(element);
                }
                completeIfNoMoreElements(demand);
                demand.release();
                log.debug("{}: processing Next completed by {}", demand, Thread.currentThread().getName());
            }
        } catch (Exception e) {
            log.error("{}: exception occurred during sending onNext:", demand, e);
            demand.onError(e);
        }
    }

    /**
     * Verify that {@link Iterator} for this {@link Demand} has more elements to process.
     *
     * @param demand is current {@link Demand} to check.
     */
    private void completeIfNoMoreElements(@Nonnull Demand demand) {
        if (demand.isCancelled() || !source.iterator(demand.key).hasNext()) {
            log.debug("{}: no more source to publish", demand);
            demand.onComplete();
        }
    }

    /**
     * {@inheritDoc}.
     */
    @Override
    public void close() {
        if (!executor.isShutdown()) {
            log.debug("shutting down {}", this);
            demands.values().forEach(Demand::cancel);
            executor.shutdownNow();
        }
    }

    @EqualsAndHashCode(of = "key")
    class Demand implements Subscription {

        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicBoolean processing = new AtomicBoolean();

        private final int key;
        private Subscriber<? super T> subscriber;
        private final AtomicLong requested = new AtomicLong();

        private Demand(@Nonnull Subscriber<? super T> subscriber, int key) {
            this.key = key;
            log.debug("{}: initialization started", this);
            this.subscriber = Objects.requireNonNull(subscriber, "Subscriber must not be null");
            log.debug("{}: initialization finished", this);
        }

        private void onNext(@Nonnull T next) {
            log.debug("{}: received onNext {} signal", this, next.getClass().getSimpleName());
            subscriber.onNext(next);
            requested.decrementAndGet();
        }

        private void onError(@Nonnull Throwable error) {
            log.debug("{}: received onError signal", this, error);
            if (canceled.compareAndSet(false, true)) {
                subscriber.onError(error);
                clear();
            }
        }

        private void onComplete() {
            if (canceled.compareAndSet(false, true)) {
                log.debug("{}: subscriber completed", this);
                subscriber.onComplete();
                clear();
            }
        }

        /**
         * {@inheritDoc}.
         */
        @Override
        public void request(long addition) {
            if (canceled.get()) {
                return;
            }
            log.debug("{}: requested {} element(s)", this, addition);
            if (addition <= 0) {
                subscriber.onError(new IllegalArgumentException("Specification rule [3.9] violation"));
                return;
            }
            while (true) {
                long count = this.requested.get();
                if (this.requested.compareAndSet(count, count + addition)) {
                    log.debug("{}: additional request(s) [{}] added to requests pool", this, addition);
                    break;
                }
            }
            executor.execute(() -> sendNext(this));
        }

        /**
         * {@inheritDoc}.
         */
        @Override
        public void cancel() {
            // no need to close resources each time cancel called
            if (canceled.compareAndSet(false, true)) {
                log.debug("{}: cancelled", this);
                clear();
            }
        }

        /**
         * Indicates if {@link Demand} is cancelled.
         *
         * @return true if demand is cancelled, false otherwise.
         */
        private boolean isCancelled() {
            return canceled.get();
        }

        /**
         * Try to get exclusive {@literal processing} lock for given {@link Demand}.
         *
         * @return true, if the acquisition was successful, false otherwise.
         */
        private boolean tryLock() {
            return processing.compareAndSet(false, true);
        }

        /**
         * Reseases exclusive {@literal processing} lock for given {@link Demand}.
         */
        private void release() {
            processing.set(false);
        }

        /**
         * @return count of demanded elements.
         */
        private long size() {
            return requested.get();
        }

        /**
         * Clean up all internal {@link Demand} resources and drop reference to it's {@link Subscriber}.
         * Usage of this method should be synchronized, cause there is no guarantee of it's idempotency.
         */
        private void clear() {
            subscriber = null;
            demands.remove(key);
            try {
                // we should try to close underlying source, but it's prohibited by the spec to throw any exceptions
                // from cancel and etc.
                source.iterator(key).close();
            } catch (Exception e) {
                log.error("Exception occurred during closing source#closableIterator for " + this, e);
            }
        }

        @Override
        public String toString() {
            return "Demand [" + key + "]";
        }

    }

}
