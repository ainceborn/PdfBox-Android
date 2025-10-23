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

import android.graphics.Bitmap;
import android.graphics.Color;

import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSInteger;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.cos.COSString;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.PDStream;

import java.io.IOException;

/**
 * Indexed color spaces allow a PDF to use a small color lookup table (palette),
 * mapping index values to colors in a base color space (such as DeviceRGB).
 * This is useful for reducing file size when only a limited number of colors are used.
 *
 * The Indexed color space is defined by a base color space, a high value (maximum index),
 * and a lookup table containing the actual color values.
 *
 * Example in PDF: [/Indexed /DeviceRGB 255 <...palette bytes...>]
 *
 * @author Kanstantsin Valeitsenak
 */

public final class PDIndexed extends PDSpecialColorSpace {

    private final PDColor initialColor = new PDColor(new float[]{0}, this);
    private PDColorSpace baseColorSpace;
    private byte[] lookupData;
    private float[][] colorTable;
    private int actualMaxIndex;
    private int[][] rgbColorTable;

    public PDIndexed() {
        array = new COSArray();
        array.add(COSName.INDEXED);
        array.add(COSName.DEVICERGB);
        array.add(COSInteger.get(255));
        array.add(null);
    }

    public PDIndexed(COSArray indexedArray) throws IOException {
        this(indexedArray, null);
    }

    public PDIndexed(COSArray indexedArray, PDResources resources) throws IOException {
        array = indexedArray;
        baseColorSpace = PDColorSpace.create(array.get(1), resources);
        readColorTable();
        initRgbColorTable();
    }

    @Override
    public String getName() {
        return COSName.INDEXED.getName();
    }

    @Override
    public int getNumberOfComponents() {
        return 1;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent) {
        return new float[]{0, (float) Math.pow(2, bitsPerComponent) - 1};
    }

    @Override
    public PDColor getInitialColor() {
        return initialColor;
    }

    private void readLookupData() throws IOException {
        if (lookupData == null) {
            COSBase lookupTable = array.getObject(3);
            if (lookupTable instanceof COSString) {
                lookupData = ((COSString) lookupTable).getBytes();
            } else if (lookupTable instanceof COSStream) {
                lookupData = new PDStream((COSStream) lookupTable).toByteArray();
            } else if (lookupTable == null) {
                lookupData = new byte[0];
            } else {
                throw new IOException("Unknown type for lookup table " + lookupTable);
            }
        }
    }

    private void readColorTable() throws IOException {
        readLookupData();

        int maxIndex = Math.min(getHival(), 255);
        int numComponents = baseColorSpace.getNumberOfComponents();

        // some tables are too short
        if (lookupData.length / numComponents < maxIndex + 1)
        {
            maxIndex = lookupData.length / numComponents - 1;
        }
        actualMaxIndex = maxIndex;  // TODO "actual" is ugly, tidy this up

        colorTable = new float[maxIndex + 1][numComponents];
        for (int i = 0, offset = 0; i <= maxIndex; i++)
        {
            for (int c = 0; c < numComponents; c++)
            {
                colorTable[i][c] = (lookupData[offset] & 0xff) / 255f;
                offset++;
            }
        }
    }

    private void initRgbColorTable() throws IOException
    {
        int numBaseComponents = baseColorSpace.getNumberOfComponents();

        if (colorTable == null || colorTable.length == 0) {
            throw new IOException("Color table is empty or null");
        }

        int[][] baseRaster = new int[actualMaxIndex + 1][numBaseComponents];
        for (int i = 0; i <= actualMaxIndex; i++) {
            for (int c = 0; c < numBaseComponents; c++) {
                baseRaster[i][c] = (int) (colorTable[i][c] * 255f);
            }
        }

        rgbColorTable = new int[actualMaxIndex + 1][3];

        for (int i = 0; i <= actualMaxIndex; i++) {
            int[] base = baseRaster[i];

            switch (numBaseComponents) {
                case 1: // Gray → RGB
                    int g = base[0];
                    rgbColorTable[i][0] = g;
                    rgbColorTable[i][1] = g;
                    rgbColorTable[i][2] = g;
                    break;

                case 3: // RGB
                    rgbColorTable[i][0] = base[0];
                    rgbColorTable[i][1] = base[1];
                    rgbColorTable[i][2] = base[2];
                    break;

                case 4: // CMYK → RGB
                    float c = base[0] / 255f;
                    float m = base[1] / 255f;
                    float y = base[2] / 255f;
                    float k = base[3] / 255f;

                    int r = (int) ((1 - Math.min(1, c * (1 - k) + k)) * 255);
                    int g2 = (int) ((1 - Math.min(1, m * (1 - k) + k)) * 255);
                    int b = (int) ((1 - Math.min(1, y * (1 - k) + k)) * 255);

                    rgbColorTable[i][0] = r;
                    rgbColorTable[i][1] = g2;
                    rgbColorTable[i][2] = b;
                    break;

                default:
                    rgbColorTable[i][0] = 0;
                    rgbColorTable[i][1] = 0;
                    rgbColorTable[i][2] = 0;
                    break;
            }
        }
    }

    @Override
    public float[] toRGB(float[] value) {
        if (value.length != 1)
        {
            throw new IllegalArgumentException("Indexed color spaces must have one color value");
        }

        // scale and clamp input value
        int index = Math.round(value[0]);
        index = Math.max(index, 0);
        index = Math.min(index, actualMaxIndex);

        // lookup rgb
        int[] rgb = rgbColorTable[index];
        return new float[] { rgb[0] / 255f, rgb[1] / 255f, rgb[2] / 255f };
    }

    public Bitmap toRGBImage(Bitmap raster) {
        int width = raster.getWidth();
        int height = raster.getHeight();
        Bitmap rgbImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = raster.getPixel(x, y);
                int index;
                if (raster.getConfig() == Bitmap.Config.ALPHA_8) {
                    index = Color.alpha(pixel) & 0xFF;
                } else {
                    index = Color.red(pixel) & 0xFF;
                }
                index = Math.min(index, actualMaxIndex);
                int[] rgb = rgbColorTable[index];
                rgbImage.setPixel(x, y, Color.rgb(rgb[0], rgb[1], rgb[2]));
            }
        }

        return rgbImage;
    }

    public PDColorSpace getBaseColorSpace() {
        return baseColorSpace;
    }

    private int getHival() {
        return ((COSNumber) array.getObject(2)).intValue();
    }

    public void setBaseColorSpace(PDColorSpace base) {
        array.set(1, base.getCOSObject());
        baseColorSpace = base;
    }

    public void setHighValue(int high) {
        array.set(2, high);
    }

    @Override
    public String toString() {
        return "Indexed{base:" + baseColorSpace + " hival:" + getHival() + " lookup:(" + colorTable.length + " entries)}";
    }
}
