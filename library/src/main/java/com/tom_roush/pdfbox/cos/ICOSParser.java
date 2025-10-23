package com.tom_roush.pdfbox.cos;

import com.tom_roush.pdfbox.io.RandomAccessReadView;

import java.io.IOException;

public interface ICOSParser
{

    /**
     * Dereference the COSBase object which is referenced by the given COSObject.
     * 
     * @param obj the COSObject which references the COSBase object to be dereferenced.
     * @return the referenced object
     * @throws IOException if something went wrong when dereferencing the COSBase object
     */
    COSBase dereferenceCOSObject(COSObject obj) throws IOException;

    /**
     * Creates a random access read view starting at the given position with the given length.
     * 
     * @param startPosition start position within the underlying random access read
     * @param streamLength stream length
     * @return the random access read view
     * @throws IOException if something went wrong when creating the view for the RandomAccessRead
     */
    RandomAccessReadView createRandomAccessReadView(long startPosition, long streamLength)
            throws IOException;

}