package ca.mixin;

import org.spongepowered.asm.mixin.Mixin;
import net.minecraft.world.entity.Entity;

/**
 * Intentionally empty: the old moveRelative input warp was removed because it made our
 * real movement impossible for Grim to reproduce from the rotation we send (AntiKB /
 * simulation flags). Vanilla movement is already consistent with a correct silent yaw.
 */
@Mixin(Entity.class)
public class MoveFixEntityMixin {
}
