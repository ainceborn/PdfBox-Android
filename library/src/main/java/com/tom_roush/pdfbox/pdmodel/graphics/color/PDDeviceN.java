package com.tom_roush.pdfbox.pdmodel.graphics.color;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;

import com.tom_roush.pdfbox.cos.COSArray;
import com.tom_roush.pdfbox.cos.COSBase;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.cos.COSName;
import com.tom_roush.pdfbox.cos.COSNull;
import com.tom_roush.pdfbox.pdmodel.PDResources;
import com.tom_roush.pdfbox.pdmodel.common.function.PDFunction;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Android-friendly port of PDDeviceN (AWT-free).
 *
 * - Uses android.graphics.Bitmap instead of BufferedImage.
 * - Replaces WritableRaster with a small SampleRaster abstraction.
 * - Converts colors per-pixel via PDColorSpace.toRGB(float[]).
 *
 * Notes:
 * - This implementation focuses on correctness and portability to Android.
 * - It intentionally uses per-pixel float conversions instead of trying to emulate
 *   complex banded rasters. For large images you may want to optimize / vectorize.
 */
public class PDDeviceN extends PDSpecialColorSpace
{
    // array indexes (same as original)
    private static final int COLORANT_NAMES = 1;
    private static final int ALTERNATE_CS = 2;
    private static final int TINT_TRANSFORM = 3;
    private static final int DEVICEN_ATTRIBUTES = 4;

    // fields
    private PDColorSpace alternateColorSpace = null;
    private PDFunction tintTransform = null;
    private PDDeviceNAttributes attributes;
    private PDColor initialColor;

    // color conversion cache
    private int numColorants;
    private int[] colorantToComponent;
    private PDColorSpace processColorSpace;
    private PDSeparation[] spotColorSpaces;

    // underlying COS array (kept for compatibility)
    protected COSArray array;

    public PDDeviceN()
    {
        array = new COSArray();
        array.add(COSName.DEVICEN);
        // placeholders
        array.add(COSNull.NULL);
        array.add(COSNull.NULL);
        array.add(COSNull.NULL);
    }

    public PDDeviceN(COSArray deviceN, PDResources resources) throws IOException
    {
        this.array = deviceN;
        alternateColorSpace = PDColorSpace.create(array.getObject(ALTERNATE_CS), resources);
        tintTransform = PDFunction.create(array.getObject(TINT_TRANSFORM));
        if (array.size() > DEVICEN_ATTRIBUTES)
        {
            attributes = new PDDeviceNAttributes((COSDictionary) array.getObject(DEVICEN_ATTRIBUTES));
        }
        initColorConversionCache(resources);

        // set initial color (all 1)
        int n = getNumberOfComponents();
        float[] initial = new float[n];
        Arrays.fill(initial, 1);
        initialColor = new PDColor(initial, this);
    }

    // ----------------------------
    // SampleRaster: minimal interface for reading per-pixel samples
    // ----------------------------
    /**
     * Abstraction used instead of WritableRaster.
     *
     * - getNumComponents() returns number of sample components per pixel (e.g. number of colorants).
     * - getPixel(x,y,out) fills out[] with samples for pixel (range: 0..255).
     *
     * Implement this interface for your backing data (byte[], int[] or whatever).
     */
    public interface SampleRaster
    {
        int getWidth();
        int getHeight();
        int getNumComponents();
        /**
         * Fill 'out' with sample values for pixel (x,y). The values should be in 0..255 range.
         * out must be of length >= getNumComponents().
         */
        void getPixel(int x, int y, float[] out);
    }

