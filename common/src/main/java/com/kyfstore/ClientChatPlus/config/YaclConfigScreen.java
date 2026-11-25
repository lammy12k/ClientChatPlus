package com.kyfstore.ClientChatPlus.config;

import com.kyfstore.ClientChatPlus.chat.ChatHistoryManager;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class YaclConfigScreen {

    public static Screen create(Screen parent) {
        ChatConfig cfg = ChatConfig.get();

        return YetAnotherConfigLib.createBuilder()
                .title(Component.literal("ClientChatPlus Config"))
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Appearance & Layout"))
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Message Spacing"))
                                .description(OptionDescription.of(Component.literal("Vertical gap in pixels between separate chat messages.")))
                                .binding(1, () -> cfg.messageSpacing, val -> cfg.messageSpacing = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 12).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Line Spacing"))
                                .description(OptionDescription.of(Component.literal("Vertical gap in pixels between wrapped lines inside a message.")))
                                .binding(1, () -> cfg.lineSpacing, val -> cfg.lineSpacing = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 8).step(1))
                                .build())
                        .option(Option.<Float>createBuilder()
                                .name(Component.literal("Chat Scale"))
                                .description(OptionDescription.of(Component.literal("Overall scale multiplier for custom chat rendering.")))
                                .binding(1.0f, () -> cfg.chatScale, val -> cfg.chatScale = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt).range(0.5f, 2.0f).step(0.05f))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("X Position Offset"))
                                .description(OptionDescription.of(Component.literal("Horizontal distance from the left edge of the screen.")))
                                .binding(4, () -> cfg.offsetX, val -> cfg.offsetX = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(0, 200).step(1))
                                .build())
                        .option(Option.<Integer>createBuilder()
                                .name(Component.literal("Bottom Y Offset"))
                                .description(OptionDescription.of(Component.literal("Vertical distance from the bottom edge of the screen.")))
                                .binding(40, () -> cfg.offsetY, val -> cfg.offsetY = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(10, 200).step(1))
                                .build())
                        .build())
                .category(ConfigCategory.createBuilder()
                        .name(Component.literal("Formatting & Media"))
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable Markdown"))
                                .description(OptionDescription.of(Component.literal("Render #, ##, ###, -#, -, **, __, and ~~ formatting.")))
                                .binding(true, () -> cfg.enableMarkdown, val -> cfg.enableMarkdown = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Enable MiniMessage"))
                                .description(OptionDescription.of(Component.literal("Parse MiniMessage tags like <red> and <gradient> alongside Markdown.")))
                                .binding(true, () -> cfg.enableMiniMessage, val -> cfg.enableMiniMessage = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Render Image Links"))
                                .description(OptionDescription.of(Component.literal("Asynchronously download and display image URLs inline.")))
                                .binding(true, () -> cfg.renderImages, val -> cfg.renderImages = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Display Images Inline"))
                                .description(OptionDescription.of(Component.literal("ON: Render full images directly in chat. OFF: Compact them into links that show on hover.")))
                                .binding(true, () -> cfg.inlineImages, val -> cfg.inlineImages = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Website Hover Previews"))
                                .description(OptionDescription.of(Component.literal("Show a cached thumbnail preview when hovering over website links.")))
                                .binding(true, () -> cfg.websiteHoverPreviews, val -> cfg.websiteHoverPreviews = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Render Discord File Attachments"))
                                .description(OptionDescription.of(Component.literal("Display clickable attachment cards for Discord CDN files.")))
                                .binding(true, () -> cfg.renderDiscordAttachments, val -> cfg.renderDiscordAttachments = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .option(Option.<Boolean>createBuilder()
                                .name(Component.literal("Save Chat History to File"))
                                .description(OptionDescription.of(Component.literal("Automatically log incoming messages to disk.")))
                                .binding(false, () -> cfg.saveHistoryToFile, val -> cfg.saveHistoryToFile = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build())
                        .build())
                .save(() -> {
                    ChatConfig.save();
                    ChatHistoryManager.getInstance().invalidateLayouts();
                })
                .build()
                .generateScreen(parent);
    }
}
