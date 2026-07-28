package org.batchpacket.submitchange_batchpacket;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@Mod("create_block_rotation_menu_wholesale")
public class Submitchange_batchpacket {
    public static Submitchange_batchpacket INSTANCE;
    public static final KeyMapping CONFIG_KEY;

    public Submitchange_batchpacket() {
        INSTANCE = this;
    }

    static {
        CONFIG_KEY = new KeyMapping("key.submitchange_batchpacket.config", InputConstants.Type.KEYSYM, 298, "category.submitchange_batchpacket");
    }

    @EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static class ModRegistration {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(CONFIG_KEY);
        }
    }
}
