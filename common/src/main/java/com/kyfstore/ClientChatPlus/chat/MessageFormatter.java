package com.kyfstore.ClientChatPlus.chat;

import com.kyfstore.ClientChatPlus.config.ChatConfig;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.node.*;
import org.commonmark.parser.Parser;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageFormatter {
    private static final List<Extension> EXTENSIONS = List.of(StrikethroughExtension.create());
    private static final Parser MARKDOWN_PARSER = Parser.builder().extensions(EXTENSIONS).build();
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .strict(false)
            .tags(TagResolver.standard())
            .build();
    private static final Pattern UNDERLINE_PRE_PATTERN = Pattern.compile("(?<!\\\\)__(.+?)(?<!\\\\)__");
    private static final Pattern SENDER_PREFIX_PATTERN = Pattern.compile("^(<[^>]+>\\s+|\\[[^\\]]+\\]\\s*[^:»>]+(?:>>|:|»)\\s+|[^:<>\\s]{1,24}:\\s+)");
    private static final Pattern INLINE_HEADING_PATTERN = Pattern.compile("(?:^|(?<=\\s))(### |## |# |-# )");
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"\\)\\]]+)");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```([a-zA-Z0-9_-]*)\\s*\\n?([\\s\\S]*?)```");
    private static final Pattern SINGLE_TICK_BLOCK_PATTERN = Pattern.compile("(?<!`)`([\\s\\S]+?)`(?!`)");
    private static final Set<String> KNOWN_LANGS = Set.of(
            "java", "python", "py", "js", "javascript", "ts", "typescript", "json", "yml", "yaml",
            "html", "css", "xml", "sql", "sh", "bash", "lua", "rust", "rs", "go", "c", "cpp", "cs", "kt", "kotlin", "toml", "md", "text"
    );

    public static void extractMediaAndFormat(RichChatMessage msg) {
        ChatConfig cfg = ChatConfig.get();
        msg.inlineImageUrls.clear();
        msg.attachmentUrls.clear();
        if (!msg.hasMergedCodeBlocks) {
            msg.codeBlocks.clear();
        }

        if (msg.originalComponent.getContents() instanceof TranslatableContents tc) {
            if (!tc.getKey().startsWith("chat.type.")) {
                msg.formattedLines = List.of(new ChatFormattedLine(msg.originalComponent, 1.0f, false));
                return;
            }
        }

        msg.senderName = extractSenderName(msg.rawText);
        msg.isMention = cfg.highlightMentions && checkMention(msg.rawText, msg.senderName);

        Matcher m = URL_PATTERN.matcher(msg.rawText);
        while (m.find()) {
            String url = m.group(1);
            if (cfg.renderImages && MediaCacheManager.isImageUrl(url)) {
                if (cfg.inlineImages) {
                    if (!msg.inlineImageUrls.contains(url)) {
                        msg.inlineImageUrls.add(url);
                        MediaCacheManager.get().getOrFetchImage(url);
                    }
                } else {
                    MediaCacheManager.get().getOrFetchImage(url);
                }
            } else if (cfg.renderDiscordAttachments && MediaCacheManager.isFileAttachmentUrl(url)) {
                if (!msg.attachmentUrls.contains(url)) {
                    msg.attachmentUrls.add(url);
                    MediaCacheManager.get().getOrFetchAttachment(url);
                }
            } else if (cfg.renderImages || cfg.renderDiscordAttachments) {
                MediaCacheManager.get().probeUrlIfUnknown(url);
            }
        }

        List<int[]> promotedRanges = new ArrayList<>();
        if (cfg.enableMarkdown) {
            if (msg.rawText.contains("```")) {
                Matcher cbMatcher = CODE_BLOCK_PATTERN.matcher(msg.rawText);
                while (cbMatcher.find()) {
                    String lang = cbMatcher.group(1);
                    String code = cbMatcher.group(2);
                    if (code.endsWith("\n")) {
                        code = code.substring(0, code.length() - 1);
                    }
                    msg.codeBlocks.add(new ChatCodeBlock(lang, code));
                    promotedRanges.add(new int[]{cbMatcher.start(), cbMatcher.end()});
                }
            }

            if (promotedRanges.isEmpty() && msg.rawText.contains("`")) {
                String bodyAfterPrefix = msg.rawText;
                int bodyOffset = 0;
                Matcher prefixMatcher = SENDER_PREFIX_PATTERN.matcher(msg.rawText);
                if (prefixMatcher.find()) {
                    bodyOffset = prefixMatcher.end();
                    bodyAfterPrefix = msg.rawText.substring(bodyOffset);
                }
                String trimmedBody = bodyAfterPrefix.trim();
                Matcher singleMatcher = SINGLE_TICK_BLOCK_PATTERN.matcher(bodyAfterPrefix);
                while (singleMatcher.find()) {
                    String inner = singleMatcher.group(1);
                    boolean isFullBody = singleMatcher.group(0).trim().equals(trimmedBody);
                    boolean isMultiLine = inner.contains("\n");
                    if (isFullBody || isMultiLine) {
                        String lang = "";
                        String code = inner;
                        int firstNl = inner.indexOf('\n');
                        if (firstNl > 0) {
                            String firstLine = inner.substring(0, firstNl).trim().toLowerCase();
                            if (KNOWN_LANGS.contains(firstLine)) {
                                lang = firstLine;
                                code = inner.substring(firstNl + 1);
                            }
                        } else {
                            int firstSpace = inner.indexOf(' ');
                            if (firstSpace > 0) {
                                String firstWord = inner.substring(0, firstSpace).trim().toLowerCase();
                                if (KNOWN_LANGS.contains(firstWord)) {
                                    lang = firstWord;
                                    code = inner.substring(firstSpace + 1);
                                }
                            }
                        }
                        if (code.endsWith("\n")) {
                            code = code.substring(0, code.length() - 1);
                        }
                        if (lang.isEmpty()) {
                            lang = detectLanguage(code);
                        }
                        msg.codeBlocks.add(new ChatCodeBlock(lang, code.strip()));
                        promotedRanges.add(new int[]{bodyOffset + singleMatcher.start(), bodyOffset + singleMatcher.end()});
                    }
                }
            }

            if (promotedRanges.isEmpty()) {
                String bodyAfterPrefix = msg.rawText;
                int bodyOffset = 0;
                Matcher prefixMatcher = SENDER_PREFIX_PATTERN.matcher(msg.rawText);
                if (prefixMatcher.find()) {
                    bodyOffset = prefixMatcher.end();
                    bodyAfterPrefix = msg.rawText.substring(bodyOffset);
                }
                String unescaped = bodyAfterPrefix.replace("\\n", "\n").trim();
                String detected = detectLanguage(unescaped);
                if (!detected.equals("text") && (unescaped.contains("{") || unescaped.contains("\n") || unescaped.endsWith(";"))) {
                    msg.codeBlocks.add(new ChatCodeBlock(detected, unescaped));
                    promotedRanges.add(new int[]{bodyOffset, msg.rawText.length()});
                }
            }
        }

        if (msg.hasMergedCodeBlocks && promotedRanges.isEmpty()) {
            int tickIdx = msg.rawText.indexOf('`');
            if (tickIdx >= 0) {
                promotedRanges.add(new int[]{tickIdx, msg.rawText.length()});
            }
        }

        msg.formattedLines = parse(msg.originalComponent, promotedRanges);
    }

    static String detectLanguage(String code) {
        String trimmed = code.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) return "json";
        if (trimmed.contains("class ") || trimmed.contains("interface ") || trimmed.contains("System.out.") || trimmed.contains("void "))
            return "java";
        if (trimmed.contains("def ") || (trimmed.contains("import ") && trimmed.contains(":"))) return "python";
        if (trimmed.contains("function ") || trimmed.contains("const ") || trimmed.contains("console.log")) return "js";
        return "text";
    }

    public static String extractSenderName(String rawText) {
        Matcher m = SENDER_PREFIX_PATTERN.matcher(rawText);
        if (!m.find()) return "";
        String prefix = m.group(1).trim();
        if (prefix.startsWith("<") && prefix.endsWith(">")) {
            return prefix.substring(1, prefix.length() - 1).trim();
        }
        int closeBracket = prefix.lastIndexOf(']');
        String afterBracket = closeBracket >= 0 ? prefix.substring(closeBracket + 1).trim() : prefix;
        afterBracket = afterBracket.replaceFirst("(?:>>|:|»)$", "").trim();
        int lastSpace = afterBracket.lastIndexOf(' ');
        return lastSpace >= 0 ? afterBracket.substring(lastSpace + 1).trim() : afterBracket;
    }

    private static boolean checkMention(String rawText, String sender) {
        String selfName = "";
        if (Minecraft.getInstance().getUser() != null) {
            selfName = Minecraft.getInstance().getUser().getName();
        }
        String body = rawText;
        Matcher prefixMatcher = SENDER_PREFIX_PATTERN.matcher(rawText);
        if (prefixMatcher.find()) {
            body = rawText.substring(prefixMatcher.end());
        }
        String lowerBody = body.toLowerCase();
        if (lowerBody.contains("@everyone") || lowerBody.contains("@here")) {
            return true;
        }
        if (!selfName.isEmpty()) {
            Pattern p = Pattern.compile("(?i)(?:^|[^a-z0-9_])@" + Pattern.quote(selfName) + "(?:$|[^a-z0-9_])");
            return p.matcher(body).find();
        }
        return false;
    }

    public static List<ChatFormattedLine> parse(Component rawComponent) {
        return parse(rawComponent, List.of());
    }

    private static List<ChatFormattedLine> parse(Component rawComponent, List<int[]> skipRanges) {
        ChatConfig cfg = ChatConfig.get();
        List<ChatFormattedLine> result = new ArrayList<>();

        if (rawComponent.getContents() instanceof TranslatableContents tc) {
            if (!tc.getKey().startsWith("chat.type.")) {
                result.add(new ChatFormattedLine(rawComponent, 1.0f, false));
                return result;
            }
        }

        Component componentWithoutCodeBlocks = !skipRanges.isEmpty() ? stripRangesFromComponent(rawComponent, skipRanges) : rawComponent;
        List<StyledLine> physicalLines = splitComponentByNewlines(componentWithoutCodeBlocks);

        for (StyledLine line : physicalLines) {
            String plain = line.getPlainText();
            if (plain.isBlank() && physicalLines.size() > 1) {
                continue;
            }

            int contentStart = 0;
            Matcher prefixMatcher = SENDER_PREFIX_PATTERN.matcher(plain);
            if (prefixMatcher.find()) {
                contentStart = prefixMatcher.end();
            }

            String bodyPlain = plain.substring(contentStart);
            boolean isBullet = false;
            boolean isQuote = false;
            int stripPrefix = 0;

            if (cfg.enableMarkdown) {
                if (bodyPlain.startsWith("- ")) {
                    isBullet = true;
                    stripPrefix = 2;
                } else if (bodyPlain.startsWith("> ")) {
                    isQuote = true;
                    stripPrefix = 2;
                } else if (bodyPlain.equals(">")) {
                    isQuote = true;
                    stripPrefix = 1;
                }
            }

            List<ChatFormattedLine.ScaledSpan> spans = new ArrayList<>();

            if (contentStart > 0) {
                MutableComponent prefixComp = Component.empty();
                StyledLine prefixLine = line.slice(0, contentStart);
                for (StyledRun run : prefixLine.runs) {
                    prefixComp.append(Component.literal(run.text).withStyle(run.style));
                }
                spans.add(new ChatFormattedLine.ScaledSpan(prefixComp, 1.0f));
            }

            int bodyStartIdx = contentStart + stripPrefix;
            String remainingBody = plain.substring(bodyStartIdx);
            if (cfg.enableMarkdown) {
                Matcher headingMatcher = INLINE_HEADING_PATTERN.matcher(remainingBody);
                if (headingMatcher.find()) {
                    int markerStart = headingMatcher.start();
                    int markerEnd = headingMatcher.end();
                    String marker = headingMatcher.group(1);
                    if (markerStart > 0) {
                        StyledLine normalBefore = line.slice(bodyStartIdx, bodyStartIdx + markerStart);
                        spans.add(new ChatFormattedLine.ScaledSpan(formatStyledLine(normalBefore, isQuote, cfg), 1.0f));
                    }
                    float headingScale = switch (marker) {
                        case "### " -> 1.15f;
                        case "## " -> 1.3f;
                        case "# " -> 1.5f;
                        case "-# " -> 0.75f;
                        default -> 1.0f;
                    };
                    boolean isMuted = marker.equals("-# ") || isQuote;
                    StyledLine headingPart = line.slice(bodyStartIdx + markerEnd, plain.length());
                    spans.add(new ChatFormattedLine.ScaledSpan(formatStyledLine(headingPart, isMuted, cfg), headingScale));
                } else {
                    StyledLine bodyLine = line.slice(bodyStartIdx, plain.length());
                    spans.add(new ChatFormattedLine.ScaledSpan(formatStyledLine(bodyLine, isQuote, cfg), 1.0f));
                }
            } else {
                StyledLine bodyLine = line.slice(bodyStartIdx, plain.length());
                spans.add(new ChatFormattedLine.ScaledSpan(formatStyledLine(bodyLine, isQuote, cfg), 1.0f));
            }
            boolean hasAnyText = false;
            for (ChatFormattedLine.ScaledSpan s : spans) {
                if (!s.content().getString().isEmpty()) {
                    hasAnyText = true;
                    break;
                }
            }
            if (hasAnyText || result.isEmpty()) {
                result.add(new ChatFormattedLine(spans, isBullet, isQuote));
            }
        }
        if (result.isEmpty()) {
            result.add(new ChatFormattedLine(Component.empty(), 1.0f, false));
        }
        return result;
    }

    private static Component formatStyledLine(StyledLine slice, boolean isSubtext, ChatConfig cfg) {
        MutableComponent formatted = Component.empty();
        for (StyledRun run : slice.runs) {
            Style baseStyle = run.style;
            if (isSubtext && baseStyle.getColor() == null) {
                baseStyle = baseStyle.withColor(ChatFormatting.GRAY);
            }
            formatted.append(formatSegment(run.text, baseStyle, cfg));
        }
        return formatted;
    }

    private static Component stripRangesFromComponent(Component root, List<int[]> skipRanges) {
        MutableComponent rebuilt = Component.empty();
        List<StyledLine> lines = splitComponentByNewlines(root);
        List<StyledRun> allRuns = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                allRuns.add(new StyledRun("\n", Style.EMPTY));
            }
            allRuns.addAll(lines.get(i).runs);
        }
        int pos = 0;
        for (StyledRun run : allRuns) {
            int rStart = pos;
            int rEnd = pos + run.text.length();
            pos = rEnd;
            int cursor = rStart;
            for (int[] range : skipRanges) {
                if (range[1] <= cursor) continue;
                if (range[0] >= rEnd) break;
                if (range[0] > cursor) {
                    rebuilt.append(Component.literal(run.text.substring(cursor - rStart, range[0] - rStart)).withStyle(run.style));
                }
                cursor = Math.min(rEnd, range[1]);
            }
            if (cursor < rEnd) {
                rebuilt.append(Component.literal(run.text.substring(cursor - rStart, rEnd - rStart)).withStyle(run.style));
            }
        }
        return rebuilt;
    }

    private static Component formatSegment(String text, Style baseStyle, ChatConfig cfg) {
        if (text.isEmpty()) return Component.empty();
        if (cfg.enableMiniMessage && text.contains("<") && text.contains(">")) {
            net.kyori.adventure.text.Component advComp = MINI_MESSAGE.deserialize(text);
            return convertAdventureToMinecraft(advComp, baseStyle, cfg.enableMarkdown);
        }
        if (cfg.enableMarkdown) {
            return parseMarkdownText(text, baseStyle);
        }
        MutableComponent out = Component.empty();
        appendAutoLinkedText(out, text, baseStyle);
        return out;
    }

    private static Component convertAdventureToMinecraft(net.kyori.adventure.text.Component adv, Style parentStyle, boolean applyMarkdown) {
        Style currentStyle = parentStyle;
        TextColor color = adv.color();
        if (color != null) {
            currentStyle = currentStyle.withColor(color.value());
        }
        if (adv.hasDecoration(TextDecoration.BOLD)) currentStyle = currentStyle.withBold(true);
        if (adv.hasDecoration(TextDecoration.ITALIC)) currentStyle = currentStyle.withItalic(true);
        if (adv.hasDecoration(TextDecoration.UNDERLINED)) currentStyle = currentStyle.withUnderlined(true);
        if (adv.hasDecoration(TextDecoration.STRIKETHROUGH)) currentStyle = currentStyle.withStrikethrough(true);
        if (adv.hasDecoration(TextDecoration.OBFUSCATED)) currentStyle = currentStyle.withObfuscated(true);
        MutableComponent out = Component.empty();
        if (adv instanceof net.kyori.adventure.text.TextComponent textComp) {
            String content = textComp.content();
            if (!content.isEmpty()) {
                if (applyMarkdown) {
                    out.append(parseMarkdownText(content, currentStyle));
                } else {
                    appendAutoLinkedText(out, content, currentStyle);
                }
            }
        }
        for (net.kyori.adventure.text.Component child : adv.children()) {
            out.append(convertAdventureToMinecraft(child, currentStyle, applyMarkdown));
        }
        return out;
    }

    private static Component parseMarkdownText(String text, Style baseStyle) {
        String preprocessed = UNDERLINE_PRE_PATTERN.matcher(text).replaceAll("\uE000$1\uE001");
        Node document = MARKDOWN_PARSER.parse(preprocessed);
        MutableComponent result = Component.empty();
        ChatConfig cfg = ChatConfig.get();
        document.accept(new AbstractVisitor() {
            Style current = baseStyle;

            @Override
            public void visit(Text textNode) {
                appendWithUnderlineTokens(result, textNode.getLiteral(), current);
            }

            @Override
            public void visit(StrongEmphasis strong) {
                Style prev = current;
                current = current.withBold(true);
                visitChildren(strong);
                current = prev;
            }

            @Override
            public void visit(Emphasis emphasis) {
                Style prev = current;
                current = current.withItalic(true);
                visitChildren(emphasis);
                current = prev;
            }

            @Override
            public void visit(Image image) {
                String url = image.getDestination();
                if (!cfg.renderImages || cfg.inlineImages) {
                    return;
                }
                Style linkStyle = current.withColor(0x58A6FF).withUnderlined(true);
                try {
                    linkStyle = linkStyle
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
                } catch (Exception ignored) {
                }
                result.append(Component.literal("image").withStyle(linkStyle));
            }

            @Override
            public void visit(Link link) {
                String url = link.getDestination();
                if (MediaCacheManager.isImageUrl(url)) {
                    if (!cfg.renderImages || cfg.inlineImages) {
                        return;
                    }
                    Style linkStyle = current.withColor(0x58A6FF).withUnderlined(true);
                    try {
                        linkStyle = linkStyle
                                .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
                    } catch (Exception ignored) {
                    }
                    result.append(Component.literal("image").withStyle(linkStyle));
                    return;
                }
                if (cfg.renderDiscordAttachments && MediaCacheManager.isFileAttachmentUrl(url)) {
                    return;
                }
                Style prev = current;
                Style linkStyle = current
                        .withColor(0x58A6FF)
                        .withUnderlined(true);
                try {
                    linkStyle = linkStyle
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
                } catch (Exception ignored) {
                }
                current = linkStyle;
                visitChildren(link);
                current = prev;
            }

            @Override
            public void visit(Code code) {
                Style codeStyle = current.withColor(0xD4D4D4);
                result.append(Component.literal(" " + code.getLiteral() + " ").withStyle(codeStyle));
            }

            @Override
            public void visit(CustomNode customNode) {
                if (customNode instanceof Strikethrough) {
                    Style prev = current;
                    current = current.withStrikethrough(true);
                    visitChildren(customNode);
                    current = prev;
                } else {
                    visitChildren(customNode);
                }
            }

            @Override
            public void visit(SoftLineBreak softLineBreak) {
                result.append(Component.literal(" ").withStyle(current));
            }
        });
        return result;
    }

    private static void appendWithUnderlineTokens(MutableComponent target, String text, Style style) {
        int idx = 0;
        while (idx < text.length()) {
            int start = text.indexOf('\uE000', idx);
            if (start == -1) {
                appendAutoLinkedText(target, text.substring(idx), style);
                break;
            }
            if (start > idx) {
                appendAutoLinkedText(target, text.substring(idx, start), style);
            }
            int end = text.indexOf('\uE001', start + 1);
            if (end == -1) {
                appendAutoLinkedText(target, text.substring(start + 1), style);
                break;
            }
            String underlinedText = text.substring(start + 1, end);
            appendAutoLinkedText(target, underlinedText, style.withUnderlined(true));
            idx = end + 1;
        }
    }

    private static void appendAutoLinkedText(MutableComponent target, String text, Style style) {
        if (text.isEmpty()) return;
        ChatConfig cfg = ChatConfig.get();
        Matcher matcher = URL_PATTERN.matcher(text);
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                target.append(Component.literal(text.substring(lastEnd, matcher.start())).withStyle(style));
            }
            String url = matcher.group(1);
            if (MediaCacheManager.isImageUrl(url)) {
                if (!cfg.renderImages || cfg.inlineImages) {
                    lastEnd = matcher.end();
                    continue;
                }
                Style linkStyle = style.withColor(0x58A6FF).withUnderlined(true);
                try {
                    linkStyle = linkStyle
                            .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
                } catch (Exception ignored) {
                }
                target.append(Component.literal("image").withStyle(linkStyle));
                lastEnd = matcher.end();
                continue;
            }
            if (cfg.renderDiscordAttachments && MediaCacheManager.isFileAttachmentUrl(url)) {
                lastEnd = matcher.end();
                continue;
            }
            Style linkStyle = style.withColor(0x58A6FF).withUnderlined(true);
            try {
                linkStyle = linkStyle
                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal(url)));
            } catch (Exception ignored) {
            }
            target.append(Component.literal(url).withStyle(linkStyle));
            lastEnd = matcher.end();
        }
        if (lastEnd < text.length()) {
            target.append(Component.literal(text.substring(lastEnd)).withStyle(style));
        }
    }

    private static List<StyledLine> splitComponentByNewlines(Component root) {
        List<StyledLine> lines = new ArrayList<>();
        StyledLine[] current = new StyledLine[]{new StyledLine()};
        root.visit((style, string) -> {
            String[] parts = string.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(current[0]);
                    current[0] = new StyledLine();
                }
                if (!parts[i].isEmpty()) {
                    current[0].runs.add(new StyledRun(parts[i], style));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        lines.add(current[0]);
        return lines;
    }

    private record StyledRun(String text, Style style) {
    }

    private static class StyledLine {
        final List<StyledRun> runs = new ArrayList<>();

        String getPlainText() {
            StringBuilder sb = new StringBuilder();
            for (StyledRun r : runs) sb.append(r.text);
            return sb.toString();
        }

        StyledLine slice(int start, int end) {
            StyledLine out = new StyledLine();
            int pos = 0;
            for (StyledRun run : runs) {
                int runStart = pos;
                int runEnd = pos + run.text.length();
                pos = runEnd;
                int sliceStart = Math.max(start, runStart);
                int sliceEnd = Math.min(end, runEnd);
                if (sliceStart < sliceEnd) {
                    out.runs.add(new StyledRun(run.text.substring(sliceStart - runStart, sliceEnd - runStart), run.style));
                }
            }
            return out;
        }
    }
}