package org.batchpacket.submitchange_batchpacket;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import org.batchpacket.submitchange_batchpacket.ModConfig;

@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.FORGE)
public enum AreaSelectionManager {
    INSTANCE;

    private boolean batchModeActive = false;
    private BlockPos selectionStart = null;
    private BlockPos selectionEnd = null;
    private BlockState originalPacketData;
    private long selectionStartTime = 0L;
    private BlockState lastPacketState = null;
    private BlockPos lastSelectionStart = null;
    private BlockPos lastSelectionEnd = null;

    public void reset() {
        this.batchModeActive = false;
        this.selectionStart = null;
        this.selectionEnd = null;
        this.originalPacketData = null;
        this.selectionStartTime = 0L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage((Component)Component.literal((String)"\u6279\u91cf\u5904\u7406\u6a21\u5f0f\u5df2\u5173\u95ed"));
        }
    }

    public void fullReset() {
        this.batchModeActive = false;
        this.selectionStart = null;
        this.selectionEnd = null;
        this.originalPacketData = null;
        this.selectionStartTime = 0L;
    }

    public void clearSelection() {
        this.selectionStart = null;
        this.selectionEnd = null;
        this.originalPacketData = null;
        this.selectionStartTime = 0L;
    }

    public boolean isBatchModeEnabled() {
        return ModConfig.getInstance().isBatchModeEnabled();
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        boolean areaBedrockModeEnabled;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        boolean bl = areaBedrockModeEnabled = ModConfig.getInstance().getAutoBreakMode() == ModConfig.AutoBreakMode.AREA_WHITELIST || ModConfig.getInstance().getAutoBreakMode() == ModConfig.AutoBreakMode.AREA_ALL;
        if (!INSTANCE.isBatchModeEnabled() && !areaBedrockModeEnabled) {
            return;
        }
        if (event.getButton() == 1 && event.getAction() == 1) {
            if (AreaSelectionManager.INSTANCE.selectionStart != null && AreaSelectionManager.INSTANCE.selectionEnd == null) {
                INSTANCE.clearSelection();
                event.setCanceled(true);
                mc.player.displayClientMessage((Component)Component.literal((String)"\u5df2\u53d6\u6d88\u6846\u9009"), true);
            }
            return;
        }
        if (event.getButton() == 2) {
            if (event.getAction() != 1) {
                return;
            }
            HitResult hitResult = mc.hitResult;
            if (hitResult instanceof BlockHitResult) {
                BlockHitResult blockHit = (BlockHitResult)hitResult;
                event.setCanceled(true);
                BlockPos clickedPos = blockHit.getBlockPos();
                if (AreaSelectionManager.INSTANCE.selectionStart != null && AreaSelectionManager.INSTANCE.selectionEnd != null) {
                    AreaSelectionManager.INSTANCE.selectionStart = clickedPos;
                    AreaSelectionManager.INSTANCE.selectionEnd = null;
                    AreaSelectionManager.INSTANCE.selectionStartTime = System.currentTimeMillis();
                    return;
                }
                if (AreaSelectionManager.INSTANCE.selectionStart == null) {
                    AreaSelectionManager.INSTANCE.selectionStart = clickedPos;
                    AreaSelectionManager.INSTANCE.selectionStartTime = System.currentTimeMillis();
                } else {
                    long elapsedTime = System.currentTimeMillis() - AreaSelectionManager.INSTANCE.selectionStartTime;
                    if (elapsedTime < 1000L) {
                        return;
                    }
                    AreaSelectionManager.INSTANCE.selectionEnd = clickedPos;
                    AreaSelectionManager.INSTANCE.lastSelectionStart = AreaSelectionManager.INSTANCE.selectionStart;
                    AreaSelectionManager.INSTANCE.lastSelectionEnd = AreaSelectionManager.INSTANCE.selectionEnd;
                    if (INSTANCE.isBatchModeEnabled()) {
                        mc.player.displayClientMessage((Component)Component.literal((String)"\u533a\u57df\u5df2\u9009\u62e9\uff01"), true);
                    } else if (areaBedrockModeEnabled) {
                        mc.player.displayClientMessage((Component)Component.literal((String)"\u533a\u57df\u5df2\u9009\u62e9\uff01"), true);
                    }
                }
            }
        }
    }

    public void setOriginalPacketData(BlockState data) {
        this.originalPacketData = data;
    }

    public void saveLastPacketData(BlockState state) {
        this.lastPacketState = state;
    }

    public boolean isBatchModeActive() {
        return this.batchModeActive;
    }

    public BlockPos getSelectionStart() {
        return this.selectionStart;
    }

    public BlockPos getSelectionEnd() {
        return this.selectionEnd;
    }

    public boolean hasActiveSelection() {
        return this.selectionStart != null && this.selectionEnd != null;
    }

    public boolean isWithinSelection(BlockPos pos) {
        if (!this.hasActiveSelection() || pos == null) {
            return false;
        }
        int minX = Math.min(this.selectionStart.getX(), this.selectionEnd.getX());
        int minY = Math.min(this.selectionStart.getY(), this.selectionEnd.getY());
        int minZ = Math.min(this.selectionStart.getZ(), this.selectionEnd.getZ());
        int maxX = Math.max(this.selectionStart.getX(), this.selectionEnd.getX());
        int maxY = Math.max(this.selectionStart.getY(), this.selectionEnd.getY());
        int maxZ = Math.max(this.selectionStart.getZ(), this.selectionEnd.getZ());
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }

    public BlockState getOriginalPacketData() {
        return this.originalPacketData;
    }

    public boolean hasLastSelection() {
        return this.lastSelectionStart != null && this.lastSelectionEnd != null;
    }

    public boolean restoreLastSelection() {
        if (!this.hasLastSelection()) {
            return false;
        }
        this.selectionStart = new BlockPos((Vec3i)this.lastSelectionStart);
        this.selectionEnd = new BlockPos((Vec3i)this.lastSelectionEnd);
        this.selectionStartTime = System.currentTimeMillis();
        return true;
    }

    public BlockPos getLastSelectionStart() {
        return this.lastSelectionStart;
    }

    public BlockPos getLastSelectionEnd() {
        return this.lastSelectionEnd;
    }
}
