/*
 * Decompiled with CFR 0.152.
 */
package org.batchpacket.submitchange_batchpacket;

import com.simibubi.create.AllItems;
import com.simibubi.create.AllPackets;
import com.simibubi.create.content.contraptions.wrench.RadialWrenchMenuSubmitPacket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.batchpacket.submitchange_batchpacket.AreaSelectionManager;
import org.batchpacket.submitchange_batchpacket.ModConfig;

public class AutoBreakBedrock {
    private static final int AREA_SCAN_HORIZONTAL_RADIUS = 4;
    private static final int AREA_SCAN_VERTICAL_RADIUS = 4;
    private static final int TARGET_SCAN_HORIZONTAL_RADIUS = 8;
    private static final int TARGET_SCAN_VERTICAL_RADIUS = 8;
    private static final double SPHERICAL_SCAN_RADIUS_SQR = 80.0;
    private static final int PASSIVE_SCAN_INTERVAL_TICKS = 1;
    private static final double RECYCLE_INTERACT_RANGE_SQR = 64.0;
    private static final int RECYCLE_RETRY_INTERVAL_TICKS = 1;
    private static final int GHOST_PROBE_INTERVAL_TICKS = 5;
    private static final int MAX_GHOST_PROBE_ATTEMPTS = 8;
    private static final int RECYCLE_NON_PISTON_CONFIRM_TICKS = 10;
    private static final int RECYCLE_CONFIRMATION_TICKS = 2;
    private static final int MAX_TASK_RETRIES = 3;
    private static final int BEDROCK_CHECK_TIMEOUT_TICKS = 6;
    private static final int PASSIVE_MONITOR_RADIUS = 8;
    private static final int PASSIVE_MONITOR_VERTICAL_RADIUS = 8;
    private static final int MIN_RECYCLE_DELAY_FOR_PISTON_A_TICKS = 4;
    private static final int MIN_PROCESSING_SPEED = 1;
    private static final int MAX_PROCESSING_SPEED = 100;
    private static final int MAX_BATCH_INTERVAL_TICKS = 5;
    private static final int MIN_BATCH_INTERVAL_TICKS = 1;
    private static final int TASK_STALL_RESET_TICKS = 5;
    private static final int MAX_PLACEMENT_OPERATIONS_PER_TICK = 2;
    private static final int MAX_RECYCLE_OPERATIONS_PER_TICK = 2;
    private static final double PISTON_OPERATION_MARGIN = 2.0;
    private static boolean isLeftKeyPressed = false;
    private static final Map<BlockPos, BedrockCheckTask> bedrockTasks = new HashMap<BlockPos, BedrockCheckTask>();
    private static final Map<BlockPos, BedrockCheckTask> pistonReservations = new HashMap<BlockPos, BedrockCheckTask>();
    private static final LinkedHashSet<BlockPos> pendingAreaTargets = new LinkedHashSet();
    private static int areaScanTickCounter = 0;
    private static int passiveScanTickCounter = 0;
    private static int ghostProbeTickCounter = 0;
    private static int areaSelectionCursor = 0;
    private static boolean areaModeSelectionHintShown = false;
    private static boolean noBreakTargetHintShown = false;
    private static final Set<BlockPos> pendingRecyclePistons = new HashSet<BlockPos>();
    private static final Set<BlockPos> nearbyObservedNonPiston = new HashSet<BlockPos>();
    private static final Set<BlockPos> nearbyObservedNonAirAroundSelection = new HashSet<BlockPos>();
    private static final Map<BlockPos, Integer> pistonAPlacedTick = new HashMap<BlockPos, Integer>();
    private static int globalTickCounter = 0;
    private static int placementBudgetTick = -1;
    private static int placementOperationsThisTick = 0;
    private static int recycleBudgetTick = -1;
    private static int recycleOperationsThisTick = 0;
    private static Object activeClientLevel;
    private static Player activeClientPlayer;
    private static final Map<BlockPos, RecycleTask> recycleTasks = new HashMap<BlockPos, RecycleTask>();
    private static final Map<BlockPos, BlockState> pendingWrenchActions = new HashMap<BlockPos, BlockState>();

    private static void registerPlacedPiston(BlockPos pos, boolean isPistonA) {
        pendingRecyclePistons.add(pos);
        if (isPistonA) {
            pistonAPlacedTick.put(pos, globalTickCounter);
        } else {
            pistonAPlacedTick.remove(pos);
        }
    }

    private static void enqueueRecycle(BlockPos pos) {
        pendingRecyclePistons.add(pos);
        pistonReservations.remove(pos);
        pendingWrenchActions.remove(pos);
        recycleTasks.computeIfAbsent(pos, RecycleTask::new);
    }

    private static int getPistonAAgeTicks(BlockPos pos) {
        Integer placedAt = pistonAPlacedTick.get(pos);
        if (placedAt == null) {
            return 0;
        }
        return Math.max(0, globalTickCounter - placedAt);
    }

    private static boolean isPistonAInDelayWindow(BlockPos pos) {
        return pistonAPlacedTick.containsKey(pos) && AutoBreakBedrock.getPistonAAgeTicks(pos) < 4;
    }

