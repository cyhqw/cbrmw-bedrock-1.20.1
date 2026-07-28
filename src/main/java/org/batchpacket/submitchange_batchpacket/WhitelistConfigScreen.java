package org.batchpacket.submitchange_batchpacket;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.batchpacket.submitchange_batchpacket.ModConfig;

public class WhitelistConfigScreen extends Screen {
    private static final int MAX_VISIBLE_IDS = 6;
    private final Screen parent;
    private EditBox whitelistInput;
    private String statusText = "";
    private int scrollOffset = 0;

    protected WhitelistConfigScreen(Screen parent) {
        super(Component.literal("\u81ea\u52a8\u7834\u65b9\u5757\u767d\u540d\u5355\u914d\u7f6e"));
        this.parent = parent;
    }

    public static WhitelistConfigScreen create(Screen parent) {
        return new WhitelistConfigScreen(parent);
    }

    @Override
    protected void init() {
        this.whitelistInput = new EditBox(this.font, this.width / 2 - 100, 50, 200, 20, Component.literal("\u767d\u540d\u5355\u65b9\u5757ID"));
        this.addRenderableWidget(this.whitelistInput);
        this.addRenderableWidget(Button.builder(Component.literal("\u6dfb\u52a0ID"), button -> this.addWhitelistByInput()).bounds(this.width / 2 - 100, 80, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u79fb\u9664ID"), button -> this.removeWhitelistByInput()).bounds(this.width / 2 - 32, 80, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u51c6\u661f\u52a0\u5165"), button -> this.addWhitelistByCrosshair()).bounds(this.width / 2 + 36, 80, 64, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("\u8fd4\u56de"), button -> this.onClose()).bounds(this.width / 2 - 100, this.height - 30, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.drawSharpCenteredString(guiGraphics, this.title.getString(), this.width / 2, 20, 0xFFFFFF);
        this.drawSharpCenteredString(guiGraphics, "\u5f53\u524d\u767d\u540d\u5355\uff08\u6eda\u52a8\u67e5\u770b\u5168\u90e8\uff09", this.width / 2, 112, 0xA0FFA0);
        List<String> ids = ModConfig.getInstance().getAutoBreakWhitelistIds();
        this.clampScrollOffset(ids.size());
        if (ids.isEmpty()) {
            this.drawSharpCenteredString(guiGraphics, "(\u7a7a)", this.width / 2, 126, 0xFF8080);
        } else {
            int y = 126;
            int endExclusive = Math.min(ids.size(), this.scrollOffset + 6);
            for (int i = this.scrollOffset; i < endExclusive; ++i) {
                String id = ids.get(i);
                this.drawSharpCenteredString(guiGraphics, this.getLocalizedBlockName(id), this.width / 2, y, 0xE0E0E0);
                y += 12;
            }
        }
        if (!this.statusText.isEmpty()) {
            this.drawSharpCenteredString(guiGraphics, this.statusText, this.width / 2, this.height - 45, 16765056);
        }
    }

    private void drawSharpCenteredString(GuiGraphics guiGraphics, String text, int centerX, int y, int color) {
        int textWidth = this.font.width(text);
        int x = centerX - textWidth / 2;
        guiGraphics.drawString(this.font, text, x, y, color, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 0.0) {
            this.scrollBy(-1);
            return true;
        }
        if (amount < 0.0) {
            this.scrollBy(1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (this.whitelistInput == null) {
            return false;
        }
        List<String> ids = ModConfig.getInstance().getAutoBreakWhitelistIds();
        if (ids.isEmpty()) {
            return false;
        }
        this.clampScrollOffset(ids.size());
        int listStartY = 126;
        int rowHeight = 12;
        int listEndY = listStartY + 6 * rowHeight;
        int listMinX = this.width / 2 - 120;
        int listMaxX = this.width / 2 + 120;
        if (mouseX < (double)listMinX || mouseX > (double)listMaxX || mouseY < (double)listStartY || mouseY >= (double)listEndY) {
            return false;
        }
        int row = (int)((mouseY - (double)listStartY) / (double)rowHeight);
        int index = this.scrollOffset + row;
        if (row < 0 || row >= 6 || index < 0 || index >= ids.size()) {
            return false;
        }
        String selectedId = ids.get(index);
        this.whitelistInput.setValue(selectedId);
        this.whitelistInput.setCursorPosition(selectedId.length());
        this.statusText = "\u5df2\u586b\u5165: " + selectedId;
        return true;
    }

    private void scrollBy(int delta) {
        List<String> ids = ModConfig.getInstance().getAutoBreakWhitelistIds();
        if (ids.isEmpty()) {
            this.scrollOffset = 0;
            return;
        }
        this.scrollOffset += delta;
        this.clampScrollOffset(ids.size());
    }

    private void clampScrollOffset(int size) {
        int maxOffset = Math.max(0, size - 6);
        if (this.scrollOffset < 0) {
            this.scrollOffset = 0;
        } else if (this.scrollOffset > maxOffset) {
            this.scrollOffset = maxOffset;
        }
    }

    private String getLocalizedBlockName(String blockId) {
        ResourceLocation key = ResourceLocation.tryParse(blockId);
        if (key == null) {
            return blockId;
        }
        return BuiltInRegistries.BLOCK.getOptional(key).map(block -> block.getName().getString()).orElse(blockId);
    }

    private void addWhitelistByInput() {
        String id = this.whitelistInput.getValue();
        if (id.trim().isEmpty()) {
            this.statusText = "\u8bf7\u8f93\u5165\u65b9\u5757ID";
            return;
        }
        boolean changed = ModConfig.getInstance().addWhitelistBlockById(id);
        this.statusText = changed ? "\u5df2\u6dfb\u52a0: " + id.trim() : "\u6dfb\u52a0\u5931\u8d25(\u65e0\u6548ID\u6216\u5df2\u5b58\u5728): " + id.trim();
    }

    private void removeWhitelistByInput() {
        String id = this.whitelistInput.getValue();
        if (id.trim().isEmpty()) {
            this.statusText = "\u8bf7\u8f93\u5165\u65b9\u5757ID";
            return;
        }
        boolean changed = ModConfig.getInstance().removeWhitelistBlockById(id);
        this.statusText = changed ? "\u5df2\u79fb\u9664: " + id.trim() : "\u79fb\u9664\u5931\u8d25(\u65e0\u6548ID\u6216\u4e0d\u5b58\u5728): " + id.trim();
    }

    private void addWhitelistByCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            this.statusText = "\u51c6\u661f\u672a\u6307\u5411\u65b9\u5757";
            return;
        }
        BlockHitResult blockHit = (BlockHitResult)mc.hitResult;
        BlockState state = mc.level.getBlockState(blockHit.getBlockPos());
        Block block = state.getBlock();
        boolean changed = ModConfig.getInstance().addWhitelistBlock(block);
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        String blockId = key.toString();
        this.statusText = changed ? "\u5df2\u4ece\u51c6\u661f\u6dfb\u52a0: " + blockId : "\u6dfb\u52a0\u5931\u8d25(\u7a7a\u6c14\u6216\u5df2\u5b58\u5728): " + blockId;
        this.whitelistInput.setValue(blockId);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(this.parent);
    }
}
