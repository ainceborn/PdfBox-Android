package com.tom_roush.pdfbox.util;

import java.util.regex.Pattern;

public final class StringUtil
{
    public static final Pattern PATTERN_SPACE = Pattern.compile("\\s");

    public static String[] splitOnSpace(String s)
    {
        return PATTERN_SPACE.split(s);
    }

    /**
     * Split at spaces but keep them
     *
     * @param s
     * @return
     */
    public static String[] tokenizeOnSpace(String s)
    {
        return s.split("(?<=" + StringUtil.PATTERN_SPACE + ")|(?=" + StringUtil.PATTERN_SPACE + ")");
    }
}