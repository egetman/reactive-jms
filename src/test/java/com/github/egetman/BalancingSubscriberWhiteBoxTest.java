package com.github.egetman;

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

    private BalancingSubscriber<Integer> balancingSubscriber;

    public BalancingSubscriberWhiteBoxTest() {
        super(new TestEnvironment(true));
    }

    @Override
    public Subscriber<Integer> createSubscriber(WhiteboxSubscriberProbe<Integer> probe) {
        balancingSubscriber = new BalancingSubscriber<Integer>(i -> log.info("{}", i)) {

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
