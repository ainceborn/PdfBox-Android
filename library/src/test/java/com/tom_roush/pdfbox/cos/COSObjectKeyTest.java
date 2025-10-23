/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tom_roush.pdfbox.cos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.graphics.Bitmap;

import com.tom_roush.pdfbox.Loader;
import com.tom_roush.pdfbox.multipdf.Splitter;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.pdmodel.graphics.image.ValidateXImage;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.tools.FileTools;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class COSObjectKeyTest
{
    @Test
    public void testInputValues()
    {
        try
        {
            new COSObjectKey(-1L, 0);
            fail("An IllegalArgumentException shouzld have been thrown");
        }
        catch (IllegalArgumentException exception)
        {

        }

        try
        {
            new COSObjectKey(1L, -1);
            fail("An IllegalArgumentException shouzld have been thrown");
        }
        catch (IllegalArgumentException exception)
        {

        }
    }

    @Test
    public void compareToInputNotNullOutputZero()
    {
        // Arrange
        final COSObjectKey objectUnderTest = new COSObjectKey(1L, 0);
        final COSObjectKey other = new COSObjectKey(1L, 0);

        // Act
        final int retval = objectUnderTest.compareTo(other);

        // Assert result
        assertEquals(0, retval);
    }

    @Test
    public void compareToInputNotNullOutputNotNull()
    {
        // Arrange
        final COSObjectKey objectUnderTest = new COSObjectKey(1L, 0);
        final COSObjectKey other = new COSObjectKey(9_999_999L, 0);

        // Act
        final int retvalNegative = objectUnderTest.compareTo(other);
        final int retvalPositive = other.compareTo(objectUnderTest);

        // Assert results
        assertEquals(-1, retvalNegative);
        assertEquals(1, retvalPositive);
    }

    @Test
    public void testEquals()
    {
        assertEquals(new COSObjectKey(100, 0), new COSObjectKey(100, 0));
        assertNotEquals(new COSObjectKey(100, 0), new COSObjectKey(101, 0));
    }

    @Test
    public void testInternalRepresentation()
    {
        COSObjectKey key = new COSObjectKey(100, 0);
        assertEquals(100, key.getNumber());
        assertEquals(0, key.getGeneration());

        key = new COSObjectKey(200, 4);
        assertEquals(200, key.getNumber());
        assertEquals(4, key.getGeneration());

        key = new COSObjectKey(200000, 0);
        assertEquals(200000, key.getNumber());
        assertEquals(0, key.getGeneration());

        key = new COSObjectKey(87654321, 123);
        assertEquals(87654321, key.getNumber());
        assertEquals(123, key.getGeneration());
    }

    @Test
    public void testSortingOrder()
    {
        // comparison is done by comparing the object numbers first
        // if they are equal the generation numbers are taken into account
        COSObjectKey key40 = new COSObjectKey(4, 0);
        COSObjectKey key41 = new COSObjectKey(4, 1);
        COSObjectKey key50 = new COSObjectKey(5, 0);

        assertEquals(0, key40.compareTo(key40));
        assertEquals(0, key41.compareTo(key41));
        assertEquals(-1, key40.compareTo(key41));
        assertEquals(-1, key40.compareTo(key50));
        assertEquals(-1, key41.compareTo(key50));
    }

    @Test
    public void checkHashCode()
    {
        // same object number 100 0
        assertEquals(new COSObjectKey(100, 0).hashCode(),
                new COSObjectKey(100, 0).hashCode());

        // different object numbers/same generation numbers 100 0 vs. 200 0
        assertNotEquals(new COSObjectKey(100, 0).hashCode(),
                new COSObjectKey(200, 0).hashCode());

        // different object numbers/different generation numbers/ sum of both numbers are equal 100 0 vs. 99 1
        assertNotEquals(new COSObjectKey(100, 0).hashCode(),
                new COSObjectKey(99, 1).hashCode());
    }

    /**
     * PDFBOX-5742: split and then check that renderings are identical. This is a test of the
     * changes with handling indirect objects in COSArray, COSDictionary and COSParser.
     */
    @Test
    public void testPDFBox5742() throws IOException
    {
        ByteArrayOutputStream baos1 = new ByteArrayOutputStream();
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        Bitmap bim1orig;
        Bitmap bim2orig;

        var pdfbox5742File = FileTools.getInternetFile("https://issues.apache.org/jira/secure/attachment/13065529/source.pdf", "PDFBOX-5742.pdf");

        try (PDDocument doc = Loader.loadPDF(pdfbox5742File))
        {
            PDFRenderer renderer = new PDFRenderer(doc);
            bim1orig = renderer.renderImage(0);
            bim2orig = renderer.renderImage(1);
            Splitter splitter = new Splitter();
            List<PDDocument> splits = splitter.split(doc);
            assertEquals(2, splits.size());
            try (PDDocument doc1 = splits.get(0);
                 PDDocument doc2 = splits.get(1))
            {
                doc1.save(baos1);
                doc2.save(baos2);
            }
        }
        try (PDDocument doc1 = Loader.loadPDF(baos1.toByteArray());
             PDDocument doc2 = Loader.loadPDF(baos2.toByteArray()))
        {
            assertEquals(1, doc1.getNumberOfPages());
            assertEquals(1, doc2.getNumberOfPages());
            PDFRenderer renderer1 = new PDFRenderer(doc1);
            PDFRenderer renderer2 = new PDFRenderer(doc2);
            Bitmap bim1new = renderer1.renderImage(0);
            Bitmap bim2new = renderer2.renderImage(0);

            ValidateXImage.checkIdent(bim1orig, bim1new);
            ValidateXImage.checkIdent(bim2orig, bim2new);
        }
    }
}
