package com.kyfstore.ClientChatPlus.platform;

import java.util.Objects;

public class Services {
    private static PlatformHelper platform;

    public static void init(PlatformHelper helper) {
        platform = Objects.requireNonNull(helper);
    }

    public static PlatformHelper platform() {
        if (platform == null) {
            throw new IllegalStateException("PlatformHelper accessed before loader entrypoint initialized it!");
        }
        return platform;
    }
}
