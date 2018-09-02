package com.github.egetman.etc;

/**
 * Represents an abstract pool, that defines the procedure
 * of returning an object to the pool.
 *
 * @param <T> the type of pooled objects.
 */
abstract class AbstractPool<T> implements Pool<T> {

    /**
     * Returns the object to the pool.
     * The method first validates the object if it is
     * re-usable and then puts returns it to the pool.
     *
     * <p>If the object validation fails,
     * some implementations
     * will try to create a new one
     * and put it into the pool; however
     * this behaviour is subject to change
     * from implementation to implementation
     */
    @Override
    public final void release(T object) {
        if (isValid(object)) {
            returnToPool(object);
        } else {
            handleInvalidReturn(object);
        }
    }

    protected abstract void handleInvalidReturn(T object);

    protected abstract void returnToPool(T object);

    protected abstract boolean isValid(T object);
}