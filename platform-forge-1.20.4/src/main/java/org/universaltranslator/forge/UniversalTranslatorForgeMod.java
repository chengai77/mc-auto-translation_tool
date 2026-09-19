package org.universaltranslator.forge;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.universaltranslator.forge.UniversalTranslatorForgeClient;

/** Forge模组入口 */
@Mod(UniversalTranslatorForgeMod.MOD_ID)
public final class UniversalTranslatorForgeMod {
    public static final String MOD_ID = "universal_translator";

    public UniversalTranslatorForgeMod() {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                UniversalTranslatorForgeClient.register(
                        FMLJavaModLoadingContext.get().getModEventBus()));
    }
}
