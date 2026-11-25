package com.kyfstore.ClientChatPlus.chat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallbackAdapter;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.UUID;

public class VlcVideoBridge {
    private static Boolean available = null;
    private static MediaPlayerFactory factory = null;

    public static boolean isAvailable() {
        if (available != null) return available;
        try {
            boolean discovered = new NativeDiscovery().discover();
            if (!discovered) {
                for (String path : new String[]{
                        "C:\\Program Files\\VideoLAN\\VLC",
                        "C:\\Program Files (x86)\\VideoLAN\\VLC",
                        "/Applications/VLC.app/Contents/MacOS/lib",
                        "/usr/lib",
                        "/usr/local/lib"
                }) {
                    if (new File(path).exists()) {
                        System.setProperty("jna.library.path", path);
                        discovered = true;
                        break;
                    }
                }
            }
            if (!discovered) {
                available = false;
                return false;
            }
            factory = new MediaPlayerFactory("--no-video-title-show", "--quiet");
            available = true;
            System.out.println("[ClientChatPlus] VLC (libVLC + vlcj) detected and initialized!");
            return true;
        } catch (Throwable t) {
            System.out.println("[ClientChatPlus] WARN: VLC not available: " + t.getMessage());
            available = false;
            return false;
        }
    }

    public static VideoPlayerHandle createPlayer(String url) {
        if (!isAvailable() || factory == null) return null;
        try {
            return new VlcHandle(factory, url);
        } catch (Throwable t) {
            System.out.println("[ClientChatPlus] WARN: Failed to create VLC player for " + url);
            t.printStackTrace();
            return null;
        }
    }

    public static class VlcHandle implements VideoPlayerHandle {
        private final EmbeddedMediaPlayer mediaPlayer;
        private final Identifier textureId;
        private DynamicTexture dynamicTexture;
        private NativeImage nativeImage;
        private int[] pixelStaging;
        private volatile int videoWidth = 0;
        private volatile int videoHeight = 0;
        private volatile boolean frameDirty = false;
        private volatile boolean broken = false;
        private volatile boolean paused = true;
        private volatile boolean muted = false;
        private volatile int volume = 100;

        public VlcHandle(MediaPlayerFactory factory, String url) {
            this.textureId = Identifier.fromNamespaceAndPath("clientchatplus", "vlc/" + UUID.randomUUID().toString().replace("-", ""));
            this.mediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer();

            CallbackVideoSurface surface = new CallbackVideoSurface(
                    new BufferFormatCallbackAdapter() {
                        @Override
                        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
                            videoWidth = sourceWidth;
                            videoHeight = sourceHeight;
                            pixelStaging = new int[sourceWidth * sourceHeight];
                            Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
                            return new BufferFormat("RV32", sourceWidth, sourceHeight, new int[]{sourceWidth * 4}, new int[]{sourceHeight});
                        }
                    },
                    new RenderCallback() {
                        @Override
                        public void lock(MediaPlayer mp) {
                        }

                        @Override
                        public void display(MediaPlayer mp, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat, int displayWidth, int displayHeight) {
                            if (nativeBuffers == null || nativeBuffers.length == 0 || pixelStaging == null) return;
                            ByteBuffer buf = nativeBuffers[0].duplicate();
                            buf.rewind();
                            int count = Math.min(pixelStaging.length, buf.remaining() / 4);
                            for (int i = 0; i < count; i++) {
                                int b = buf.get() & 0xFF;
                                int g = buf.get() & 0xFF;
                                int r = buf.get() & 0xFF;
                                int a = buf.get() & 0xFF;
                                pixelStaging[i] = (a << 24) | (r << 16) | (g << 8) | b;
                            }
                            frameDirty = true;
                        }

                        @Override
                        public void unlock(MediaPlayer mp) {
                        }
                    },
                    true,
                    VideoSurfaceAdapters.getVideoSurfaceAdapter()
            );

            this.mediaPlayer.videoSurface().set(surface);
            this.mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                @Override
                public void error(MediaPlayer mp) {
                    System.out.println("[ClientChatPlus] WARN: VLC playback error for " + url);
                    broken = true;
                }

                @Override
                public void finished(MediaPlayer mp) {
                    paused = true;
                }
            });

            this.mediaPlayer.media().prepare(url);
        }

        @Override
        public Identifier updateAndGetTextureId() {
            int w = videoWidth;
            int h = videoHeight;
            if (w <= 0 || h <= 0 || pixelStaging == null) return null;

            if (nativeImage == null || nativeImage.getWidth() != w || nativeImage.getHeight() != h) {
                if (dynamicTexture != null) {
                    dynamicTexture.close();
                }
                nativeImage = new NativeImage(NativeImage.Format.RGBA, w, h, false);
                dynamicTexture = new DynamicTexture(() -> "clientchatplus_" + textureId.getPath(), nativeImage);
                Minecraft.getInstance().getTextureManager().register(textureId, dynamicTexture);
                frameDirty = true;
            }

            if (frameDirty) {
                frameDirty = false;
                int idx = 0;
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        int argb = pixelStaging[idx++];
                        int a = (argb >>> 24) & 0xFF;
                        int r = (argb >>> 16) & 0xFF;
                        int g = (argb >>> 8) & 0xFF;
                        int b = argb & 0xFF;
                        int abgr = (a << 24) | (b << 16) | (g << 8) | r;
                        nativeImage.setPixelABGR(x, y, abgr);
                    }
                }
                dynamicTexture.upload();
            }

            return textureId;
        }

        @Override
        public int getWidth() {
            return videoWidth;
        }

        @Override
        public int getHeight() {
            return videoHeight;
        }

        @Override
        public boolean isBroken() {
            return broken;
        }

        @Override
        public boolean isPaused() {
            return paused;
        }

        @Override
        public void togglePause() {
            if (paused) {
                paused = false;
                mediaPlayer.controls().play();
            } else {
                paused = true;
                mediaPlayer.controls().setPause(true);
            }
        }

        @Override
        public boolean isMuted() {
            return muted;
        }

        @Override
        public void toggleMute() {
            muted = !muted;
            mediaPlayer.audio().setMute(muted);
        }

        @Override
        public int getVolume() {
            return muted ? 0 : volume;
        }

        @Override
        public void setVolume(int vol) {
            this.volume = Math.max(0, Math.min(100, vol));
            this.muted = (this.volume == 0);
            mediaPlayer.audio().setMute(this.muted);
            mediaPlayer.audio().setVolume(this.volume);
        }

        @Override
        public long getTimeMs() {
            return Math.max(0L, mediaPlayer.status().time());
        }

        @Override
        public long getDurationMs() {
            return Math.max(0L, mediaPlayer.status().length());
        }

        @Override
        public void seekMs(long targetMs) {
            mediaPlayer.controls().setTime(Math.max(0L, targetMs));
        }

        @Override
        public void release() {
            try {
                mediaPlayer.controls().stop();
                mediaPlayer.release();
            } catch (Throwable ignored) {
            }
            if (dynamicTexture != null) {
                dynamicTexture.close();
            }
        }
    }
}