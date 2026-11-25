package com.kyfstore.ClientChatPlus;

import com.kyfstore.ClientChatPlus.config.YaclConfigScreen;
import com.kyfstore.ClientChatPlus.platform.Services;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = ClientChatPlusCommon.MOD_ID, dist = Dist.CLIENT)
public class ClientChatPlusNeoforge {
    public ClientChatPlusNeoforge(IEventBus modEventBus, ModContainer modContainer) {
        Services.init(FMLPaths.GAMEDIR::get);

        ClientChatPlusCommon.initialize();
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, (container, parent) -> YaclConfigScreen.create(parent));
    }
}
