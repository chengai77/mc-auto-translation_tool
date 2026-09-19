package org.universaltranslator.fabric.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.universaltranslator.fabric.UniversalTranslatorFabricClient;

/** 出站聊天翻译 */
@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin {
    @Inject(method = "sendChatMessage", at = @At("HEAD"), cancellable = true)
    private void universalTranslator$translateOutgoing(String chatText, CallbackInfo callback) {
        if (!UniversalTranslatorFabricClient.beginOutgoingChat(chatText)) {
            callback.cancel();
        }
    }
}
