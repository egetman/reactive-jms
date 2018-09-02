package com.github.egetman.source;

import java.util.Iterator;

/**
 * An iterator over a data.
 * It could be manually closed as it's implement {@link AutoCloseable}.
 *
 * @param <E> is type of elements returned by this iterator.
 */
@SuppressWarnings("WeakerAccess")
public interface CloseableIterator<E> extends Iterator<E>, AutoCloseable {

}
