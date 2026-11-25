package com.kyfstore.ClientChatPlus.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

public abstract class ScreenClickInvoker extends Screen {
    private ScreenClickInvoker() {
        super(Component.empty());
    }

    public static void invoke(Screen screen, ClickEvent event) {
        Screen.defaultHandleGameClickEvent(event, Minecraft.getInstance(), screen);
    }
}
