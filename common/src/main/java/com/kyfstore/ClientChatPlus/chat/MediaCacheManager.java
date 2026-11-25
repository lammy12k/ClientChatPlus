package com.kyfstore.ClientChatPlus.chat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaCacheManager {
    private static final MediaCacheManager INSTANCE = new MediaCacheManager();
    private static final long MAX_BYTES = 16L * 1024L * 1024L;
    private static final int MAX_TEXT_PREVIEW_BYTES = 24 * 1024;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp", ".bmp");
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(".mp4", ".mov", ".webm", ".mkv", ".avi");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            ".txt", ".log", ".json", ".yml", ".yaml", ".toml", ".md", ".markdown",
            ".java", ".kt", ".kts", ".py", ".js", ".ts", ".jsx", ".tsx", ".html", ".css",
            ".xml", ".csv", ".cfg", ".conf", ".ini", ".properties", ".sh", ".bat", ".ps1",
            ".lua", ".rs", ".go", ".c", ".cpp", ".h", ".hpp", ".cs", ".sql", ".gradle"
    );
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".zip", ".rar", ".7z", ".tar", ".gz", ".jar", ".pdf", ".exe", ".dll", ".so",
            ".mp3", ".wav", ".ogg", ".flac", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx"
    );
    private static final Pattern OG_IMAGE_PATTERN = Pattern.compile(
            "<meta[^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"'][^>]+content=[\"']([^\"']+)[\"']" +
                    "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:property|name)=[\"'](?:og:image|twitter:image)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GIF_URL_IN_HTML_PATTERN = Pattern.compile(
            "https?://[^\"'\\s<>\\\\]+\\.gif(?:\\?[^\"'\\s<>\\\\]*)?",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DESC_PATTERN = Pattern.compile(
            "<meta[^>]+(?:name|property)=[\"'](?:description|og:description)[\"'][^>]+content=[\"']([^\"']+)[\"']" +
                    "|<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+(?:name|property)=[\"'](?:description|og:description)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    public enum ResolvedUrlType {
        PENDING,
        IMAGE,
        ATTACHMENT,
        WEBSITE
    }

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final Map<String, CachedImage> imageCache = new ConcurrentHashMap<>();
    private final Map<String, CachedAttachment> attachmentCache = new ConcurrentHashMap<>();
    private final Map<String, ResolvedUrlType> probedTypes = new ConcurrentHashMap<>();
    private final Map<String, String> resolvedMediaUrls = new ConcurrentHashMap<>();

    public static MediaCacheManager get() {
        return INSTANCE;
    }

    public static boolean isImageUrl(String url) {
        if (INSTANCE.resolvedMediaUrls.containsKey(url)) return true;
        String path = extractCleanPath(url).toLowerCase(Locale.ROOT);
        for (String ext : IMAGE_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return INSTANCE.probedTypes.get(url) == ResolvedUrlType.IMAGE;
    }

    public static boolean isVideoFile(String fileNameOrUrl) {
        String path = extractCleanPath(fileNameOrUrl).toLowerCase(Locale.ROOT);
        for (String ext : VIDEO_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return false;
    }

    public static boolean isPlainTextFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String ext : TEXT_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
        }
        return false;
    }

    public static String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        return "text";
    }

    public static boolean isFileAttachmentUrl(String url) {
        if (isImageUrl(url)) return false;
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.contains("://discordapp.com")
                || lower.contains("media.discordapp.net/attachments/")
                || lower.contains("://discordapp.com")) {
            return true;
        }
        String path = extractCleanPath(url).toLowerCase(Locale.ROOT);
        for (String ext : TEXT_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        for (String ext : VIDEO_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        for (String ext : BINARY_EXTENSIONS) {
            if (path.endsWith(ext)) return true;
        }
        return INSTANCE.probedTypes.get(url) == ResolvedUrlType.ATTACHMENT;
    }

    public void probeUrlIfUnknown(String url) {
        if (isImageUrl(url) || isFileAttachmentUrl(url)) return;
        probedTypes.computeIfAbsent(url, u -> {
            executor.submit(() -> {
                try {
                    String lower = u.toLowerCase(Locale.ROOT);
                    if (lower.contains("://tenor.com") || lower.contains("://klipy.com") || lower.contains("://giphy.com")) {
                        String gifOrOg = extractGifOrOgImage(u, true);
                        if (gifOrOg != null) {
                            resolvedMediaUrls.put(u, gifOrOg);
                            probedTypes.put(u, ResolvedUrlType.IMAGE);
                            getOrFetchImage(u);
                            Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
                            return;
                        }
                    }

                    HttpRequest headReq = HttpRequest.newBuilder(URI.create(u))
                            .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .timeout(Duration.ofSeconds(6))
                            .build();
                    HttpResponse<Void> resp = httpClient.send(headReq, HttpResponse.BodyHandlers.discarding());
                    String contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    if (contentType.startsWith("image/")) {
                        probedTypes.put(u, ResolvedUrlType.IMAGE);
                        getOrFetchImage(u);
                        Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
                    } else if (contentType.startsWith("video/") || contentType.equals("application/octet-stream") || contentType.equals("application/zip") || contentType.equals("application/pdf")) {
                        probedTypes.put(u, ResolvedUrlType.ATTACHMENT);
                        getOrFetchAttachment(u);
                        Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
                    } else {
                        probedTypes.put(u, ResolvedUrlType.WEBSITE);
                    }
                } catch (Exception e) {
                    probedTypes.put(u, ResolvedUrlType.WEBSITE);
                }
            });
            return ResolvedUrlType.PENDING;
        });
    }

    private String fetchHtml(String pageUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(pageUrl))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)")
                    .timeout(Duration.ofSeconds(8))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return resp.body();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractGifOrOgImage(String pageUrl, boolean preferAnimatedGif) {
        String targetPage = pageUrl;
        if (pageUrl.toLowerCase(Locale.ROOT).contains("klipy.com/gifs/") && !pageUrl.endsWith("/player")) {
            targetPage = pageUrl.replaceAll("/+$", "") + "/player";
        }

        String html = fetchHtml(targetPage);
        if (html == null && !targetPage.equals(pageUrl)) {
            html = fetchHtml(pageUrl);
        }
        if (html == null) return null;

        String ogCandidate = null;
        Matcher m = OG_IMAGE_PATTERN.matcher(html);
        while (m.find()) {
            String found = m.group(1) != null ? m.group(1) : m.group(2);
            if (found != null && !found.isBlank()) {
                try {
                    String clean = found.replace("&amp;", "&");
                    ogCandidate = URI.create(targetPage).resolve(clean).toString();
                    break;
                } catch (Exception ignored) {
                }
            }
        }

        if (preferAnimatedGif) {
            if (ogCandidate != null && ogCandidate.toLowerCase(Locale.ROOT).contains(".gif")) {
                return ogCandidate;
            }

            Matcher gifMatcher = GIF_URL_IN_HTML_PATTERN.matcher(html);
            String bestGif = null;
            while (gifMatcher.find()) {
                String candidate = gifMatcher.group().replace("\\u002F", "/").replace("\\/", "/").replace("&amp;", "&");
                String lowerCand = candidate.toLowerCase(Locale.ROOT);
                if (lowerCand.contains("favicon") || lowerCand.contains("logo")) continue;
                if (!lowerCand.contains("_s.gif") && !lowerCand.contains("static.")) {
                    return candidate;
                }
                if (bestGif == null) {
                    bestGif = candidate;
                }
            }

            if (bestGif != null) return bestGif;
        }

        return ogCandidate;
    }

    public static String extractFileName(String url) {
        String path = extractCleanPath(url);
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        return name.isBlank() ? "attachment" : name;
    }
    private static String extractCleanPath(String url) {
        int queryIdx = url.indexOf('?');
        String clean = queryIdx >= 0 ? url.substring(0, queryIdx) : url;
        int hashIdx = clean.indexOf('#');
        return hashIdx >= 0 ? clean.substring(0, hashIdx) : clean;
    }
    public CachedImage getOrFetchImage(String imageUrl) {
        String targetUrl = resolvedMediaUrls.getOrDefault(imageUrl, imageUrl);
        return imageCache.computeIfAbsent(imageUrl, key -> {
            CachedImage entry = new CachedImage();
            executor.submit(() -> downloadAndRegisterImage(targetUrl, entry));
            return entry;
        });
    }
    public CachedImage getOrFetchWebsitePreview(String siteUrl) {
        String cacheKey = "preview:" + siteUrl;
        return imageCache.computeIfAbsent(cacheKey, key -> {
            CachedImage entry = new CachedImage();
            executor.submit(() -> {
                String html = fetchHtml(siteUrl);
                if (html != null) {
                    Matcher titleM = TITLE_PATTERN.matcher(html);
                    if (titleM.find()) {
                        entry.siteTitle = titleM.group(1).replaceAll("\s+", " ").trim();
                    }
                    Matcher descM = DESC_PATTERN.matcher(html);
                    if (descM.find()) {
                        String d = descM.group(1) != null ? descM.group(1) : descM.group(2);
                        if (d != null) entry.siteDescription = d.replaceAll("\s+", " ").trim();
                    }
                    Matcher ogM = OG_IMAGE_PATTERN.matcher(html);
                    while (ogM.find()) {
                        String found = ogM.group(1) != null ? ogM.group(1) : ogM.group(2);
                        if (found != null && !found.isBlank()) {
                            try {
                                String resolvedOg = URI.create(siteUrl).resolve(found.replace("&", "&")).toString();
                                downloadAndRegisterImage(resolvedOg, entry);
                                if (entry.ready) return;
                                entry.failed = false;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                String encoded = URLEncoder.encode(siteUrl, StandardCharsets.UTF_8);
                String shotUrl = "wp.com" + encoded + "?w=400";
                downloadAndRegisterImage(shotUrl, entry);
            });
            return entry;
        });
    }
    public CachedAttachment getOrFetchAttachment(String fileUrl) {
        return attachmentCache.computeIfAbsent(fileUrl, url -> {
            CachedAttachment entry = new CachedAttachment(extractFileName(url), url);
            executor.submit(() -> fetchAttachment(url, entry));
            return entry;
        });
    }
    private void fetchAttachment(String url, CachedAttachment entry) {
        if (entry.isVideo) {
            if (VlcVideoBridge.isAvailable()) {
                entry.videoPlayer = VlcVideoBridge.createPlayer(url);
                Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
            } else if (WaterMediaBridge.isAvailable()) {
                Minecraft.getInstance().execute(() -> {
                    if (entry.videoPlayer == null) {
                        entry.videoPlayer = WaterMediaBridge.createPlayer(url);
                        ChatHistoryManager.getInstance().invalidateLayouts();
                    }
                });
            }
        }
        try {
            if (entry.isPlainText) {
                HttpRequest getReq = HttpRequest.newBuilder(URI.create(url))
                        .GET()
                        .header("User-Agent", "Mozilla/5.0 ClientChatPlus/1.0")
                        .timeout(Duration.ofSeconds(8))
                        .build();
                HttpResponse<byte[]> resp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300 && resp.body() != null) {
                    byte[] raw = resp.body();
                    entry.sizeLabel = formatBytes(raw.length);
                    boolean truncated = raw.length > MAX_TEXT_PREVIEW_BYTES;
                    byte[] slice = truncated ? Arrays.copyOf(raw, MAX_TEXT_PREVIEW_BYTES) : raw;
                    String text = new String(slice, StandardCharsets.UTF_8).replace("\r\n", "\n");
                    if (truncated) {
                        text += "\n... (truncated)";
                    }
                    String[] allLines = text.split("\n", -1);
                    if (allLines.length > 14) {
                        text = String.join("\n", Arrays.copyOf(allLines, 14)) + "\n... (" + (allLines.length - 14) + " more lines)";
                    }
                    entry.textPreviewBlock = new ChatCodeBlock(extractExtension(entry.fileName), text);
                    Minecraft.getInstance().execute(() -> ChatHistoryManager.getInstance().invalidateLayouts());
                    return;
                }
            }
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .header("User-Agent", "Mozilla/5.0 ClientChatPlus/1.0")
                    .timeout(Duration.ofSeconds(6))
                    .build();
            HttpResponse resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            long len = resp.headers().firstValueAsLong("Content-Length").orElse(-1L);
            String prefix = entry.isVideo ? (extractExtension(entry.fileName).toUpperCase(Locale.ROOT) + " Video • ") : "";
            if (len >= 0) {
                entry.sizeLabel = prefix + formatBytes(len);
            } else {
                entry.sizeLabel = entry.isVideo ? (extractExtension(entry.fileName).toUpperCase(Locale.ROOT) + " Video") : "File Attachment";
            }
        } catch (Exception e) {
            entry.sizeLabel = entry.isVideo ? "Video Attachment" : "Attachment";
        }
    }
    private void downloadAndRegisterImage(String url, CachedImage entry) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "image/webp,image/apng,image/png,image/jpeg,image/gif,image/,/*;q=0.8")
                    .timeout(Duration.ofSeconds(12))
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                entry.failed = true;
                return;
            }
            byte[] data = resp.body();
            if (data == null || data.length == 0 || data.length > MAX_BYTES) {
                entry.failed = true;
                return;
            }
            String contentType = resp.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
            if (contentType.contains("gif") || extractCleanPath(url).toLowerCase(Locale.ROOT).endsWith(".gif")) {
                List<DecodedGifFrame> gifFrames = decodeAnimatedGif(data);
                if (gifFrames != null && gifFrames.size() > 1) {
                    int w = gifFrames.getFirst().image.getWidth();
                    int h = gifFrames.getFirst().image.getHeight();
                    Minecraft.getInstance().execute(() -> {
                        for (DecodedGifFrame f : gifFrames) {
                            Identifier texId = Identifier.fromNamespaceAndPath("clientchatplus", "cache/" + UUID.randomUUID().toString().replace("-", ""));
                            DynamicTexture tex = new DynamicTexture(() -> "clientchatplus_" + texId.getPath(), f.image);
                            Minecraft.getInstance().getTextureManager().register(texId, tex);
                            entry.frames.add(new GifFrame(texId, tex, f.delayMs));
                        }
                        entry.textureId = entry.frames.getFirst().textureId;
                        entry.texture = entry.frames.getFirst().texture;
                        entry.width = w;
                        entry.height = h;
                        entry.ready = true;
                        ChatHistoryManager.getInstance().invalidateLayouts();
                    });
                    return;
                }
            }
            NativeImage nativeImage = decodeToNativeImage(data);
            if (nativeImage == null) {
                entry.failed = true;
                return;
            }
            int w = nativeImage.getWidth();
            int h = nativeImage.getHeight();
            Identifier texId = Identifier.fromNamespaceAndPath("clientchatplus", "cache/" + UUID.randomUUID().toString().replace("-", ""));
            Minecraft.getInstance().execute(() -> {
                DynamicTexture texture = new DynamicTexture(() -> "clientchatplus_" + texId.getPath(), nativeImage);
                Minecraft.getInstance().getTextureManager().register(texId, texture);
                entry.textureId = texId;
                entry.texture = texture;
                entry.width = w;
                entry.height = h;
                entry.ready = true;
                ChatHistoryManager.getInstance().invalidateLayouts();
            });
        } catch (Exception e) {
            entry.failed = true;
        }
    }
    private static List<DecodedGifFrame> decodeAnimatedGif(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes);
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            reader.setInput(iis, false);
            int count = reader.getNumImages(true);
            if (count <= 1) {
                reader.dispose();
                return null;
            }
            int canvasW = reader.getWidth(0);
            int canvasH = reader.getHeight(0);
            BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            List<DecodedGifFrame> result = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                BufferedImage rawFrame = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                int left = 0;
                int top = 0;
                int delayMs = 80;
                String disposal = "none";
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                NodeList children = root.getChildNodes();
                for (int c = 0; c < children.getLength(); c++) {
                    Node node = children.item(c);
                    if ("ImageDescriptor".equals(node.getNodeName())) {
                        NamedNodeMap attrs = node.getAttributes();
                        left = Integer.parseInt(attrs.getNamedItem("imageLeftPosition").getNodeValue());
                        top = Integer.parseInt(attrs.getNamedItem("imageTopPosition").getNodeValue());
                    } else if ("GraphicControlExtension".equals(node.getNodeName())) {
                        NamedNodeMap attrs = node.getAttributes();
                        int delayCs = Integer.parseInt(attrs.getNamedItem("delayTime").getNodeValue());
                        delayMs = Math.max(20, delayCs * 10);
                        Node dispNode = attrs.getNamedItem("disposalMethod");
                        if (dispNode != null) disposal = dispNode.getNodeValue();
                    }
                }
                g.drawImage(rawFrame, left, top, null);
                ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
                ImageIO.write(canvas, "PNG", pngOut);
                try (InputStream pngIn = new ByteArrayInputStream(pngOut.toByteArray())) {
                    NativeImage nativeFrame = NativeImage.read(pngIn);
                    result.add(new DecodedGifFrame(nativeFrame, delayMs));
                }
                if ("restoreToBackgroundColor".equals(disposal)) {
                    g.setComposite(AlphaComposite.Clear);
                    g.fillRect(left, top, rawFrame.getWidth(), rawFrame.getHeight());
                    g.setComposite(AlphaComposite.SrcOver);
                }
            }
            g.dispose();
            reader.dispose();
            return result;
        } catch (Exception e) {
            return null;
        }
    }
    private static NativeImage decodeToNativeImage(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return NativeImage.read(in);
        } catch (Exception ignored) {
        }
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage buffered = ImageIO.read(in);
            if (buffered == null) return null;
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(buffered, "PNG", pngOut);
            try (InputStream pngIn = new ByteArrayInputStream(pngOut.toByteArray())) {
                return NativeImage.read(pngIn);
            }
        } catch (Exception e) {
            return null;
        }
    }
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.1f MB", mb);
    }
    public void clearCache() {
        Minecraft.getInstance().execute(() -> {
            for (CachedImage img : imageCache.values()) {
                for (GifFrame frame : img.frames) {
                    if (frame.texture != null) frame.texture.close();
                }
                if (img.frames.isEmpty() && img.texture != null) {
                    img.texture.close();
                }
            }
            for (CachedAttachment att : attachmentCache.values()) {
                if (att.videoPlayer != null) {
                    att.videoPlayer.release();
                }
            }
            imageCache.clear();
            attachmentCache.clear();
            probedTypes.clear();
            resolvedMediaUrls.clear();
        });
    }
    private record DecodedGifFrame(NativeImage image, int delayMs) {}
    public record GifFrame(Identifier textureId, DynamicTexture texture, int delayMs) {}
    public static class CachedImage {
        public volatile boolean ready = false;
        public volatile boolean failed = false;
        public volatile Identifier textureId;
        public volatile DynamicTexture texture;
        public volatile int width;
        public volatile int height;
        public volatile String siteTitle = null;
        public volatile String siteDescription = null;
        public final List<GifFrame> frames = new ArrayList<>();
        private int currentFrameIdx = 0;
        private long lastFrameSwitchMs = System.currentTimeMillis();
        public Identifier getCurrentTextureId() {
            if (frames.size() <= 1) {
                return textureId;
            }
            long now = System.currentTimeMillis();
            GifFrame current = frames.get(currentFrameIdx);
            if (now - lastFrameSwitchMs >= current.delayMs) {
                currentFrameIdx = (currentFrameIdx + 1) % frames.size();
                lastFrameSwitchMs = now;
            }
            return frames.get(currentFrameIdx).textureId;
        }
    }
    public static class CachedAttachment {
        public final String fileName;
        public final String url;
        public final boolean isPlainText;
        public final boolean isVideo;
        public volatile String sizeLabel = "Loading...";
        public volatile ChatCodeBlock textPreviewBlock = null;
        public volatile VideoPlayerHandle videoPlayer = null;
        public CachedAttachment(String fileName, String url) {
            this.fileName = fileName;
            this.url = url;
            this.isPlainText = isPlainTextFile(fileName);
            this.isVideo = isVideoFile(fileName);
        }
    }
}