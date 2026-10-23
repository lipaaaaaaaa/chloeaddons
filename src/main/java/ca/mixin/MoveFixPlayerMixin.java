package ca.mixin;

import ca.Rotation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Move fix. The server (Grim) simulates each movement packet using the yaw/pitch that
 * packet carries. With a silent rotation that is Rotation.yaw/pitch, not the camera.
 * So the whole of aiStep (where movement is calculated) must run with that same
 * rotation, and the WASD input must be remapped so the player still walks where the
 * camera points.
 */
@Mixin(LocalPlayer.class)
public abstract class MoveFixPlayerMixin {

    /** First-person hand sway state. The held item is drawn relative to these. */
    @Shadow
    public float xBob;

    @Shadow
    public float yBob;

    @Unique
    private float ca$tickYaw;

    @Unique
    private float ca$tickPitch;

    @Unique
    private float ca$cameraYaw;

    @Unique
    private float ca$cameraPitch;

    @Unique
    private float ca$xBobBefore;

    @Unique
    private float ca$yBobBefore;

    @Unique
    private boolean ca$swapped;

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void ca$aiStepHead(CallbackInfo ci) {
        ca$swapped = false;

        if (!Rotation.spoof || !Rotation.moveFix) {
            return;
        }

        LocalPlayer player = (LocalPlayer) (Object) this;

        // Read the shared rotation once; the remap below uses the same values.
        ca$tickYaw = Rotation.yaw;
        ca$tickPitch = Rotation.pitch;

        ca$cameraYaw = player.getYRot();
        ca$cameraPitch = player.getXRot();

        ca$xBobBefore = xBob;
        ca$yBobBefore = yBob;

        player.setYRot(ca$tickYaw);
        player.setXRot(ca$tickPitch);

        ca$swapped = true;
    }

    /**
     * Runs right after the input object produced this tick's keys and move vector.
     * Picks the valid WASD combination whose world direction (relative to the tick
     * yaw) is closest to the direction the player intends (relative to the camera).
     */
    @Inject(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/ClientInput;tick()V",
                    shift = At.Shift.AFTER
            )
    )
    private void ca$correctMovementInput(CallbackInfo ci) {
        if (!ca$swapped) {
            return;
        }

        LocalPlayer player = (LocalPlayer) (Object) this;
        var input = player.input;

        Vec2 original = input.getMoveVector();

        if (original.lengthSquared() < 1.0E-7F) {
            return;
        }

        double cameraRadians = Math.toRadians(ca$cameraYaw);
        double tickRadians = Math.toRadians(ca$tickYaw);

        // Same formula vanilla uses: x = strafe (left positive), y = forward.
        double desiredX =
                original.x * Math.cos(cameraRadians)
                        - original.y * Math.sin(cameraRadians);

        double desiredZ =
                original.y * Math.cos(cameraRadians)
                        + original.x * Math.sin(cameraRadians);

        int bestX = 0;
        int bestZ = 0;
        double bestDot = -Double.MAX_VALUE;

        for (int candidateX = -1; candidateX <= 1; candidateX++) {
            for (int candidateZ = -1; candidateZ <= 1; candidateZ++) {
                if (candidateX == 0 && candidateZ == 0) {
                    continue;
                }

                double length = Math.hypot(candidateX, candidateZ);

                double inputX = candidateX / length;
                double inputZ = candidateZ / length;

                double worldX =
                        inputX * Math.cos(tickRadians)
                                - inputZ * Math.sin(tickRadians);

                double worldZ =
                        inputZ * Math.cos(tickRadians)
                                + inputX * Math.sin(tickRadians);

                double dot = worldX * desiredX + worldZ * desiredZ;

                if (dot > bestDot) {
                    bestDot = dot;
                    bestX = candidateX;
                    bestZ = candidateZ;
                }
            }
        }

        Input old = input.keyPresses;

        input.keyPresses = new Input(
                bestZ > 0,       // forward
                bestZ < 0,       // backward
                bestX > 0,       // left
                bestX < 0,       // right
                old.jump(),
                old.shift(),
                old.sprint()
        );

        float length = (float) Math.hypot(bestX, bestZ);

        ((AccessorClientInput) input).ca$setMoveVector(
                new Vec2(bestX / length, bestZ / length)
        );
    }

    /** RETURN covers every return path of aiStep, so the camera is always restored. */
    @Inject(method = "aiStep", at = @At("RETURN"))
    private void ca$aiStepReturn(CallbackInfo ci) {
        if (!ca$swapped) {
            return;
        }

        LocalPlayer player = (LocalPlayer) (Object) this;

        player.setYRot(ca$cameraYaw);
        player.setXRot(ca$cameraPitch);

        // Vanilla eased the hand sway toward the silent rotation during aiStep, which
        // makes the held item swing away from the camera. Redo that step toward the
        // camera instead. If vanilla did not touch them in aiStep, nothing changes.
        yBob = ca$easeToCamera(ca$yBobBefore, yBob, ca$tickYaw, ca$cameraYaw);
        xBob = ca$easeToCamera(ca$xBobBefore, xBob, ca$tickPitch, ca$cameraPitch);

        ca$swapped = false;
    }

    /**
     * Vanilla computed after = before + (spoof - before) * k. Recover k and apply it
     * toward the camera value instead.
     */
    @Unique
    private static float ca$easeToCamera(float before, float after, float spoof, float camera) {
        float span = spoof - before;

        if (after == before || Math.abs(span) < 1.0E-4F) {
            return after;
        }

        float k = (after - before) / span;

        return before + (camera - before) * k;
    }
}