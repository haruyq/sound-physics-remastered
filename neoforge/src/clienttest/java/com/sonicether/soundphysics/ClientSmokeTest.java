package com.sonicether.soundphysics;

import com.sonicether.soundphysics.utils.LevelAccessUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = SoundPhysicsMod.MODID, value = Dist.CLIENT)
public class ClientSmokeTest {

    private static boolean finished;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (finished || minecraft.getOverlay() != null || (!(minecraft.screen instanceof TitleScreen) && !(minecraft.screen instanceof AccessibilityOnboardingScreen))) {
            return;
        }
        finished = true;
        boolean sableLoaded = ModList.get().isLoaded("sable");
        if (LevelAccessUtils.hasMovingLevelSupport() != sableLoaded) {
            throw new IllegalStateException("Sable sound support does not match the loaded mods");
        }
        Loggers.log("Client smoke test passed (Sable: {})", sableLoaded);
        minecraft.stop();
    }
}
