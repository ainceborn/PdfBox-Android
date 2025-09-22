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
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.function.PDFunction;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A Separation color space used to specify either additional colorants or for isolating the
 * control of individual colour components of a device colour space for a subtractive device.
 * When such a space is the current colour space, the current colour shall be a single-component
 * value, called a tint, that controls the given colorant or colour components only.
 *
 * @author Ben Litchfield
 * @author John Hewson
 * @author Kanstantsin Valeitsenak
 */

public class PDSeparation extends PDSpecialColorSpace {
    private final COSName name;
    private final PDColorSpace alternateColorSpace;
    private final PDFunction tintTransform;

    public PDSeparation(COSArray separation, PDResources resources) throws IOException {
        name = (COSName) separation.getObject(1);
        alternateColorSpace = PDColorSpace.create(separation.getObject(2), resources);
        tintTransform = PDFunction.create(separation.getObject(3));
    }

    @Override
    public float[] toRGB(float[] value) throws IOException {
        float[] alt = tintTransform.eval(value);
        return alternateColorSpace.toRGB(alt);
    }

    @Override
    public Bitmap toRGBImage(Bitmap raster) throws IOException {
        int width = raster.getWidth();
        int height = raster.getHeight();
        Bitmap rgbImage = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[width * height];
        raster.getPixels(pixels, 0, width, 0, 0, width, height);

        int numAltComponents = alternateColorSpace.getNumberOfComponents();
        float[] samples = new float[1]; // tint value
        Map<Integer, float[]> calculatedValues = new HashMap<>();

        for (int i = 0; i < pixels.length; i++) {
            int gray = pixels[i] & 0xFF;
            samples[0] = gray / 255f;

            int hash = Float.floatToIntBits(samples[0]);
            float[] alt = calculatedValues.get(hash);

            if (alt == null) {
                alt = new float[numAltComponents];
                tintTransform.eval(samples);
                calculatedValues.put(hash, alt);
            }

            float[] rgb = alternateColorSpace.toRGB(alt);

            int r = Math.round(rgb[0] * 255);
            int g = Math.round(rgb[1] * 255);
            int b = Math.round(rgb[2] * 255);

            pixels[i] = Color.rgb(r, g, b);
        }

        rgbImage.setPixels(pixels, 0, width, 0, 0, width, height);
        return rgbImage;
    }

    @Override
    public String getName() {
        return name.getName();
    }

    @Override
    public int getNumberOfComponents() {
        return 1; // tint value
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent) {
        return new float[]{0f, 1f};
    }

    @Override
    public PDColor getInitialColor() {
        return new PDColor(new float[]{1f}, this);
    }
}