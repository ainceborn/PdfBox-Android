package com.tom_roush.fontbox.ttf.table;

import java.util.Map;

public class ScriptTable
{
    private final LangSysTable defaultLangSysTable;
    private final Map<String, LangSysTable> langSysTables;

    public ScriptTable(LangSysTable defaultLangSysTable, Map<String, LangSysTable> langSysTables)
    {
        this.defaultLangSysTable = defaultLangSysTable;
        this.langSysTables = langSysTables;
    }

    public LangSysTable getDefaultLangSysTable()
    {
        return defaultLangSysTable;
    }

    public Map<String, LangSysTable> getLangSysTables()
    {
        return langSysTables;
    }

    @Override
    public String toString()
    {
        return String.format("ScriptTable[hasDefault=%s,langSysRecordsCount=%d]",
                defaultLangSysTable != null, langSysTables.size());
    }
}