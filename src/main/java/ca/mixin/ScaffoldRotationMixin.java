package ca.mixin;

import ca.modules.combat.KillAura;
import ca.modules.player.Scaffold;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Silent rotate: the movement packet carries the spoofed rotation, the camera keeps the real one.
 * Kill Aura spoofs yaw and pitch and wins over Scaffold, which only spoofs the pitch.
 */
@Mixin(LocalPlayer.class)
public class ScaffoldRotationMixin {

    @ModifyExpressionValue(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float ca$yaw(float original) {
        return KillAura.spoof ? KillAura.spoofYaw : original;
    }

    @ModifyExpressionValue(method = "sendPosition",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float ca$pitch(float original) {
        if (KillAura.spoof) return KillAura.spoofPitch;
        return Scaffold.spoof ? Scaffold.spoofPitch : original;
    }
}