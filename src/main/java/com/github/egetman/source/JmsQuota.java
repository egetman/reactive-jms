package com.github.egetman.source;

import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;

import com.github.egetman.etc.Pool;
import com.github.egetman.etc.PoolFactory;

import lombok.extern.slf4j.Slf4j;

import static javax.jms.Session.AUTO_ACKNOWLEDGE;

/**
 * Transacted passed inside the {@link JmsQuota} cause it could be changed for source later, but not for already
 * created {@link JmsQuota}.
 */
@Slf4j
class JmsQuota<T> implements CloseableIterator<T> {

    private static final int POOL_SIZE = 3;
    private static final int RETRY_TIME_SECONDS = 10;

    private final int key;
    private final String queue;
    private final boolean transacted;
    private final int acknowledgeMode;

    private final Pool<JmsUnit> units;
    private final Function<Message, T> function;
    private final Lock lock = new ReentrantLock();

    @SuppressWarnings("unused")
    JmsQuota(int key,
             @Nonnull Function<Message, T> function,
             @Nonnull ConnectionFactory factory,
             @Nonnull String queue,
             String user, String password, boolean transacted) {
        this(key, function, factory, queue, user, password, transacted, AUTO_ACKNOWLEDGE);
    }

    @SuppressWarnings("squid:S00107")
    JmsQuota(int key,
             @Nonnull Function<Message, T> function,
             @Nonnull ConnectionFactory factory,
             @Nonnull String queue,
             String user, String password, boolean transacted, int acknowledgeMode) {
        try {
            // initialization should be sync'ed
            lock.lock();
            this.key = key;
            this.queue = Objects.requireNonNull(queue);
            this.function = Objects.requireNonNull(function);
            this.transacted = transacted;
            this.acknowledgeMode = acknowledgeMode;

            this.units = newPool(Objects.requireNonNull(factory), queue, user, password, transacted, acknowledgeMode);
        } finally {
            lock.unlock();
        }
    }

    private Pool<JmsUnit> newPool(@Nonnull ConnectionFactory factory,
                                  @Nonnull String queue,
                                  String user,
                                  String password, boolean transacted, int acknowledgeMode) {

        final Supplier<JmsUnit> supplier = () -> {
            final Connection connection;
            try {
                // password could be missing?
                if (user != null) {
                    connection = factory.createConnection(user, password);
                } else {
                    connection = factory.createConnection();
                }
                return new JmsUnit(connection, queue, transacted, acknowledgeMode);
            } catch (JMSException e) {
                log.error("Exception during pool connection initialization: {}", e.getMessage());
                throw new IllegalStateException("Exception during pool connection initialization", e);
            }
        };
        final Predicate<JmsUnit> validator = jmsUnit -> !jmsUnit.isFailed();
        return PoolFactory.newBoundedBlockingPool(POOL_SIZE, supplier, validator, JmsUnit::close);
    }

    @Override
    public boolean hasNext() {
        // it's always true for infinite source
        return true;
    }

    @Nonnull
    @Override
    public T next() {
        // iterator contract
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        T result = null;
        while (result == null) {
            // outside of try-catch for exit 'next' when pool is closed
            JmsUnit unit = units.get();
            try {
                result = next(unit);
            } catch (Exception e) {
                log.error("", e);
            } finally {
                units.release(unit);
            }
        }
        return result;
    }

    @Nullable
    private T next(@Nonnull JmsUnit unit) {
        Message message = null;
        try {
            message = unit.receive();
            return function.apply(message);
        } catch (Exception ex) {
            log.error("Exception during message receiving for {}: {}", this, ex.getMessage());
            log.warn("Prepare to recover session {}", this);
            try {
                if (!recover(unit)) {
                    unit.fail();
                    wait(RETRY_TIME_SECONDS, TimeUnit.SECONDS);
                    return null;
                }
                message = unit.receive();
                return function.apply(message);
            } catch (Exception e) {
                log.warn("Session recovery failed for {} with cause {}. Releasing unit", this, e.getMessage());
                unit.fail();
                wait(RETRY_TIME_SECONDS, TimeUnit.SECONDS);
                return null;
            }
        } finally {
            acknowledge(message);
        }
    }

    private boolean recover(@Nonnull JmsUnit unit) {
        try {
            // try to recover with exclusive lock
            lock.lock();
            unit.recover();
            return true;
        } catch (Exception e) {
            log.error("Recovery failed for {}", this);
            return false;
        } finally {
            lock.unlock();
        }
    }

    private void acknowledge(@Nullable Message message) {
        if (message != null && Session.CLIENT_ACKNOWLEDGE == acknowledgeMode) {
            try {
                message.acknowledge();
            } catch (JMSException e) {
                log.error("Fail to acknowledge message {}: {}", message, e.getMessage());
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void wait(long time, @Nonnull TimeUnit timeUnit) {
        try {
            Thread.sleep(timeUnit.toMillis(time));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void remove() {
        // noop
    }

    @Override
    public String toString() {
        String name = "JmsQuota [" + key + "]" + "[" + queue + "]";
        return transacted ? "Transacted " + name : name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        JmsQuota quota = (JmsQuota) other;
        return key == quota.key && Objects.equals(queue, quota.queue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, queue);
    }

    @Override
    public void close() {
        try {
            // utilization should be sync'ed
            lock.lock();
            units.shutdown();
        } finally {
            lock.unlock();
        }
    }

}
