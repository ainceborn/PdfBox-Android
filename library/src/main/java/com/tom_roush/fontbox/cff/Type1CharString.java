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
package com.tom_roush.fontbox.cff;

import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import com.tom_roush.Global;
import com.tom_roush.fontbox.encoding.StandardEncoding;
import com.tom_roush.fontbox.type1.Type1CharStringReader;
import com.tom_roush.harmony.awt.geom.AffineTransform;
import com.tom_roush.pdfbox.io.IOUtils;
import com.tom_roush.pdfbox.util.Matrix;

/**
 * This class represents and renders a Type 1 CharString.
 *
 * @author Villu Ruusmann
 * @author John Hewson
 */
public class Type1CharString
{

    private final Type1CharStringReader font;
    private final String fontName;
    private final String glyphName;
    private Path path = null;
    private int width = 0;
    private PointF leftSideBearing = null;
    private PointF current = null;
    private boolean isFlex = false;
    private final List<PointF> flexPoints = new ArrayList<>();
    private final List<Object> type1Sequence = new ArrayList<>();
    private int commandCount = 0;

    /**
     * Constructs a new Type1CharString object.
     *
     * @param font Parent Type 1 CharString font.
     * @param fontName Name of the font.
     * @param glyphName Name of the glyph.
     * @param sequence Type 1 char string sequence
     */
    public Type1CharString(Type1CharStringReader font, String fontName, String glyphName,
                           List<Object> sequence)
    {
        this(font, fontName, glyphName);
        type1Sequence.addAll(sequence);
    }

    /**
     * Constructor for use in subclasses.
     *
     * @param font Parent Type 1 CharString font.
     * @param fontName Name of the font.
     * @param glyphName Name of the glyph.
     */
    protected Type1CharString(Type1CharStringReader font, String fontName, String glyphName)
    {
        this.font = font;
        this.fontName = fontName;
        this.glyphName = glyphName;
        this.current = new PointF(0, 0);
    }

    // todo: NEW name (or CID as hex)
    public String getName()
    {
        return glyphName;
    }

    /**
     * Returns the bounds of the renderer path.
     * @return the bounds as Rectangle2D
     */
    public RectF getBounds()
    {
        synchronized(Global.TAG)
        {
            if (path == null)
            {
                render();
            }
        }

        if (path == null)
        {
            render();
        }
        
        RectF retval = new RectF();
        path.computeBounds(retval, true);
        return retval;
    }

    /**
     * Returns the advance width of the glyph.
     * @return the width
     */
    public int getWidth()
    {
        synchronized(Global.TAG)
        {
            if (path == null)
            {
                render();
            }
        }
        return width;
    }

    /**
     * Returns the path of the character.
     * @return the path
     */
    public Path getPath()
    {
        synchronized(Global.TAG)
        {
            if (path == null)
            {
                render();
            }
        }
        return path;
    }

    /**
     * Renders the Type 1 char string sequence to a GeneralPath.
     */
    private void render()
    {
        path = new Path();
        leftSideBearing = new PointF(0, 0);
        width = 0;
        List<Number> numbers = new ArrayList<>();
        type1Sequence.forEach(obj -> {
            if (obj instanceof CharStringCommand)
            {
                handleType1Command(numbers, (CharStringCommand) obj);
            }
            else
            {
                numbers.add((Number) obj);
            }
        });
    }

