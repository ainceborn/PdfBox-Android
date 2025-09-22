package com.tom_roush.fontbox.ttf.table.common;

public class FeatureTable
{
    private final int featureParams;
    private final int lookupIndexCount;
    private final int[] lookupListIndices;

    public FeatureTable(int featureParams, int lookupIndexCount, int[] lookupListIndices)
    {
        this.featureParams = featureParams;
        this.lookupIndexCount = lookupIndexCount;
        this.lookupListIndices = lookupListIndices;
    }

    public int getFeatureParams()
    {
        return featureParams;
    }

    public int getLookupIndexCount()
    {
        return lookupIndexCount;
    }

    public int[] getLookupListIndices()
    {
        return lookupListIndices;
    }

    @Override
    public String toString()
    {
        return String.format("FeatureTable[lookupListIndicesCount=%d]", lookupListIndices.length);
    }
}