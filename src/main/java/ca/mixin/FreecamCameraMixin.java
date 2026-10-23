package ca.mixin;

import ca.modules.player.Freecam;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class FreecamCameraMixin {
    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void ca$freecam(CallbackInfo ci) {
        if (!Freecam.active) return;
        Freecam.step();
        setRotation(Freecam.yaw, Freecam.pitch);
        setPosition(Freecam.x, Freecam.y, Freecam.z);
    }

    @Inject(method = "isDetached", at = @At("HEAD"), cancellable = true)
    private void ca$detached(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.active) cir.setReturnValue(true);
    }
}