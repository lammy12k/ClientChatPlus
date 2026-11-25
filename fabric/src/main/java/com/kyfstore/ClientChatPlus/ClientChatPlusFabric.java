package com.kyfstore.ClientChatPlus;

import com.kyfstore.ClientChatPlus.platform.Services;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class ClientChatPlusFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Services.init(() -> FabricLoader.getInstance().getGameDir());

        ClientChatPlusCommon.initialize();
    }
}
