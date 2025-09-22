package com.tom_roush.fontbox.ttf.table;

public class LigatureTable
{
    private final int ligatureGlyph;
    private final int componentCount;
    private final int[] componentGlyphIDs;

    public LigatureTable(int ligatureGlyph, int componentCount, int[] componentGlyphIDs)
    {
        this.ligatureGlyph = ligatureGlyph;
        this.componentCount = componentCount;
        this.componentGlyphIDs = componentGlyphIDs;
    }

    public int getLigatureGlyph()
    {
        return ligatureGlyph;
    }

    public int getComponentCount()
    {
        return componentCount;
    }

    public int[] getComponentGlyphIDs()
    {
        return componentGlyphIDs;
    }

    @Override
    public String toString()
    {
        return String.format("%s[ligatureGlyph=%d, componentCount=%d]",
                LigatureTable.class.getSimpleName(), ligatureGlyph, componentCount);
    }
}