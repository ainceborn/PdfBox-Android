package com.tom_roush.pdfbox.cos;

/**
 * An instance of {@link COSDocumentState} collects all known states a {@link COSDocument} may have and shall allow
 * their evaluation.
 *
 * @author Christian Appl
 * @see COSDocument
 */
public class COSDocumentState
{
    
    /**
     * The parsing state of the document.
     * <ul>
     * <li>{@code true}, if the document is currently being parsed. (initial state)</li>
     * <li>{@code false}, if the document's parsing completed and it may be edited and updated.</li>
     * </ul>
     */
    private boolean parsing = true;
    
    /**
     * Sets the {@link #parsing} state of the document.
     *
     * @param parsing The {@link #parsing} state to set.
     */
    public void setParsing(boolean parsing)
    {
        this.parsing = parsing;
    }
    
    /**
     * Returns {@code true}, if the document´s {@link #parsing} is completed and it may be updated.
     *
     * @return {@code true}, if the document´s {@link #parsing} is completed and it may be updated.
     */
    public boolean isAcceptingUpdates()
    {
        return !parsing;
    }
    
}
