package com.tom_roush.fontbox.ttf.gsub;

import java.util.List;

/**
 * This class is responsible for replacing GlyphIDs with new ones according to the GSUB tables. Each language should
 * have an implementation of this.
 * 
 * @author Palash Ray
 * 
 */
public interface GsubWorker
{
    /**
     * Applies language-specific transforms including GSUB and any other pre or post-processing necessary for displaying
     * Glyphs correctly.
     * 
     * @param originalGlyphIds list of original glyph IDs
     * @return list of transformed glyph IDs
     * 
     */
    List<Integer> applyTransforms(List<Integer> originalGlyphIds);

}
