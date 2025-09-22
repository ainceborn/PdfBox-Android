package com.tom_roush.fontbox.ttf.table;

public class LangSysTable
{
    private final int lookupOrder;
    private final int requiredFeatureIndex;
    private final int featureIndexCount;
    private final int[] featureIndices;

    public LangSysTable(int lookupOrder, int requiredFeatureIndex, int featureIndexCount,
            int[] featureIndices)
    {
        this.lookupOrder = lookupOrder;
        this.requiredFeatureIndex = requiredFeatureIndex;
        this.featureIndexCount = featureIndexCount;
        this.featureIndices = featureIndices;
    }

    public int getLookupOrder()
    {
        return lookupOrder;
    }

    public int getRequiredFeatureIndex()
    {
        return requiredFeatureIndex;
    }

    public int getFeatureIndexCount()
    {
        return featureIndexCount;
    }

    public int[] getFeatureIndices()
    {
        return featureIndices;
    }

    @Override
    public String toString()
    {
        return String.format("LangSysTable[requiredFeatureIndex=%d]", requiredFeatureIndex);
    }
}