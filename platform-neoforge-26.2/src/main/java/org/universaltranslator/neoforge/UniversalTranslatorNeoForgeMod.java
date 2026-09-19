package org.universaltranslator.neoforge;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(value = UniversalTranslatorNeoForgeMod.MOD_ID, dist = Dist.CLIENT)
public final class UniversalTranslatorNeoForgeMod {
    public static final String MOD_ID = "universal_translator";
    public UniversalTranslatorNeoForgeMod(IEventBus modBus) { UniversalTranslatorNeoForgeClient.register(modBus); }
}
