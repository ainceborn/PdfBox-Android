package com.tom_roush.pdfbox.io;

import java.io.IOException;

/**
 * A default implementation of the interface RandomAccessStreamCache using a memory backed RandomAccessReadWriteBuffer.
 */
public class RandomAccessStreamCacheImpl implements RandomAccessStreamCache
{

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException
    {
        // nothing to do
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RandomAccess createBuffer() throws IOException
    {
        return new RandomAccessReadWriteBuffer();
    }

}