    private void handleType1Command(List<Number> numbers, CharStringCommand command)
    {
        commandCount++;
        CharStringCommand.Type1KeyWord type1KeyWord = command.getType1KeyWord();
        if (type1KeyWord == null)
        {
            numbers.clear();
            return;
        }
        switch(type1KeyWord)
        {
            case RMOVETO:
                if (numbers.size() >= 2)
                {
                    if (isFlex)
                    {
                        flexPoints.add(new PointF(numbers.get(0).floatValue(), numbers.get(1).floatValue()));
                    }
                    else
                    {
                        rmoveTo(numbers.get(0), numbers.get(1));
                    }
                }
                break;
            case VMOVETO:
                if (!numbers.isEmpty())
                {
                    if (isFlex)
                    {
                        // not in the Type 1 spec, but exists in some fonts
                        flexPoints.add(new PointF(0f, numbers.get(0).floatValue()));
                    }
                    else
                    {
                        rmoveTo(0, numbers.get(0));
                    }
                }
                break;
            case HMOVETO:
                if (!numbers.isEmpty())
                {
                    if (isFlex)
                    {
                        // not in the Type 1 spec, but exists in some fonts
                        flexPoints.add(new PointF(numbers.get(0).floatValue(), 0f));
                    }
                    else
                    {
                        rmoveTo(numbers.get(0), 0);
                    }
                }
                break;
            case RLINETO:
                if (numbers.size() >= 2)
                {
                    rlineTo(numbers.get(0), numbers.get(1));
                }
                break;
            case HLINETO:
                if (!numbers.isEmpty())
                {
                    rlineTo(numbers.get(0), 0);
                }
                break;
            case VLINETO:
                if (!numbers.isEmpty())
                {
                    rlineTo(0, numbers.get(0));
                }
                break;
            case RRCURVETO:
                if (numbers.size() >= 6)
                {
                    rrcurveTo(numbers.get(0), numbers.get(1), numbers.get(2),
                            numbers.get(3), numbers.get(4), numbers.get(5));
                }
                break;
            case CLOSEPATH:
                closeCharString1Path();
                break;
            case SBW:
                if (numbers.size() >= 3)
                {
                    leftSideBearing = new PointF(numbers.get(0).floatValue(), numbers.get(1).floatValue());
                    width = numbers.get(2).intValue();
                    current.set(leftSideBearing);
                }
                break;
            case HSBW:
                if (numbers.size() >= 2)
                {
                    leftSideBearing = new PointF(numbers.get(0).floatValue(), 0);
                    width = numbers.get(1).intValue();
                    current.set(leftSideBearing);
                }
                break;
            case VHCURVETO:
                if (numbers.size() >= 4)
                {
                    rrcurveTo(0, numbers.get(0), numbers.get(1),
                            numbers.get(2), numbers.get(3), 0);
                }
                break;
            case HVCURVETO:
                if (numbers.size() >= 4)
                {
                    rrcurveTo(numbers.get(0), 0, numbers.get(1),
                            numbers.get(2), 0, numbers.get(3));
                }
                break;
            case SEAC:
                if (numbers.size() >= 5)
                {
                    seac(numbers.get(0), numbers.get(1), numbers.get(2), numbers.get(3), numbers.get(4));
                }
                break;
            case SETCURRENTPOINT:
                if (numbers.size() >= 2)
                {
                    setcurrentpoint(numbers.get(0), numbers.get(1));
                }
                break;
            case CALLOTHERSUBR:
                if (!numbers.isEmpty())
                {
                    callothersubr(numbers.get(0).intValue());
                }
                break;
            case DIV:
                if (numbers.size() >= 2)
                {
                    float b = numbers.get(numbers.size() - 1).floatValue();
                    float a = numbers.get(numbers.size() - 2).floatValue();

                    float result = a / b;

                    numbers.remove(numbers.size() - 1);
                    numbers.remove(numbers.size() - 1);
                    numbers.add(result);
                    return;
                }
                break;
            case HSTEM:
            case VSTEM:
            case HSTEM3:
            case VSTEM3:
            case DOTSECTION:
                // ignore hints
                break;
            case ENDCHAR:
                // end
                break;
            case RET:
            case CALLSUBR:
                // indicates an invalid charstring
                Log.w("PdfBox-Android", "Unexpected charstring command: " + type1KeyWord + " in glyph " +
                        glyphName + " of font " + fontName);
                break;
            default:
                // indicates a PDFBox bug
                throw new IllegalArgumentException("Unhandled command: " + type1KeyWord);
        }
        numbers.clear();
    }

    /**
     * Sets the current absolute point without performing a moveto.
     * Used only with results from callothersubr
     */
    private void setcurrentpoint(Number x, Number y)
    {
        current.set(x.floatValue(), y.floatValue());
    }

