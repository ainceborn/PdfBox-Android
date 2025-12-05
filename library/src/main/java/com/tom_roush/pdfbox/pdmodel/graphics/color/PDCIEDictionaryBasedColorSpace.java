/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSFloat;
import com.tom_roush.pdfbox.cos.COSName;

/**
 * CIE-based colour spaces that use a dictionary.
 */
public abstract class PDCIEDictionaryBasedColorSpace extends PDCIEBasedColorSpace
{
    protected final COSDictionary dictionary;

    // cached whitepoint
    protected float wpX = 1f;
    protected float wpY = 1f;
    protected float wpZ = 1f;

    protected PDCIEDictionaryBasedColorSpace(COSName cosName)
    {
        array = new COSArray();
        dictionary = new COSDictionary();
        array.add(cosName);
        array.add(dictionary);
        fillWhitepointCache(getWhitepoint());
    }

    protected PDCIEDictionaryBasedColorSpace(COSArray arr)
    {
        array = arr;
        dictionary = (COSDictionary) array.getObject(1);
        fillWhitepointCache(getWhitepoint());
    }

    protected boolean isWhitePoint()
    {
        return Float.compare(wpX, 1f) == 0 &&
                Float.compare(wpY, 1f) == 0 &&
                Float.compare(wpZ, 1f) == 0;
    }

    private void fillWhitepointCache(PDTristimulus whitepoint)
    {
        wpX = whitepoint.getX();
        wpY = whitepoint.getY();
        wpZ = whitepoint.getZ();
    }

    /**
     * Convert CIE XYZ (D65) to sRGB (0..1 floats).
     */
    protected float[] convXYZtoRGB(float x, float y, float z)
    {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (z < 0) z = 0;

        // XYZ -> linear sRGB
        float rLin =  3.2406f * x - 1.5372f * y - 0.4986f * z;
        float gLin = -0.9689f * x + 1.8758f * y + 0.0415f * z;
        float bLin =  0.0557f * x - 0.2040f * y + 1.0570f * z;

        float r = encodeSRGB(rLin);
        float g = encodeSRGB(gLin);
        float b = encodeSRGB(bLin);

        return new float[] { r, g, b };
    }

    private static float encodeSRGB(float c)
    {
        if (c <= 0f) return 0f;
        if (c <= 0.0031308f) return 12.92f * c;
        return (float)(1.055 * Math.pow(c, 1.0 / 2.4) - 0.055);
    }

    public final PDTristimulus getWhitepoint()
    {
        COSArray wp = dictionary.getCOSArray(COSName.WHITE_POINT);
        if (wp == null)
        {
            wp = new COSArray();
            wp.add(COSFloat.ONE);
            wp.add(COSFloat.ONE);
            wp.add(COSFloat.ONE);
        }
        return new PDTristimulus(wp);
    }

    public final PDTristimulus getBlackPoint()
    {
        COSArray bp = dictionary.getCOSArray(COSName.BLACK_POINT);
        if (bp == null)
        {
            bp = new COSArray();
            bp.add(COSFloat.ZERO);
            bp.add(COSFloat.ZERO);
            bp.add(COSFloat.ZERO);
        }
        return new PDTristimulus(bp);
    }

    public void setWhitePoint(PDTristimulus whitepoint)
    {
        if (whitepoint == null)
        {
            throw new IllegalArgumentException("Whitepoint may not be null");
        }
        dictionary.setItem(COSName.WHITE_POINT, whitepoint);
        fillWhitepointCache(whitepoint);
    }

    public void setBlackPoint(PDTristimulus blackpoint)
    {
        dictionary.setItem(COSName.BLACK_POINT, blackpoint);
    }
}

