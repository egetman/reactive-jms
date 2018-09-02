package com.github.egetman.source;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

import com.github.egetman.etc.Pool;
import com.github.egetman.etc.PoolFactory;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@SuppressWarnings("unused")
public class UnicastJmsQueueSource<T> implements Source<T> {

    private String user;
    private String password;
    private boolean transacted;

    private final String queue;
    private final ConnectionFactory factory;
    private final Function<Message, T> function;

    private final Map<Integer, JmsQuota<T>> pool = new ConcurrentHashMap<>();

    public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                                  @Nonnull String queue) {
        this.queue = Objects.requireNonNull(queue, "Queue name ust not be null");
        this.factory = Objects.requireNonNull(factory, "Factory must not be null");
        this.function = Objects.requireNonNull(function, "Function must not be null");
    }

    public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                                 @Nonnull String queue, String user, String password, boolean transacted) {
        this(factory, function, queue);
        this.user = user;
        this.password = password;
        this.transacted = transacted;
    }

    @Nonnull
    @Override
    public CloseableIterator<T> iterator(int key) {
        return pool.computeIfAbsent(key, value -> new JmsQuota<>(key, function, factory, queue, user, password,
                transacted));
    }

    /**
     * Transacted passed inside the {@link JmsQuota} cause it could be changed for source later, but not for already
     * created {@link JmsQuota}.
     */
    @Slf4j
    @SuppressWarnings("unused")
    private static class JmsQuota<T> implements CloseableIterator<T> {

        private static final int POOL_SIZE = 3;
        private static final int RETRY_TIME_SECONDS = 30;

        private final int key;
        private final String queue;
        private final boolean transacted;

        private final Pool<JmsUnit> units;
        private final Function<Message, T> function;
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        private JmsQuota(int key, @Nonnull Function<Message, T> function, @Nonnull ConnectionFactory factory,
                         @Nonnull String queue, String user, String password, boolean transacted) {
            try {
                // initialization should be sync'ed
                lock.writeLock().lock();
                this.key = key;
                this.queue = queue;
                this.function = function;
                this.transacted = transacted;

                units = newPool(factory, user, password, queue, transacted);
            } finally {
                lock.writeLock().unlock();
            }
        }

        Pool<JmsUnit> newPool(ConnectionFactory factory, String user, String password, String queue, boolean tx) {

            Supplier<JmsUnit> supplier = () -> {
                Connection connection;
                try {
                    // password could be missing?
                    if (user != null) {
                        connection = factory.createConnection(user, password);
                    } else {
                        connection = factory.createConnection();
                    }
                    return new JmsUnit(connection, queue, tx);
                } catch (JMSException e) {
                    log.error("Exception during pool connection initialization: ", e.getMessage());
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

        @Override
        public T next() {
            // iterator contract
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return next(units.get());
        }

        // we need release eagerly sometimes cause of recursion. There could be case, when we will call release twice
        // without some checks - in that case poll will became boundless, and it's not good.
        // in other case, you can't remove the release call from finally, because it can cause pool starvation.
        private T next(JmsUnit unit) {
            boolean released = false;
            try {
                return function.apply(unit.consumer().receive());
            } catch (Exception ex) {
                log.error("Exception during message receiving for {}: {}", this, ex.getMessage());
                log.warn("Prepare to recover session {}", this);
                try {
                    if (!recover(unit)) {
                        // return failed object and wait for some time to retry
                        released = release(unit);
                        return next();
                    }
                    return function.apply(unit.consumer().receive());
                } catch (Exception e) {
                    log.warn("Session recovery failed for {} with cause {}. Releasing unit", this, e.getMessage());
                    released = release(unit);
                    return next();
                }
            } finally {
                if (!released) {
                    units.release(unit);
                }
            }
        }

        private boolean release(JmsUnit unit) {
            unit.fail();
            units.release(unit);
            wait(RETRY_TIME_SECONDS, TimeUnit.SECONDS);
            return true;
        }

        private boolean recover(JmsUnit unit) {
            try {
                // try to recover with exclusive lock
                lock.writeLock().lock();
                unit.session().recover();
                return true;
            } catch (JMSException e) {
                log.error("Recovery failed for {}", this);
                return false;
            } finally {
                lock.writeLock().unlock();
            }
        }

        @SuppressWarnings("SameParameterValue")
        void wait(long time, TimeUnit timeUnit) {
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
                //noinspection StatementWithEmptyBody
                while (!lock.writeLock().tryLock()) {
                    // do nothing
                }
                units.shutdown();
            } finally {
                lock.writeLock().unlock();
            }
        }

        private static class JmsUnit implements AutoCloseable {

            private final AtomicBoolean failed = new AtomicBoolean();
            private final Lock initializationLock = new ReentrantLock();
            private final AtomicBoolean initialized = new AtomicBoolean();

            // one session and consumer per connection
            private Session session;
            private MessageConsumer consumer;
            private final Connection connection;

            private final Supplier<Session> sessionSupplier;
            private final Function<Session, MessageConsumer> sessionToConsumer;

            JmsUnit(Connection connection, String queue, boolean transacted) {
                this.connection = connection;

                this.sessionSupplier = () -> {
                    try {
                        return connection.createSession(transacted, Session.AUTO_ACKNOWLEDGE);
                    } catch (JMSException e) {
                        log.error("Exception during session creation: ", e.getMessage());
                        throw new IllegalStateException(e);
                    }
                };
                this.sessionToConsumer = currentSession -> {
                    try {
                        return currentSession.createConsumer(currentSession.createQueue(queue));
                    } catch (JMSException e) {
                        log.error("Exception during consumer creation: ", e.getMessage());
                        throw new IllegalStateException(e);
                    }
                };
            }

            /**
             * It's ok to sync this method. Called just ones.
             */
            private void initialize() {
                try {
                    initializationLock.lock();
                    if (initialized.get()) {
                        // check if already initialized
                        return;
                    }
                    session = sessionSupplier.get();
                    consumer = sessionToConsumer.apply(session);
                    try {
                        connection.start();
                        log.debug("Unit {} successfully initialized", super.hashCode());
                    } catch (JMSException e) {
                        log.error("Exception during connection start: {}", e.getMessage());
                        throw new IllegalStateException(e);
                    }
                    initialized.set(true);
                } finally {
                    initializationLock.unlock();
                }
            }

            Session session() {
                while (!initialized.get()) {
                    initialize();
                }
                return session;
            }

            MessageConsumer consumer() {
                while (!initialized.get()) {
                    initialize();
                }
                return consumer;
            }

            void fail() {
                failed.set(true);
            }

            boolean isFailed() {
                return failed.get();
            }

            @Override
            public void close() {
                try {
                    closeResources(consumer, session, connection);
                } catch (Exception e) {
                    log.error("Exception during resource close: " + e.getMessage());
                }
            }

            /**
             * Closes all resources.
             *
             * @param resources - resources to close.
             */
            @SuppressWarnings("unused")
            private static void closeResources(AutoCloseable... resources) {
                Exception toThrowUp = null;
                for (AutoCloseable resource : resources) {
                    toThrowUp = closeAndReturnException(resource, toThrowUp);
                }
                throwIfNonNull(toThrowUp);
            }

            private static Exception closeAndReturnException(AutoCloseable closeable, Exception thrown) {
                if (closeable != null) {
                    try {
                        closeable.close();
                    } catch (Exception cause) {
                        if (thrown != null) {
                            thrown.addSuppressed(cause);
                        } else {
                            return cause;
                        }
                    }
                }
                return thrown;
            }

            @SneakyThrows
            private static void throwIfNonNull(Exception exception) {
                if (exception != null) {
                    throw exception;
                }
            }
        }
    }
}
