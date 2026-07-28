package org.batchpacket.submitchange_batchpacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.batchpacket.submitchange_batchpacket.ModConfig;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private Button autoBreakModeButton;
    private ProcessingSpeedSlider processingSpeedSlider;

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
                case OFF -> ModConfig.AutoBreakMode.CLICK;
                case CLICK -> ModConfig.AutoBreakMode.AREA;
                case AREA -> ModConfig.AutoBreakMode.AREA_CHUNK;
                case AREA_CHUNK -> ModConfig.AutoBreakMode.AREA_ALL;
                case AREA_ALL -> ModConfig.AutoBreakMode.OFF;
                default -> throw new IllegalStateException();
            };
            config.setAutoBreakMode(next);
            button.setMessage(this.getAutoBreakModeText());
            if (next != ModConfig.AutoBreakMode.AREA && next != ModConfig.AutoBreakMode.AREA_CHUNK && next != ModConfig.AutoBreakMode.AREA_ALL) {
                AreaSelectionManager.INSTANCE.clearSelection();
            }
            this.sendModeMessage(next);
        }).bounds(this.width / 2 - 100, 40, 200, 20).build();
        this.addRenderableWidget(this.autoBreakModeButton);

        this.processingSpeedSlider = new ProcessingSpeedSlider(this.width / 2 - 100, 75, 200, 20, config.getProcessingSpeed());
        this.addRenderableWidget(this.processingSpeedSlider);

    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.drawSharpCenteredString(guiGraphics, this.title.getString(), this.width / 2, 20, 0xFFFFFF);
    }

    private Component getAutoBreakModeText() {
        return switch (ModConfig.getInstance().getAutoBreakMode()) {
            case OFF -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u5173\u95ed");
            case CLICK -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u51c6\u661f\u5904\u7406");
            case AREA -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u533a\u57df\u5904\u7406");
            case AREA_CHUNK -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u533a\u5757\u5904\u7406");
            case AREA_ALL -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u6a21\u5f0f: \u533a\u57df\u5168\u90e8");
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
            case CLICK -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u51c6\u661f\u5904\u7406\u6a21\u5f0f");
            case AREA -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u533a\u57df\u5904\u7406\u6a21\u5f0f");
            case AREA_CHUNK -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u533a\u5757\u5904\u7406\u6a21\u5f0f (\u4e2d\u952e\u9009\u533a\u5757)");
            case AREA_ALL -> Component.literal("\u81ea\u52a8\u7834\u65b9\u5757: \u533a\u57df\u5168\u90e8\u6a21\u5f0f (\u5f3a\u5236\u9009\u53d6)");
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
        ModConfig.getInstance().saveToDisk();
        Minecraft.getInstance().setScreen(this.parent);
    }

    @Override
    public void removed() {
        ModConfig.getInstance().saveToDisk();
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
            ModConfig.getInstance().updateProcessingSpeed(speed);
            this.updateMessage();
        }
    }
}