    private static void recyclePendingPistons(Minecraft mc) {
        if (pendingRecyclePistons.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        for (BlockPos pos : new HashSet<BlockPos>(pendingRecyclePistons)) {
            if (!AutoBreakBedrock.isWithinRecycleRange((Player)mc.player, pos)) continue;
            BlockState state = mc.level.getBlockState(pos);
            if (state.getBlock() == Blocks.PISTON) {
                AutoBreakBedrock.enqueueRecycle(pos);
                continue;
            }
            AutoBreakBedrock.refreshClientBlock(mc, pos);
            AutoBreakBedrock.enqueueRecycle(pos);
        }
    }

    private static void scanPassiveNewPistons(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (!AutoBreakBedrock.isAreaMode()) {
            nearbyObservedNonPiston.clear();
            passiveScanTickCounter = 0;
            return;
        }
        AreaSelectionManager selectionManager = AreaSelectionManager.INSTANCE;
        if (!selectionManager.hasActiveSelection()) {
            nearbyObservedNonPiston.clear();
            passiveScanTickCounter = 0;
            return;
        }
        BlockPos start = selectionManager.getSelectionStart();
        BlockPos end = selectionManager.getSelectionEnd();
        if (start == null || end == null) {
            nearbyObservedNonPiston.clear();
            passiveScanTickCounter = 0;
            return;
        }
        if (++passiveScanTickCounter < PASSIVE_SCAN_INTERVAL_TICKS) {
            return;
        }
        passiveScanTickCounter = 0;
        int border = 3;
        int minX = Math.min(start.getX(), end.getX()) - 3;
        int minY = Math.min(start.getY(), end.getY()) - 3;
        int minZ = Math.min(start.getZ(), end.getZ()) - 3;
        int maxX = Math.max(start.getX(), end.getX()) + 3;
        int maxY = Math.max(start.getY(), end.getY()) + 3;
        int maxZ = Math.max(start.getZ(), end.getZ()) + 3;
        BlockPos playerPos = mc.player.blockPosition();
        nearbyObservedNonPiston.removeIf(pos -> pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ);
        for (int dy = -PASSIVE_MONITOR_VERTICAL_RADIUS; dy <= PASSIVE_MONITOR_VERTICAL_RADIUS; ++dy) {
            for (int dx = -PASSIVE_MONITOR_RADIUS; dx <= PASSIVE_MONITOR_RADIUS; ++dx) {
                for (int dz = -PASSIVE_MONITOR_RADIUS; dz <= PASSIVE_MONITOR_RADIUS; ++dz) {
                    if (dx * dx + dy * dy + dz * dz > SPHERICAL_SCAN_RADIUS_SQR) {
                        continue;
                    }
                    BlockPos pos2 = playerPos.offset(dx, dy, dz);
                    if (pos2.getX() < minX || pos2.getX() > maxX || pos2.getY() < minY || pos2.getY() > maxY || pos2.getZ() < minZ || pos2.getZ() > maxZ) continue;
                    BlockState state = mc.level.getBlockState(pos2);
                    if (state.getBlock() == Blocks.PISTON) {
                        if (!nearbyObservedNonPiston.remove(pos2)) continue;
                        AutoBreakBedrock.enqueueRecycle(pos2);
                        continue;
                    }
                    nearbyObservedNonPiston.add(pos2);
                }
            }
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(AutoBreakBedrock::tick);
            return;
        }
        if (activeClientLevel != mc.level || activeClientPlayer != mc.player) {
            AutoBreakBedrock.resetForClientContextChange();
            activeClientLevel = mc.level;
            activeClientPlayer = mc.player;
        }
        ++globalTickCounter;
        if (ModConfig.getInstance().getAutoBreakMode() == ModConfig.AutoBreakMode.OFF) {
            // Recycle work must continue after the user turns automation off.
            AutoBreakBedrock.recyclePendingPistons(mc);
            AutoBreakBedrock.processRecycleTasks(mc);
            AutoBreakBedrock.clearAutomationRuntimeState();
            return;
        }
        AutoBreakBedrock.scanPassiveNewPistons(mc);
        AutoBreakBedrock.probeGhostAirTransitionsNearSelection(mc);
        AutoBreakBedrock.recyclePendingPistons(mc);
        AutoBreakBedrock.processAreaBedrockMode(mc);
        AutoBreakBedrock.processPendingWrenchActions(mc);
        AutoBreakBedrock.processBedrockTasks(mc);
        AutoBreakBedrock.processRecycleTasks(mc);
    }

    private static void probeGhostAirTransitionsNearSelection(Minecraft mc) {
        if (!AutoBreakBedrock.isAreaMode() || mc.level == null || mc.player == null) {
            nearbyObservedNonAirAroundSelection.clear();
            ghostProbeTickCounter = 0;
            return;
        }
        AreaSelectionManager selectionManager = AreaSelectionManager.INSTANCE;
        if (!selectionManager.hasActiveSelection()) {
            nearbyObservedNonAirAroundSelection.clear();
            ghostProbeTickCounter = 0;
            return;
        }
        BlockPos start = selectionManager.getSelectionStart();
        BlockPos end = selectionManager.getSelectionEnd();
        if (start == null || end == null) {
            nearbyObservedNonAirAroundSelection.clear();
            ghostProbeTickCounter = 0;
            return;
        }
        if (++ghostProbeTickCounter < GHOST_PROBE_INTERVAL_TICKS) {
            return;
        }
        ghostProbeTickCounter = 0;
        boolean border = true;
        int minX = Math.min(start.getX(), end.getX()) - 1;
        int minY = Math.min(start.getY(), end.getY()) - 1;
        int minZ = Math.min(start.getZ(), end.getZ()) - 1;
        int maxX = Math.max(start.getX(), end.getX()) + 1;
        int maxY = Math.max(start.getY(), end.getY()) + 1;
        int maxZ = Math.max(start.getZ(), end.getZ()) + 1;
        BlockPos playerPos = mc.player.blockPosition();
        nearbyObservedNonAirAroundSelection.removeIf(pos -> {
            if (Math.abs(pos.getX() - playerPos.getX()) > 4 || Math.abs(pos.getY() - playerPos.getY()) > 4 || Math.abs(pos.getZ() - playerPos.getZ()) > 4) {
                return true;
            }
            return pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY || pos.getZ() < minZ || pos.getZ() > maxZ;
        });
        for (int dy = -AREA_SCAN_VERTICAL_RADIUS; dy <= AREA_SCAN_VERTICAL_RADIUS; ++dy) {
            for (int dx = -AREA_SCAN_HORIZONTAL_RADIUS; dx <= AREA_SCAN_HORIZONTAL_RADIUS; ++dx) {
                for (int dz = -AREA_SCAN_HORIZONTAL_RADIUS; dz <= AREA_SCAN_HORIZONTAL_RADIUS; ++dz) {
                    if (dx * dx + dy * dy + dz * dz > SPHERICAL_SCAN_RADIUS_SQR) {
                        continue;
                    }
                    BlockPos pos2 = playerPos.offset(dx, dy, dz);
                    if (pos2.getX() < minX || pos2.getX() > maxX || pos2.getY() < minY || pos2.getY() > maxY || pos2.getZ() < minZ || pos2.getZ() > maxZ) continue;
                    BlockState state = mc.level.getBlockState(pos2);
                    if (!state.isAir() && !AutoBreakBedrock.hasFluid(state)) {
                        nearbyObservedNonAirAroundSelection.add(pos2);
                        continue;
                    }
                    if (AutoBreakBedrock.hasFluid(state)) {
                        nearbyObservedNonAirAroundSelection.remove(pos2);
                        continue;
                    }
                    if (!nearbyObservedNonAirAroundSelection.remove(pos2)) continue;
                    if (AutoBreakBedrock.isPositionConflicting(pos2) || bedrockTasks.containsKey(pos2)) continue;
                    AutoBreakBedrock.rightClickWithWrenchOnce(mc, pos2);
                }
            }
        }
    }

    private static void processPendingWrenchActions(Minecraft mc) {
        if (pendingWrenchActions.isEmpty() || mc.level == null) {
            return;
        }
        Iterator<Map.Entry<BlockPos, BlockState>> iterator = pendingWrenchActions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, BlockState> entry = iterator.next();
            BlockPos pos = entry.getKey();
            if (AutoBreakBedrock.getPistonAAgeTicks(pos) < 1) {
                continue;
            }
            RadialWrenchMenuSubmitPacket packet = new RadialWrenchMenuSubmitPacket(pos, entry.getValue());
            AllPackets.getChannel().sendToServer(packet);
            iterator.remove();
        }
    }

