package ca.mixin;

import ca.Rotation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent rotation inside vanilla's own sendPosition():
 * - The "did the look change" test compares the SPOOFED rotation against what we last sent.
 *   Without this, when only silent aim moved (camera still), vanilla sent no Rot packet ->
 *   the server kept an old look on file while our attack used the new one (Grim PacketOrderB
 *   pre/post-attack) and consecutive packets jumped rotation (Simulation / AntiKB).
 * - yRotLast/xRotLast remember what we SENT, not where the camera points, so vanilla's
 *   rotation delta stays small and continuous even though the camera never moved there.
 */
@Mixin(LocalPlayer.class)
public class SendRotationSyncMixin {

    @Unique
    private float ca$sentYaw;
    @Unique
    private float ca$sentPitch;
    @Unique
    private boolean ca$haveSent;
    @Unique
    private float ca$camYaw, ca$camPitch;

    // The packet yaw/pitch reads inside sendPosition always hand out the spoofed values.
    @ModifyVariable(method = "sendPosition", at = @At(value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float ca$yaw(float original) {
        return Rotation.spoof ? Rotation.yaw : original;
    }

    @ModifyVariable(method = "sendPosition", at = @At(value = "INVOKE_ASSIGN",
            target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float ca$pitch(float original) {
        return Rotation.spoof ? Rotation.pitch : original;
    }

    // Vanilla decides "rotation changed?" by comparing the camera against yRotLast/xRotLast.
    // While spoofing we temporarily set those fields to what we last SENT, so the decision
    // is made against the rotation the server actually has on file: a silent-only change
    // still produces a Rot packet (no attack-before-look -> PacketOrderB) and vanilla's own
    // delta bookkeeping stays continuous (no rotation jump -> Simulation/AntiKB).
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void ca$before(CallbackInfo ci) {
        if (!Rotation.spoof) return;
        LocalPlayer p = (LocalPlayer) (Object) this;
        if (ca$haveSent) {
            ca$camYaw = p.getYRot();
            ca$camPitch = p.getXRot();
            p.yRotLast = ca$sentYaw;
            p.xRotLast = ca$sentPitch;
        }
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void ca$after(CallbackInfo ci) {
        LocalPlayer p = (LocalPlayer) (Object) this;
        if (Rotation.spoof) {
            ca$sentYaw = Rotation.yaw;
            ca$sentPitch = Rotation.pitch;
            ca$haveSent = true;
            Rotation.yawSent = Rotation.yaw;
            Rotation.pitchSent = Rotation.pitch;
            // Restore the camera so rendering/input are untouched by our little trick.
            if (ca$haveSent && (p.getYRot() != ca$camYaw || p.getXRot() != ca$camPitch)) {
                p.setYRot(ca$camYaw);
                p.setXRot(ca$camPitch);
            }
            // yRotLast/xRotLast now hold the SPOOFED values (vanilla wrote them at TAIL),
            // which is exactly what we want: they track what went out.
        } else if (ca$haveSent) {
            ca$haveSent = false;
        }
    }
}
