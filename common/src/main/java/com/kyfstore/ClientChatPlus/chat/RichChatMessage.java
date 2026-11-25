package com.kyfstore.ClientChatPlus.chat;

import com.kyfstore.ClientChatPlus.config.ChatConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RichChatMessage {
    public static final long ANIMATION_DURATION_NS = 220_000_000L;

    public final UUID id = UUID.randomUUID();
    public final long createdAtNanos = System.nanoTime();
    public final Component originalComponent;
    public final String rawText;

    public boolean bookmarked = false;

    public List<ChatFormattedLine> formattedLines;
    public final List<ChatCodeBlock> codeBlocks = new ArrayList<>();
    public final List<String> inlineImageUrls = new ArrayList<>();
    public final List<String> attachmentUrls = new ArrayList<>();
    public int lastWrapWidth = -1;
    public int totalHeight = 0;

    public boolean hasMergedCodeBlocks = false;

    public RichChatMessage(Component component) {
        this.originalComponent = component;
        this.rawText = component.getString();
    }

    public float getAppearProgress() {
        long elapsed = System.nanoTime() - createdAtNanos;
        if (elapsed >= ANIMATION_DURATION_NS) return 1.0f;
        if (elapsed <= 0L) return 0.0f;

        float t = (float) elapsed / (float) ANIMATION_DURATION_NS;
        float inv = 1.0f - t;
        return 1.0f - (inv * inv * inv * inv);
    }

    public float getIdleFadeAlpha(boolean chatFocused) {
        if (chatFocused) return 1.0f;
        double elapsedSeconds = (System.nanoTime() - createdAtNanos) / 1_000_000_000.0;
        if (elapsedSeconds < 9.0) return 1.0f;
        if (elapsedSeconds >= 10.0) return 0.0f;
        return (float) (10.0 - elapsedSeconds);
    }

    public void recalculateLayout(Font font, int maxWidth) {
        ChatConfig cfg = ChatConfig.get();
        if (this.formattedLines != null && this.lastWrapWidth == maxWidth) return;
        this.lastWrapWidth = maxWidth;
        MessageFormatter.extractMediaAndFormat(this);

        int total = 0;
        for (ChatFormattedLine line : this.formattedLines) {
            total += line.wrapAndMeasure(font, maxWidth, cfg.lineSpacing);
        }

        for (ChatCodeBlock block : this.codeBlocks) {
            total += block.wrapAndMeasure(font, maxWidth) + 4;
        }

        for (String imgUrl : this.inlineImageUrls) {
            MediaCacheManager.CachedImage img = MediaCacheManager.get().getOrFetchImage(imgUrl);
            if (img.ready && img.width > 0 && img.height > 0) {
                int maxImgW = Math.min(maxWidth - 4, 220);
                int maxImgH = 120;
                float ratio = Math.min(1.0f, Math.min((float) maxImgW / img.width, (float) maxImgH / img.height));
                total += Mth.ceil(img.height * ratio) + 4;
            } else if (!img.failed) {
                total += font.lineHeight + 4;
            }
        }

        for (String attUrl : this.attachmentUrls) {
            MediaCacheManager.CachedAttachment att = MediaCacheManager.get().getOrFetchAttachment(attUrl);
            if (att.videoPlayer != null && !att.videoPlayer.isBroken()) {
                int vw = att.videoPlayer.getWidth() > 0 ? att.videoPlayer.getWidth() : 176;
                int vh = att.videoPlayer.getHeight() > 0 ? att.videoPlayer.getHeight() : 99;
                int maxVidW = Math.min(maxWidth - 4, 220);
                int maxVidH = 120;
                float ratio = Math.min(1.0f, Math.min((float) maxVidW / vw, (float) maxVidH / vh));
                total += 14 + Math.max(64, Mth.ceil(vh * ratio)) + 18 + 4;
            } else if (att.textPreviewBlock != null) {
                att.textPreviewBlock.wrapAndMeasure(font, maxWidth);
                int innerCodeH = Math.max(font.lineHeight, att.textPreviewBlock.wrappedLines.size() * (font.lineHeight + 2));
                total += 24 + innerCodeH + 6;
            } else {
                total += 24;
            }
        }

        this.totalHeight = Math.max(font.lineHeight, total) + cfg.messageSpacing;
    }
}
