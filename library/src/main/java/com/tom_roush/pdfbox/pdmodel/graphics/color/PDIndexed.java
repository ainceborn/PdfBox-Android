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
import android.os.Build;

import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSStream;
import com.tom_roush.pdfbox.cos.COSString;
import com.tom_roush.pdfbox.cos.COSNumber;
import com.tom_roush.pdfbox.cos.COSName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

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

public class PDIndexed extends PDColorSpace
{
    private final PDColorSpace baseColorSpace;
    private final int highVal;
    private final byte[] lookupData;

    public PDIndexed(COSArray indexedArray) throws IOException
    {
        this.array = indexedArray;

        COSBase base = array.getObject(1);
        this.baseColorSpace = PDColorSpace.create(base);

        COSNumber hivalObj = (COSNumber) array.getObject(2);
        this.highVal = hivalObj.intValue();

        COSBase lookup = array.getObject(3);

        if (lookup instanceof COSString)
        {
            this.lookupData = ((COSString) lookup).getBytes();
        }
        else if (lookup instanceof COSStream)
        {
            try (InputStream is = ((COSStream) lookup).createInputStream())
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    this.lookupData = is.readAllBytes(); // API 26+
                } else {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = is.read(buffer)) != -1) {
                        baos.write(buffer, 0, read);
                    }
                    this.lookupData = baos.toByteArray();
                }
            }
        }
        else
        {
            throw new IOException("Invalid lookup data in Indexed color space");
        }
    }

    @Override
    public String getName()
    {
        return COSName.INDEXED.getName();
    }

    @Override
    public int getNumberOfComponents()
    {
        return 1;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent)
    {
        return new float[] { 0, highVal };
    }

    @Override
    public PDColor getInitialColor()
    {
        return new PDColor(new float[] { 0 }, this);
    }

    @Override
    public float[] toRGB(float[] value)
    {
        int index = Math.round(value[0]);
        int numColorComponents = baseColorSpace.getNumberOfComponents();
        int paletteIndex = index * numColorComponents;

        if (paletteIndex + numColorComponents > lookupData.length)
        {
            return baseColorSpace.getInitialColor().getComponents(); // fallback
        }

        float[] rgb = new float[3];
        float[] component = new float[numColorComponents];
        for (int i = 0; i < numColorComponents; i++)
        {
            int byteVal = lookupData[paletteIndex + i] & 0xFF;
            component[i] = byteVal / 255f;
        }

        try {
            rgb = baseColorSpace.toRGB(component);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return rgb;
    }

    @Override
    public Bitmap toRGBImage(Bitmap raster) throws IOException {
        if (raster.getConfig() != Bitmap.Config.ALPHA_8)
        {
            throw new IOException("Expected ALPHA_8 bitmap as Indexed source.");
        }

        int width = raster.getWidth();
        int height = raster.getHeight();

        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[width];
        for (int y = 0; y < height; y++)
        {
            raster.getPixels(pixels, 0, width, 0, y, width, 1);

            for (int x = 0; x < width; x++)
            {
                int index = pixels[x] & 0xFF;

                int baseIndex = index * baseColorSpace.getNumberOfComponents();

                if (baseIndex + 2 >= lookupData.length)
                {
                    rgbBitmap.setPixel(x, y, android.graphics.Color.BLACK); // fallback
                    continue;
                }

                float[] baseColor = new float[baseColorSpace.getNumberOfComponents()];
                for (int i = 0; i < baseColor.length; i++)
                {
                    baseColor[i] = (lookupData[baseIndex + i] & 0xFF) / 255f;
                }

                float[] rgb = baseColorSpace.toRGB(baseColor);

                int r = (int)(rgb[0] * 255);
                int g = (int)(rgb[1] * 255);
                int b = (int)(rgb[2] * 255);

                rgbBitmap.setPixel(x, y, android.graphics.Color.rgb(r, g, b));
            }
        }

        return rgbBitmap;
    }
}
