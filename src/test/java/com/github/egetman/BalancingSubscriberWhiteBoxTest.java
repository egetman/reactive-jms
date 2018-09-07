package com.github.egetman;

import com.github.egetman.barrier.OpenBarrier;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.reactivestreams.tck.SubscriberWhiteboxVerification;
import org.reactivestreams.tck.TestEnvironment;
import org.testng.annotations.AfterMethod;

import lombok.extern.slf4j.Slf4j;

/**
 * TCK {@link Subscriber} white box verification.
 */
@Slf4j
public class BalancingSubscriberWhiteBoxTest extends SubscriberWhiteboxVerification<Integer> {

    private static final int BATCH_SIZE = 100;
    private static final int POLL_INTERVAL = 1;
    private static final int DEFAULT_TIMEOUT_MILLIS = 300;

    private BalancingSubscriber<Integer> subscriber;

    public BalancingSubscriberWhiteBoxTest() {
        super(new TestEnvironment(DEFAULT_TIMEOUT_MILLIS, DEFAULT_TIMEOUT_MILLIS, true));
    }

    @Override
    public Subscriber<Integer> createSubscriber(WhiteboxSubscriberProbe<Integer> probe) {
        subscriber = new BalancingSubscriber<Integer>(i -> log.info("{}", i), new OpenBarrier(), BATCH_SIZE, POLL_INTERVAL) {

            @Override
            public void onSubscribe(Subscription subscription) {
                super.onSubscribe(subscription);
                probe.registerOnSubscribe(new SubscriberPuppet() {

                    @Override
                    public void triggerRequest(long elements) {
                        subscription.request(elements);
                    }

                    @Override
                    public void signalCancel() {
                        subscription.cancel();
                    }
                });
            }

            @Override
            public void onNext(Integer next) {
                super.onNext(next);
                probe.registerOnNext(next);
            }

            @Override
            public void onError(Throwable throwable) {
                super.onError(throwable);
                probe.registerOnError(throwable);
            }

            @Override
            public void onComplete() {
                super.onComplete();
                probe.registerOnComplete();
            }
        };
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
