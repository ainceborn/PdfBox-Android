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
package com.tom_roush.pdfbox.rendering;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.RectF;

import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.pdfbox.cos.COSDictionary;
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColorSpace;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDPattern;
import com.tom_roush.pdfbox.pdmodel.graphics.pattern.PDTilingPattern;
import com.tom_roush.pdfbox.util.Matrix;
import com.tom_roush.pdfbox.util.PdfBoxAndroidUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Android Paint for a tiling pattern, which consists of a small repeating graphical figure.
 * Uses BitmapShader instead of TexturePaint.
 *
 * @author Tilman Hausherr
 * @author Kanstantsin Valeitsenak
 */
class TilingPaintFactory {

    private static final int MAXEDGE;
    private static final String DEFAULTMAXEDGE = "3000";

    static {
        String s = System.getProperty("pdfbox.rendering.tilingpaint.maxedge", DEFAULTMAXEDGE);
        int val;
        try {
            val = Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            val = Integer.parseInt(DEFAULTMAXEDGE);
        }
        MAXEDGE = val;
    }

    private final PageDrawer drawer;
    private final Map<TilingPaintParameter, WeakReference<Paint>> weakCache = new WeakHashMap<>();

    TilingPaintFactory(PageDrawer drawer) {
        this.drawer = drawer;
    }

    /**
     * Returns a Paint object for the given PDColor. Supports solid colors and tiling patterns.
     */
    Paint create(PDTilingPattern pattern, PDColorSpace colorSpace,
                 PDColor color, AffineTransform xform) throws IOException {

        if (!(colorSpace instanceof PDPattern)) {
            int argb = PdfBoxAndroidUtils.getColorInt(color, drawer.getGraphicsState().getAlphaConstant());
            Paint paint = new Paint();
            paint.setColor(argb);
            paint.setAntiAlias(true);
            return paint;
        }

        TilingPaintParameter param = new TilingPaintParameter(drawer.getInitialMatrix(), pattern.getCOSObject(), colorSpace, color, xform);

        WeakReference<Paint> weakRef = weakCache.get(param);
        Paint paint = (weakRef != null) ? weakRef.get() : null;

        if (paint == null) {
            paint = new TilingPaint(drawer, pattern, colorSpace, color, xform).getPaint();
            weakCache.put(param, new WeakReference<>(paint));
        }

        return paint;
    }

    // ---------------------------------------------------------
    // Internal class for cache key
    private static class TilingPaintParameter {
        private final Matrix matrix;
        private final COSDictionary patternDict;
        private final PDColorSpace colorSpace;
        private final PDColor color;
        private final AffineTransform xform;

        private TilingPaintParameter(Matrix matrix, COSDictionary patternDict, PDColorSpace colorSpace,
                                     PDColor color, AffineTransform xform) {
            this.matrix = matrix.clone();
            this.patternDict = patternDict;
            this.colorSpace = colorSpace;
            this.color = color;
            this.xform = xform;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TilingPaintParameter)) return false;

            TilingPaintParameter other = (TilingPaintParameter) obj;
            if (!Objects.equals(this.matrix, other.matrix)) return false;
            if (!Objects.equals(this.patternDict, other.patternDict)) return false;
            if (!Objects.equals(this.colorSpace, other.colorSpace)) return false;

            if (this.color == null && other.color != null) return false;
            if (this.color != null && other.color == null) return false;

            if (this.color != null && other.color != null &&
                    this.color != other.color) {
                try {
                    if (this.color.toRGB() != other.color.toRGB()) return false;
                } catch (IOException e) {
                    return false;
                }
            }

