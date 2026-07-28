package org.batchpacket.submitchange_batchpacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.batchpacket.submitchange_batchpacket.ModConfig;
import org.batchpacket.submitchange_batchpacket.WhitelistConfigScreen;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private Button autoBreakModeButton;
    private ProcessingSpeedSlider processingSpeedSlider;
    private Button whitelistConfigButton;

    protected ConfigScreen(Screen parent) {
        super((Component)Component.literal((String)"\u673a\u68b0\u52a8\u529b:\u65b9\u5757\u65cb\u8f6c\u83dc\u5355\u6279\u53d1 \u914d\u7f6e"));
        this.parent = parent;
    }

    public static ConfigScreen create(Screen parent) {
        return new ConfigScreen(parent);
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.getInstance();
        this.autoBreakModeButton = Button.builder(this.getAutoBreakModeText(), button -> {
            ModConfig.AutoBreakMode current = config.getAutoBreakMode();
            ModConfig.AutoBreakMode next = switch (current) {
                case OFF -> ModConfig.AutoBreakMode.CLICK_WHITELIST;
                case CLICK_WHITELIST -> ModConfig.AutoBreakMode.AREA_WHITELIST;
                case AREA_WHITELIST -> ModConfig.AutoBreakMode.OFF;
                default -> throw new IllegalStateException();
            };
            config.setAutoBreakMode(next);
            button.setMessage(this.getAutoBreakModeText());
            this.sendModeMessage(next);
        }).bounds(this.width / 2 - 100, 40, 200, 20).build();
        this.addRenderableWidget(this.autoBreakModeButton);

        this.processingSpeedSlider = new ProcessingSpeedSlider(this.width / 2 - 100, 75, 200, 20, config.getProcessingSpeed());
        this.addRenderableWidget(this.processingSpeedSlider);

        this.whitelistConfigButton = Button.builder((Component)Component.literal((String)"\u81ea\u52a8\u7834\u65b9\u5757\u767d\u540d\u5355\u914d\u7f6e"), button -> {
            Minecraft.getInstance().setScreen(WhitelistConfigScreen.create(this));
        }).bounds(this.width / 2 - 100, 110, 200, 20).build();
        this.addRenderableWidget(this.whitelistConfigButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.drawSharpCenteredString(guiGraphics, this.title.getString(), this.width / 2, 20, 0xFFFFFF);
    }

    private Component getAutoBreakModeText() {
        return switch (ModConfig.getInstance().getAutoBreakMode()) {
            case OFF -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u5173\u95ed");
            case CLICK_WHITELIST -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u51c6\u661f\u767d\u540d\u5355");
            case AREA_WHITELIST -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u533a\u57df\u767d\u540d\u5355");
            default -> throw new IllegalStateException();
        };
    }

    private void sendModeMessage(ModConfig.AutoBreakMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Component msg = switch (mode) {
            case OFF -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u5df2\u5173\u95ed");
            case CLICK_WHITELIST -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u51c6\u661f\u767d\u540d\u5355\u6a21\u5f0f");
            case AREA_WHITELIST -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u533a\u57df\u767d\u540d\u5355\u6a21\u5f0f");
            default -> throw new IllegalStateException();
        };
        mc.player.sendSystemMessage(msg);
    }

    private void drawSharpCenteredString(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        int textWidth = this.font.width(text);
        int x = centerX - textWidth / 2;
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }

    private static class ProcessingSpeedSlider extends AbstractSliderButton {
        public ProcessingSpeedSlider(int x, int y, int width, int height, int initialValue) {
            super(x, y, width, height, Component.literal(""), (double)(initialValue - 1) / 99.0);
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            int speed = (int)(1.0 + this.value * 99.0);
            this.setMessage(Component.literal("\u5904\u7406\u901f\u5ea6: " + speed));
        }

        @Override
        protected void applyValue() {
            int speed = (int)(1.0 + this.value * 99.0);
            ModConfig.getInstance().setProcessingSpeed(speed);
            this.updateMessage();
        }
    }
}