    private static void processRecycleTasks(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        HashSet<RecycleTask> finished = new HashSet<RecycleTask>();
        for (RecycleTask task : recycleTasks.values()) {
            if (!AutoBreakBedrock.advanceRecycleTask(mc, task)) continue;
            finished.add(task);
        }
        for (RecycleTask task : finished) {
            recycleTasks.remove(task.pos);
            pendingRecyclePistons.remove(task.pos);
            pistonAPlacedTick.remove(task.pos);
            pistonReservations.remove(task.pos);
        }
    }

    private static boolean advanceRecycleTask(Minecraft mc, RecycleTask task) {
        if (!AutoBreakBedrock.isWithinRecycleRange((Player)mc.player, task.pos)) {
            return false;
        }
        if (task.cooldownTicks > 0) {
            --task.cooldownTicks;
            return false;
        }
        BlockState state = mc.level.getBlockState(task.pos);
        if (state.getBlock() == Blocks.PISTON) {
            task.observedPiston = true;
            task.manualRetry = false;
            task.removalAttemptAccepted = false;
            task.removalConfirmTicks = 0;
            return AutoBreakBedrock.advanceRecycleTaskWhenPistonPresent(mc, task);
        }
        if (task.observedPiston) {
            task.manualRetry = false;
            task.removalAttemptAccepted = false;
            task.nonPistonTicks = 0;
            if (++task.removalConfirmTicks >= RECYCLE_CONFIRMATION_TICKS) {
                return true;
            }
            return false;
        }
        if (task.manualRetry) {
            AutoBreakBedrock.refreshClientBlock(mc, task.pos);
            if (task.observedPiston && AutoBreakBedrock.findWrenchInHotbar((Player)mc.player) != -1) {
                task.manualRetry = false;
                task.attempts = 0;
                task.nonPistonTicks = 0;
                task.cooldownTicks = 0;
                return AutoBreakBedrock.advanceRecycleTaskWhenNonPiston(mc, task);
            }
            task.cooldownTicks = RECYCLE_NON_PISTON_CONFIRM_TICKS;
            return false;
        }
        return AutoBreakBedrock.advanceRecycleTaskWhenNonPiston(mc, task);
    }

    private static boolean advanceRecycleTaskWhenPistonPresent(Minecraft mc, RecycleTask task) {
        BlockState afterState;
        if (AutoBreakBedrock.isPistonAInDelayWindow(task.pos) || !AutoBreakBedrock.consumeRecycleOperationBudget()) {
            return false;
        }
        boolean attempted = AutoBreakBedrock.breakBlockWithEquippedWrench(mc, task.pos);
        task.cooldownTicks = RECYCLE_RETRY_INTERVAL_TICKS;
        if (attempted) {
            ++task.attempts;
            task.removalAttemptAccepted = true;
        }
        if ((afterState = mc.level.getBlockState(task.pos)).getBlock() == Blocks.PISTON) {
            task.nonPistonTicks = 0;
            return false;
        }
        if (attempted) {
            task.nonPistonTicks = 1;
            task.removalConfirmTicks = 0;
        }
        return false;
    }

    private static boolean advanceRecycleTaskWhenNonPiston(Minecraft mc, RecycleTask task) {
        if (!task.observedPiston) {
            ++task.nonPistonTicks;
            AutoBreakBedrock.refreshClientBlock(mc, task.pos);
            if (task.nonPistonTicks >= RECYCLE_NON_PISTON_CONFIRM_TICKS) {
                return true;
            }
            return false;
        }
        if (task.removalAttemptAccepted) {
            if (mc.level.getBlockState(task.pos).getBlock() == Blocks.PISTON) {
                task.removalAttemptAccepted = false;
                task.removalConfirmTicks = 0;
                task.nonPistonTicks = 0;
                return false;
            }
            if (++task.removalConfirmTicks >= RECYCLE_CONFIRMATION_TICKS) {
                return true;
            }
            return false;
        }
        ++task.nonPistonTicks;
        AutoBreakBedrock.refreshClientBlock(mc, task.pos);
        if (task.nonPistonTicks % RECYCLE_RETRY_INTERVAL_TICKS == 0
            && task.attempts < MAX_GHOST_PROBE_ATTEMPTS
            && AutoBreakBedrock.consumeRecycleOperationBudget()) {
            BlockState afterProbe;
            boolean attempted = AutoBreakBedrock.breakBlockWithEquippedWrench(mc, task.pos);
            task.cooldownTicks = RECYCLE_RETRY_INTERVAL_TICKS;
            if (attempted) {
                ++task.attempts;
                task.removalAttemptAccepted = true;
                task.removalConfirmTicks = 0;
            }
            if ((afterProbe = mc.level.getBlockState(task.pos)).getBlock() == Blocks.PISTON) {
                task.nonPistonTicks = 0;
                task.removalAttemptAccepted = false;
                task.removalConfirmTicks = 0;
                return false;
            }
        }
        if (task.nonPistonTicks < RECYCLE_NON_PISTON_CONFIRM_TICKS) {
            return false;
        }
        // Keep an unresolved position for a later client state update.
        if (!task.manualRetry) {
            task.manualRetry = true;
            if (!task.warningShown) {
                AutoBreakBedrock.showActionBarMessage((Player)mc.player, "\u6d3b\u585e\u56de\u6536\u5931\u8d25\uff0c\u6b63\u5728\u91cd\u8bd5");
                task.warningShown = true;
            }
        }
        task.cooldownTicks = RECYCLE_NON_PISTON_CONFIRM_TICKS;
        return false;
    }

    private static boolean hasBlockedRecycleTask() {
        for (RecycleTask task : recycleTasks.values()) {
            if (task.manualRetry) {
                return true;
            }
        }
        return false;
    }

    private static void processBedrockTasks(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        ArrayList<BlockPos> finished = new ArrayList<BlockPos>();
        if (bedrockTasks.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, BedrockCheckTask> entry : bedrockTasks.entrySet()) {
            BlockPos pos = entry.getKey();
            BedrockCheckTask task = entry.getValue();
            if (task.isAreaTask() && !AutoBreakBedrock.isWithinPlayerAreaWindow((Player)mc.player, pos)) continue;
            if (task.check()) {
                finished.add(pos);
            }
        }
        for (BlockPos pos : finished) {
            bedrockTasks.remove(pos);
        }
    }

    private static void clearAutomationRuntimeState() {
        clearBedrockTasksAndQueueRecycling();
        pendingAreaTargets.clear();
        nearbyObservedNonPiston.clear();
        nearbyObservedNonAirAroundSelection.clear();
        areaScanTickCounter = 0;
        areaSelectionCursor = 0;
        areaModeSelectionHintShown = false;
        isLeftKeyPressed = false;
        globalTickCounter = 0;
        passiveScanTickCounter = 0;
        ghostProbeTickCounter = 0;
        noBreakTargetHintShown = false;
        pendingWrenchActions.clear();
    }

    private static void clearBedrockTasksAndQueueRecycling() {
        for (BedrockCheckTask task : bedrockTasks.values()) {
            task.enqueueAllRecycleCandidates();
        }
        bedrockTasks.clear();
    }

