package com.kyfstore.ClientChatPlus.chat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public class WaterMediaBridge {
    private static Boolean available = null;
    private static Class<?> playerApiClass;
    private static Class<?> mediaPlayerClass;
    private static Class<?> mrlClass;
    private static Thread renderThread;
    private static Executor renderExecutor;

    public static boolean isAvailable() {
        if (available != null) return available;
        try {
            playerApiClass = Class.forName("org.watermedia.api.player.PlayerAPI");
            mediaPlayerClass = Class.forName("org.watermedia.api.player.MediaPlayer");
            mrlClass = Class.forName("org.watermedia.api.player.MRL");
            available = true;
            return true;
        } catch (Throwable t) {
            available = false;
            return false;
        }
    }

    public static VideoPlayerHandle createPlayer(String url) {
        if (!isAvailable()) return null;
        try {
            if (renderThread == null) {
                renderThread = Thread.currentThread();
                renderExecutor = cmd -> Minecraft.getInstance().execute(cmd);
            }
            Method mrlMethod = playerApiClass.getMethod("mrl", String.class);
            Object mrl = mrlMethod.invoke(null, url);
            return new VideoHandle(url, mrl);
        } catch (Throwable t) {
            return null;
        }
    }

    public static class VideoHandle implements VideoPlayerHandle {
        private final String url;
        private final Object mrl;
        private final Identifier textureId;
        private Object player;
        private boolean initFailed = false;
        private GlTexture wrappedGlTexture;
        private int lastBoundGlId = -1;
        private boolean muted = false;
        private boolean paused = true;
        private int volume = 100;

        public VideoHandle(String url, Object mrl) {
            this.url = url;
            this.mrl = mrl;
            this.textureId = Identifier.fromNamespaceAndPath("clientchatplus", "video/" + UUID.randomUUID().toString().replace("-", ""));
        }

        private void tryBuildPlayer() {
            if (player != null || initFailed) return;
            try {
                Object status = mrlClass.getMethod("status").invoke(mrl);
                String statusName = status != null ? status.toString() : "UNKNOWN";
                if ("FETCHING".equals(statusName) || "WAITING".equals(statusName)) {
                    return;
                }
                if ("ERROR".equals(statusName)) {
                    initFailed = true;
                    return;
                }

                Method glEngineMethod = playerApiClass.getMethod("glEngine", Thread.class, Executor.class);
                Method alEngineMethod = playerApiClass.getMethod("alEngine");
                Supplier<Object> gfxSupplier = () -> {
                    try {
                        return glEngineMethod.invoke(null, renderThread, renderExecutor);
                    } catch (Throwable t) {
                        return null;
                    }
                };
                Supplier<Object> sfxSupplier = () -> {
                    try {
                        return alEngineMethod.invoke(null);
                    } catch (Throwable t) {
                        return null;
                    }
                };

                Method playerMethod = playerApiClass.getMethod("player", mrlClass, Supplier.class, Supplier.class);
                Object created = playerMethod.invoke(null, mrl, gfxSupplier, sfxSupplier);
                if (created != null) {
                    this.player = created;
                    this.paused = true;
                    try {
                        mediaPlayerClass.getMethod("startPaused").invoke(this.player);
                    } catch (Throwable ignored) {
                        mediaPlayerClass.getMethod("start").invoke(this.player);
                        mediaPlayerClass.getMethod("pause", boolean.class).invoke(this.player, true);
                    }
                    ChatHistoryManager.getInstance().invalidateLayouts();
                } else {
                    Object afterStatus = mrlClass.getMethod("status").invoke(mrl);
                    if (afterStatus != null && !"FETCHING".equals(afterStatus.toString())) {
                        initFailed = true;
                    }
                }
            } catch (Throwable t) {
                initFailed = true;
            }
        }

        @Override
        public Identifier updateAndGetTextureId() {
            if (player == null) {
                tryBuildPlayer();
                if (player == null) return null;
            }
            try {
                Object texObj = mediaPlayerClass.getMethod("texture").invoke(player);
                int glId = (texObj instanceof Number num) ? num.intValue() : -1;
                if (glId <= 0) return null;

                int w = getWidth();
                int h = getHeight();
                if (w <= 0 || h <= 0) return null;

                if (glId != lastBoundGlId || wrappedGlTexture == null) {
                    lastBoundGlId = glId;
                    wrappedGlTexture = new ExternalGlTexture(glId, w, h);
                    GpuTextureView view = RenderSystem.getDevice().createTextureView(wrappedGlTexture);
                    AbstractTexture mcTex = new AbstractTexture() {
                        {
                            this.texture = wrappedGlTexture;
                            this.textureView = view;
                        }

                        @Override
                        public GpuTexture getTexture() {
                            return wrappedGlTexture;
                        }

                        @Override
                        public GpuTextureView getTextureView() {
                            return view;
                        }
                    };
                    Minecraft.getInstance().getTextureManager().register(textureId, mcTex);
                    ChatHistoryManager.getInstance().invalidateLayouts();
                }
                return textureId;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public int getWidth() {
            if (player == null) return 0;
            try {
                Object res = mediaPlayerClass.getMethod("width").invoke(player);
                return (res instanceof Number num) ? num.intValue() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public int getHeight() {
            if (player == null) return 0;
            try {
                Object res = mediaPlayerClass.getMethod("height").invoke(player);
                return (res instanceof Number num) ? num.intValue() : 0;
            } catch (Throwable t) {
                return 0;
            }
        }

        @Override
        public boolean isBroken() {
            if (initFailed) return true;
            if (player == null) return false;
            try {
                Object status = mediaPlayerClass.getMethod("status").invoke(player);
                return status != null && "ERROR".equals(status.toString());
            } catch (Throwable t) {
                return false;
            }
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public boolean isMuted() {
            return muted;
        }

        @Override
        public void togglePause() {
            if (player == null) return;
            paused = !paused;
            try {
                mediaPlayerClass.getMethod("pause", boolean.class).invoke(player, paused);
            } catch (Throwable t) {
            }
        }

        @Override
        public void toggleMute() {
            if (player == null) return;
            muted = !muted;
            try {
                mediaPlayerClass.getMethod("mute", boolean.class).invoke(player, muted);
            } catch (Throwable t) {
            }
        }

        @Override
        public int getVolume() {
            return muted ? 0 : volume;
        }

        @Override
        public void setVolume(int vol) {
            this.volume = Math.max(0, Math.min(100, vol));
            this.muted = (this.volume == 0);
            if (player == null) return;
            try {
                mediaPlayerClass.getMethod("mute", boolean.class).invoke(player, this.muted);
                mediaPlayerClass.getMethod("volume", int.class).invoke(player, this.volume);
            } catch (Throwable ignored) {
            }
        }

        @Override
        public long getTimeMs() {
            if (player == null) return 0L;
            try {
                Object res = mediaPlayerClass.getMethod("time").invoke(player);
                return (res instanceof Number num) ? Math.max(0L, num.longValue()) : 0L;
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        @Override
        public long getDurationMs() {
            if (player == null) return 0L;
            try {
                Object res = mediaPlayerClass.getMethod("duration").invoke(player);
                return (res instanceof Number num) ? Math.max(0L, num.longValue()) : 0L;
            } catch (Throwable ignored) {
                return 0L;
            }
        }

        @Override
        public void seekMs(long targetMs) {
            if (player == null) return;
            try {
                mediaPlayerClass.getMethod("seek", long.class).invoke(player, Math.max(0L, targetMs));
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void release() {
            if (player == null) return;
            try {
                mediaPlayerClass.getMethod("release").invoke(player);
            } catch (Throwable t) {
            }
        }

        private static class ExternalGlTexture extends GlTexture {
            protected ExternalGlTexture(int glId, int width, int height) {
                super(GpuTexture.USAGE_TEXTURE_BINDING, "clientchatplus_watermedia", GpuFormat.RGBA8_UNORM, width, height, 1, 1, glId, null);
            }

            @Override
            public void close() {
            }
        }
    }
}