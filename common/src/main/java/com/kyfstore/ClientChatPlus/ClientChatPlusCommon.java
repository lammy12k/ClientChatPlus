package com.kyfstore.ClientChatPlus;

import com.kyfstore.ClientChatPlus.chat.ChatHistoryManager;
import com.kyfstore.ClientChatPlus.config.ChatConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientChatPlusCommon {
    public static final String MOD_ID = "clientchatplus";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void initialize() {
        ChatConfig.load();
        ChatHistoryManager.getInstance().loadBookmarks();
    }
}