    private static void resetForClientContextChange() {
        // Coordinates from another level or player must never be reused.
        AreaSelectionManager.INSTANCE.clearSelection();
        bedrockTasks.clear();
        pistonReservations.clear();
        pendingAreaTargets.clear();
        recycleTasks.clear();
        pendingRecyclePistons.clear();
        pistonAPlacedTick.clear();
        pendingWrenchActions.clear();
        nearbyObservedNonPiston.clear();
        nearbyObservedNonAirAroundSelection.clear();
        areaScanTickCounter = 0;
        areaSelectionCursor = 0;
        areaModeSelectionHintShown = false;
        noBreakTargetHintShown = false;
        globalTickCounter = 0;
    }

    private static boolean isWithinRecycleRange(Player player, BlockPos pos) {
        return player.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0;
    }

    private static boolean isWithinPlayerAreaWindow(Player player, BlockPos pos) {
        double operationRange = Math.sqrt(RECYCLE_INTERACT_RANGE_SQR) - PISTON_OPERATION_MARGIN;
        double dx = (double)pos.getX() + 0.5 - player.getX();
        double dy = (double)pos.getY() + 0.5 - player.getY();
        double dz = (double)pos.getZ() + 0.5 - player.getZ();
        return dx * dx + dy * dy + dz * dz <= operationRange * operationRange;
    }

    private static int getProcessingSpeed() {
        int speed = ModConfig.getInstance().getProcessingSpeed();
        return Math.max(1, Math.min(100, speed));
    }

    private static int getDynamicAreaScanIntervalTicks() {
        return AutoBreakBedrock.getBatchIntervalTicks();
    }

    private static int getDynamicMaxActiveBreakTasks() {
        int speed = AutoBreakBedrock.getProcessingSpeed();
        return Math.max(4, Math.min(16, 4 + speed / 10));
    }

    private static int getDynamicNewTasksPerAreaScan() {
        return AutoBreakBedrock.getBatchPacketCount();
    }

    private static int getBatchPacketCount() {
        return AutoBreakBedrock.getProcessingSpeed();
    }

    private static int getBatchIntervalTicks() {
        int speed = AutoBreakBedrock.getProcessingSpeed();
        double t = (double)(speed - 1) / 99.0;
        int mapped = (int)Math.round(5.0 - 4.0 * t);
        return Math.max(1, Math.min(5, mapped));
    }

    private static boolean consumeOperationBudget() {
        if (placementBudgetTick != globalTickCounter) {
            placementBudgetTick = globalTickCounter;
            placementOperationsThisTick = 0;
        }
        int maxOperations = Math.max(MAX_PLACEMENT_OPERATIONS_PER_TICK,
            Math.min(16, 1 + AutoBreakBedrock.getProcessingSpeed() / 8));
        if (placementOperationsThisTick >= maxOperations) {
            return false;
        }
        ++placementOperationsThisTick;
        return true;
    }

    private static boolean consumeRecycleOperationBudget() {
        if (recycleBudgetTick != globalTickCounter) {
            recycleBudgetTick = globalTickCounter;
            recycleOperationsThisTick = 0;
        }
        int maxOperations = Math.max(MAX_RECYCLE_OPERATIONS_PER_TICK,
            Math.min(16, 1 + AutoBreakBedrock.getProcessingSpeed() / 8));
        if (recycleOperationsThisTick >= maxOperations) {
            return false;
        }
        ++recycleOperationsThisTick;
        return true;
    }

    private static void showActionBarMessage(Player player, String message) {
        if (player != null) {
            player.displayClientMessage((Component)Component.literal((String)message), true);
        }
    }

    public static boolean handleLeftClick(Minecraft mc, boolean isPressed) {
        boolean shouldHandle;
        AutoBreakBedrock.recyclePendingPistons(mc);
        ModConfig.AutoBreakMode autoBreakMode = ModConfig.getInstance().getAutoBreakMode();
        if (autoBreakMode == ModConfig.AutoBreakMode.OFF) {
            return false;
        }
        if (autoBreakMode == ModConfig.AutoBreakMode.AREA || autoBreakMode == ModConfig.AutoBreakMode.AREA_CHUNK || autoBreakMode == ModConfig.AutoBreakMode.AREA_ALL) {
            return false;
        }
        if (!isPressed) {
            isLeftKeyPressed = false;
            return false;
        }
        if (isLeftKeyPressed) {
            return false;
        }
        isLeftKeyPressed = true;
        if (!AutoBreakBedrock.isPlayerReadyForInteraction(mc)) {
            return false;
        }
        ItemStack mainHandItem = mc.player.getMainHandItem();
        if (!AllItems.WRENCH.isIn(mainHandItem)) {
            return false;
        }
        if (!AutoBreakBedrock.hasPistonInHotbar((Player)mc.player)) {
            AutoBreakBedrock.showActionBarMessage((Player)mc.player, "\u5feb\u6377\u680f\u4e2d\u6ca1\u6709\u6d3b\u585e\uff01");
            return false;
        }
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        BlockHitResult hitResult = (BlockHitResult)mc.hitResult;
        BlockPos targetPos = hitResult.getBlockPos();
        BlockState targetState = mc.level.getBlockState(targetPos);
        boolean bl = shouldHandle = autoBreakMode == ModConfig.AutoBreakMode.CLICK && AutoBreakBedrock.isEligibleBreakTarget(targetState);
        if (shouldHandle) {
            if (bedrockTasks.containsKey(targetPos)) {
                AutoBreakBedrock.showActionBarMessage((Player)mc.player, "\u8be5\u65b9\u5757\u6b63\u5728\u5904\u7406\u4e2d\uff0c\u8bf7\u7a0d\u5019\uff01");
                return true;
            }
            return AutoBreakBedrock.tryCreateBreakTask(mc, targetPos, targetState, true, false);
        }
        return false;
    }

