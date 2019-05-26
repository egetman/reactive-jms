package com.github.egetman.source;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.jms.Connection;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TransactionInProgressException;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import static java.util.Arrays.asList;
import static javax.jms.Session.AUTO_ACKNOWLEDGE;
import static javax.jms.Session.CLIENT_ACKNOWLEDGE;

@Slf4j
class JmsUnit implements AutoCloseable {

    private static final Set<Integer> ALLOWED_ACKNOWLEDGE_MODES = new HashSet<>(asList(AUTO_ACKNOWLEDGE,
            CLIENT_ACKNOWLEDGE));

    private final AtomicBoolean failed = new AtomicBoolean();
    private final Lock initializationLock = new ReentrantLock();
    private final AtomicBoolean initialized = new AtomicBoolean();

    // one session and consumer per connection
    private Session session;
    private MessageConsumer consumer;
    private final boolean transacted;
    private final Connection connection;

    private final Supplier<Session> sessionSupplier;
    private final Function<Session, MessageConsumer> sessionToConsumer;

    JmsUnit(@Nonnull Connection connection, @Nonnull String queue, boolean transacted, int acknowledgeMode) {
        this.connection = connection;
        this.transacted = transacted;
        if (!ALLOWED_ACKNOWLEDGE_MODES.contains(acknowledgeMode)) {
            throw new IllegalArgumentException("Acknowledge mode " + acknowledgeMode + " not supported");
        }

        this.sessionSupplier = () -> {
            try {
                return connection.createSession(transacted, acknowledgeMode);
            } catch (JMSException e) {
                log.error("Exception during session creation: {}", e.getMessage());
                throw new java.lang.IllegalStateException(e);
            }
        };
        this.sessionToConsumer = currentSession -> {
            try {
                return currentSession.createConsumer(currentSession.createQueue(queue));
            } catch (JMSException e) {
                log.error("Exception during consumer creation: {}", e.getMessage());
                throw new java.lang.IllegalStateException(e);
            }
        };
    }

    /**
     * It's ok to sync this method. Called just ones.
     */
    @SneakyThrows
    private void initialize() {
        try {
            initializationLock.lock();
            if (initialized.get()) {
                // check if already initialized
                return;
            }
            session = sessionSupplier.get();
            consumer = sessionToConsumer.apply(session);

            connection.start();
            log.debug("Unit {} successfully initialized", super.hashCode());
            initialized.set(true);
        } finally {
            initializationLock.unlock();
        }
    }

    @Nonnull
    private Session session() {
        waitUntilInitialized();
        return session;
    }

    @Nonnull
    private MessageConsumer consumer() {
        waitUntilInitialized();
        return consumer;
    }

    @SneakyThrows
    Message receive() {
        final Message message = consumer().receive();
        if (transacted && session().getTransacted()) {
            try {
                session().commit();
            } catch (IllegalStateException | TransactionInProgressException e) {
                log.trace("Can't commit. Possible JTA transaction:", e);
            }
        }
        return message;
    }

    @SneakyThrows
    void recover() {
        session().recover();
    }

    private void waitUntilInitialized() {
        if (!initialized.get()) {
            initialize();
        }
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
    private void closeResources(@Nonnull AutoCloseable... resources) {
        Exception toThrowUp = null;
        for (AutoCloseable resource : resources) {
            toThrowUp = closeAndReturnException(resource, toThrowUp);
        }
        throwIfNonNull(toThrowUp);
    }

    private Exception closeAndReturnException(AutoCloseable closeable, Exception thrown) {
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

