package ca.mixin;

import ca.Rotation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * sendPosition builds the outgoing movement packet and compares the rotation with
 * yRotLast/xRotLast. Both the comparison and the packet use the silent rotation.
 * The player's own yaw/pitch are never touched here, so nothing needs restoring.
 */
@Mixin(LocalPlayer.class)
public class SendRotationSyncMixin {

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float ca$yaw(LocalPlayer player) {
        return Rotation.spoof ? Rotation.yaw : player.getYRot();
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float ca$pitch(LocalPlayer player) {
        return Rotation.spoof ? Rotation.pitch : player.getXRot();
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void ca$after(CallbackInfo ci) {
        if (!Rotation.spoof) {
            return;
        }

        AccessorLocalPlayer acc = (AccessorLocalPlayer) (Object) this;

        Rotation.yawSent = acc.ca$getYRotLast();
        Rotation.pitchSent = acc.ca$getXRotLast();
    }
}