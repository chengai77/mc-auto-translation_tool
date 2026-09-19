package org.universaltranslator.forge;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(UniversalTranslatorForgeMod.MOD_ID)
public final class UniversalTranslatorForgeMod {
    public static final String MOD_ID = "universal_translator";
    public UniversalTranslatorForgeMod() { if (FMLEnvironment.dist.isClient()) UniversalTranslatorForgeClient.register(); }
}
