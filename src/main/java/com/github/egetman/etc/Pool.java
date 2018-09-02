package com.github.egetman.etc;

/**
 * Represents a cached pool of objects.
 *
 * @param <T> the type of object to pool.
 */
@SuppressWarnings("unused")
public interface Pool<T> {

    /**
     * Returns an instance from the pool.
     * The call may be a blocking one or a non-blocking one
     * and that is determined by the internal implementation.
     *
     * <p>If the call is a blocking call,
     * the call returns immediately with a valid object
     * if available, else the thread is made to wait
     * until an object becomes available.
     * In case of a blocking call,
     * it is advised that clients react
     * to {@link InterruptedException} which might be thrown
     * when the thread waits for an object to become available.
     *
     * <p>If the call is a non-blocking one,
     * the call returns immediately irrespective of
     * whether an object is available or not.
     * If any object is available the call returns it
     * else the call returns <code>null</code>.
     *
     * <p>The validity of the objects are determined using the
     * {@link java.util.function.Predicate} interface, such that
     * an object <code>o</code> is valid if
     * <code> Predicate.test(o) == true </code >.
     *
     * @return T one of the pooled objects.
     */
    T get();

    /**
     * Releases the object and puts it back to the pool.
     *
     * <p>The mechanism of putting the object back to the pool is
     * generally asynchronous,
     * however future implementations might differ.
     *
     * @param object the object to return to the pool
     */

    void release(T object);

    /**
     * Shuts down the pool. In essence this call will not
     * accept any more requests
     * and will release all resources.
     */

    void shutdown();

}
