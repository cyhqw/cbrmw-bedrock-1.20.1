package org.batchpacket.submitchange_batchpacket;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafml.FMLJavaModLoadingContext;

@Mod("create_block_rotation_menu_wholesale")
public class Submitchange_batchpacket {
    public static Submitchange_batchpacket INSTANCE;
    public static final KeyMapping CONFIG_KEY;

    public Submitchange_batchpacket() {
        INSTANCE = this;
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::registerKeyMappings);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(CONFIG_KEY);
    }

    static {
        CONFIG_KEY = new KeyMapping("key.submitchange_batchpacket.config", InputConstants.Type.KEYSYM, 298, "category.submitchange_batchpacket");
    }
}
