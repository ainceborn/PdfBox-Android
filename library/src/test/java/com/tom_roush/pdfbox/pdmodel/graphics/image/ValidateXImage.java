package com.tom_roush.pdfbox.pdmodel.graphics.image;

import android.graphics.Bitmap;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class ValidateXImage {

    /**
     * Compare two Android Bitmaps pixel by pixel.
     * Throws an assertion failure if any pixel differs.
     */
    public static void checkIdent(Bitmap expectedImage, Bitmap actualImage) {
        if (expectedImage == null || actualImage == null) {
            fail("One or both bitmaps are null");
        }

        int w = expectedImage.getWidth();
        int h = expectedImage.getHeight();

        assertEquals("Widths differ", w, actualImage.getWidth());
        assertEquals("Heights differ", h, actualImage.getHeight());

        int[] expectedPixels = new int[w * h];
        int[] actualPixels = new int[w * h];

        expectedImage.getPixels(expectedPixels, 0, w, 0, 0, w, h);
        actualImage.getPixels(actualPixels, 0, w, 0, 0, w, h);

        for (int i = 0; i < expectedPixels.length; i++) {
            if (expectedPixels[i] != actualPixels[i]) {
                int x = i % w;
                int y = i / w;
                String errMsg = String.format(
                        "(%d,%d) expected: <%08X> but was: <%08X>",
                        x, y, expectedPixels[i], actualPixels[i]
                );
                fail(errMsg);
            }
        }
    }

    public static void assertBitmapEquals(Bitmap expected, Bitmap actual) {
        assert expected.getWidth() == actual.getWidth() : "Width mismatch";
        assert expected.getHeight() == actual.getHeight() : "Height mismatch";

        int width = expected.getWidth();
        int height = expected.getHeight();
        int size = width * height;

        int[] expectedPixels = new int[size];
        int[] actualPixels = new int[size];

        expected.getPixels(expectedPixels, 0, width, 0, 0, width, height);
        actual.getPixels(actualPixels, 0, width, 0, 0, width, height);

        assertArrayEquals(expectedPixels, actualPixels);
    }
}