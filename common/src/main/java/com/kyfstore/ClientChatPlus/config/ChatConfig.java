package com.kyfstore.ClientChatPlus.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kyfstore.ClientChatPlus.platform.Services;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ChatConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ChatConfig INSTANCE = new ChatConfig();

    public int messageSpacing = 1;
    public int lineSpacing = 1;
    public float chatScale = 1.0f;
    public int offsetX = 4;
    public int offsetY = 40;
    public boolean enableMarkdown = true;
    public boolean enableMiniMessage = true;
    public boolean renderImages = true;
    public boolean inlineImages = true;
    public boolean websiteHoverPreviews = true;
    public boolean renderDiscordAttachments = true;
    public boolean saveHistoryToFile = false;

    public static ChatConfig get() {
        return INSTANCE;
    }

    private static Path getConfigPath() {;
        return Services.platform().getGameDir().resolve("config").resolve("clientchatplus.json");
    }

    public static void load() {
        Path path = getConfigPath();
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                ChatConfig loaded = GSON.fromJson(json, ChatConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public static void save() {
        Path path = getConfigPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
