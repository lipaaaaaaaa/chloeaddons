package ca.mixin;

import ca.Rotation;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Silent rotation at the packet level: every outgoing movement packet (Pos, PosRot, Rot)
 * carries the spoofed yaw/pitch. Before this, only sendPosition() was patched, so Pos
 * packets went out with the real camera rotation while attack packets used the spoofed
 * one -> Grim's Simulation / AntiKB could not reproduce our motion from the rotation it
 * had on file, and PacketOrderB fired because position and look disagreed around attacks.
 */
@Mixin({ServerboundMovePlayerPacket.Pos.class, ServerboundMovePlayerPacket.PosRot.class})
public class RotationPacketMixin {

    // Pos(Vec3 pos, boolean onGround, boolean horizontalCollision, float yRot, float xRot)
    @ModifyVariable(method = "<init>(Lnet/minecraft/world/phys/Vec3;ZZFF)V",
            at = @At("HEAD"), argsOnly = true, index = 3)
    private static float ca$posYaw(float yRot) {
        return Rotation.spoof ? Rotation.yaw : yRot;
    }

    @ModifyVariable(method = "<init>(Lnet/minecraft/world/phys/Vec3;ZZFF)V",
            at = @At("HEAD"), argsOnly = true, index = 4)
    private static float ca$posPitch(float xRot) {
        return Rotation.spoof ? Rotation.pitch : xRot;
    }

    // Pos(double x, double y, double z, boolean onGround, boolean horizontalCollision, float yRot, float xRot)
    @ModifyVariable(method = "<init>(DDDZZFF)V",
            at = @At("HEAD"), argsOnly = true, index = 5)
    private static float ca$posDYaw(float yRot) {
        return Rotation.spoof ? Rotation.yaw : yRot;
    }

    @ModifyVariable(method = "<init>(DDDZZFF)V",
            at = @At("HEAD"), argsOnly = true, index = 6)
    private static float ca$posDPitch(float xRot) {
        return Rotation.spoof ? Rotation.pitch : xRot;
    }

    // PosRot(Vec3 pos, float yRot, float xRot, boolean onGround, boolean horizontalCollision)
    @ModifyVariable(method = "<init>(Lnet/minecraft/world/phys/Vec3;FFZZ)V",
            at = @At("HEAD"), argsOnly = true, index = 2)
    private static float ca$posRotYaw(float yRot) {
        return Rotation.spoof ? Rotation.yaw : yRot;
    }

    @ModifyVariable(method = "<init>(Lnet/minecraft/world/phys/Vec3;FFZZ)V",
            at = @At("HEAD"), argsOnly = true, index = 3)
    private static float ca$posRotPitch(float xRot) {
        return Rotation.spoof ? Rotation.pitch : xRot;
    }

    // PosRot(double x, double y, double z, float yRot, float xRot, boolean onGround, boolean horizontalCollision)
    @ModifyVariable(method = "<init>(DDDFFZZ)V",
            at = @At("HEAD"), argsOnly = true, index = 3)
    private static float ca$posRotDYaw(float yRot) {
        return Rotation.spoof ? Rotation.yaw : yRot;
    }

    @ModifyVariable(method = "<init>(DDDFFZZ)V",
            at = @At("HEAD"), argsOnly = true, index = 4)
    private static float ca$posRotDPitch(float xRot) {
        return Rotation.spoof ? Rotation.pitch : xRot;
    }
}
