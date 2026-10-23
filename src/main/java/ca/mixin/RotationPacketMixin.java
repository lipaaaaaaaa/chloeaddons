package ca.mixin;

import ca.Rotation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ServerboundMovePlayerPacket.class)
public abstract class RotationPacketMixin {
    @ModifyVariable(
            method = "<init>(DDDFFZZZZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 7
    )
    private static float ca$yaw(float yRot) {
        return Rotation.spoof ? Rotation.yaw : yRot;
    }

    @ModifyVariable(
            method = "<init>(DDDFFZZZZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            index = 8
    )
    private static float ca$pitch(float xRot) {
        return Rotation.spoof ? Rotation.pitch : xRot;
    }
}