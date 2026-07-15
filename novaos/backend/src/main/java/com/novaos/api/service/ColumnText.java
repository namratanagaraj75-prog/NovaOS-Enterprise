package com.novaos.api.service;

import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.PdfContentByte;

final class ColumnText {
    private ColumnText() {}
    static void showTextAligned(PdfContentByte canvas, int alignment, Phrase phrase,
            float x, float y, float rotation) {
        com.itextpdf.text.pdf.ColumnText.showTextAligned(canvas, alignment, phrase, x, y, rotation);
    }
}