    // ----------------------------
    // Initialization helper (same logic as original)
    // ----------------------------
    // initializes the color conversion cache
    private void initColorConversionCache(PDResources resources) throws IOException
    {
        // there's nothing to cache for non-attribute spaces
        if (attributes == null)
        {
            return;
        }

        // colorant names
        List<String> colorantNames = getColorantNames();
        numColorants = colorantNames.size();

        // process components
        colorantToComponent = new int[numColorants];
        if (attributes.getProcess() != null)
        {
            List<String> components = attributes.getProcess().getComponents();

            // map each colorant name to the corresponding process component name (if any)
            for (int c = 0; c < numColorants; c++)
            {
                colorantToComponent[c] = components.indexOf(colorantNames.get(c));
            }

            // process color space
            processColorSpace = attributes.getProcess().getColorSpace();
        }
        else
        {
            for (int c = 0; c < numColorants; c++)
            {
                colorantToComponent[c] = -1;
            }
        }

        // spot colorants
        spotColorSpaces = new PDSeparation[numColorants];

        // spot color spaces
        Map<String, PDSeparation> spotColorants = attributes.getColorants(resources);

        // map each colorant to the corresponding spot color space
        for (int c = 0; c < numColorants; c++)
        {
            String name = colorantNames.get(c);
            PDSeparation spot = spotColorants.get(name);
            if (spot != null)
            {
                // spot colorant
                spotColorSpaces[c] = spot;

                // spot colors may replace process colors with same name
                // providing that the subtype is not NChannel.
                if (!isNChannel())
                {
                    colorantToComponent[c] = -1;
                }
            }
            else
            {
                // process colorant
                spotColorSpaces[c] = null;
            }
        }
    }

    // ----------------------------
    // Public API: convert sample-raster to Android Bitmap
    // ----------------------------
    /**
     * Convert the sample raster (with colorant components) to an RGB Bitmap (Config ARGB_8888).
     * Each sample value provided by SampleRaster.getPixel must be in 0..255 range.
     */
    public Bitmap toRGBImage(SampleRaster raster) throws IOException
    {
        if (attributes != null)
        {
            return toRGBWithAttributes(raster);
        }
        else
        {
            return toRGBWithTintTransform(raster);
        }
    }

