package com.github.egetman;

import java.util.function.Function;
import java.util.stream.IntStream;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.github.egetman.source.Source;
import com.github.egetman.source.UnicastJmsQueueSource;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterMethod;

import lombok.extern.slf4j.Slf4j;

import static java.lang.String.*;

/**
 * TCK {@link Publisher} verification.
 */
@Slf4j
public abstract class UnicastJmsQueueColdPublisherTest extends PublisherVerification<String> {

    private static final int MESSAGES_IN_QUEUE_SIZE = 10;
    private static final int DEFAULT_TIMEOUT_MILLIS = 300;

    private static final String QUEUE = "queue";
    private static final String BROKER_URL = "vm://localhost?broker.persistent=false";
    private static final Function<Message, String> MESSAGE_TO_STRING = message -> {
        try {
            return ((TextMessage) message).getText();
        } catch (Exception e) {
            log.error("Exception during message transformation", e);
            throw new IllegalStateException(e);
        }
    };

    private final int acknowledgeMode;
    private ActiveMQConnectionFactory factory;
    private ColdPublisher<String> coldPublisher;

    @AfterMethod
    public void shutdown() {
        if (coldPublisher != null) {
            log.debug("Closing {}", coldPublisher);
            coldPublisher.close();
        }
    }

    UnicastJmsQueueColdPublisherTest(int acknowledgeMode) {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, true));
        this.acknowledgeMode = acknowledgeMode;
        // set prefetch to 1, so every consumer can receive some messages.
        factory = new ActiveMQConnectionFactory(BROKER_URL);
        factory.getPrefetchPolicy().setAll(1);
    }

    @Override
    public Publisher<String> createPublisher(long elements) {
        log.debug("Requested {} elements", elements);
        try (final Connection connection = factory.createConnection()) {
            connection.start();
            try (final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)) {
                final Queue queue = session.createQueue(QUEUE);
                try (final MessageProducer producer = session.createProducer(queue)) {
                    // add 10 messages into queue for each test
                    IntStream.range(0, MESSAGES_IN_QUEUE_SIZE).forEach(element -> {
                        try {
                            producer.send(session.createTextMessage(valueOf(element)));
                        } catch (JMSException e) {
                            log.error("Failed to send {} element into {}", element, QUEUE);
                        }
                    });
                }
                log.debug("{} mesages sent into {}", elements, QUEUE);

            }
        } catch (Exception e) {
            log.error("Exception on publisher creation", e);
        }
        final Source<String> source = new UnicastJmsQueueSource<>(factory, MESSAGE_TO_STRING, QUEUE, "u1", null, true,
                acknowledgeMode);
        coldPublisher = new ColdPublisher<>(source);
        return coldPublisher;
    }

    @Override
    public Publisher<String> createFailedPublisher() {
        return null;
    }

    @Override
    public long maxElementsFromPublisher() {
        // unbounded one
        return Long.MAX_VALUE;
    }

}
