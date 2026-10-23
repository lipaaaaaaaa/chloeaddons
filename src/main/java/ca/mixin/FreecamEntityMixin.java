package ca.mixin;

import ca.modules.player.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FreecamEntityMixin {
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void ca$turn(double yaw, double pitch, CallbackInfo ci) {
        if (Freecam.active && (Object) this == Minecraft.getInstance().player) {
            Freecam.yaw += (float) yaw * 0.15f;
            Freecam.pitch = Math.clamp(Freecam.pitch + (float) pitch * 0.15f, -90f, 90f);
            ci.cancel();
        }
    }
}