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
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNull;
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

public class PDSeparation extends PDSpecialColorSpace
{
    private final PDColor initialColor = new PDColor(new float[] { 1 }, this);

    // array indexes
    private static final int COLORANT_NAMES = 1;
    private static final int ALTERNATE_CS = 2;
    private static final int TINT_TRANSFORM = 3;

    // fields
    private PDColorSpace alternateColorSpace = null;
    private PDFunction tintTransform = null;

    private Map<Integer, float[]> toRGBMap = null;

    /**
     * Creates a new Separation color space.
     */
    public PDSeparation()
    {
        array = new COSArray();
        array.add(COSName.SEPARATION);
        array.add(COSName.getPDFName(""));
        // add some placeholder
        array.add(COSNull.NULL);
        array.add(COSNull.NULL);
    }

    /**
     * Creates a new Separation color space from a PDF color space array.
     * @param separation an array containing all separation information.
     * @param resources resources, can be null.
     * @throws IOException if the color space or the function could not be created.
     */
    public PDSeparation(COSArray separation, PDResources resources) throws IOException
    {
        array = separation;
        alternateColorSpace = PDColorSpace.create(array.getObject(ALTERNATE_CS), resources);
        tintTransform = PDFunction.create(array.getObject(TINT_TRANSFORM));
        int numberOfOutputParameters = tintTransform.getNumberOfOutputParameters();
        if (numberOfOutputParameters > 0 &&
                numberOfOutputParameters < alternateColorSpace.getNumberOfComponents())
        {
            throw new IOException("The tint transform function has less output parameters (" +
                    tintTransform.getNumberOfOutputParameters() + ") than the alternate colorspace " +
                    alternateColorSpace + " (" + alternateColorSpace.getNumberOfComponents() + ")");
        }
    }

    @Override
    public String getName()
    {
        return COSName.SEPARATION.getName();
    }

    @Override
    public int getNumberOfComponents()
    {
        return 1;
    }

    @Override
    public float[] getDefaultDecode(int bitsPerComponent)
    {
        return new float[] { 0, 1 };
    }

    @Override
    public PDColor getInitialColor()
    {
        return initialColor;
    }

    @Override
    public float[] toRGB(float[] value) throws IOException
    {
        if (toRGBMap == null)
        {
            toRGBMap = new HashMap<>();
        }
        int key = (int) (value[0] * 255);
        float[] retval = toRGBMap.get(key);
        if (retval != null)
        {
            return retval;
        }
        float[] altColor = tintTransform.eval(value);
        retval = alternateColorSpace.toRGB(altColor);
        toRGBMap.put(key, retval);
        return retval;
    }

    // java
    @Override
    public Bitmap toRGBImage(Bitmap raster) throws IOException {
        if (raster == null) {
            return null;
        }

        final int width = raster.getWidth();
        final int height = raster.getHeight();
        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] in = new int[width];
        int[] out = new int[width];
        float[] samples = new float[1];

        // Cache computed RGB arrays per tint to avoid repeated function eval
        Map<Integer, int[]> calculatedValues = new HashMap<>();

        // Handle Lab and Lab-based ICC the same way as the AWT impl’s toRGBImage2()
        boolean useDirectLabPath = (alternateColorSpace instanceof PDLab);
        if (!useDirectLabPath && (alternateColorSpace instanceof PDICCBased)) {
            PDColorSpace iccAlt = ((PDICCBased) alternateColorSpace).getAlternateColorSpace();
            useDirectLabPath = (iccAlt instanceof PDLab);
        }

        for (int y = 0; y < height; y++) {
            raster.getPixels(in, 0, width, 0, y, width, 1);

            for (int x = 0; x < width; x++) {
                // Read tint sample from red channel (single-component band)
                int px = in[x];
                int tintByte = Color.red(px); // 0..255
                Integer hash = tintByte;      // cache key for 0..255

                int[] rgb = calculatedValues.get(hash);
                if (rgb == null) {
                    if (useDirectLabPath) {
                        // Same logic as toRGBImage2(): normalize, eval tint transform, convert to RGB
                        samples[0] = tintByte / 255f;
                        float[] altColor = tintTransform.eval(samples);
                        float[] fltab = alternateColorSpace.toRGB(altColor);
                        rgb = new int[3];
                        rgb[0] = (int) (fltab[0] * 255f);
                        rgb[1] = (int) (fltab[1] * 255f);
                        rgb[2] = (int) (fltab[2] * 255f);
                    } else {
                        // Generic path: use helper that mirrors AWT tintTransform() scaling
                        samples[0] = tintByte; // 0..255, will be normalized inside tintTransform()
                        int[] alt = new int[alternateColorSpace.getNumberOfComponents()];
                        tintTransform(samples, alt);

                        // Convert alt components to RGB
                        float[] altF = new float[alt.length];
                        for (int i = 0; i < alt.length; i++) {
                            altF[i] = alt[i] / 255f;
                        }
                        float[] fltab = alternateColorSpace.toRGB(altF);
                        rgb = new int[3];
                        rgb[0] = (int) (fltab[0] * 255f);
                        rgb[1] = (int) (fltab[1] * 255f);
                        rgb[2] = (int) (fltab[2] * 255f);
                    }
                    calculatedValues.put(hash, rgb);
                }

                out[x] = Color.argb(255, clamp8(rgb[0]), clamp8(rgb[1]), clamp8(rgb[2]));
            }

            rgbBitmap.setPixels(out, 0, width, 0, y, width, 1);
        }

        return rgbBitmap;
    }

    private static int clamp8(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    protected void tintTransform(float[] samples, int[] alt) throws IOException
    {
        samples[0] /= 255; // 0..1
        float[] result = tintTransform.eval(samples);
        for (int s = 0; s < alt.length; s++)
        {
            // scale to 0..255
            alt[s] = (int) (result[s] * 255);
        }
    }

    /**
     * Returns the colorant name.
     * @return the name of the colorant
     */
    public PDColorSpace getAlternateColorSpace()
    {
        return alternateColorSpace;
    }

    /**
     * Returns the colorant name.
     * @return the name of the colorant
     */
    public String getColorantName()
    {
        COSName name = (COSName)array.getObject(COLORANT_NAMES);
        return name.getName();
    }

    /**
     * Sets the colorant name.
     * @param name the name of the colorant
     */
    public void setColorantName(String name)
    {
        array.set(1, COSName.getPDFName(name));
    }

    /**
     * Sets the alternate color space.
     * @param colorSpace The alternate color space.
     */
    public void setAlternateColorSpace(PDColorSpace colorSpace)
    {
        alternateColorSpace = colorSpace;
        COSBase space = null;
        if (colorSpace != null)
        {
            space = colorSpace.getCOSObject();
        }
        array.set(ALTERNATE_CS, space);
    }

    /**
     * Sets the tint transform function.
     * @param tint the tint transform function
     */
    public void setTintTransform(PDFunction tint)
    {
        tintTransform = tint;
        array.set(TINT_TRANSFORM, tint);
    }

    @Override
    public String toString()
    {
        return getName() + "{" +
                "\"" + getColorantName() + "\"" + " " +
                alternateColorSpace.getName() + " " +
                tintTransform + "}";
    }
}