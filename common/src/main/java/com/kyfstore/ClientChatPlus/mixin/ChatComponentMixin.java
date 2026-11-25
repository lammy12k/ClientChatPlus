package com.kyfstore.ClientChatPlus.mixin;

import com.kyfstore.ClientChatPlus.chat.ChatHistoryManager;
import com.kyfstore.ClientChatPlus.chat.CustomChatRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatComponent.class, remap = false)
public abstract class ChatComponentMixin {

    @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component contents, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        ChatHistoryManager.getInstance().addMessage(contents);
        ci.cancel();
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"), cancellable = true)
    private void onExtractRenderState(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        CustomChatRenderer.render(graphics, mouseX, mouseY, displayMode.foreground);
        ci.cancel();
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void onClearMessages(boolean history, CallbackInfo ci) {
        ChatHistoryManager.getInstance().clear();
        if (!history) {
            ci.cancel();
        }
    }
}
