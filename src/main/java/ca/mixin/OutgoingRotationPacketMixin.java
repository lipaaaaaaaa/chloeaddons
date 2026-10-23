package ca.mixin;

import ca.Rotation;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public abstract class OutgoingRotationPacketMixin {

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;)V",
            at = @At("HEAD")
    )
    private void ca$captureOutgoingRotation(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ServerboundMovePlayerPacket movement)) {
            return;
        }

        if (!movement.hasRotation()) {
            return;
        }

        Rotation.yawSent = movement.getYRot(Rotation.yawSent);
        Rotation.pitchSent = movement.getXRot(Rotation.pitchSent);
        Rotation.packetRotationKnown = true;
    }
}