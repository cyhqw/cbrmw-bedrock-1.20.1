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
    private BlockPos cachedBoundsStart;
    private BlockPos cachedBoundsEnd;
    private int minSelectionX;
    private int minSelectionY;
    private int minSelectionZ;
    private int maxSelectionX;
    private int maxSelectionY;
    private int maxSelectionZ;

    public void reset() {
        this.batchModeActive = false;
        this.selectionStart = null;
        this.selectionEnd = null;
        this.originalPacketData = null;
        this.selectionStartTime = 0L;
        this.invalidateSelectionBounds();
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
        this.invalidateSelectionBounds();
    }

    public void clearSelection() {
        this.selectionStart = null;
        this.selectionEnd = null;
        this.originalPacketData = null;
        this.selectionStartTime = 0L;
        this.invalidateSelectionBounds();
    }

    public boolean isBatchModeEnabled() {
        return ModConfig.getInstance().isBatchModeEnabled();
    }

    private static boolean isAreaMode() {
        ModConfig.AutoBreakMode mode = ModConfig.getInstance().getAutoBreakMode();
        return mode == ModConfig.AutoBreakMode.AREA
            || mode == ModConfig.AutoBreakMode.AREA_CHUNK
            || mode == ModConfig.AutoBreakMode.AREA_ALL;
    }

    private static boolean isChunkMode() {
        return ModConfig.getInstance().getAutoBreakMode() == ModConfig.AutoBreakMode.AREA_CHUNK;
    }

    @SubscribeEvent
    public static void onMouseClick(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }
        boolean areaBedrockModeEnabled = isAreaMode();
        if (!INSTANCE.isBatchModeEnabled() && !areaBedrockModeEnabled) {
            if (INSTANCE.hasActiveSelection()) {
                INSTANCE.clearSelection();
            }
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

                if (isChunkMode()) {
                    int chunkX = clickedPos.getX() >> 4;
                    int chunkZ = clickedPos.getZ() >> 4;
                    int minY = mc.level.getMinBuildHeight();
                    int maxY = mc.level.getMaxBuildHeight() - 1;
                    BlockPos chunkStart = new BlockPos(chunkX << 4, minY, chunkZ << 4);
                    BlockPos chunkEnd = new BlockPos((chunkX << 4) + 15, maxY, (chunkZ << 4) + 15);
                    AreaSelectionManager.INSTANCE.selectionStart = chunkStart;
                    AreaSelectionManager.INSTANCE.selectionEnd = chunkEnd;
                    AreaSelectionManager.INSTANCE.selectionStartTime = System.currentTimeMillis();
                    AreaSelectionManager.INSTANCE.lastSelectionStart = new BlockPos((Vec3i)chunkStart);
                    AreaSelectionManager.INSTANCE.lastSelectionEnd = new BlockPos((Vec3i)chunkEnd);
                    mc.player.displayClientMessage((Component)Component.literal((String)"\u533a\u5757\u5df2\u9009\u62e9\uff01"), true);
                    return;
                }

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
        this.updateSelectionBounds();
        return pos.getX() >= this.minSelectionX && pos.getX() <= this.maxSelectionX
            && pos.getY() >= this.minSelectionY && pos.getY() <= this.maxSelectionY
            && pos.getZ() >= this.minSelectionZ && pos.getZ() <= this.maxSelectionZ;
    }

    private void updateSelectionBounds() {
        if (this.selectionStart == this.cachedBoundsStart && this.selectionEnd == this.cachedBoundsEnd) {
            return;
        }
        this.cachedBoundsStart = this.selectionStart;
        this.cachedBoundsEnd = this.selectionEnd;
        this.minSelectionX = Math.min(this.selectionStart.getX(), this.selectionEnd.getX());
        this.minSelectionY = Math.min(this.selectionStart.getY(), this.selectionEnd.getY());
        this.minSelectionZ = Math.min(this.selectionStart.getZ(), this.selectionEnd.getZ());
        this.maxSelectionX = Math.max(this.selectionStart.getX(), this.selectionEnd.getX());
        this.maxSelectionY = Math.max(this.selectionStart.getY(), this.selectionEnd.getY());
        this.maxSelectionZ = Math.max(this.selectionStart.getZ(), this.selectionEnd.getZ());
    }

    private void invalidateSelectionBounds() {
        this.cachedBoundsStart = null;
        this.cachedBoundsEnd = null;
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
