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
package com.tom_roush.pdfbox.util;

import android.graphics.Color;
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor;
import java.io.IOException;

public class PdfBoxAndroidUtils {

    /**
     * Convert PDColor в ARGB int -> alphaConstant (0..1).
     * @author Kanstantsin Valeitsenak
     */
    public static int getColorInt(PDColor color, double alphaConstant) throws IOException {
        float[] rgb = color.getColorSpace().toRGB(color.getComponents());

        int alpha = (int) Math.round(alphaConstant * 255.0);
        int r = Math.round(rgb[0] * 255);
        int g = Math.round(rgb[1] * 255);
        int b = Math.round(rgb[2] * 255);

        // 0..255
        r = clamp(r);
        g = clamp(g);
        b = clamp(b);
        alpha = clamp(alpha);

        return Color.argb(alpha, r, g, b);
    }

    private static int clamp(int val) {
        return Math.max(0, Math.min(255, val));
    }
}