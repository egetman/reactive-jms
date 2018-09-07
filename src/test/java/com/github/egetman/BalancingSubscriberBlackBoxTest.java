package com.github.egetman;

import com.github.egetman.barrier.OpenBarrier;

import org.reactivestreams.Subscriber;
import org.reactivestreams.tck.SubscriberBlackboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterMethod;

import lombok.extern.slf4j.Slf4j;

/**
 * TCK {@link Subscriber} black box verification.
 */
@Slf4j
public class BalancingSubscriberBlackBoxTest extends SubscriberBlackboxVerification<Integer> {

    private static final int BATCH_SIZE = 100;
    private static final int POLL_INTERVAL = 1;
    private static final int DEFAULT_TIMEOUT_MILLIS = 300;

    private BalancingSubscriber<Integer> subscriber;

    public BalancingSubscriberBlackBoxTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, true));
    }

    @Override
    public Subscriber<Integer> createSubscriber() {
        subscriber = new BalancingSubscriber<>(i -> log.info("{}", i), new OpenBarrier(), BATCH_SIZE, POLL_INTERVAL);
        return subscriber;
    }

    @Override
    public Integer createElement(int element) {
        return element;
    }

    @AfterMethod
    private void shutdown() {
        if (subscriber != null) {
            log.debug("Closing {}", subscriber);
            subscriber.close();
        }
    }

}
