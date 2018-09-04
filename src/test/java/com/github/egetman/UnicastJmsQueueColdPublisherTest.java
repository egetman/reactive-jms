package com.github.egetman;

import java.util.function.Function;
import java.util.stream.LongStream;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.github.egetman.source.UnicastJmsQueueSource;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterMethod;

import lombok.extern.slf4j.Slf4j;

/**
 * TCK {@link Publisher} verification.
 */
@Slf4j
public class UnicastJmsQueueColdPublisherTest extends PublisherVerification<String> {

    private static final String QUEUE_NAME = "queue";
    private static final String BROKER_URL = "vm://localhost?broker.persistent=false";
    private static final Function<Message, String> MESSAGE_TO_STRING = message -> {
        try {
            return ((TextMessage) message).getText();
        } catch (Exception e) {
            log.error("Exception during message transformation", e);
            throw new IllegalStateException(e);
        }
    };

    private ActiveMQConnectionFactory factory;
    private ColdPublisher<String> coldPublisher;

    @AfterMethod
    public void shutdown() {
        if (coldPublisher != null) {
            log.debug("Closing {}", coldPublisher);
            coldPublisher.close();
        }
    }

    public UnicastJmsQueueColdPublisherTest() {
        super(new TestEnvironment(200, 200, true));
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
                final Queue queue = session.createQueue(QUEUE_NAME);
                try (final MessageProducer producer = session.createProducer(queue)) {
                    // add 10 messages into queue for each test
                    LongStream.range(0, 10).forEach(element -> {
                        try {
                            producer.send(session.createTextMessage(String.valueOf(element)));
                        } catch (JMSException e) {
                            log.error("Failed to send {} element into {}", element, QUEUE_NAME);
                        }
                    });
                }
                log.debug("{} mesages sent into {}", elements, QUEUE_NAME);

            }
        } catch (Exception e) {
            log.error("Exception on publisher creation", e);
        }
        coldPublisher = new ColdPublisher<>(new UnicastJmsQueueSource<>(factory, MESSAGE_TO_STRING, QUEUE_NAME));
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
