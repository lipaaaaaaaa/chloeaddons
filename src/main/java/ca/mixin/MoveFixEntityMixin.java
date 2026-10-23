package ca.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Intentionally does nothing to movement input.
 *
 * The old version re-projected and 8-direction-snapped the local player's movement
 * vector whenever KillAura's silent rotation was active. That made our actual movement
 * differ from what Grim simulates from the rotation we send, which is exactly how the
 * "AntiKB" and "simulation" flags get triggered (the server sees motion it cannot
 * reproduce from our last known rotation).
 *
 * With a correct silent rotation (deltas that look like real mouse input, sent in the
 * right packet order), vanilla movement is already consistent with the spoofed yaw, so
 * no input warp is needed. This class stays mixed in as an inert placeholder so the
 * existing mixin config entry keeps working.
 */
@Mixin(Entity.class)
public class MoveFixEntityMixin {
}
