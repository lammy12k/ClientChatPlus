package com.kyfstore.ClientChatPlus.chat;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatCodeBlock {
    private static final Set<String> KEYWORDS = Set.of(
            "public", "private", "protected", "static", "final", "abstract", "class", "interface", "enum", "record",
            "void", "return", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue",
            "new", "try", "catch", "finally", "throw", "throws", "import", "package", "extends", "implements",
            "true", "false", "null", "this", "super", "var", "val", "fun", "def", "async", "await", "function",
            "const", "let", "from", "as", "in", "is", "not", "and", "or", "elif", "lambda", "yield", "with",
            "struct", "impl", "fn", "mut", "pub", "use", "mod", "match", "self", "Self", "local", "then", "end"
    );

    private static final Set<String> TYPES = Set.of(
            "int", "long", "float", "double", "boolean", "char", "byte", "short", "String", "Object", "List",
            "Map", "Set", "Optional", "UUID", "Component", "Style", "Font", "Integer", "Boolean", "Double",
            "Float", "str", "dict", "list", "tuple", "bool", "any", "number", "string", "void", "u8", "u16",
            "u32", "u64", "i32", "i64", "f32", "f64", "usize", "Vec", "Option", "Result"
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(//.*$|#.*$|/\\*.*?\\*/)" +
                    "|(\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)" +
                    "|(@[A-Za-z_][A-Za-z0-9_]*)" +
                    "|(\\b\\d+(?:\\.\\d+)?[fFdDlL]?\\b)" +
                    "|(\\b[A-Za-z_][A-Za-z0-9_]*\\b)" +
                    "|([^A-Za-z0-9_\\s]+|\\s+)"
    );

    public final String language;
    public final String rawCode;
    public final List<Component> highlightedLines = new ArrayList<>();
    public List<FormattedCharSequence> wrappedLines = new ArrayList<>();
    public int boxHeight = 0;

    public ChatCodeBlock(String language, String rawCode) {
        this.language = (language == null || language.isBlank()) ? "text" : language.trim().toLowerCase(Locale.ROOT);
        this.rawCode = rawCode == null ? "" : rawCode;
        buildHighlightedLines();
    }

    private void buildHighlightedLines() {
        String[] lines = rawCode.split("\n", -1);
        for (String line : lines) {
            highlightedLines.add(highlightLine(line, language));
        }
    }

    public int wrapAndMeasure(Font font, int maxWidth) {
        this.wrappedLines.clear();
        int innerWidth = Math.max(40, maxWidth - 12);
        for (Component line : highlightedLines) {
            if (line.getString().isEmpty()) {
                this.wrappedLines.add(FormattedCharSequence.EMPTY);
            } else {
                this.wrappedLines.addAll(font.split(line, innerWidth));
            }
        }
        int headerHeight = 14;
        int codeHeight = Math.max(font.lineHeight, this.wrappedLines.size() * (font.lineHeight + 2));
        this.boxHeight = headerHeight + codeHeight + 6;
        return this.boxHeight;
    }

    private static Component highlightLine(String line, String lang) {
        if (line.isEmpty() || lang.equals("text") || lang.equals("txt") || lang.equals("plain")) {
            return Component.literal(line).withStyle(Style.EMPTY.withColor(0xDCDDDE));
        }

        if (lang.equals("json")) {
            return highlightJsonLine(line);
        }

        MutableComponent out = Component.empty();
        Matcher m = TOKEN_PATTERN.matcher(line);
        while (m.find()) {
            String token = m.group();
            int color = 0xDCDDDE;

            if (m.group(1) != null) {
                if (token.startsWith("#") && !(lang.equals("py") || lang.equals("python") || lang.equals("sh") || lang.equals("bash") || lang.equals("yaml") || lang.equals("yml") || lang.equals("toml"))) {
                    color = 0xC678DD;
                } else {
                    color = 0x6A9955;
                }
            } else if (m.group(2) != null) {
                color = 0xCE9178;
            } else if (m.group(3) != null) {
                color = 0xDCDCAA;
            } else if (m.group(4) != null) {
                color = 0xB5CEA8;
            } else if (m.group(5) != null) {
                if (KEYWORDS.contains(token)) {
                    color = 0x569CD6;
                } else if (TYPES.contains(token) || Character.isUpperCase(token.charAt(0))) {
                    color = 0x4EC9B0;
                } else {
                    color = 0x9CDCFE;
                }
            } else {
                color = 0xD4D4D4;
            }

            out.append(Component.literal(token).withStyle(Style.EMPTY.withColor(color)));
        }
        return out;
    }

    private static Component highlightJsonLine(String line) {
        MutableComponent out = Component.empty();
        Pattern jsonPattern = Pattern.compile("(\"(?:\\\\.|[^\"\\\\])*\")(\\s*:)?|(\\b(?:true|false|null)\\b)|(-?\\b\\d+(?:\\.\\d+)?\\b)|([^\"\\w\\s]+|\\s+)");
        Matcher m = jsonPattern.matcher(line);
        while (m.find()) {
            String token = m.group();
            int color = 0xD4D4D4;
            if (m.group(1) != null) {
                color = m.group(2) != null ? 0x9CDCFE : 0xCE9178;
            } else if (m.group(3) != null) {
                color = 0x569CD6;
            } else if (m.group(4) != null) {
                color = 0xB5CEA8;
            }
            out.append(Component.literal(token).withStyle(Style.EMPTY.withColor(color)));
        }
        return out;
    }
}
