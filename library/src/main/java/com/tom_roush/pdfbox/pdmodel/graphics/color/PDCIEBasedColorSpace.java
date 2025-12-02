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

import java.io.IOException;

/**
 * CIE-based colour spaces specify colours in a way that is independent of the characteristics
 * of any particular output device. They are based on an international standard for colour
 * specification created by the Commission Internationale de l'Éclairage (CIE).
 *
 * @author John Hewson
 */
public abstract class PDCIEBasedColorSpace extends PDColorSpace
{
    //
    // WARNING: this method is performance sensitive, modify with care!
    //

    @Override
    public Bitmap toRGBImage(Bitmap raster) throws IOException {
        if (raster == null) {
            return null;
        }
        final int width = raster.getWidth();
        final int height = raster.getHeight();

        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] inRow = new int[width];
        int[] outRow = new int[width];

        // always three components: ABC
        float[] abc = new float[3];
        for (int y = 0; y < height; y++) {
            raster.getPixels(inRow, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int px = inRow[x];

                // 0..255 -> 0..1
                abc[0] = Color.red(px) / 255f;
                abc[1] = Color.green(px) / 255f;
                abc[2] = Color.blue(px) / 255f;
                float[] rgb = toRGB(abc); // expects normalized components, returns 0..1

                int r = clamp8((int) (rgb[0] * 255f));
                int g = clamp8((int) (rgb[1] * 255f));
                int b = clamp8((int) (rgb[2] * 255f));

                outRow[x] = Color.argb(255, r, g, b);
            }
            rgbBitmap.setPixels(outRow, 0, width, 0, y, width, 1);
        }
        return rgbBitmap;

    }

    private static int clamp8(int v)
    {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    @Override
    public String toString()
    {
        return getName();   // TODO return more info
    }
}
