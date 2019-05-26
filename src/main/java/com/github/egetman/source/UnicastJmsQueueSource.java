package com.github.egetman.source;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.Session;

public class UnicastJmsQueueSource<T> implements Source<T> {

    private boolean tx;
    private String user;
    private String password;

    private final String queue;
    private final int acknowledgeMode;
    private final ConnectionFactory factory;
    private final Function<Message, T> function;

    private final Map<Integer, JmsQuota<T>> pool = new ConcurrentHashMap<>();

    public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                                 @Nonnull String queue) {
        this(factory, function, queue, null, null, false, Session.AUTO_ACKNOWLEDGE);
    }

    public UnicastJmsQueueSource(@Nonnull ConnectionFactory factory, @Nonnull Function<Message, T> function,
                                 @Nonnull String queue, String user, String password, boolean transacted,
                                 int acknowledgeMode) {
        this.queue = Objects.requireNonNull(queue, "Queue name ust not be null");
        this.factory = Objects.requireNonNull(factory, "Factory must not be null");
        this.function = Objects.requireNonNull(function, "Function must not be null");

        this.user = user;
        this.password = password;

        this.tx = transacted;
        this.acknowledgeMode = acknowledgeMode;
    }

    /**
     * {@inheritDoc}.
     *
     * @param key uniq key to obtain iterator instance.
     * @return cached {@link JmsQuota} instance.
     */
    @Nonnull
    @Override
    public CloseableIterator<T> iterator(int key) {
        return pool.computeIfAbsent(key,
                value -> new JmsQuota<>(key, function, factory, queue, user, password, tx, acknowledgeMode));
    }

}