    private static void processAreaBedrockMode(Minecraft mc) {
        if (!AutoBreakBedrock.isAreaMode()) {
            AutoBreakBedrock.clearAreaSelectionAndCaches(mc);
            return;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        AreaSelectionManager selectionManager = AreaSelectionManager.INSTANCE;
        if (!AllItems.WRENCH.isIn(mc.player.getMainHandItem())) {
            return;
        }
        if (!AutoBreakBedrock.hasPistonInHotbar((Player)mc.player)) {
            return;
        }
        if (!selectionManager.hasActiveSelection()) {
            pendingAreaTargets.clear();
            areaScanTickCounter = 0;
            areaSelectionCursor = 0;
            noBreakTargetHintShown = false;
            if (!areaModeSelectionHintShown) {
                AutoBreakBedrock.showActionBarMessage((Player)mc.player, "\u8bf7\u5148\u7528\u4e2d\u952e\u6846\u9009\u533a\u57df (\u5f3a\u5236\u8981\u6c42)");
                areaModeSelectionHintShown = true;
            }
            return;
        }
        areaModeSelectionHintShown = false;
        if (++areaScanTickCounter < AutoBreakBedrock.getDynamicAreaScanIntervalTicks()) {
            return;
        }
        areaScanTickCounter = 0;
        if (!AutoBreakBedrock.selectionContainsBreakTargetsInSelection(mc, selectionManager)) {
            ModConfig.AutoBreakMode mode = ModConfig.getInstance().getAutoBreakMode();
            if (!noBreakTargetHintShown) {
                AutoBreakBedrock.showActionBarMessage((Player)mc.player, "\u6846\u9009\u8303\u56f4\u5185\u5df2\u65e0\u53ef\u5904\u7406\u65b9\u5757");
                noBreakTargetHintShown = true;
            }
            return;
        }
        noBreakTargetHintShown = false;
        AutoBreakBedrock.collectAreaTargetsToPending(mc, selectionManager);
        int maxActiveBreakTasks = AutoBreakBedrock.getDynamicMaxActiveBreakTasks();
        int nearbyActiveAreaTasks = AutoBreakBedrock.countNearbyActiveAreaTasks((Player)mc.player);
        if (nearbyActiveAreaTasks >= maxActiveBreakTasks) {
            return;
        }
        int availableSlots = maxActiveBreakTasks - nearbyActiveAreaTasks;
        int createBudget = Math.min(AutoBreakBedrock.getDynamicNewTasksPerAreaScan(), availableSlots);
        if (createBudget <= 0) {
            return;
        }
        AutoBreakBedrock.drainPendingAreaTargetsToTasks(mc, selectionManager, createBudget);
    }

    private static void collectAreaTargetsToPending(Minecraft mc, AreaSelectionManager selectionManager) {
        if (mc.level == null || mc.player == null || !selectionManager.hasActiveSelection()) {
            pendingAreaTargets.clear();
            return;
        }
        pendingAreaTargets.clear();
        BlockPos playerPos = mc.player.blockPosition();
        for (int dy = -TARGET_SCAN_VERTICAL_RADIUS; dy <= TARGET_SCAN_VERTICAL_RADIUS; ++dy) {
            for (int dx = -TARGET_SCAN_HORIZONTAL_RADIUS; dx <= TARGET_SCAN_HORIZONTAL_RADIUS; ++dx) {
                for (int dz = -TARGET_SCAN_HORIZONTAL_RADIUS; dz <= TARGET_SCAN_HORIZONTAL_RADIUS; ++dz) {
                    if (dx * dx + dy * dy + dz * dz > SPHERICAL_SCAN_RADIUS_SQR) {
                        continue;
                    }
                    BlockPos targetPos = playerPos.offset(dx, dy, dz);
                    if (!selectionManager.isWithinSelection(targetPos)) {
                        continue;
                    }
                    if (bedrockTasks.containsKey(targetPos) || pendingAreaTargets.contains(targetPos)) {
                        continue;
                    }
                    if (isValidBreakTarget(mc, targetPos)) {
                        pendingAreaTargets.add(targetPos);
                    }
                }
            }
        }
    }

    private static void drainPendingAreaTargetsToTasks(Minecraft mc, AreaSelectionManager selectionManager, int createBudget) {
        if (createBudget <= 0 || pendingAreaTargets.isEmpty() || mc.level == null || mc.player == null) {
            return;
        }
        int created = 0;
        Iterator iterator = pendingAreaTargets.iterator();
        while (iterator.hasNext() && created < createBudget) {
            BlockPos targetPos = (BlockPos)iterator.next();
            if (bedrockTasks.containsKey(targetPos)) {
                iterator.remove();
                continue;
            }
            if (!AutoBreakBedrock.isWithinPlayerAreaWindow((Player)mc.player, targetPos) || !selectionManager.isWithinSelection(targetPos)) {
                iterator.remove();
                continue;
            }
            if (!isValidBreakTarget(mc, targetPos)) {
                iterator.remove();
                continue;
            }
            BlockState targetState = mc.level.getBlockState(targetPos);
            if (!AutoBreakBedrock.tryCreateBreakTask(mc, targetPos, targetState, false, true)) continue;
            iterator.remove();
            ++created;
        }
    }

    private static int countNearbyActiveAreaTasks(Player player) {
        int count = 0;
        for (Map.Entry<BlockPos, BedrockCheckTask> entry : bedrockTasks.entrySet()) {
            if (!entry.getValue().isAreaTask() || !AutoBreakBedrock.isWithinPlayerAreaWindow(player, entry.getKey())) continue;
            ++count;
        }
        return count;
    }

    private static boolean selectionContainsBreakTargetsInSelection(Minecraft mc, AreaSelectionManager selectionManager) {
        if (mc.level == null || !selectionManager.hasActiveSelection()) {
            return false;
        }
        BlockPos start = selectionManager.getSelectionStart();
        BlockPos end = selectionManager.getSelectionEnd();
        if (start == null || end == null) {
            return false;
        }
        BlockPos playerPos = mc.player.blockPosition();
        int minX = Math.max(Math.min(start.getX(), end.getX()), playerPos.getX() - TARGET_SCAN_HORIZONTAL_RADIUS);
        int minY = Math.max(Math.min(start.getY(), end.getY()), playerPos.getY() - TARGET_SCAN_VERTICAL_RADIUS);
        int minZ = Math.max(Math.min(start.getZ(), end.getZ()), playerPos.getZ() - TARGET_SCAN_HORIZONTAL_RADIUS);
        int maxX = Math.min(Math.max(start.getX(), end.getX()), playerPos.getX() + TARGET_SCAN_HORIZONTAL_RADIUS);
        int maxY = Math.min(Math.max(start.getY(), end.getY()), playerPos.getY() + TARGET_SCAN_VERTICAL_RADIUS);
        int maxZ = Math.min(Math.max(start.getZ(), end.getZ()), playerPos.getZ() + TARGET_SCAN_HORIZONTAL_RADIUS);
        for (int y = minY; y <= maxY; ++y) {
            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    int dx = x - playerPos.getX();
                    int dy = y - playerPos.getY();
                    int dz = z - playerPos.getZ();
                    if (dx * dx + dy * dy + dz * dz > SPHERICAL_SCAN_RADIUS_SQR) {
                        continue;
                    }
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isValidBreakTarget(mc, pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void clearAreaSelectionAndCaches(Minecraft mc) {
        AreaSelectionManager selectionManager = AreaSelectionManager.INSTANCE;
        if (selectionManager.hasActiveSelection()) {
            selectionManager.clearSelection();
        }
        pendingAreaTargets.clear();
        nearbyObservedNonAirAroundSelection.clear();
        Iterator<Map.Entry<BlockPos, BedrockCheckTask>> iterator = bedrockTasks.entrySet().iterator();
        while (iterator.hasNext()) {
            BedrockCheckTask task = iterator.next().getValue();
            if (!task.isAreaTask()) {
                continue;
            }
            task.enqueueAllRecycleCandidates();
            iterator.remove();
        }
        areaScanTickCounter = 0;
        areaSelectionCursor = 0;
        areaModeSelectionHintShown = false;
        noBreakTargetHintShown = false;
    }

    private static boolean isAreaMode() {
        ModConfig.AutoBreakMode mode = ModConfig.getInstance().getAutoBreakMode();
        return mode == ModConfig.AutoBreakMode.AREA
            || mode == ModConfig.AutoBreakMode.AREA_CHUNK
            || mode == ModConfig.AutoBreakMode.AREA_ALL;
    }

    private static boolean isEligibleBreakTarget(BlockState targetState) {
        if (targetState == null || targetState.isAir() || AutoBreakBedrock.hasFluid(targetState)) {
            return false;
        }
        if (targetState.getBlock() == Blocks.PISTON || targetState.getBlock() == Blocks.STICKY_PISTON) {
            return false;
        }
        return true;
    }

    private static boolean hasFluid(BlockState state) {
        return state != null && !state.getFluidState().isEmpty();
    }

    private static boolean isPistonPlacementSpace(BlockState state) {
        return state != null && !AutoBreakBedrock.hasFluid(state)
            && (state.isAir() || state.canBeReplaced());
    }

    private static boolean isSurfaceExposed(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        for (Direction dir : Direction.values()) {
            BlockState neighborState = mc.level.getBlockState(pos.relative(dir));
            if (neighborState.isAir() || AutoBreakBedrock.isPistonPlacementSpace(neighborState)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidBreakTarget(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!isEligibleBreakTarget(state)) {
            return false;
        }
        if (bedrockTasks.containsKey(pos) || pendingRecyclePistons.contains(pos)) {
            return false;
        }
        return isSurfaceExposed(mc, pos);
    }

    private static boolean reservePistonPosition(Minecraft mc, BlockPos pos, BedrockCheckTask task) {
        if (!AutoBreakBedrock.isWithinBuildHeight(mc, pos)) {
            return false;
        }
        BedrockCheckTask owner = pistonReservations.get(pos);
        if (owner != null && owner != task) {
            return false;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (!AutoBreakBedrock.isPistonPlacementSpace(state)) {
            return false;
        }
        pistonReservations.put(pos, task);
        return true;
    }

    private static boolean isWithinBuildHeight(Minecraft mc, BlockPos pos) {
        return mc.level != null
            && pos.getY() >= mc.level.getMinBuildHeight()
            && pos.getY() < mc.level.getMaxBuildHeight();
    }

    private static void releasePistonPosition(BlockPos pos, BedrockCheckTask task) {
        if (pistonReservations.get(pos) == task) {
            pistonReservations.remove(pos);
        }
    }

    private static boolean isPositionConflicting(BlockPos pos) {
        return pistonReservations.containsKey(pos)
            || pendingRecyclePistons.contains(pos)
            || recycleTasks.containsKey(pos);
    }

    private static boolean tryCreateBreakTask(Minecraft mc, BlockPos targetPos, BlockState targetState, boolean notifyWhenNoSpace, boolean areaTask) {
        ArrayList<AWithB> validAList = new ArrayList<AWithB>();
        for (Direction dir : Direction.values()) {
            BlockPos pistonAPos = targetPos.relative(dir);
            if (!AutoBreakBedrock.isWithinBuildHeight(mc, pistonAPos)) continue;
            if (AutoBreakBedrock.isPositionConflicting(pistonAPos)) continue;
            BlockState pistonAState = mc.level.getBlockState(pistonAPos);
            if (!AutoBreakBedrock.isPistonPlacementSpace(pistonAState)) continue;
            ArrayList<BlockPos> bList = new ArrayList<BlockPos>();
            for (Direction bDir : Direction.values()) {
                BlockState bState;
                BlockPos bPos = pistonAPos.relative(bDir);
                if (!AutoBreakBedrock.isWithinBuildHeight(mc, bPos)) continue;
                if (bPos.equals((Object)targetPos) || AutoBreakBedrock.isPositionConflicting(bPos)) continue;
                if (!AutoBreakBedrock.isPistonPlacementSpace(bState = mc.level.getBlockState(bPos))) continue;
                bList.add(bPos);
            }
            if (bList.isEmpty()) continue;
            validAList.add(new AWithB(pistonAPos, bList));
        }
        if (validAList.isEmpty()) {
            return false;
        }
        bedrockTasks.put(targetPos, new BedrockCheckTask(targetPos, targetState, validAList, areaTask));
        return true;
    }

    private static boolean hasPistonInHotbar(Player player) {
        return AutoBreakBedrock.findPistonInHotbar(player) != -1;
    }

    private static boolean placePistonA(Minecraft mc, BlockPos pos, BlockPos bedrockPos) {
        boolean accepted;
        Direction facing = Direction.getNearest((float)(bedrockPos.getX() - pos.getX()), (float)(bedrockPos.getY() - pos.getY()), (float)(bedrockPos.getZ() - pos.getZ()));
        int pistonSlot = AutoBreakBedrock.findPistonInHotbar((Player)mc.player);
        if (pistonSlot == -1) {
            return false;
        }
        InteractionResult placeResult = AutoBreakBedrock.withHotbarSlot((Player)mc.player, pistonSlot, () -> AutoBreakBedrock.useMainHandOnBlock(mc, pos, facing.getOpposite()));
        BlockState state = mc.level.getBlockState(pos);
        boolean bl = accepted = state.getBlock() == Blocks.PISTON || placeResult.consumesAction();
        if (!accepted) {
            return false;
        }
        AutoBreakBedrock.registerPlacedPiston(pos, true);
        AutoBreakBedrock.refreshClientBlock(mc, pos);
        BlockState pistonState = (BlockState)((BlockState)Blocks.PISTON.defaultBlockState().setValue((Property)BlockStateProperties.FACING, (Comparable)facing)).setValue((Property)BlockStateProperties.EXTENDED, (Comparable)Boolean.valueOf(true));
        pendingWrenchActions.put(pos, pistonState);
        return true;
    }

    private static boolean placePistonB(Minecraft mc, BlockPos pos) {
        boolean accepted;
        int pistonSlot = AutoBreakBedrock.findPistonInHotbar((Player)mc.player);
        if (pistonSlot == -1) {
            return false;
        }
        InteractionResult placeResult = AutoBreakBedrock.withHotbarSlot((Player)mc.player, pistonSlot, () -> AutoBreakBedrock.useMainHandOnBlock(mc, pos, Direction.UP));
        BlockState state = mc.level.getBlockState(pos);
        boolean bl = accepted = state.getBlock() == Blocks.PISTON || placeResult.consumesAction();
        if (accepted) {
            AutoBreakBedrock.registerPlacedPiston(pos, false);
            AutoBreakBedrock.refreshClientBlock(mc, pos);
        }
        return accepted;
    }

    private static InteractionResult breakBlockWithWrench(Minecraft mc, BlockPos pos) {
        boolean wasShiftDown = mc.player.isShiftKeyDown();
        mc.player.setShiftKeyDown(true);
        if (!wasShiftDown) {
            ServerboundPlayerCommandPacket shiftPacket = new ServerboundPlayerCommandPacket((Entity)mc.player, ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY);
            mc.player.connection.send((Packet)shiftPacket);
        }
        BlockHitResult hitResult = new BlockHitResult(pos.getCenter(), Direction.UP, pos, false);
        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        AutoBreakBedrock.refreshClientBlock(mc, pos);
        mc.player.setShiftKeyDown(wasShiftDown);
        if (!wasShiftDown) {
            ServerboundPlayerCommandPacket releaseShiftPacket = new ServerboundPlayerCommandPacket((Entity)mc.player, ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY);
            mc.player.connection.send((Packet)releaseShiftPacket);
        }
        return result;
    }

    private static boolean breakBlockWithEquippedWrench(Minecraft mc, BlockPos pos) {
        int wrenchSlot = AutoBreakBedrock.findWrenchInHotbar((Player)mc.player);
        if (wrenchSlot == -1) {
            return false;
        }
        return AutoBreakBedrock.withHotbarSlot((Player)mc.player, wrenchSlot, () -> {
            InteractionResult result = AutoBreakBedrock.breakBlockWithWrench(mc, pos);
            return result.consumesAction();
        });
    }

    private static boolean rightClickWithWrenchOnce(Minecraft mc, BlockPos pos) {
        int wrenchSlot = AutoBreakBedrock.findWrenchInHotbar((Player)mc.player);
        if (wrenchSlot == -1) {
            return false;
        }
        return AutoBreakBedrock.withHotbarSlot((Player)mc.player, wrenchSlot, () -> AutoBreakBedrock.useMainHandOnBlock(mc, pos, Direction.UP).consumesAction());
    }

    private static boolean isPlayerReadyForInteraction(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.screen == null;
    }

    private static InteractionResult useMainHandOnBlock(Minecraft mc, BlockPos pos, Direction face) {
        BlockHitResult hitResult = new BlockHitResult(pos.getCenter(), face, pos, false);
        return mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static <T> T withHotbarSlot(Player player, int slot, Supplier<T> action) {
        int previousSlot = AutoBreakBedrock.selectHotbarSlot(player, slot);
        try {
            T t = action.get();
            return t;
        }
        finally {
            AutoBreakBedrock.restoreHotbarSlot(player, previousSlot);
        }
    }

    private static void refreshClientBlock(Minecraft mc, BlockPos pos) {
        if (mc.level == null) {
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        mc.level.sendBlockUpdated(pos, state, state, 3);
    }

    private static int selectHotbarSlot(Player player, int slot) {
        int previousSlot = player.getInventory().selected;
        if (previousSlot == slot) {
            return previousSlot;
        }
        player.getInventory().selected = slot;
        if (player instanceof LocalPlayer localPlayer) {
            localPlayer.connection.send(new ServerboundSetCarriedItemPacket(slot));
        }
        return previousSlot;
    }

    private static void restoreHotbarSlot(Player player, int previousSlot) {
        if (player.getInventory().selected == previousSlot) {
            return;
        }
        player.getInventory().selected = previousSlot;
        if (player instanceof LocalPlayer localPlayer) {
            localPlayer.connection.send(new ServerboundSetCarriedItemPacket(previousSlot));
        }
    }

    private static int findWrenchInHotbar(Player player) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!AllItems.WRENCH.isIn(stack)) continue;
            return i;
        }
        return -1;
    }

    private static int findPistonInHotbar(Player player) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() != Items.PISTON) continue;
            return i;
        }
        return -1;
    }

    private static class RecycleTask {
        public final BlockPos pos;
        public int attempts = 0;
        public int cooldownTicks = 0;
        public int nonPistonTicks = 0;
        public int removalConfirmTicks = 0;
        public boolean observedPiston = false;
        public boolean removalAttemptAccepted = false;
        public boolean manualRetry = false;
        public boolean warningShown = false;

        public RecycleTask(BlockPos pos) {
            this.pos = pos;
        }
    }

    private static class BedrockCheckTask {
        private final BlockPos targetPos;
        private final BlockState initialTargetState;
        private final List<AWithB> validAList;
        private final boolean areaTask;
        private final Set<BlockPos> placedPistons = new HashSet<BlockPos>();
        private int tickCount = 0;
        private boolean pistonsPlaced = false;
        private boolean checkingBedrock = false;
        private boolean breakingPistons = false;
        private int tryAIndex = 0;
        private boolean waitingRecycleBeforeNextA = false;
        private BlockPos pendingFailedAPos = null;
        private int tryBIndex = 0;
        private BlockPos placedAPos = null;
        private BlockPos placedBPos = null;
        private int lastProgressTick;
        private int retryCount = 0;

        public BedrockCheckTask(BlockPos targetPos, BlockState initialTargetState, List<AWithB> validAList, boolean areaTask) {
            this.targetPos = targetPos;
            this.initialTargetState = initialTargetState;
            this.validAList = validAList;
            this.areaTask = areaTask;
            this.lastProgressTick = globalTickCounter;
        }

        public boolean isAreaTask() {
            return this.areaTask;
        }

        public boolean check() {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null) {
                this.enqueueAllRecycleCandidates();
                return true;
            }
            if (!this.isCurrentTargetBlock(mc)) {
                this.enqueueAllRecycleCandidates();
                return true;
            }
            if (!this.waitingRecycleBeforeNextA
                && globalTickCounter - this.lastProgressTick >= TASK_STALL_RESET_TICKS) {
                if (this.retryCount >= MAX_TASK_RETRIES) {
                    this.enqueueAllRecycleCandidates();
                    return true;
                }
                ++this.retryCount;
                int nextAIndex = this.getNextAIndexAfterStall();
                this.enqueueAllRecycleCandidates();
                this.resetForRetry(nextAIndex);
                this.touchProgress();
                return false;
            }
            if (!this.pistonsPlaced) {
                return this.advancePlacementPhase(mc);
            }
            if (this.checkingBedrock) {
                return this.advanceBedrockCheckPhase(mc);
            }
            if (this.breakingPistons) {
                return this.advanceOrderedRecyclePhase(mc);
            }
            return false;
        }

        private boolean isCurrentTargetBlock(Minecraft mc) {
            BlockState currentState = mc.level.getBlockState(this.targetPos);
            return currentState.getBlock() == this.initialTargetState.getBlock() && AutoBreakBedrock.isEligibleBreakTarget(currentState);
        }

        private boolean advancePlacementPhase(Minecraft mc) {
            if (this.waitingRecycleBeforeNextA && this.pendingFailedAPos != null) {
                if (recycleTasks.containsKey(this.pendingFailedAPos)) {
                    return false;
                }
                return this.advanceWaitingFailedARecycle(mc);
            }
            if (this.placedAPos == null) {
                if (this.tryAIndex >= this.validAList.size()) {
                    this.breakingPistons = true;
                    this.pistonsPlaced = true;
                    return false;
                }
                AWithB ab = this.validAList.get(this.tryAIndex);
                if (!AutoBreakBedrock.reservePistonPosition(mc, ab.aPos, this)) {
                    ++this.tryAIndex;
                    this.tryBIndex = 0;
                    this.touchProgress();
                    return false;
                }
                if (!AutoBreakBedrock.consumeOperationBudget()) {
                    AutoBreakBedrock.releasePistonPosition(ab.aPos, this);
                    this.touchProgress();
                    return false;
                }
                if (!AutoBreakBedrock.placePistonA(mc, ab.aPos, this.targetPos)) {
                    AutoBreakBedrock.releasePistonPosition(ab.aPos, this);
                    ++this.tryAIndex;
                    this.tryBIndex = 0;
                    this.touchProgress();
                    return false;
                }
                this.placedPistons.add(ab.aPos);
                this.placedAPos = ab.aPos;
                this.touchProgress();
                return false;
            }
            AWithB ab = this.validAList.get(this.tryAIndex);
            if (this.tryBIndex >= ab.bList.size()) {
                AutoBreakBedrock.enqueueRecycle(ab.aPos);
                this.waitingRecycleBeforeNextA = true;
                this.pendingFailedAPos = ab.aPos;
                this.tryBIndex = 0;
                return false;
            }
            BlockPos bPos = ab.bList.get(this.tryBIndex);
            if (!this.isCurrentTargetBlock(mc)) {
                this.enqueueAllRecycleCandidates();
                return true;
            }
            if (!AutoBreakBedrock.reservePistonPosition(mc, bPos, this)) {
                ++this.tryBIndex;
                this.touchProgress();
                return false;
            }
            if (!AutoBreakBedrock.consumeOperationBudget()) {
                AutoBreakBedrock.releasePistonPosition(bPos, this);
                this.touchProgress();
                return false;
            }
            if (AutoBreakBedrock.placePistonB(mc, bPos)) {
                this.placedPistons.add(bPos);
                this.placedBPos = bPos;
                this.pistonsPlaced = true;
                this.checkingBedrock = true;
                this.tickCount = 0;
                this.tryBIndex = 0;
                this.touchProgress();
                return false;
            }
            AutoBreakBedrock.releasePistonPosition(bPos, this);
            ++this.tryBIndex;
            this.touchProgress();
            return false;
        }

        private boolean advanceOrderedRecyclePhase(Minecraft mc) {
            if (this.placedBPos != null) {
                AutoBreakBedrock.enqueueRecycle(this.placedBPos);
                if (this.isRecycleTargetPending(mc, this.placedBPos)) {
                    return false;
                }
                this.placedBPos = null;
                this.touchProgress();
            }
            if (this.placedAPos != null) {
                AutoBreakBedrock.enqueueRecycle(this.placedAPos);
                if (this.isRecycleTargetPending(mc, this.placedAPos)) {
                    return false;
                }
                this.placedAPos = null;
                this.touchProgress();
            }
            this.enqueueRemainingRecycleCandidates();
            this.touchProgress();
            return true;
        }

        private boolean isRecycleTargetPending(Minecraft mc, BlockPos pos) {
            if (recycleTasks.containsKey(pos)) {
                return true;
            }
            return mc.level.getBlockState(pos).getBlock() == Blocks.PISTON;
        }

        private void enqueueRemainingRecycleCandidates() {
            for (BlockPos pos : this.placedPistons) {
                AutoBreakBedrock.enqueueRecycle(pos);
            }
            if (this.pendingFailedAPos != null) {
                AutoBreakBedrock.enqueueRecycle(this.pendingFailedAPos);
            }
        }

        private boolean advanceWaitingFailedARecycle(Minecraft mc) {
            BlockState failedAState = mc.level.getBlockState(this.pendingFailedAPos);
            if (failedAState.getBlock() == Blocks.PISTON) {
                AutoBreakBedrock.enqueueRecycle(this.pendingFailedAPos);
                return false;
            }
            this.placedPistons.remove(this.pendingFailedAPos);
            if (this.pendingFailedAPos.equals((Object)this.placedAPos)) {
                this.placedAPos = null;
            }
            this.waitingRecycleBeforeNextA = false;
            this.pendingFailedAPos = null;
            ++this.tryAIndex;
            this.tryBIndex = 0;
            this.touchProgress();
            return false;
        }

        private boolean advanceBedrockCheckPhase(Minecraft mc) {
            boolean targetChanged;
            ++this.tickCount;
            BlockState currentState = mc.level.getBlockState(this.targetPos);
            boolean bl = targetChanged = currentState.getBlock() != this.initialTargetState.getBlock();
            if (targetChanged || this.tickCount >= BEDROCK_CHECK_TIMEOUT_TICKS) {
                this.checkingBedrock = false;
                this.breakingPistons = true;
                this.touchProgress();
            }
            return false;
        }

        private int getNextAIndexAfterStall() {
            int failedIndex;
            if (this.validAList.isEmpty()) {
                return 0;
            }
            int baseIndex = Math.min(this.tryAIndex, this.validAList.size() - 1);
            if (this.pendingFailedAPos != null && (failedIndex = this.findAIndexByPos(this.pendingFailedAPos)) >= 0) {
                baseIndex = failedIndex;
            }
            return Math.floorMod(baseIndex + 1, this.validAList.size());
        }

        private int findAIndexByPos(BlockPos aPos) {
            for (int i = 0; i < this.validAList.size(); ++i) {
                if (!this.validAList.get((int)i).aPos.equals((Object)aPos)) continue;
                return i;
            }
            return -1;
        }

        private void resetForRetry(int nextAIndex) {
            this.tickCount = 0;
            this.pistonsPlaced = false;
            this.checkingBedrock = false;
            this.breakingPistons = false;
            this.tryAIndex = this.validAList.isEmpty() ? 0 : Math.floorMod(nextAIndex, this.validAList.size());
            this.waitingRecycleBeforeNextA = false;
            this.pendingFailedAPos = null;
            this.tryBIndex = 0;
            this.placedAPos = null;
            this.placedBPos = null;
            this.placedPistons.clear();
        }

        private void touchProgress() {
            this.lastProgressTick = globalTickCounter;
            this.retryCount = 0;
        }

        private void enqueueAllRecycleCandidates() {
            if (this.placedBPos != null) {
                AutoBreakBedrock.enqueueRecycle(this.placedBPos);
            }
            if (this.placedAPos != null) {
                AutoBreakBedrock.enqueueRecycle(this.placedAPos);
            }
            this.enqueueRemainingRecycleCandidates();
        }
    }

    private static class AWithB {
        public final BlockPos aPos;
        public final List<BlockPos> bList;

        public AWithB(BlockPos aPos, List<BlockPos> bList) {
            this.aPos = aPos;
            this.bList = bList;
        }
    }
}