            return Objects.equals(this.xform, other.xform);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 23 * hash + (matrix != null ? matrix.hashCode() : 0);
            hash = 23 * hash + (patternDict != null ? patternDict.hashCode() : 0);
            hash = 23 * hash + (colorSpace != null ? colorSpace.hashCode() : 0);
            hash = 23 * hash + (color != null ? color.hashCode() : 0);
            hash = 23 * hash + (xform != null ? xform.hashCode() : 0);
            return hash;
        }

        @Override
        public String toString() {
            return "TilingPaintParameter{" +
                    "matrix=" + matrix +
                    ", pattern=" + patternDict +
                    ", colorSpace=" + colorSpace +
                    ", color=" + color +
                    ", xform=" + xform +
                    '}';
        }
    }

    /**
     * Android replacement for TilingPaint.
     * It renders one tile into a Bitmap, then returns a Paint with BitmapShader.
     *
     * @author John Hewson
     * @author Kanstantsin Valeitsenak
     */
    private static class TilingPaint {
        private final Paint paint;
        private final Matrix patternMatrix;

        TilingPaint(PageDrawer drawer, PDTilingPattern pattern, PDColorSpace colorSpace,
                    PDColor color, AffineTransform xform) throws IOException {

            patternMatrix = Matrix.concatenate(drawer.getInitialMatrix(), pattern.getMatrix());
            RectF anchorRect = getAnchorRect(pattern, patternMatrix);

            Bitmap bitmap = renderTileBitmap(drawer, pattern, colorSpace, color, xform, anchorRect);

            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            paint = new Paint();
            paint.setAntiAlias(true);
            paint.setShader(shader);
        }

        Paint getPaint() {
            return paint;
        }

        private Bitmap renderTileBitmap(PageDrawer drawer, PDTilingPattern pattern,
                                               PDColorSpace colorSpace, PDColor color, AffineTransform xform,
                                               RectF anchorRect) throws IOException {

            float width = Math.abs(anchorRect.width());
            float height = Math.abs(anchorRect.height());

            Matrix xformMatrix = new Matrix(xform);
            float xScale = Math.abs(xformMatrix.getScalingFactorX());
            float yScale = Math.abs(xformMatrix.getScalingFactorY());
            width *= xScale;
            height *= yScale;

            int rasterWidth = Math.max(1, ceiling(width));
            int rasterHeight = Math.max(1, ceiling(height));

            Bitmap bitmap = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            // flip negative steps
            if (pattern.getYStep() < 0) {
                canvas.translate(0, rasterHeight);
                canvas.scale(1, -1);
            }
            if (pattern.getXStep() < 0) {
                canvas.translate(rasterWidth, 0);
                canvas.scale(-1, 1);
            }

            canvas.scale(xScale, yScale);

            // apply only scaling from pattern matrix
            Matrix newPatternMatrix = Matrix.getScaleInstance(
                    Math.abs(patternMatrix.getScalingFactorX()),
                    Math.abs(patternMatrix.getScalingFactorY()));

            PDRectangle bbox = pattern.getBBox();
            newPatternMatrix.translate(-bbox.getLowerLeftX(), -bbox.getLowerLeftY());

            // render the tile using PageDrawer
            drawer.drawTilingPattern(canvas, pattern, colorSpace, color, newPatternMatrix);

            return bitmap;
        }

        private static RectF getAnchorRect(PDTilingPattern pattern, Matrix patternMatrix) throws IOException {
            PDRectangle bbox = pattern.getBBox();
            if (bbox == null) throw new IOException("Pattern / Box is missing");

            float xStep = pattern.getXStep();
            if (xStep == 0) xStep = bbox.getWidth();

            float yStep = pattern.getYStep();
            if (yStep == 0) yStep = bbox.getHeight();

            float width = xStep * patternMatrix.getScalingFactorX();
            float height = yStep * patternMatrix.getScalingFactorY();

            if (Math.abs(width * height) > MAXEDGE * MAXEDGE) {
                width = Math.min(MAXEDGE, Math.abs(width)) * Math.signum(width);
                height = Math.min(MAXEDGE, Math.abs(height)) * Math.signum(height);
            }

            return new RectF(bbox.getLowerLeftX() * patternMatrix.getScalingFactorX(),
                    bbox.getLowerLeftY() * patternMatrix.getScalingFactorY(),
                    bbox.getLowerLeftX() * patternMatrix.getScalingFactorX() + width,
                    bbox.getLowerLeftY() * patternMatrix.getScalingFactorY() + height);
        }

        private static int ceiling(double num) {
            BigDecimal decimal = BigDecimal.valueOf(num);
            decimal = decimal.setScale(5, RoundingMode.CEILING);
            return decimal.intValue();
        }
    }
}
