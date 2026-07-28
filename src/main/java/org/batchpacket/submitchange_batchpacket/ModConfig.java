package org.batchpacket.submitchange_batchpacket;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fml.loading.FMLPaths;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("create_block_rotation_menu_wholesale-client.json");
    private static final List<String> DEFAULT_WHITELIST_IDS = Arrays.asList("minecraft:bedrock", "minecraft:netherrack", "minecraft:deepslate");
    private static final int MIN_PROCESSING_SPEED = 1;
    private static final int MAX_PROCESSING_SPEED = 100;
    private static final ModConfig INSTANCE = new ModConfig();
    private BatchMode batchMode = BatchMode.OFF;
    private int processingSpeed = 50;
    private BlockState lastSavedPacketState = null;
    private String lastSavedPacketInfo = "";
    private AutoBreakMode autoBreakMode = AutoBreakMode.OFF;
    private final Set<Block> autoBreakWhitelist = new LinkedHashSet<Block>();
    private boolean suppressSave = false;

    private ModConfig() {
        this.applyDefaultWhitelist();
        this.loadFromDisk();
    }

    public static ModConfig getInstance() {
        return INSTANCE;
    }

    public BatchMode getBatchMode() {
        return this.batchMode;
    }

    public void setBatchMode(BatchMode mode) {
        this.batchMode = mode == null ? BatchMode.OFF : mode;
        this.saveIfAllowed();
    }

    public boolean isBatchModeEnabled() {
        return this.batchMode != BatchMode.OFF;
    }

    public int getPacketSendDelay() {
        double ratio = (double)(this.processingSpeed - 1) / 99.0;
        return (int)Math.round(500.0 - ratio * 495.0);
    }

    public void setPacketSendDelay(int delay) {
        int clampedDelay = Math.max(5, Math.min(500, delay));
        double ratio = (500.0 - (double)clampedDelay) / 495.0;
        this.processingSpeed = ModConfig.clampSpeed((int)Math.round(1.0 + ratio * 99.0));
        this.saveIfAllowed();
    }

    public int getPacketsPerBatch() {
        double ratio = (double)(this.processingSpeed - 1) / 99.0;
        return (int)Math.round(1.0 + ratio * 999.0);
    }

    public void setPacketsPerBatch(int count) {
        int clampedCount = Math.max(1, Math.min(1000, count));
        double ratio = (double)(clampedCount - 1) / 999.0;
        this.processingSpeed = ModConfig.clampSpeed((int)Math.round(1.0 + ratio * 99.0));
        this.saveIfAllowed();
    }

    public int getProcessingSpeed() {
        return this.processingSpeed;
    }

    public void setProcessingSpeed(int speed) {
        this.processingSpeed = ModConfig.clampSpeed(speed);
        this.saveIfAllowed();
    }

    public BlockState getLastSavedPacketState() {
        return this.lastSavedPacketState;
    }

    public void setLastSavedPacketState(BlockState state) {
        this.lastSavedPacketState = state;
    }

    public String getLastSavedPacketInfo() {
        return this.lastSavedPacketInfo;
    }

    public void setLastSavedPacketInfo(String info) {
        this.lastSavedPacketInfo = info;
    }

    public AutoBreakMode getAutoBreakMode() {
        return this.autoBreakMode;
    }

    public void setAutoBreakMode(AutoBreakMode mode) {
        this.autoBreakMode = mode == null ? AutoBreakMode.OFF : mode;
        this.saveIfAllowed();
    }

    public Set<Block> getAutoBreakWhitelist() {
        return Collections.unmodifiableSet(this.autoBreakWhitelist);
    }

    public void setAutoBreakWhitelist(Set<Block> whitelist) {
        this.autoBreakWhitelist.clear();
        if (whitelist == null || whitelist.isEmpty()) {
            this.applyDefaultWhitelist();
            this.saveIfAllowed();
            return;
        }
        this.autoBreakWhitelist.addAll(whitelist);
        this.saveIfAllowed();
    }

    public List<String> getAutoBreakWhitelistIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (Block block : this.autoBreakWhitelist) {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) continue;
            ids.add(key.toString());
        }
        return ids;
    }

    public boolean addWhitelistBlockById(String blockId) {
        Optional<Block> block = this.resolveBlock(blockId);
        if (block.isEmpty()) {
            return false;
        }
        return this.addWhitelistBlock(block.get());
    }

    public boolean removeWhitelistBlockById(String blockId) {
        Optional<Block> block = this.resolveBlock(blockId);
        if (block.isEmpty()) {
            return false;
        }
        return this.removeWhitelistBlock(block.get());
    }

    public boolean addWhitelistBlock(Block block) {
        if (block == null || block == Blocks.AIR) {
            return false;
        }
        boolean changed = this.autoBreakWhitelist.add(block);
        if (changed) {
            this.saveIfAllowed();
        }
        return changed;
    }

    public boolean removeWhitelistBlock(Block block) {
        if (block == null) {
            return false;
        }
        boolean changed = this.autoBreakWhitelist.remove(block);
        if (this.autoBreakWhitelist.isEmpty()) {
            this.applyDefaultWhitelist();
            changed = true;
        }
        if (changed) {
            this.saveIfAllowed();
        }
        return changed;
    }

    public boolean isWhitelistedAutoBreakBlock(Block block) {
        return block != null && this.autoBreakWhitelist.contains(block);
    }

    public String getAutoBreakWhitelistSummary() {
        if (this.autoBreakWhitelist.isEmpty()) {
            return "(\u7a7a)";
        }
        return this.autoBreakWhitelist.stream().map(block -> {
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            return key == null ? "unknown" : key.toString();
        }).collect(Collectors.joining(", "));
    }

    public synchronized void loadFromDisk() {
        if (!Files.exists(CONFIG_PATH, new LinkOption[0])) {
            this.saveToDisk();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH);){
            PersistedConfig data = (PersistedConfig)GSON.fromJson((Reader)reader, PersistedConfig.class);
            if (data == null) {
                return;
            }
            this.suppressSave = true;
            if (data.batchMode != null) {
                this.batchMode = ModConfig.parseEnum(data.batchMode, BatchMode.class, BatchMode.OFF);
            }
            if (data.processingSpeed != null) {
                this.processingSpeed = ModConfig.clampSpeed(data.processingSpeed);
            } else {
                int delay = data.packetSendDelay == null ? 50 : Math.max(5, Math.min(500, data.packetSendDelay));
                int batch = data.packetsPerBatch == null ? 10 : Math.max(1, Math.min(1000, data.packetsPerBatch));
                int speedByDelay = ModConfig.speedFromDelay(delay);
                int speedByBatch = ModConfig.speedFromBatchSize(batch);
                this.processingSpeed = ModConfig.clampSpeed((speedByDelay + speedByBatch) / 2);
            }
            if (data.autoBreakMode != null) {
                this.autoBreakMode = ModConfig.parseAutoBreakMode(data.autoBreakMode);
            }
            this.autoBreakWhitelist.clear();
            if (data.autoBreakWhitelist != null) {
                for (String id : data.autoBreakWhitelist) {
                    this.resolveBlock(id).ifPresent(this.autoBreakWhitelist::add);
                }
            }
            if (this.autoBreakWhitelist.isEmpty()) {
                this.applyDefaultWhitelist();
            }
        }
        catch (Exception e) {
            System.err.println("[submitChange_batchpacket] \u52a0\u8f7d\u914d\u7f6e\u5931\u8d25: " + e.getMessage());
        }
        finally {
            this.suppressSave = false;
        }
    }

    public synchronized void saveToDisk() {
        PersistedConfig data = new PersistedConfig();
        data.batchMode = this.batchMode.name();
        data.processingSpeed = this.processingSpeed;
        data.packetSendDelay = this.getPacketSendDelay();
        data.packetsPerBatch = this.getPacketsPerBatch();
        data.autoBreakMode = this.autoBreakMode.name();
        data.autoBreakWhitelist = this.getAutoBreakWhitelistIds();
        try {
            Path parent = CONFIG_PATH.getParent();
            if (parent != null) {
                Files.createDirectories(parent, new FileAttribute[0]);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH, new OpenOption[0]);){
                GSON.toJson((Object)data, (Appendable)writer);
            }
        }
        catch (IOException e) {
            System.err.println("[submitChange_batchpacket] \u4fdd\u5b58\u914d\u7f6e\u5931\u8d25: " + e.getMessage());
        }
    }

    private void saveIfAllowed() {
        if (!this.suppressSave) {
            this.saveToDisk();
        }
    }

    private void applyDefaultWhitelist() {
        this.autoBreakWhitelist.clear();
        for (String id : DEFAULT_WHITELIST_IDS) {
            this.resolveBlock(id).ifPresent(this.autoBreakWhitelist::add);
        }
        if (this.autoBreakWhitelist.isEmpty()) {
            this.autoBreakWhitelist.add(Blocks.BEDROCK);
            this.autoBreakWhitelist.add(Blocks.NETHERRACK);
            this.autoBreakWhitelist.add(Blocks.DEEPSLATE);
        }
    }

    private Optional<Block> resolveBlock(String blockId) {
        if (blockId == null) {
            return Optional.empty();
        }
        String normalized = blockId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        ResourceLocation key = ResourceLocation.tryParse((String)normalized);
        if (key == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.BLOCK.getOptional(key).filter(block -> block != Blocks.AIR);
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> enumClass, T fallback) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase(Locale.ROOT));
        }
        catch (Exception ignored) {
            return fallback;
        }
    }

    private static int clampSpeed(int speed) {
        return Math.max(1, Math.min(100, speed));
    }

    private static int speedFromDelay(int delay) {
        double ratio = (500.0 - (double)delay) / 495.0;
        return ModConfig.clampSpeed((int)Math.round(1.0 + ratio * 99.0));
    }

    private static int speedFromBatchSize(int batchSize) {
        double ratio = (double)(batchSize - 1) / 999.0;
        return ModConfig.clampSpeed((int)Math.round(1.0 + ratio * 99.0));
    }

    private static AutoBreakMode parseAutoBreakMode(String value) {
        String normalized;
        if (value == null) {
            return AutoBreakMode.OFF;
        }
        return switch (normalized = value.trim().toUpperCase(Locale.ROOT)) {
            case "BEDROCK_ONLY", "GENERIC_BLOCK", "CLICK_WHITELIST" -> AutoBreakMode.CLICK_WHITELIST;
            case "AREA_BEDROCK", "AREA_WHITELIST" -> AutoBreakMode.AREA_WHITELIST;
            default -> AutoBreakMode.OFF;
        };
    }

    public boolean isAutoBreakBedrock() {
        return this.autoBreakMode != AutoBreakMode.OFF;
    }

    public void setAutoBreakBedrock(boolean enabled) {
        this.autoBreakMode = enabled ? AutoBreakMode.CLICK_WHITELIST : AutoBreakMode.OFF;
        this.saveIfAllowed();
    }

    public static enum BatchMode {
        OFF,
        GENERAL,
        DRAIN_WATER;
    }

    public static enum AutoBreakMode {
        OFF,
        CLICK_WHITELIST,
        AREA_WHITELIST;
    }

    private static class PersistedConfig {
        String batchMode;
        Integer processingSpeed;
        Integer packetSendDelay;
        Integer packetsPerBatch;
        String autoBreakMode;
        List<String> autoBreakWhitelist;

        private PersistedConfig() {
        }
    }
}
