package com.kyfstore.ClientChatPlus.mixin;

import com.kyfstore.ClientChatPlus.chat.ChatHistoryManager;
import com.kyfstore.ClientChatPlus.chat.CustomChatRenderer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChatScreen.class, remap = false)
public abstract class ChatScreenMixin extends Screen {

    @Shadow
    protected EditBox input;

    @Shadow
    public abstract void handleChatInput(String message, boolean addToHistory);

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (CustomChatRenderer.handleMouseClick(this, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void onMouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
        ChatHistoryManager.getInstance().scroll((int) (scrollY * 18));
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        ChatHistoryManager manager = ChatHistoryManager.getInstance();
        int key = event.key();

        if (key == InputConstants.KEY_F && event.hasControlDown()) {
            manager.toggleSearch();
            cir.setReturnValue(true);
            return;
        }

        if (manager.isSearchActive()) {
            if (key == InputConstants.KEY_ESCAPE) {
                manager.setSearchActive(false);
                cir.setReturnValue(true);
            } else if (key == InputConstants.KEY_BACKSPACE) {
                manager.backspaceSearch();
                cir.setReturnValue(true);
            } else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                manager.setSearchActive(false);
                cir.setReturnValue(true);
            }
            return;
        }

        if ((key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) && this.input != null) {
            if (event.hasShiftDown()) {
                this.input.setMaxLength(2048);
                this.input.insertText(" ↵ ");
                cir.setReturnValue(true);
                return;
            }
            String text = this.input.getValue();
            if (text.contains("↵")) {
                String[] lines = text.split("\\s*↵\\s*");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty()) {
                        this.handleChatInput(line, i == 0);
                    }
                }
                if (this.minecraft != null) {
                    this.minecraft.setScreenAndShow(null);
                }
                cir.setReturnValue(true);
            }
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        ChatHistoryManager manager = ChatHistoryManager.getInstance();
        if (manager.isSearchActive()) {
            char c = (char) event.codepoint();
            if (c >= 32) {
                manager.appendSearchChar(c);
            }
            return true;
        }
        return super.charTyped(event);
    }

    @Inject(method = "removed", at = @At("HEAD"))
    private void onRemoved(CallbackInfo ci) {
        ChatHistoryManager.getInstance().resetScroll();
    }
}