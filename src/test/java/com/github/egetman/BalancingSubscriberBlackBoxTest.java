package com.github.egetman;

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

    private BalancingSubscriber<Integer> balancingSubscriber;

    public BalancingSubscriberBlackBoxTest() {
        super(new TestEnvironment(true));
    }

    @Override
    public Subscriber<Integer> createSubscriber() {
        balancingSubscriber = new BalancingSubscriber<>(i -> log.info("{}", i));
        return balancingSubscriber;
    }

    @Override
    public Integer createElement(int element) {
        return element;
    }

    @AfterMethod
    private void shutdown() {
        if (balancingSubscriber != null) {
            log.debug("Closing {}", balancingSubscriber);
            balancingSubscriber.close();
        }
    }

}
