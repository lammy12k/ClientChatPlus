package com.kyfstore.ClientChatPlus.chat;

import com.kyfstore.ClientChatPlus.config.ChatConfig;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CustomChatRenderer {
    private static Style hoveredStyle = null;
    private static final List<ClickZone> activeClickZones = new ArrayList<>();
    private static ChatCodeBlock lastCopiedBlock = null;
    private static long lastCopiedAtNanos = 0L;
    private static UUID lastCopiedMessageId = null;
    private static long lastCopiedMessageAtNanos = 0L;

    public static int getChatWidth(Minecraft mc) {
        return Mth.floor(mc.options.chatWidth().get() * 280.0 + 40.0);
    }

    public static int getChatHeight(Minecraft mc, boolean focused) {
        double optionValue = focused ? mc.options.chatHeightFocused().get() : mc.options.chatHeightUnfocused().get();
        return Mth.floor(optionValue * 160.0 + 20.0);
    }

    private static String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long mins = totalSec / 60L;
        long secs = totalSec % 60L;
        return mins + ":" + (secs < 10 ? "0" + secs : secs);
    }

    public static void render(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean focused) {
        Minecraft mc = Minecraft.getInstance();
        ChatHistoryManager manager = ChatHistoryManager.getInstance();
        List<RichChatMessage> messages = manager.getVisibleMessages();

        Font font = mc.font;
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        ChatConfig cfg = ChatConfig.get();
        float scale = cfg.chatScale;
        int chatWidth = Mth.floor(getChatWidth(mc) / scale);
        int chatHeight = Mth.floor(getChatHeight(mc, focused) / scale);

        int leftX = cfg.offsetX;
        int bottomY = screenHeight - cfg.offsetY;
        int topY = bottomY - chatHeight;

        if (focused) {
            hoveredStyle = null;
            activeClickZones.clear();
            renderTopToolbar(graphics, font, leftX, Math.max(2, topY - 14), mouseX, mouseY, manager);
        }

        if (messages.isEmpty()) return;

        float pushOffset = manager.updateAndGetPushOffset();

        int totalHistoryHeight = 0;
        for (RichChatMessage msg : messages) {
            msg.recalculateLayout(font, chatWidth);
            totalHistoryHeight += msg.totalHeight;
        }
        manager.setMaxScrollPixels(Math.max(0, totalHistoryHeight - chatHeight));
        int scrollPixels = focused ? manager.getScrollPixels() : 0;

        graphics.enableScissor(leftX - 2, topY, leftX + Mth.ceil(chatWidth * scale) + 8, bottomY + 4);

        float cursorY = bottomY + pushOffset + (scrollPixels * scale);

        for (RichChatMessage msg : messages) {
            float idleAlpha = msg.getIdleFadeAlpha(focused);
            if (idleAlpha <= 0.01f) continue;

            msg.recalculateLayout(font, chatWidth);
            float scaledHeight = msg.totalHeight * scale;
            cursorY -= scaledHeight;

            if (cursorY > bottomY) {
                continue;
            }
            if (cursorY + scaledHeight < topY) {
                break;
            }

            float progress = msg.getAppearProgress();
            float combinedAlpha = progress * idleAlpha;
            if (combinedAlpha <= 0.01f) continue;

            float slideOffsetY = (1.0f - progress) * 12.0f;
            float drawY = cursorY + slideOffsetY;

            int screenMsgLeft = leftX - 2;
            int screenMsgTop = Mth.floor(drawY - 1);
            int screenMsgRight = leftX + Mth.ceil((chatWidth + 4) * scale);
            int screenMsgBottom = Mth.floor(drawY + scaledHeight - 1);

            boolean isMsgHovered = focused
                    && mouseX >= screenMsgLeft && mouseX <= screenMsgRight
                    && mouseY >= Math.max(topY, screenMsgTop) && mouseY <= Math.min(bottomY, screenMsgBottom);

            graphics.pose().pushMatrix();
            graphics.pose().translate((float) leftX, drawY);
            graphics.pose().scale(scale, scale);

            int bgOpacity = (int) (mc.options.textBackgroundOpacity().get() * 255.0 * combinedAlpha);
            if (isMsgHovered) {
                bgOpacity = Math.min(255, bgOpacity + 45);
            }
            if (bgOpacity > 3) {
                int bgColor = (bgOpacity << 24) | (msg.bookmarked ? 0x2B2200 : 0x000000);
                graphics.fill(-2, -1, chatWidth + 4, msg.totalHeight - 1, bgColor);
            }

            int textAlpha = Math.max(4, Mth.clamp((int) (combinedAlpha * 255.0f), 0, 255));
            int textColor = (textAlpha << 24) | 0xFFFFFF;

            int lineY = 1;
            for (ChatFormattedLine fLine : msg.formattedLines) {
                if (fLine.visualRows.isEmpty() && !msg.codeBlocks.isEmpty()) {
                    continue;
                }
                int indent = fLine.isBullet ? 10 : 0;
                if (fLine.isBullet) {
                    graphics.text(font, "•", 2, lineY, textColor, true);
                }

                for (ChatFormattedLine.VisualRow row : fLine.visualRows) {
                    for (ChatFormattedLine.PositionedPiece piece : row.pieces) {
                        float pieceX = indent + piece.offsetX();
                        float pieceY = lineY + (row.height - (font.lineHeight + cfg.lineSpacing) * piece.scale()) * 0.5f;

                        graphics.pose().pushMatrix();
                        graphics.pose().translate(pieceX, pieceY);
                        graphics.pose().scale(piece.scale(), piece.scale());
                        graphics.text(font, piece.seq(), 0, 0, textColor, true);
                        graphics.pose().popMatrix();

                        if (isMsgHovered) {
                            float rowScreenTop = drawY + lineY * scale;
                            float rowScreenBottom = rowScreenTop + row.height * scale;
                            if (mouseY >= rowScreenTop && mouseY <= rowScreenBottom) {
                                int localX = Mth.floor(((mouseX - leftX) / scale - pieceX) / piece.scale());
                                Style s = getStyleAtX(font, piece.seq(), localX);
                                if (s != null) {
                                    hoveredStyle = s;
                                }
                            }
                        }
                    }
                    lineY += row.height;
                }
            }

            for (ChatCodeBlock block : msg.codeBlocks) {
                int boxW = chatWidth - 2;
                int boxH = block.boxHeight;
                int borderCol = (textAlpha << 24) | 0x2B2D31;
                int bodyCol = (textAlpha << 24) | 0x16181D;
                int headerCol = (textAlpha << 24) | 0x1E2128;

                graphics.fill(0, lineY, boxW, lineY + boxH, borderCol);
                graphics.fill(1, lineY + 1, boxW - 1, lineY + boxH - 1, bodyCol);
                graphics.fill(1, lineY + 1, boxW - 1, lineY + 13, headerCol);

                graphics.text(font, block.language, 5, lineY + 3, (textAlpha << 24) | 0x99AAB5, false);

                renderCopyButton(graphics, font, mc, block, boxW - 2, lineY + 2, leftX, drawY, scale, textAlpha, mouseX, mouseY, focused);

                int codeY = lineY + 16;
                for (FormattedCharSequence codeLine : block.wrappedLines) {
                    graphics.text(font, codeLine, 5, codeY, textColor, false);
                    codeY += font.lineHeight + 2;
                }

                lineY += boxH + 4;
            }

            for (String imgUrl : msg.inlineImageUrls) {
                MediaCacheManager.CachedImage img = MediaCacheManager.get().getOrFetchImage(imgUrl);
                Identifier currentTex = img.getCurrentTextureId();
                if (img.ready && currentTex != null && img.width > 0 && img.height > 0) {
                    int maxImgW = Math.min(chatWidth - 4, 220);
                    int maxImgH = 120;
                    float ratio = Math.min(1.0f, Math.min((float) maxImgW / img.width, (float) maxImgH / img.height));
                    int drawW = Math.max(1, Mth.floor(img.width * ratio));
                    int drawH = Math.max(1, Mth.floor(img.height * ratio));
                    graphics.blit(RenderPipelines.GUI_TEXTURED, currentTex, 0, lineY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
                    lineY += drawH + 4;
                } else if (!img.failed) {
                    graphics.text(font, "§7[Loading image...]", 0, lineY, textColor, true);
                    lineY += font.lineHeight + 4;
                }
            }

            for (String attUrl : msg.attachmentUrls) {
                MediaCacheManager.CachedAttachment att = MediaCacheManager.get().getOrFetchAttachment(attUrl);
                if (att.videoPlayer != null && !att.videoPlayer.isBroken()) {
                    VideoPlayerHandle vp = att.videoPlayer;
                    Identifier vidTex = vp.updateAndGetTextureId();
                    int vw = vp.getWidth() > 0 ? vp.getWidth() : 176;
                    int vh = vp.getHeight() > 0 ? vp.getHeight() : 99;
                    int maxVidW = Math.min(chatWidth - 4, 220);
                    int maxVidH = 120;
                    float ratio = Math.min(1.0f, Math.min((float) maxVidW / vw, (float) maxVidH / vh));
                    int drawW = Math.max(150, Mth.floor(vw * ratio));
                    int drawH = Math.max(64, Mth.floor(vh * ratio));
                    int cardW = drawW + 4;
                    int controlsY = lineY + 13 + drawH;
                    int cardH = 13 + drawH + 17;

                    graphics.fill(0, lineY, cardW, lineY + cardH, (textAlpha << 24) | 0x2B2D31);
                    graphics.fill(1, lineY + 1, cardW - 1, lineY + cardH - 1, (textAlpha << 24) | 0x16181D);

                    String shortName = att.fileName.length() > 28 ? att.fileName.substring(0, 25) + "..." : att.fileName;
                    graphics.text(font, shortName, 5, lineY + 3, (textAlpha << 24) | 0x58A6FF, false);

                    int vidTop = lineY + 13;
                    graphics.fill(2, vidTop, 2 + drawW, vidTop + drawH, (textAlpha << 24) | 0x050507);
                    if (vidTex != null && vp.getWidth() > 0 && vp.getHeight() > 0) {
                        graphics.blit(RenderPipelines.GUI_TEXTURED, vidTex, 2, vidTop, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
                    } else {
                        String statusLabel = vp.isPaused() ? "Click ▶ to Play" : "Loading video...";
                        int labelX = 2 + Math.max(4, (drawW - font.width(statusLabel)) / 2);
                        int labelY = vidTop + Math.max(4, (drawH - font.lineHeight) / 2);
                        graphics.text(font, statusLabel, labelX, labelY, (textAlpha << 24) | 0x8899AA, false);
                    }

                    graphics.fill(2, controlsY, cardW - 2, controlsY + 15, (textAlpha << 24) | 0x1E2128);
                    String pauseIcon = vp.isPaused() ? "▶" : "⏸";
                    graphics.text(font, pauseIcon, 5, controlsY + 4, (textAlpha << 24) | 0xFFFFFF, false);

                    long curMs = vp.getTimeMs();
                    long durMs = vp.getDurationMs();
                    String timeLabel = formatDuration(curMs) + "/" + formatDuration(durMs);
                    int timeW = font.width(timeLabel);

                    int muteX = cardW - 36;
                    int volBarX1 = cardW - 25;
                    int volBarW = 20;
                    int timeX = muteX - timeW - 4;
                    int seekBarX1 = 16;
                    int seekBarX2 = Math.max(seekBarX1 + 20, timeX - 4);
                    int seekBarW = seekBarX2 - seekBarX1;

                    graphics.fill(seekBarX1, controlsY + 6, seekBarX2, controlsY + 9, (textAlpha << 24) | 0x3A3F4B);
                    float vidProgress = (durMs > 0) ? Mth.clamp((float) curMs / (float) durMs, 0.0f, 1.0f) : 0.0f;
                    int fillW = Mth.floor(seekBarW * vidProgress);
                    if (fillW > 0) {
                        graphics.fill(seekBarX1, controlsY + 6, seekBarX1 + fillW, controlsY + 9, (textAlpha << 24) | 0x5865F2);
                    }
                    graphics.fill(seekBarX1 + fillW - 1, controlsY + 5, seekBarX1 + fillW + 2, controlsY + 10, (textAlpha << 24) | 0xFFFFFF);

                    graphics.text(font, timeLabel, timeX, controlsY + 4, (textAlpha << 24) | 0xB9BBBE, false);

                    String muteIcon = vp.isMuted() ? "🔇" : "🔊";
                    graphics.text(font, muteIcon, muteX, controlsY + 4, (textAlpha << 24) | 0xDCDDDE, false);

                    graphics.fill(volBarX1, controlsY + 6, volBarX1 + volBarW, controlsY + 9, (textAlpha << 24) | 0x3A3F4B);
                    int volFillW = Mth.floor(volBarW * (vp.getVolume() / 100.0f));
                    if (volFillW > 0) {
                        graphics.fill(volBarX1, controlsY + 6, volBarX1 + volFillW, controlsY + 9, (textAlpha << 24) | 0x57F287);
                    }

                    if (focused) {
                        int ctrlTopScr = Mth.floor(drawY + controlsY * scale);
                        int ctrlBotScr = Mth.floor(drawY + (controlsY + 15) * scale);

                        int pX1 = leftX + Mth.floor(2 * scale);
                        int pX2 = leftX + Mth.ceil(15 * scale);
                        activeClickZones.add(new ClickZone(pX1, ctrlTopScr, pX2, ctrlBotScr, vp::togglePause));

                        int sX1 = leftX + Mth.floor(seekBarX1 * scale);
                        int sX2 = leftX + Mth.ceil(seekBarX2 * scale);
                        final long totalDur = durMs;
                        final int sLeft = sX1;
                        final int sWidth = Math.max(1, sX2 - sX1);
                        activeClickZones.add(new ClickZone(sX1, ctrlTopScr, sX2, ctrlBotScr, () -> {
                            if (totalDur > 0) {
                                double pct = Mth.clamp((mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth() - sLeft) / (double) sWidth, 0.0, 1.0);
                                vp.seekMs((long) (totalDur * pct));
                            }
                        }));

                        int mX1 = leftX + Mth.floor(muteX * scale);
                        int mX2 = leftX + Mth.ceil((muteX + 10) * scale);
                        activeClickZones.add(new ClickZone(mX1, ctrlTopScr, mX2, ctrlBotScr, vp::toggleMute));

                        int vX1 = leftX + Mth.floor(volBarX1 * scale);
                        int vX2 = leftX + Mth.ceil((volBarX1 + volBarW) * scale);
                        final int vLeft = vX1;
                        final int vWidth = Math.max(1, vX2 - vX1);
                        activeClickZones.add(new ClickZone(vX1, ctrlTopScr, vX2, ctrlBotScr, () -> {
                            double pct = Mth.clamp((mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth() - vLeft) / (double) vWidth, 0.0, 1.0);
                            vp.setVolume((int) Math.round(pct * 100.0));
                        }));

                        int linkX1 = leftX + Mth.floor(4 * scale);
                        int linkY1 = Mth.floor(drawY + (lineY + 1) * scale);
                        int linkX2 = leftX + Mth.ceil(cardW * scale);
                        int linkY2 = Mth.floor(drawY + (lineY + 12) * scale);
                        activeClickZones.add(new ClickZone(linkX1, linkY1, linkX2, linkY2, () -> {
                            try {
                                ScreenClickInvoker.invoke(mc.gui.screen(), new ClickEvent.OpenUrl(URI.create(att.url)));
                            } catch (Exception ignored) {
                            }
                        }));
                    }
                    lineY += cardH + 4;
                    continue;
                }

                boolean hasPreview = att.textPreviewBlock != null;
                int cardW = hasPreview ? (chatWidth - 2) : Math.min(chatWidth - 4, Math.max(145, font.width(att.fileName) + 42));
                int innerCodeH = hasPreview ? Math.max(font.lineHeight, att.textPreviewBlock.wrappedLines.size() * (font.lineHeight + 2)) : 0;
                int cardH = hasPreview ? (24 + innerCodeH + 4) : 20;

                graphics.fill(0, lineY, cardW, lineY + cardH, (textAlpha << 24) | 0x2B2D31);
                graphics.fill(1, lineY + 1, cardW - 1, lineY + cardH - 1, (textAlpha << 24) | 0x1E2229);
                graphics.fill(0, lineY, 2, lineY + cardH, (textAlpha << 24) | 0x5865F2);

                if (att.isVideo) {
                    graphics.fill(5, lineY + 4, 16, lineY + 15, (textAlpha << 24) | 0x5865F2);
                    graphics.text(font, "▶", 8, lineY + 6, (textAlpha << 24) | 0xFFFFFF, false);
                } else {
                    drawFilePaperclipIcon(graphics, 6, lineY + 4, textAlpha);
                }
                graphics.text(font, att.fileName, 20, lineY + 2, (textAlpha << 24) | 0x58A6FF, false);
                graphics.text(font, att.sizeLabel, 20, lineY + 11, (textAlpha << 24) | 0x99AAB5, false);
                if (hasPreview) {
                    renderCopyButton(graphics, font, mc, att.textPreviewBlock, cardW - 3, lineY + 4, leftX, drawY, scale, textAlpha, mouseX, mouseY, focused);
                    graphics.fill(2, lineY + 21, cardW - 2, lineY + cardH - 2, (textAlpha << 24) | 0x14161B);
                    int codeY = lineY + 24;
                    for (FormattedCharSequence codeLine : att.textPreviewBlock.wrappedLines) {
                        graphics.text(font, codeLine, 6, codeY, textColor, false);
                        codeY += font.lineHeight + 2;
                    }
                }
                if (isMsgHovered) {
                    int screenCardX1 = leftX + Mth.floor(20 * scale);
                    int screenCardY1 = Mth.floor(drawY + (lineY + 1) * scale);
                    int screenCardX2 = leftX + Mth.ceil((20 + font.width(att.fileName)) * scale);
                    int screenCardY2 = screenCardY1 + Mth.ceil(11 * scale);
                    if (mouseX >= screenCardX1 && mouseX <= screenCardX2 && mouseY >= screenCardY1 && mouseY <= screenCardY2) {
                        graphics.requestCursor(CursorTypes.POINTING_HAND);
                    }
                    activeClickZones.add(new ClickZone(screenCardX1, screenCardY1, screenCardX2, screenCardY2, () -> {
                        try {
                            ScreenClickInvoker.invoke(mc.gui.screen(), new ClickEvent.OpenUrl(URI.create(att.url)));
                        } catch (Exception ignored) {
                        }
                    }));
                }
                lineY += cardH + 4;
            }
            graphics.pose().popMatrix();
            if (isMsgHovered) {
                renderMessageButtons(graphics, font, mc, screenMsgRight - 2, screenMsgTop + 1, mouseX, mouseY, msg, manager);
            }
        }
        graphics.disableScissor();
        if (focused && manager.getUnreadNewMessages() > 0 && manager.getScrollPixels() > 0) {
            renderNewMessagesPill(graphics, font, leftX, bottomY - 14, Mth.ceil(chatWidth * scale), mouseX, mouseY, manager);
        }
        if (focused && hoveredStyle != null) {
            if (hoveredStyle.getClickEvent() != null) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
            if (hoveredStyle.getClickEvent() instanceof ClickEvent.OpenUrl(URI uri)) {
                String url = uri.toString();
                boolean isImg = MediaCacheManager.isImageUrl(url);
                MediaCacheManager.CachedImage preview = null;
                if (isImg && cfg.renderImages) {
                    preview = MediaCacheManager.get().getOrFetchImage(url);
                } else if (!isImg && cfg.websiteHoverPreviews) {
                    preview = MediaCacheManager.get().getOrFetchWebsitePreview(url);
                }
                if (preview != null) {
                    renderHoverPreviewPopup(graphics, font, url, preview, mouseX, mouseY, mc.getWindow().getGuiScaledWidth(), screenHeight);
                    return;
                }
            }
            HoverEvent hover = hoveredStyle.getHoverEvent();
            if (hover instanceof HoverEvent.ShowText(Component value)) {
                graphics.setTooltipForNextFrame(font, font.split(value, 220), mouseX, mouseY);
            }
        }
    }
    private static void renderNewMessagesPill(GuiGraphicsExtractor graphics, Font font, int leftX, int y, int scaledChatWidth, int mouseX, int mouseY, ChatHistoryManager manager) {
        int count = manager.getUnreadNewMessages();
        String label = "↓ " + count + (count == 1 ? " New Message" : " New Messages");
        int w = font.width(label) + 12;
        int h = 13;
        int x = leftX + Math.max(4, (scaledChatWidth - w) / 2);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        int borderCol = hovered ? 0xFFFF6B6B : 0xFFDA373C;
        int bgCol = hovered ? 0xF0B8252A : 0xE68F1B1F;
        graphics.fill(x, y, x + w, y + h, borderCol);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bgCol);
        graphics.text(font, label, x + 6, y + 3, 0xFFFFFFFF, true);
        if (hovered) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        activeClickZones.add(new ClickZone(x, y, x + w, y + h, manager::jumpToBottom));
    }
    private static void drawFilePaperclipIcon(GuiGraphicsExtractor graphics, int x, int y, int alpha) {
        int docCol = (alpha << 24) | 0xB9BBBE;
        int foldCol = (alpha << 24) | 0x72767D;
        int clipCol = (alpha << 24) | 0x58A6FF;
        graphics.fill(x, y, x + 7, y + 11, docCol);
        graphics.fill(x + 7, y + 2, x + 9, y + 11, docCol);
        graphics.fill(x + 7, y, x + 9, y + 2, foldCol);
        graphics.fill(x + 2, y + 3, x + 6, y + 4, foldCol);
        graphics.fill(x + 2, y + 5, x + 7, y + 6, foldCol);
        graphics.fill(x + 2, y + 7, x + 5, y + 8, foldCol);
        graphics.fill(x + 8, y + 4, x + 11, y + 12, clipCol);
        graphics.fill(x + 9, y + 5, x + 10, y + 11, (alpha << 24) | 0x1E2229);
    }
    private static void renderCopyButton(GuiGraphicsExtractor graphics, Font font, Minecraft mc, ChatCodeBlock block, int rightX, int topY, int leftX, float drawY, float scale, int textAlpha, int mouseX, int mouseY, boolean focused) {
        boolean recentlyCopied = (block == lastCopiedBlock) && (System.nanoTime() - lastCopiedAtNanos < 1_500_000_000L);
        String copyLabel = recentlyCopied ? "Copied!" : "Copy";
        int copyW = font.width(copyLabel) + 6;
        int copyX1 = rightX - copyW;
        int copyY1 = topY;
        int copyX2 = rightX;
        int copyY2 = topY + 10;
        int screenCopyX1 = leftX + Mth.floor(copyX1 * scale);
        int screenCopyY1 = Mth.floor(drawY + copyY1 * scale);
        int screenCopyX2 = leftX + Mth.ceil(copyX2 * scale);
        int screenCopyY2 = Mth.floor(drawY + copyY2 * scale);
        boolean copyHovered = focused && mouseX >= screenCopyX1 && mouseX <= screenCopyX2 && mouseY >= screenCopyY1 && mouseY <= screenCopyY2;
        graphics.fill(copyX1, copyY1, copyX2, copyY2, (textAlpha << 24) | (copyHovered ? 0x3B404A : 0x262930));
        graphics.text(font, copyLabel, copyX1 + 3, copyY1 + 1, (textAlpha << 24) | (recentlyCopied ? 0x57F287 : 0xDCDDDE), false);
        if (copyHovered) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        if (focused) {
            activeClickZones.add(new ClickZone(screenCopyX1, screenCopyY1, screenCopyX2, screenCopyY2, () -> {
                mc.keyboardHandler.setClipboard(block.rawCode);
                lastCopiedBlock = block;
                lastCopiedAtNanos = System.nanoTime();
            }));
        }
    }
    private static void renderHoverPreviewPopup(GuiGraphicsExtractor graphics, Font font, String url, MediaCacheManager.CachedImage img, int mouseX, int mouseY, int screenW, int screenH) {
        int boxW = 180;
        int drawW = 0;
        int drawH = 0;
        Identifier currentTex = img.getCurrentTextureId();
        boolean hasImage = img.ready && currentTex != null && img.width > 0 && img.height > 0;
        boolean hasTitle = img.siteTitle != null && !img.siteTitle.isBlank();
        boolean hasDesc = img.siteDescription != null && !img.siteDescription.isBlank();

        if (hasImage) {
            float ratio = Math.min(1.0f, Math.min(180.0f / img.width, 100.0f / img.height));
            drawW = Math.max(1, Mth.floor(img.width * ratio));
            drawH = Math.max(1, Mth.floor(img.height * ratio));
            boxW = Math.max(150, drawW + 8);
        }

        List<FormattedCharSequence> descLines = hasDesc ? font.split(Component.literal(img.siteDescription), boxW - 8) : List.of();
        if (descLines.size() > 3) {
            descLines = descLines.subList(0, 3);
        }

        int contentH = 14;
        if (hasTitle) contentH += 11;
        if (!descLines.isEmpty()) contentH += descLines.size() * (font.lineHeight + 1) + 2;
        if (hasImage) {
            contentH += drawH + 4;
        } else if (!hasTitle && !hasDesc) {
            contentH += 12;
        }

        int px = Math.min(screenW - boxW - 4, mouseX + 10);
        int py = Math.max(4, Math.min(screenH - contentH - 4, mouseY - contentH - 6));

        graphics.fill(px - 1, py - 1, px + boxW + 1, py + contentH + 1, 0xFF3A3F4B);
        graphics.fill(px, py, px + boxW, py + contentH, 0xF214161A);

        int curY = py + 4;
        String shortUrl = url.length() > 34 ? url.substring(0, 31) + "..." : url;
        graphics.text(font, shortUrl, px + 4, curY, 0xFF58A6FF, false);
        curY += 11;

        if (hasTitle) {
            String title = img.siteTitle.length() > 34 ? img.siteTitle.substring(0, 31) + "..." : img.siteTitle;
            graphics.text(font, title, px + 4, curY, 0xFFFFFFFF, true);
            curY += 11;
        }

        for (FormattedCharSequence line : descLines) {
            graphics.text(font, line, px + 4, curY, 0xFFB9BBBE, false);
            curY += font.lineHeight + 1;
        }

        if (hasImage) {
            if (!descLines.isEmpty()) curY += 2;
            graphics.blit(RenderPipelines.GUI_TEXTURED, currentTex, px + 4, curY, 0.0f, 0.0f, drawW, drawH, drawW, drawH);
        } else if (!hasTitle && !hasDesc) {
            graphics.text(font, img.failed ? "No preview metadata" : "Loading preview...", px + 4, curY, 0xFFAAAAAA, false);
        }
    }
    private static void renderTopToolbar(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY, ChatHistoryManager manager) {
        String filterLabel = manager.isShowingBookmarksOnly() ? "★ Bookmarks" : "All";
        int curX = x;
        curX += drawPillButton(graphics, font, curX, y, filterLabel, mouseX, mouseY, manager.isShowingBookmarksOnly() ? 0xAA664400 : 0xAA111111, manager::toggleShowBookmarksOnly) + 4;
        curX += drawPillButton(graphics, font, curX, y, "Export", mouseX, mouseY, 0xAA111111, () -> {
            Path exported = manager.exportBookmarks();
            manager.addMessage(Component.literal("§aExported bookmarks to: " + exported.getFileName()));
        }) + 4;
        curX += drawPillButton(graphics, font, curX, y, "Import", mouseX, mouseY, 0xAA111111, () -> {
            manager.importBookmarksWithFileDialog(count -> {
                manager.addMessage(Component.literal("§aImported " + count + " bookmark(s)."));
            });
        }) + 4;
        if (manager.isSearchActive()) {
            boolean blink = (System.currentTimeMillis() / 500L) % 2L == 0L;
            String queryText = manager.getSearchQuery() + (blink ? "" : " ");
            String display = "🔍 " + (manager.getSearchQuery().isEmpty() ? "Type to filter..." + (blink ? "" : "") : queryText);
            int searchW = Math.max(95, font.width(display) + 18);
            int h = 11;
            graphics.fill(curX, y, curX + searchW, y + h, 0xDD1A2639);
            graphics.fill(curX, y + h - 1, curX + searchW, y + h, 0xFF58A6FF);
            graphics.text(font, display, curX + 4, y + 2, manager.getSearchQuery().isEmpty() ? 0xFF8899AA : 0xFFFFFFFF, false);
            activeClickZones.add(new ClickZone(curX, y, curX + searchW - 12, y + h, () -> {}));
            int closeX = curX + searchW - 11;
            boolean closeHover = mouseX >= closeX && mouseX <= curX + searchW && mouseY >= y && mouseY <= y + h;
            graphics.text(font, "✕", closeX + 2, y + 2, closeHover ? 0xFFFF5555 : 0xFFAAAAAA, false);
            if (closeHover) {
                graphics.requestCursor(CursorTypes.POINTING_HAND);
            }
            activeClickZones.add(new ClickZone(closeX, y, curX + searchW, y + h, () -> manager.setSearchActive(false)));
        } else {
            drawPillButton(graphics, font, curX, y, "🔍 Search", mouseX, mouseY, 0xAA111111, () -> manager.setSearchActive(true));
        }
    }
    private static int drawPillButton(GuiGraphicsExtractor graphics, Font font, int x, int y, String label, int mouseX, int mouseY, int baseColor, Runnable action) {
        int w = font.width(label) + 8;
        int h = 11;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        graphics.fill(x, y, x + w, y + h, hovered ? 0xDD333333 : baseColor);
        graphics.text(font, label, x + 4, y + 2, hovered ? 0xFFFFFF55 : 0xFFDDDDDD, false);
        if (hovered) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        activeClickZones.add(new ClickZone(x, y, x + w, y + h, action));
        return w;
    }
    private static void renderMessageButtons(GuiGraphicsExtractor graphics, Font font, Minecraft mc, int rightEdge, int topY, int mouseX, int mouseY, RichChatMessage msg, ChatHistoryManager manager) {
        int x = rightEdge;
        x -= drawMiniIcon(graphics, font, x, topY, "✕", 0xFFFF5555, "Delete Message", mouseX, mouseY, () -> manager.deleteMessage(msg.id)) + 2;
        x -= drawMiniIcon(graphics, font, x, topY, "💾", 0xFF55FFFF, "Save to File", mouseX, mouseY, () -> {
            manager.saveSingleMessageToFile(msg);
            manager.addMessage(Component.literal("§7Saved message to clientchatplus/saved_messages.txt"));
        }) + 2;
        boolean msgCopied = msg.id.equals(lastCopiedMessageId) && (System.nanoTime() - lastCopiedMessageAtNanos < 1_500_000_000L);
        x -= drawMiniIcon(graphics, font, x, topY, msgCopied ? "✓" : "📋", msgCopied ? 0xFF57F287 : 0xFFCCCCCC, msgCopied ? "Copied!" : "Copy Message", mouseX, mouseY, () -> {
            mc.keyboardHandler.setClipboard(msg.rawText);
            lastCopiedMessageId = msg.id;
            lastCopiedMessageAtNanos = System.nanoTime();
        }) + 2;
        drawMiniIcon(graphics, font, x, topY, "★", msg.bookmarked ? 0xFFFFAA00 : 0xFFAAAAAA, msg.bookmarked ? "Unbookmark" : "Bookmark", mouseX, mouseY, () -> manager.toggleBookmark(msg));
    }
    private static int drawMiniIcon(GuiGraphicsExtractor graphics, Font font, int rightX, int y, String icon, int color, String tooltip, int mouseX, int mouseY, Runnable action) {
        int w = font.width(icon) + 4;
        int h = 10;
        int leftX = rightX - w;
        boolean hovered = mouseX >= leftX && mouseX <= rightX && mouseY >= y && mouseY <= y + h;
        graphics.fill(leftX, y, rightX, y + h, hovered ? 0xEE444444 : 0xCC181818);
        graphics.text(font, icon, leftX + 2, y + 1, color, false);
        if (hovered) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            graphics.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
        }
        activeClickZones.add(new ClickZone(leftX, y, rightX, y + h, action));
        return w;
    }
    private static Style getStyleAtX(Font font, FormattedCharSequence seq, int targetX) {
        if (targetX < 0) return null;
        final float[] currentX = {0.0f};
        final Style[] found = {null};
        seq.accept((index, style, codePoint) -> {
            float charWidth = font.width(Component.literal(Character.toString(codePoint)).withStyle(style));
            if (targetX >= currentX[0] && targetX < currentX[0] + charWidth) {
                found[0] = style;
                return false;
            }
            currentX[0] += charWidth;
            return true;
        });
        return found[0];
    }
    public static boolean handleMouseClick(Screen screen, double mouseX, double mouseY, int button) {
        if (button != 1) return false;
        for (int i = activeClickZones.size() - 1; i >= 0; i--) {
            ClickZone zone = activeClickZones.get(i);
            if (mouseX >= zone.x1 && mouseX <= zone.x2 && mouseY >= zone.y1 && mouseY <= zone.y2) {
                zone.action.run();
                return true;
            }
        }
        if (hoveredStyle != null && hoveredStyle.getClickEvent() != null) {
            ScreenClickInvoker.invoke(screen, hoveredStyle.getClickEvent());
            return true;
        }
        return false;
    }
    private record ClickZone(int x1, int y1, int x2, int y2, Runnable action) {}
}