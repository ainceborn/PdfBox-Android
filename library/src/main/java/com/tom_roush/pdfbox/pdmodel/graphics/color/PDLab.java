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
package com.tom_roush.pdfbox.pdmodel.graphics.color;

import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSFloat;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.common.PDRange;

import java.io.IOException;

/**
 * A Lab colour space is a CIE-based ABC colour space with two transformation stages.
 *
 * @author Ben Litchfield
 * @author John Hewson
 */
public final class PDLab extends PDCIEDictionaryBasedColorSpace
{
    private PDColor initialColor;
    
    /**
     * Creates a new Lab color space.
     */
    public PDLab()
    {
        super(COSName.LAB);
    }

    /**
     * Creates a new Lab color space from a PDF array.
     * @param lab the color space array
     */
    public PDLab(COSArray lab)
    {
        super(lab);
    }
    
    @Override
    public String getName()
    {
        return COSName.LAB.getName();
    }

    //
    // WARNING: this method is performance sensitive, modify with care!
    //
    // java
    @Override
    public android.graphics.Bitmap toRGBImage(android.graphics.Bitmap raster) throws java.io.IOException {
        if (raster == null) {
            return null;
        }

        final int width = raster.getWidth();
        final int height = raster.getHeight();

        android.graphics.Bitmap rgbBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);

        int[] inRow = new int[width];
        int[] outRow = new int[width];

        PDRange aRange = getARange();
        PDRange bRange = getBRange();
        float minA = aRange.getMin();
        float maxA = aRange.getMax();
        float minB = bRange.getMin();
        float maxB = bRange.getMax();
        float deltaA = maxA - minA;
        float deltaB = maxB - minB;

        float[] abc = new float[3];
        for (int y = 0; y < height; y++) {
            raster.getPixels(inRow, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int px = inRow[x];

                // extract 0..255 components from input pixel
                int rIn = android.graphics.Color.red(px);
                int gIn = android.graphics.Color.green(px);
                int bIn = android.graphics.Color.blue(px);

                // 0..255 -> 0..1 then scale to Lab ranges used in original code
                abc[0] = (rIn / 255f) * 100f;                 // L* in 0..100
                abc[1] = minA + (gIn / 255f) * deltaA;       // a in [minA..maxA]
                abc[2] = minB + (bIn / 255f) * deltaB;       // b in [minB..maxB]

                float[] rgb = toRGB(abc); // returns 0..1 floats

                int rr = clamp8((int) (rgb[0] * 255f));
                int gg = clamp8((int) (rgb[1] * 255f));
                int bb = clamp8((int) (rgb[2] * 255f));

                outRow[x] = android.graphics.Color.argb(255, rr, gg, bb);
            }
            rgbBitmap.setPixels(outRow, 0, width, 0, y, width, 1);
        }

        return rgbBitmap;
    }

    private static int clamp8(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }


    @Override
    public float[] toRGB(float[] value)
    {
        // CIE LAB to RGB, see http://en.wikipedia.org/wiki/Lab_color_space

        // L*
        float lstar = (value[0] + 16f) * (1f / 116f);

        // TODO: how to use the blackpoint? scale linearly between black & white?

        // XYZ
        float x = wpX * inverse(lstar + value[1] * (1f / 500f));
        float y = wpY * inverse(lstar);
        float z = wpZ * inverse(lstar - value[2] * (1f / 200f));
        
        return convXYZtoRGB(x, y, z);
    }

    // reverse transformation (f^-1)
    private float inverse(float x)
    {
        if (x > 6.0 / 29.0)
        {
            return x * x * x;
        }
        else
        {
            return (108f / 841f) * (x - (4f / 29f));
        }
    }

    @Override
    public int getNumberOfComponents()
    {
        return 3;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent)
    {
        PDRange a = getARange();
        PDRange b = getBRange();
        return new float[] { 0, 100, a.getMin(), a.getMax(), b.getMin(), b.getMax() };
    }

    @Override
    public PDColor getInitialColor()
    {
        if (initialColor == null)
        {
            initialColor = new PDColor(new float[] {
                    0,
                    Math.max(0, getARange().getMin()),
                    Math.max(0, getBRange().getMin()) },
                    this);
        }
        return initialColor;
    }

    /**
     * creates a range array with default values (-100..100 -100..100).
     * @return the new range array.
     */
    private COSArray getDefaultRangeArray()
    {
        COSFloat minus100 = new COSFloat(-100f);
        COSFloat plus100 = new COSFloat(100f);
        COSArray range = new COSArray();
        range.add(minus100);
        range.add(plus100);
        range.add(minus100);
        range.add(plus100);
        return range;
    }

    /**
     * This will get the valid range for the "a" component.
     * If none is found then the default will be returned, which is -100..100.
     * @return the "a" range.
     */
    public PDRange getARange()
    {
        COSArray rangeArray = dictionary.getCOSArray(COSName.RANGE);
        if (rangeArray == null)
        {
            rangeArray = getDefaultRangeArray();
        }
        return new PDRange(rangeArray, 0);
    }

    /**
     * This will get the valid range for the "b" component.
     * If none is found  then the default will be returned, which is -100..100.
     * @return the "b" range.
     */
    public PDRange getBRange()
    {
        COSArray rangeArray = dictionary.getCOSArray(COSName.RANGE);
        if (rangeArray == null)
        {
            rangeArray = getDefaultRangeArray();
        }
        return new PDRange(rangeArray, 1);
    }

    /**
     * This will set the range for the "a" component.
     * @param range the new range for the "a" component, 
     * or null if defaults (-100..100) are to be set.
     */
    public void setARange(PDRange range)
    {
        setComponentRangeArray(range, 0);
    }

    /**
     * This will set the range for the "b" component.
     * @param range the new range for the "b" component,
     * or null if defaults (-100..100) are to be set.
     */
    public void setBRange(PDRange range)
    {
        setComponentRangeArray(range, 2);
    }

    private void setComponentRangeArray(PDRange range, int index)
    {
        COSArray rangeArray = dictionary.getCOSArray(COSName.RANGE);
        if (rangeArray == null)
        {
            rangeArray = getDefaultRangeArray();
        }
        if (range == null)
        {
            // reset to defaults
            rangeArray.set(index, new COSFloat(-100));
            rangeArray.set(index + 1, new COSFloat(100));
        }
        else
        {
            rangeArray.set(index, new COSFloat(range.getMin()));
            rangeArray.set(index + 1, new COSFloat(range.getMax()));
        }
        dictionary.setItem(COSName.RANGE, rangeArray);
        initialColor = null;
    }

}
