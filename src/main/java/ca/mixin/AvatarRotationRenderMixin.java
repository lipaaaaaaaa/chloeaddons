package ca.mixin;

import ca.Rotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRotationRenderMixin {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL")
    )
    private void ca$applyOutgoingRotation(
            Avatar avatar,
            AvatarRenderState state,
            float partialTick,
            CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null
                || avatar != mc.player
                || mc.options.getCameraType().isFirstPerson()
                || !Rotation.showPacketRotation
                || !Rotation.packetRotationKnown) {
            return;
        }

        // Rotate the whole model to the last observed outgoing yaw.
        state.bodyRot = Rotation.yawSent;

        // The head yaw is relative to the body in this render state.
        state.yRot = 0.0F;

        state.xRot = Mth.clamp(
                Rotation.pitchSent,
                -90.0F,
                90.0F
        );
    }
}