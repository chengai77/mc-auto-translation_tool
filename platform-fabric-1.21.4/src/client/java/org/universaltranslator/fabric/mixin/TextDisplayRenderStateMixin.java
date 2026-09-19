package org.universaltranslator.fabric.mixin;

import net.minecraft.client.render.entity.state.TextDisplayEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.universaltranslator.fabric.TextDisplayAnchorState;

@Mixin(TextDisplayEntityRenderState.class)
abstract class TextDisplayRenderStateMixin implements TextDisplayAnchorState {
    @Unique
    private float universalTranslator$topAnchorOffset;

    @Unique
    private float universalTranslator$horizontalOffset;

    @Unique
    private boolean universalTranslator$playerFollowHidden;

    @Override
    public float universalTranslator$getTopAnchorOffset() {
        return universalTranslator$topAnchorOffset;
    }

    @Override
    public void universalTranslator$setTopAnchorOffset(float offset) {
        universalTranslator$topAnchorOffset = offset;
    }

    @Override
    public float universalTranslator$getHorizontalOffset() {
        return universalTranslator$horizontalOffset;
    }

    @Override
    public void universalTranslator$setHorizontalOffset(float offset) {
        universalTranslator$horizontalOffset = offset;
    }

    @Override
    public boolean universalTranslator$isPlayerFollowHidden() {
        return universalTranslator$playerFollowHidden;
    }

    @Override
    public void universalTranslator$setPlayerFollowHidden(boolean hidden) {
        universalTranslator$playerFollowHidden = hidden;
    }
}
