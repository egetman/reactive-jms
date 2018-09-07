package com.github.egetman.etc;


import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Factory and utility methods for
 * {@link Pool} and {@link BlockingPool} classes
 * defined in this package.
 * This class supports the following kinds of methods:
 *
 * <ul>
 * <li> Method that creates and returns a default non-blocking
 * implementation of the {@link Pool} interface.
 * </li>
 *
 * <li> Method that creates and returns a
 * default implementation of
 * the {@link BlockingPool} interface.
 * </li>
 * </ul>
 */
@SuppressWarnings("unused")
public interface PoolFactory {

    /**
     * Creates a and returns a new object pool,
     * that is an implementation of the {@link BlockingPool},
     * whose size is limited by
     * the <tt> size </tt> parameter.
     *
     * @param size      the number of objects in the pool.
     * @param factory   the factory to create new objects.
     * @param validator the validator to
     *                  validate the re-usability of returned objects.
     * @param cleaner   the cleaner to clean up the resources. (optional - may be null)
     * @param <T>       type of elements in the pool.
     * @return a blocking object pool bounded by <tt> size </tt>
     */
    @Nonnull
    static <T> Pool<T> newBoundedBlockingPool(int size, @Nonnull Supplier<T> factory, @Nonnull Predicate<T> validator,
                                              @Nullable Consumer<T> cleaner) {
        return new BoundedBlockingPool<>(size, validator, factory, cleaner);
    }

    /**
     * Creates a and returns a new object pool,
     * that is an implementation of the {@link BlockingPool},
     * whose size is limited by
     * the <tt> size </tt> parameter.
     *
     * @param size    the number of objects in the pool.
     * @param factory the factory to create new objects.
     * @param <T>       type of elements in the pool.
     * @return a blocking object pool bounded by <tt> size </tt>
     */
    @Nonnull
    static <T> Pool<T> newBoundedBlockingPool(int size, @Nonnull Supplier<T> factory) {
        return new BoundedBlockingPool<>(size, Objects::nonNull, factory, null);
    }

}
