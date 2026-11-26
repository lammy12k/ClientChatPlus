package com.kyfstore.ClientChatPlus.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.kyfstore.ClientChatPlus.config.ChatConfig;
import com.kyfstore.ClientChatPlus.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatHistoryManager {
    private static final ChatHistoryManager INSTANCE = new ChatHistoryManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<RichChatMessage> messages = new CopyOnWriteArrayList<>();
    private final List<BookmarkEntry> bookmarks = new CopyOnWriteArrayList<>();

    private float currentPushOffset = 0.0f;
    private long lastFrameNanos = System.nanoTime();
    private int scrollPixels = 0;
    private int maxScrollPixels = 0;

    public enum FilterTab {
        ALL,
        BOOKMARKS,
        MENTIONS
    }

    public enum SearchMode {
        NONE,
        TEXT,
        USER
    }

    private FilterTab activeTab = FilterTab.ALL;
    private SearchMode searchMode = SearchMode.NONE;
    private String searchQuery = "";
    private int unreadNewMessages = 0;

    private RichChatMessage pendingCodeBlockMessage = null;
    private String pendingCodeBlockLang = "text";
    private final StringBuilder pendingCodeBlockBuffer = new StringBuilder();

    public static ChatHistoryManager getInstance() {
        return INSTANCE;
    }

    public void addMessage(Component component) {
        Minecraft mc = Minecraft.getInstance();
        String raw = component.getString();
        String trimmed = raw.trim();

        if (pendingCodeBlockMessage != null) {
            String strippedLine = raw.replaceFirst("^\\[[^\\]]+\\]\\s*[^:»>]+(?:>>|:|»)\\s*", "");
            String trimmedLine = strippedLine.trim();

            if (trimmedLine.equals("`") || trimmedLine.equals("```")) {
                String finalCode = pendingCodeBlockBuffer.toString();
                if (finalCode.endsWith("\n")) {
                    finalCode = finalCode.substring(0, finalCode.length() - 1);
                }
                String lang = pendingCodeBlockLang.isBlank() ? MessageFormatter.detectLanguage(finalCode) : pendingCodeBlockLang;
                pendingCodeBlockMessage.codeBlocks.add(new ChatCodeBlock(lang, finalCode));
                pendingCodeBlockMessage.hasMergedCodeBlocks = true;
                pendingCodeBlockMessage.lastWrapWidth = -1;
                int chatWidth = CustomChatRenderer.getChatWidth(mc);
                pendingCodeBlockMessage.recalculateLayout(mc.font, chatWidth);
                pendingCodeBlockMessage = null;
                pendingCodeBlockBuffer.setLength(0);
                return;
            }

            if (pendingCodeBlockBuffer.length() > 0) {
                pendingCodeBlockBuffer.append('\n');
            }
            pendingCodeBlockBuffer.append(strippedLine);
            return;
        }

        String afterPrefix = raw.replaceFirst("^\\[[^\\]]+\\]\\s*[^:»>]+(?:>>|:|»)\\s*", "").trim();
        if (afterPrefix.matches("^(?:```|`)[a-zA-Z0-9_-]*$")) {
            String lang = afterPrefix.replace("`", "").trim();
            RichChatMessage headerMsg = new RichChatMessage(component);
            headerMsg.hasMergedCodeBlocks = true;
            int chatWidth = CustomChatRenderer.getChatWidth(mc);
            headerMsg.recalculateLayout(mc.font, chatWidth);
            messages.addFirst(headerMsg);

            if (scrollPixels > 0) {
                scrollPixels += headerMsg.totalHeight;
                unreadNewMessages++;
            } else {
                currentPushOffset += headerMsg.totalHeight;
                unreadNewMessages = 0;
            }

            pendingCodeBlockMessage = headerMsg;
            pendingCodeBlockLang = lang;
            pendingCodeBlockBuffer.setLength(0);
            return;
        }

        RichChatMessage msg = new RichChatMessage(component);
        int chatWidth = CustomChatRenderer.getChatWidth(mc);
        msg.recalculateLayout(mc.font, chatWidth);

        if (msg.isMention && ChatConfig.get().mentionSound && mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 1.25f);
        }

        messages.addFirst(msg);

        if (scrollPixels > 0) {
            scrollPixels += msg.totalHeight;
            unreadNewMessages++;
        } else {
            currentPushOffset += msg.totalHeight;
            unreadNewMessages = 0;
        }

        if (ChatConfig.get().saveHistoryToFile) {
            appendLineToFile(getBaseDir().resolve("logs").resolve(LocalDate.now() + ".txt"), msg.rawText);
        }
    }

    public List<RichChatMessage> getVisibleMessages() {
        boolean hasSearch = searchMode != SearchMode.NONE && !searchQuery.isBlank();
        if (activeTab == FilterTab.ALL && !hasSearch) return messages;

        String needle = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<RichChatMessage> filtered = new ArrayList<>();
        for (RichChatMessage msg : messages) {
            if (activeTab == FilterTab.BOOKMARKS && !msg.bookmarked) continue;
            if (activeTab == FilterTab.MENTIONS && !msg.isMention) continue;
            if (hasSearch) {
                if (searchMode == SearchMode.TEXT) {
                    if (!msg.rawText.toLowerCase(Locale.ROOT).contains(needle)) continue;
                } else if (searchMode == SearchMode.USER) {
                    if (msg.senderName.isEmpty() || !msg.senderName.toLowerCase(Locale.ROOT).contains(needle)) continue;
                }
            }
            filtered.add(msg);
        }
        return filtered;
    }

    public FilterTab getActiveTab() {
        return activeTab;
    }

    public void setActiveTab(FilterTab tab) {
        this.activeTab = tab;
        this.scrollPixels = 0;
        this.unreadNewMessages = 0;
    }

    public SearchMode getSearchMode() {
        return searchMode;
    }

    public boolean isSearchActive() {
        return searchMode != SearchMode.NONE;
    }

    public void setSearchMode(SearchMode mode) {
        this.searchMode = mode;
        if (mode == SearchMode.NONE) {
            this.searchQuery = "";
        }
        this.scrollPixels = 0;
        this.unreadNewMessages = 0;
    }

    public void setSearchActive(boolean active) {
        setSearchMode(active ? SearchMode.TEXT : SearchMode.NONE);
    }

    public void toggleSearch() {
        setSearchMode(this.searchMode == SearchMode.NONE ? SearchMode.TEXT : SearchMode.NONE);
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String query) {
        this.searchQuery = query;
        this.scrollPixels = 0;
    }

    public void appendSearchChar(char c) {
        if (searchQuery.length() < 48) {
            searchQuery += c;
            scrollPixels = 0;
        }
    }

    public void backspaceSearch() {
        if (!searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            scrollPixels = 0;
        }
    }

    public List<String> getUserAutocompleteSuggestions() {
        if (searchMode != SearchMode.USER) return List.of();
        Set<String> candidates = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                if (info.getProfile() != null && info.getProfile().name() != null) {
                    candidates.add(info.getProfile().name());
                }
            }
        }
        for (RichChatMessage msg : messages) {
            if (!msg.senderName.isEmpty()) {
                candidates.add(msg.senderName);
            }
        }
        String prefix = searchQuery.toLowerCase(Locale.ROOT).trim();
        List<String> matches = new ArrayList<>();
        for (String name : candidates) {
            if (prefix.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(prefix) || name.toLowerCase(Locale.ROOT).contains(prefix)) {
                matches.add(name);
                if (matches.size() >= 6) break;
            }
        }
        return matches;
    }

    public int getUnreadNewMessages() {
        return unreadNewMessages;
    }

    public void jumpToBottom() {
        currentPushOffset = Math.min(scrollPixels, 40);
        scrollPixels = 0;
        unreadNewMessages = 0;
    }

    public void deleteMessage(UUID id) {
        messages.removeIf(m -> m.id.equals(id));
    }

    public void toggleBookmark(RichChatMessage msg) {
        msg.bookmarked = !msg.bookmarked;
        if (msg.bookmarked) {
            bookmarks.removeIf(b -> b.id.equals(msg.id.toString()));
            bookmarks.addFirst(new BookmarkEntry(msg.id.toString(), msg.rawText, System.currentTimeMillis()));
        } else {
            bookmarks.removeIf(b -> b.id.equals(msg.id.toString()));
        }
        saveBookmarks();
    }

    public void saveSingleMessageToFile(RichChatMessage msg) {
        appendLineToFile(getBaseDir().resolve("saved_messages.txt"), msg.rawText);
    }

    private void appendLineToFile(Path file, String text) {
        try {
            Files.createDirectories(file.getParent());
            String stamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            Files.writeString(file, "[" + stamp + "] " + text + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Path getBaseDir() {
        return Services.platform().getGameDir().resolve("clientchatplus");
    }

    public void loadBookmarks() {
        Path file = getBaseDir().resolve("bookmarks.json");
        if (Files.exists(file)) {
            try {
                List<BookmarkEntry> loaded = GSON.fromJson(Files.readString(file), new TypeToken<List<BookmarkEntry>>() {
                }.getType());
                if (loaded != null) {
                    bookmarks.clear();
                    bookmarks.addAll(loaded);
                    for (BookmarkEntry entry : bookmarks) {
                        RichChatMessage msg = new RichChatMessage(Component.literal(entry.rawText));
                        msg.bookmarked = true;
                        messages.add(msg);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void saveBookmarks() {
        Path file = getBaseDir().resolve("bookmarks.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(bookmarks));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Path exportBookmarks() {
        Path file = getBaseDir().resolve("exports").resolve("bookmarks_" + System.currentTimeMillis() + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(bookmarks));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    public void importBookmarksWithFileDialog(java.util.function.Consumer<Integer> onComplete) {
        Thread.startVirtualThread(() -> {
            Path defaultDir = getBaseDir().resolve("exports");
            try {
                Files.createDirectories(defaultDir);
            } catch (IOException ignored) {
            }

            org.lwjgl.PointerBuffer filterPatterns = org.lwjgl.system.MemoryUtil.memAllocPointer(1);
            try {
                filterPatterns.put(org.lwjgl.system.MemoryUtil.memUTF8("*.json"));
                filterPatterns.flip();

                String selected = TinyFileDialogs.tinyfd_openFileDialog(
                        "Import ClientChatPlus Bookmarks",
                        defaultDir.toAbsolutePath() + java.io.File.separator,
                        filterPatterns,
                        "JSON Bookmark Files (*.json)",
                        false
                );

                if (selected != null && !selected.isBlank()) {
                    Path chosen = Path.of(selected);
                    List<BookmarkEntry> imported = GSON.fromJson(Files.readString(chosen), new TypeToken<List<BookmarkEntry>>() {
                    }.getType());
                    int added = 0;
                    if (imported != null) {
                        for (BookmarkEntry entry : imported) {
                            if (bookmarks.stream().noneMatch(b -> b.id.equals(entry.id))) {
                                bookmarks.addFirst(entry);
                                RichChatMessage msg = new RichChatMessage(Component.literal(entry.rawText));
                                msg.bookmarked = true;
                                messages.addFirst(msg);
                                added++;
                            }
                        }
                        saveBookmarks();
                    }
                    int finalAdded = added;
                    Minecraft.getInstance().execute(() -> onComplete.accept(finalAdded));
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(filterPatterns);
            }
        });
    }

    public void setMaxScrollPixels(int maxScroll) {
        this.maxScrollPixels = Math.max(0, maxScroll);
        if (this.scrollPixels > this.maxScrollPixels) {
            this.scrollPixels = this.maxScrollPixels;
        }
    }

    public void scroll(int deltaPixels) {
        this.scrollPixels = Math.max(0, Math.min(this.maxScrollPixels, this.scrollPixels + deltaPixels));
        if (this.scrollPixels == 0) {
            this.unreadNewMessages = 0;
        }
    }

    public int getScrollPixels() {
        return scrollPixels;
    }

    public void resetScroll() {
        scrollPixels = 0;
        unreadNewMessages = 0;
        searchMode = SearchMode.NONE;
        searchQuery = "";
    }

    public void invalidateLayouts() {
        for (RichChatMessage msg : messages) {
            msg.lastWrapWidth = -1;
        }
    }

    public float updateAndGetPushOffset() {
        long now = System.nanoTime();
        double deltaSeconds = (now - lastFrameNanos) / 1_000_000_000.0;
        lastFrameNanos = now;
        if (currentPushOffset > 0.05f) {
            float decay = (float) Math.exp(-18.0 * Math.min(deltaSeconds, 0.1));
            currentPushOffset *= decay;
        } else {
            currentPushOffset = 0.0f;
        }
        return currentPushOffset;
    }

    public void clear() {
        messages.clear();
        currentPushOffset = 0.0f;
        scrollPixels = 0;
        unreadNewMessages = 0;
    }

    public record BookmarkEntry(String id, String rawText, long savedAt) {
    }
}