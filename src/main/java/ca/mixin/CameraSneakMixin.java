package ca.mixin;

import ca.modules.visuals.SneakFix;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraSneakMixin {

    @Shadow
    private Entity entity;

    @Shadow
    private float eyeHeight;

    @Shadow
    private float eyeHeightOld;

    /**
     * Vanilla already moved eyeHeight halfway toward the entity's eye height this tick.
     * Redo that single step from the previous value with our own factor, so the
     * camera makes one clean move per tick instead of two stacked ones.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void ca$sneakEase(CallbackInfo ci) {
        if (!SneakFix.active || entity == null) {
            return;
        }

        eyeHeight = eyeHeightOld + (entity.getEyeHeight() - eyeHeightOld) * SneakFix.factor;
    }
}