    // performance-sensitive: attribute path
    private Bitmap toRGBWithAttributes(SampleRaster raster) throws IOException
    {
        final int width = raster.getWidth();
        final int height = raster.getHeight();

        // create white background ARGB bitmap
        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(rgbBitmap);
        canvas.drawColor(Color.WHITE); // white background

        // allocate composite floats in 0..1
        float[] rgbComposite = new float[3];

        // per-pixel processing: start with white (1,1,1) and multiply each colorant contribution
        float[] samples = new float[raster.getNumComponents()];

        // We'll build a cache for tint-based fallbacks if needed (not used in attribute path here)
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                // initialize composite to white
                rgbComposite[0] = 1f;
                rgbComposite[1] = 1f;
                rgbComposite[2] = 1f;

                raster.getPixel(x, y, samples);

                boolean fallbackToTint = false;

                for (int c = 0; c < numColorants; c++)
                {
                    PDColorSpace componentColorSpace;
                    boolean isProcessColorant = colorantToComponent[c] >= 0;

                    if (isProcessColorant)
                    {
                        componentColorSpace = processColorSpace;
                    }
                    else if (spotColorSpaces[c] == null)
                    {
                        // missing spot color -> fallback to tint transform for entire pixel
                        fallbackToTint = true;
                        break;
                    }
                    else
                    {
                        componentColorSpace = spotColorSpaces[c];
                    }

                    // prepare component samples (range 0..1)
                    int compNum = componentColorSpace.getNumberOfComponents();
                    float[] compSamples = new float[compNum];
                    if (isProcessColorant)
                    {
                        int compIndex = colorantToComponent[c];
                        // map sample value for colorant into its process component slot
                        compSamples[compIndex] = samples[c] / 255f;
                    }
                    else
                    {
                        compSamples[0] = samples[c] / 255f;
                    }

                    // convert single component to RGB via color space
                    float[] rgbComponent = componentColorSpace.toRGB(compSamples); // values in 0..1

                    // multiply (blend)
                    rgbComposite[0] *= rgbComponent[0];
                    rgbComposite[1] *= rgbComponent[1];
                    rgbComposite[2] *= rgbComponent[2];
                } // end for each colorant

                int pixelInt;
                if (fallbackToTint)
                {
                    // fallback to tint transform path for this pixel
                    float[] src = new float[raster.getNumComponents()];
                    for (int s = 0; s < src.length; s++) src[s] = samples[s] / 255f;
                    float[] alt = tintTransform.eval(src);
                    float[] rgbFloat = alternateColorSpace.toRGB(alt);
                    int r = clamp255((int) (rgbFloat[0] * 255f));
                    int g = clamp255((int) (rgbFloat[1] * 255f));
                    int b = clamp255((int) (rgbFloat[2] * 255f));
                    pixelInt = Color.rgb(r, g, b);
                }
                else
                {
                    int r = clamp255((int) (rgbComposite[0] * 255f));
                    int g = clamp255((int) (rgbComposite[1] * 255f));
                    int b = clamp255((int) (rgbComposite[2] * 255f));
                    pixelInt = Color.rgb(r, g, b);
                }

                rgbBitmap.setPixel(x, y, pixelInt);
            } // x
        } // y

        return rgbBitmap;
    }

    // performance-sensitive: tintTransform path
    private Bitmap toRGBWithTintTransform(SampleRaster raster) throws IOException
    {
        final int width = raster.getWidth();
        final int height = raster.getHeight();
        final int numSrcComponents = raster.getNumComponents();

        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        float[] src = new float[numSrcComponents];
        float[] alt;
        float[] rgbFloat;

        float[] samples = new float[numSrcComponents];

        // simple cache (string key of component ints) to avoid recomputing identical combinations
        Map<String, Integer> cache = new HashMap<>();
        StringBuilder keyBuilder = new StringBuilder();

        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                raster.getPixel(x, y, samples);

                // key
                keyBuilder.setLength(0);
                keyBuilder.append((int) samples[0]);
                for (int s = 1; s < numSrcComponents; s++)
                {
                    keyBuilder.append('#').append((int) samples[s]);
                }
                String key = keyBuilder.toString();

                Integer cachedPixel = cache.get(key);
                if (cachedPixel != null)
                {
                    rgbBitmap.setPixel(x, y, cachedPixel);
                    continue;
                }

                // scale to 0..1
                for (int s = 0; s < numSrcComponents; s++)
                {
                    src[s] = samples[s] / 255f;
                }

                // convert via tint transform to alternate color space
                alt = tintTransform.eval(src);

                // convert alternate to RGB
                rgbFloat = alternateColorSpace.toRGB(alt);

                int r = clamp255((int) (rgbFloat[0] * 255f));
                int g = clamp255((int) (rgbFloat[1] * 255f));
                int b = clamp255((int) (rgbFloat[2] * 255f));

                int pixelInt = Color.rgb(r, g, b);
                cache.put(key, pixelInt);
                rgbBitmap.setPixel(x, y, pixelInt);
            }
        }

        return rgbBitmap;
    }

    private static int clamp255(int v)
    {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    // ----------------------------
    // Other methods ported / adapted
    // ----------------------------
    public float[] toRGB(float[] value) throws IOException
    {
        if (attributes != null)
        {
            return toRGBWithAttributes(value);
        }
        else
        {
            return toRGBWithTintTransform(value);
        }
    }

    @Override
    public Bitmap toRGBImage(Bitmap raster) throws IOException {
        int width = raster.getWidth();
        int height = raster.getHeight();

        Bitmap rgbBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        int[] pixels = new int[width];
        float[] src;

        boolean useAttributes = (attributes != null);
        int numComponents = getNumberOfComponents();

        src = new float[numComponents];

        for (int y = 0; y < height; y++) {
            raster.getPixels(pixels, 0, width, 0, y, width, 1);
            for (int x = 0; x < width; x++) {
                int color = pixels[x];

                float r = ((color >> 16) & 0xFF) / 255f;
                float g = ((color >> 8) & 0xFF) / 255f;
                float b = (color & 0xFF) / 255f;

                src[0] = r;
                if (numComponents > 1) src[1] = g;
                if (numComponents > 2) src[2] = b;

                float[] rgbResult;

                if (useAttributes) {
                    rgbResult = toRGBWithAttributes(src);
                } else {
                    rgbResult = toRGBWithTintTransform(src);
                }

                int outR = clamp255((int) (rgbResult[0] * 255));
                int outG = clamp255((int) (rgbResult[1] * 255));
                int outB = clamp255((int) (rgbResult[2] * 255));

                pixels[x] = 0xFF000000 | (outR << 16) | (outG << 8) | outB;
            }
            rgbBitmap.setPixels(pixels, 0, width, 0, y, width, 1);
        }

        return rgbBitmap;
    }

    private float[] toRGBWithAttributes(float[] value) throws IOException
    {
        float[] rgbValue = { 1, 1, 1 };

        for (int c = 0; c < numColorants; c++)
        {
            PDColorSpace componentColorSpace;
            boolean isProcessColorant = colorantToComponent[c] >= 0;
            if (isProcessColorant)
            {
                componentColorSpace = processColorSpace;
            }
            else if (spotColorSpaces[c] == null)
            {
                // fallback
                return toRGBWithTintTransform(value);
            }
            else
            {
                componentColorSpace = spotColorSpaces[c];
            }

            float[] componentSamples = new float[componentColorSpace.getNumberOfComponents()];
            if (isProcessColorant)
            {
                int componentIndex = colorantToComponent[c];
                componentSamples[componentIndex] = value[c];
            }
            else
            {
                componentSamples[0] = value[c];
            }

            float[] rgbComponent = componentColorSpace.toRGB(componentSamples);
            rgbValue[0] *= rgbComponent[0];
            rgbValue[1] *= rgbComponent[1];
            rgbValue[2] *= rgbComponent[2];
        }

        return rgbValue;
    }

    private float[] toRGBWithTintTransform(float[] value) throws IOException
    {
        float[] altValue = tintTransform.eval(value);
        return alternateColorSpace.toRGB(altValue);
    }

    public boolean isNChannel()
    {
        return attributes != null && attributes.isNChannel();
    }

    public String getName()
    {
        return COSName.DEVICEN.getName();
    }

    public final int getNumberOfComponents()
    {
        return getColorantNames().size();
    }

    public float[] getDefaultDecode(int bitsPerComponent)
    {
        int n = getNumberOfComponents();
        float[] decode = new float[n * 2];
        for (int i = 0; i < n; i++)
        {
            decode[i * 2 + 1] = 1;
        }
        return decode;
    }

    public PDColor getInitialColor()
    {
        return initialColor;
    }

    public List<String> getColorantNames()
    {
        return ((COSArray) array.getObject(COLORANT_NAMES)).toCOSNameStringList();
    }

    public PDDeviceNAttributes getAttributes()
    {
        return attributes;
    }

    public void setColorantNames(List<String> names)
    {
        COSArray namesArray = COSArray.ofCOSNames(names);
        array.set(COLORANT_NAMES, namesArray);
    }

    public void setAttributes(PDDeviceNAttributes attributes)
    {
        this.attributes = attributes;
        if (attributes == null)
        {
            array.remove(DEVICEN_ATTRIBUTES);
        }
        else
        {
            while (array.size() <= DEVICEN_ATTRIBUTES)
            {
                array.add(COSNull.NULL);
            }
            array.set(DEVICEN_ATTRIBUTES, attributes.getCOSDictionary());
        }
    }

    public PDColorSpace getAlternateColorSpace() throws IOException
    {
        if (alternateColorSpace == null)
        {
            alternateColorSpace = PDColorSpace.create(array.getObject(ALTERNATE_CS));
        }
        return alternateColorSpace;
    }

    public void setAlternateColorSpace(PDColorSpace cs)
    {
        alternateColorSpace = cs;
        COSBase space = null;
        if (cs != null)
        {
            space = cs.getCOSObject();
        }
        array.set(ALTERNATE_CS, space);
    }

    public PDFunction getTintTransform() throws IOException
    {
        if (tintTransform == null)
        {
            tintTransform = PDFunction.create(array.getObject(TINT_TRANSFORM));
        }
        return tintTransform;
    }

    public void setTintTransform(PDFunction tint)
    {
        tintTransform = tint;
        array.set(TINT_TRANSFORM, tint);
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder(getName());
        sb.append('{');
        for (String col : getColorantNames())
        {
            sb.append('\"');
            sb.append(col);
            sb.append("\" ");
        }
        if (alternateColorSpace != null)
        {
            sb.append(alternateColorSpace.getName());
            sb.append(' ');
        }
        sb.append(tintTransform);
        sb.append(' ');
        if (attributes != null)
        {
            sb.append(attributes);
        }
        sb.append('}');
        return sb.toString();
    }
}
