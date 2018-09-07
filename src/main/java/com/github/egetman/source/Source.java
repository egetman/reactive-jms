package com.github.egetman.source;

import java.util.Iterator;
import javax.annotation.Nonnull;

/**
 * Abstraction of elements source, that could give an {@link Iterator} for that source.
 *
 * @param <E> type of elements returned by this source.
 */
public interface Source<E> {

    /**
     * Return iterator for given key.
     * Source should create new iterator for given {@code key} and cache it.
     * Otherwise it could be inconsistent state of the Publisher, that uses that source.
     * Note: if source supports concurrent processing, it should synchronize correctly data access.
     *
     * @param key uniq key to obtain iterator instance.
     * @return {@link CloseableIterator}.
     */
    @Nonnull
    CloseableIterator<E> iterator(int key);

}
