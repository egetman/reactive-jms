package com.github.egetman.barrier;

/**
 * Interface used to limit throughput of the {@link org.reactivestreams.Subscriber} impl.
 */
public interface Barrier {

    /**
     * Indicates that barrier is open, i.e. it possible to demand new elements.
     * If {@link Barrier} is closed (isOpen() return {@code false}) new elements requests should be avoided.
     * Implementations may rely on this mechanism, or may not.
     *
     * @return true if it's possible to demand new elements.
     */
    boolean isOpen();

}
