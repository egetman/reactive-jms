package com.github.egetman;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.util.concurrent.Executors.newScheduledThreadPool;

@Slf4j
class ColdPublisher<T> implements Publisher<T>, AutoCloseable {

    private final Source<T> source;
    private final AtomicInteger demandKey = new AtomicInteger();
    private final Collection<Demand> demands = new CopyOnWriteArraySet<>();

    private final int poolSize = Runtime.getRuntime().availableProcessors();
    private final ThreadFactory threadFactory = new CustomizableThreadFactory("cp-worker", true);
    private final ExecutorService executor = newScheduledThreadPool(poolSize, threadFactory);

    ColdPublisher(@Nonnull Source<T> source) {
        this.source = Objects.requireNonNull(source, "Source must not be null");
    }

    @Override
    public void subscribe(@Nonnull Subscriber<? super T> subscriber) {
        Objects.requireNonNull(subscriber, "Subscriber must not be null");
        try {
            final Demand demand = new Demand(subscriber, demandKey.getAndIncrement());
            log.debug("{}: subscribed with {}", demand, subscriber);
            demands.add(demand);
            log.debug("Total subscriptions count: {}", demands.size());
            executor.execute(() -> sendNext(demand));
        } catch (Exception e) {
            log.error("Exception occurred during subscription: " + e, e);
            subscriber.onError(e);
        }
    }

    private void sendNext(Demand demand) {
        // if cancel was requested, skip execution.
        if (demand.canceled.get()) {
            return;
        }

        try {
            // wait until Subscriber#onSubscribe called
            // noinspection StatementWithEmptyBody
            while (!demand.initialized.get()) {
                // we need to wait in case of sync call Subscription#request from Subscriber#onSubscribe
                // until Subscriber complete it's subscription registration.
            }

            // we could add new requested elements from different threads, but process from one
            if (demand.processing.compareAndSet(false, true)) {
                log.debug("{}: processing Next for total pool of: {} requests(s)", demand, demand.requested.get());
                final Iterator<T> iterator = source.iterator(demand.key);

                checkNoMoreElements(demand);
                while (!demand.canceled.get() && demand.requested.get() > 0 && iterator.hasNext()) {
                    final T element = Objects.requireNonNull(iterator.next());
                    log.debug("Publishing next element with type {}", element.getClass().getSimpleName());
                    demand.onNext(element);
                    demand.requested.decrementAndGet();
                }
                checkNoMoreElements(demand);
                demand.processing.set(false);
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
    private void checkNoMoreElements(Demand demand) {
        if (!source.iterator(demand.key).hasNext()) {
            log.debug("{}: no more source to publish", demand);
            demand.onComplete();
        }
    }

    @Override
    public void close() {
        if (!executor.isShutdown()) {
            log.debug("shutting down {}", this);
            demands.forEach(Demand::cancel);
            executor.shutdownNow();
        }
    }

    @EqualsAndHashCode(of = "key")
    class Demand implements Subscription {

        private final AtomicBoolean canceled = new AtomicBoolean();
        private final AtomicBoolean processing = new AtomicBoolean();
        private final AtomicBoolean initialized = new AtomicBoolean();

        private final AtomicLong requested = new AtomicLong();
        @Getter
        private final int key;
        private Subscriber<? super T> subscriber;

        private Demand(@Nonnull Subscriber<? super T> subscriber, int key) {
            this.key = key;
            log.debug("{}: initialization started", this);
            this.subscriber = Objects.requireNonNull(subscriber, "Subscriber must not be null");
            this.subscriber.onSubscribe(this);
            log.debug("{}: initialization finished", this);
            initialized.set(true);
        }

        private void onNext(@Nonnull T next) {
            log.debug("{}: received onNext {} signal", this, next.getClass().getSimpleName());
            subscriber.onNext(next);
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

        @Override
        public void request(long n) {
            if (canceled.get()) {
                return;
            }
            log.debug("{}: requested {} element(s)", this, n);
            if (n <= 0) {
                subscriber.onError(new IllegalArgumentException("Specification rule [3.9] violation"));
                return;
            }
            while (true) {
                long count = this.requested.get();
                if (this.requested.compareAndSet(count, count + n)) {
                    log.debug("{}: additional request(s) [{}] added to requests pool", this, n);
                    break;
                }
            }
            executor.execute(() -> sendNext(this));
        }

        @Override
        public void cancel() {
            // no need to close resources each time cancel called
            if (canceled.compareAndSet(false, true)) {
                log.debug("{}: cancelled", this);
                clear();
            }
        }

        /**
         * Usage of this method should be synchronized, cause there is no guarantee of it's idempotency.
         */
        private void clear() {
            subscriber = null;
            demands.remove(this);
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