    /**
     * Flex (via OtherSubrs)
     * @param num OtherSubrs entry number
     */
    private void callothersubr(int num)
    {
        if (num == 0)
        {
            // end flex
            isFlex = false;

            if (flexPoints.size() < 7)
            {
                Log.w("PdfBox-Android", "flex without moveTo in font " + fontName + ", glyph " + glyphName +
                    ", command " + commandCount);
                return;
            }

            // reference point is relative to start point
            PointF reference = flexPoints.get(0);
            reference.set(current.x + reference.x,
                current.y + reference.y);

            // first point is relative to reference point
            PointF first = flexPoints.get(1);
            first.set(reference.x + first.x, reference.y + first.y);

            // make the first point relative to the start point
            first.set(first.x - current.x, first.y - current.y);

            PointF p1 = flexPoints.get(1);
            PointF p2 = flexPoints.get(2);
            PointF p3 = flexPoints.get(3);
            rrcurveTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y);

            PointF p4 = flexPoints.get(4);
            PointF p5 = flexPoints.get(5);
            PointF p6 = flexPoints.get(6);
            rrcurveTo(p4.x, p4.y, p5.x, p5.y, p6.x, p6.y);

            flexPoints.clear();
        }
        else if (num == 1)
        {
            // begin flex
            isFlex = true;
        }
        else
        {
            Log.w("PdfBox-Android", "Invalid callothersubr parameter: " + num);
        }
    }

    /**
     * Relative moveto.
     */
    private void rmoveTo(Number dx, Number dy)
    {
        float x = (float)current.x + dx.floatValue();
        float y = (float)current.y + dy.floatValue();
        path.moveTo(x, y);
        current.set(x, y);
    }

    /**
     * Relative lineto.
     */
    private void rlineTo(Number dx, Number dy)
    {
        float x = (float)current.x + dx.floatValue();
        float y = (float)current.y + dy.floatValue();
        if (path.isEmpty())
        {
            Log.w("PdfBox-Android", "rlineTo without initial moveTo in font " + fontName + ", glyph " + glyphName);
            path.moveTo(x, y);
        }
        else
        {
            path.lineTo(x, y);
        }
        current.set(x, y);
    }

    /**
     * Relative curveto.
     */
    private void rrcurveTo(Number dx1, Number dy1, Number dx2, Number dy2,
                           Number dx3, Number dy3)
    {
        float x1 = (float) current.x + dx1.floatValue();
        float y1 = (float) current.y + dy1.floatValue();
        float x2 = x1 + dx2.floatValue();
        float y2 = y1 + dy2.floatValue();
        float x3 = x2 + dx3.floatValue();
        float y3 = y2 + dy3.floatValue();
        if (path.isEmpty())
        {
            Log.w("PdfBox-Android", "rrcurveTo without initial moveTo in font " + fontName + ", glyph " + glyphName);
            path.moveTo(x3, y3);
        }
        else
        {
            path.cubicTo(x1, y1, x2, y2, x3, y3);
        }
        current.set(x3, y3);
    }

    /**
     * Close path.
     */
    private void closeCharString1Path()
    {
        if (path.isEmpty())
        {
            Log.w("PdfBox-Android", "closepath without initial moveTo in font " + fontName + ", glyph " + glyphName);
        }
        else
        {
            path.close();
        }
        path.moveTo(current.x, current.y);
    }

    /**
     * Standard Encoding Accented Character
     *
     * Makes an accented character from two other characters.
     * @param asb
     */
    private void seac(Number asb, Number adx, Number ady, Number bchar, Number achar)
    {
        // base character
        String baseName = StandardEncoding.INSTANCE.getName(bchar.intValue());
        try
        {
            Type1CharString base = font.getType1CharString(baseName);
            IOUtils.appendPath(path, base.getPath(), null);
        }
        catch (IOException e)
        {
            Log.w("PdfBox-Android", "invalid seac character in glyph " + glyphName + " of font " + fontName);
        }
        // accent character
        String accentName = StandardEncoding.INSTANCE.getName(achar.intValue());
        try
        {
            Type1CharString accent = font.getType1CharString(accentName);
            Path accentPath = accent.getPath();
            if (path == accentPath)
            {
                // PDFBOX-5339: avoid ArrayIndexOutOfBoundsException 
                // reproducable with poc file crash-4698e0dc7833a3f959d06707e01d03cda52a83f4
                Log.w("PdfBox-Android", "Path for " + baseName + " and for accent " + accentName + " are same, ignored");
                return;
            }
            AffineTransform at = AffineTransform.getTranslateInstance(
                    leftSideBearing.x + adx.floatValue() - asb.floatValue(),
                    leftSideBearing.y + ady.floatValue());

            IOUtils.appendPath(path,accentPath,at.toMatrix());
        }
        catch (IOException e)
        {
            Log.w("PdfBox-Android", "invalid seac character in glyph " + glyphName + " of font " + fontName);
        }
    }

    /**
     * Add a command to the type1 sequence.
     *
     * @param numbers the parameters of the command to be added
     * @param command the command to be added
     */
    protected void addCommand(List<Number> numbers, CharStringCommand command)
    {
        type1Sequence.addAll(numbers);
        type1Sequence.add(command);
    }

    /**
     * Indicates if the underlying type1 sequence is empty.
     *
     * @return true if the sequence is empty
     */
    protected boolean isSequenceEmpty()
    {
        return type1Sequence.isEmpty();
    }

    /**
     * Returns the last entry of the underlying type1 sequence.
     *
     * @return the last entry of the type 1 sequence or null if empty
     */
    protected Object getLastSequenceEntry()
    {
        if (!type1Sequence.isEmpty())
        {
            return type1Sequence.get(type1Sequence.size() - 1);
        }
        return null;
    }

    @Override
    public String toString()
    {
        return type1Sequence.toString().replace("|","\n").replace(",", " ");
    }
}
