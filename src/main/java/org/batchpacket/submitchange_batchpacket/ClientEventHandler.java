package org.batchpacket.submitchange_batchpacket;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import org.batchpacket.submitchange_batchpacket.AutoBreakBedrock;
import org.batchpacket.submitchange_batchpacket.ConfigScreen;
import org.batchpacket.submitchange_batchpacket.Submitchange_batchpacket;

@EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientEventHandler {
    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (Submitchange_batchpacket.CONFIG_KEY.consumeClick()) {
            mc.setScreen((Screen)ConfigScreen.create(mc.screen));
        }
    }

    @SubscribeEvent
    public static void onMouseLeftClick(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (AutoBreakBedrock.handleLeftClick(mc, event.getAction() == 1)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!event.player.level.isClientSide()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.player != mc.player) {
            return;
        }
        AutoBreakBedrock.tick();
    }
}
