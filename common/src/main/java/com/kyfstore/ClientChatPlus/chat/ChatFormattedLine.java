package com.kyfstore.ClientChatPlus.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class ChatFormattedLine {
    public final List<ScaledSpan> spans;
    public final boolean isBullet;
    public final boolean isQuote;
    public final List<VisualRow> visualRows = new ArrayList<>();

    public ChatFormattedLine(Component content, float scale, boolean isBullet) {
        this(content, scale, isBullet, false);
    }

    public ChatFormattedLine(Component content, float scale, boolean isBullet, boolean isQuote) {
        this.spans = List.of(new ScaledSpan(content, scale));
        this.isBullet = isBullet;
        this.isQuote = isQuote;
    }

    public ChatFormattedLine(List<ScaledSpan> spans, boolean isBullet) {
        this(spans, isBullet, false);
    }

    public ChatFormattedLine(List<ScaledSpan> spans, boolean isBullet, boolean isQuote) {
        this.spans = spans;
        this.isBullet = isBullet;
        this.isQuote = isQuote;
    }

    public int wrapAndMeasure(Font font, int maxChatWidth, int lineSpacing) {
        this.visualRows.clear();
        int indent = (isBullet || isQuote) ? 10 : 0;
        int maxRowWidth = Math.max(20, maxChatWidth - indent);

        VisualRow currentRow = new VisualRow();
        float cursorX = 0.0f;

        for (ScaledSpan span : spans) {
            if (span.content.getString().isEmpty()) continue;

            float remainingWidth = maxRowWidth - cursorX;
            if (remainingWidth < 16.0f && !currentRow.pieces.isEmpty()) {
                this.visualRows.add(currentRow);
                currentRow = new VisualRow();
                cursorX = 0.0f;
                remainingWidth = maxRowWidth;
            }

            int availFirstLine = Math.max(12, Mth.floor(remainingWidth / span.scale));
            List<FormattedCharSequence> firstSplit = font.split(span.content, availFirstLine);

            if (firstSplit.isEmpty()) continue;

            FormattedCharSequence firstSeq = firstSplit.getFirst();
            float firstWidth = font.width(firstSeq) * span.scale;

            if (firstWidth > remainingWidth + 1.0f && !currentRow.pieces.isEmpty()) {
                this.visualRows.add(currentRow);
                currentRow = new VisualRow();
                cursorX = 0.0f;
            } else {
                currentRow.addPiece(firstSeq, cursorX, span.scale, font.lineHeight, lineSpacing);
                cursorX += firstWidth;
                if (firstSplit.size() == 1) {
                    continue;
                }
                this.visualRows.add(currentRow);
                currentRow = new VisualRow();
                cursorX = 0.0f;
            }

            int fullAvail = Math.max(20, Mth.floor(maxRowWidth / span.scale));
            List<FormattedCharSequence> fullSplit = font.split(span.content, fullAvail);
            int startIndex = (firstWidth <= remainingWidth + 1.0f) ? 1 : 0;

            for (int i = startIndex; i < fullSplit.size(); i++) {
                FormattedCharSequence seq = fullSplit.get(i);
                float w = font.width(seq) * span.scale;
                currentRow.addPiece(seq, cursorX, span.scale, font.lineHeight, lineSpacing);
                cursorX += w;
                if (i < fullSplit.size() - 1) {
                    this.visualRows.add(currentRow);
                    currentRow = new VisualRow();
                    cursorX = 0.0f;
                }
            }
        }

        if (!currentRow.pieces.isEmpty()) {
            this.visualRows.add(currentRow);
        }

        int totalHeight = 0;
        for (VisualRow row : this.visualRows) {
            totalHeight += row.height;
        }
        return totalHeight;
    }

    public record ScaledSpan(Component content, float scale) {
    }

    public record PositionedPiece(FormattedCharSequence seq, float offsetX, float scale) {
    }

    public static class VisualRow {
        public final List<PositionedPiece> pieces = new ArrayList<>();
        public int height = 0;

        public void addPiece(FormattedCharSequence seq, float offsetX, float scale, int fontLineHeight, int lineSpacing) {
            pieces.add(new PositionedPiece(seq, offsetX, scale));
            int pieceHeight = Mth.ceil((fontLineHeight + lineSpacing) * scale);
            if (pieceHeight > this.height) {
                this.height = pieceHeight;
            }
        }
    }
}
