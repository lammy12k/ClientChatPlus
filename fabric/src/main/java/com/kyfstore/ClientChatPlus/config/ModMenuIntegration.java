package com.kyfstore.ClientChatPlus.config;

import com.kyfstore.ClientChatPlus.config.YaclConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return YaclConfigScreen::create;
    }
}
