package com.kyfstore.ClientChatPlus.chat;

import net.minecraft.resources.Identifier;

public interface VideoPlayerHandle {
    Identifier updateAndGetTextureId();

    int getWidth();

    int getHeight();

    boolean isBroken();

    boolean isPaused();

    void togglePause();

    boolean isMuted();

    void toggleMute();

    int getVolume();

    void setVolume(int volume);

    long getTimeMs();

    long getDurationMs();

    void seekMs(long targetMs);

    void release();
}
