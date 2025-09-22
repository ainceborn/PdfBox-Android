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

import android.graphics.Path;

import com.tom_roush.pdfbox.pdmodel.font.PDFontLike;
import com.tom_roush.pdfbox.pdmodel.font.PDSimpleFont;
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font;
import com.tom_roush.pdfbox.pdmodel.font.PDVectorFont;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple glyph outline cache.
 *
 * @author John Hewson
 */
final class GlyphCache
{
    
    private final PDVectorFont font;
    private final Map<Integer, Path> cache = new HashMap<>();

    GlyphCache(PDVectorFont font)
    {
        this.font = font;
    }
    
    public Path getPathForCharacterCode(int code)
    {
        Path path = cache.get(code);
        if (path != null)
        {
            return path;
        }

        try
        {
            if (!font.hasGlyph(code))
            {
                String fontName = ((PDFontLike) font).getName();
                if (font instanceof PDType0Font)
                {
                    int cid = ((PDType0Font) font).codeToCID(code);
                    String cidHex = String.format("%04x", cid);
                }
                else if (font instanceof PDSimpleFont)
                {
                    PDSimpleFont simpleFont = (PDSimpleFont) font;
                    if (code == 10 && simpleFont.isStandard14())
                    {
                        // PDFBOX-4001 return empty path for line feed on std14
                        path = new Path();
                        cache.put(code, path);
                        return path;
                    }
                }
                else
                {
                }
            }

            path = font.getNormalizedPath(code);
            cache.put(code, path);
            return path;
        }
        catch (IOException e)
        {
            // todo: escalate this error?
            String fontName = ((PDFontLike) font).getName();
            return new Path();
        }
    }
}
