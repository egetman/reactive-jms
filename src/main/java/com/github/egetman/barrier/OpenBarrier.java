package com.github.egetman.barrier;

/**
 * Simple {@link Barrier} implementation that always return {@code true} on {@link Barrier#isOpen()}.
 */
public class OpenBarrier implements Barrier {

    @Override
    public boolean isOpen() {
        return true;
    }
}
