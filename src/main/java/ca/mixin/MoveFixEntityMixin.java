package ca.mixin;

import ca.modules.combat.KillAura;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Move fix: physics for the local player use inputs the server can predict from the silent yaw. */
@Mixin(Entity.class)
public class MoveFixEntityMixin {

    @ModifyVariable(method = "moveRelative", at = @At("HEAD"), argsOnly = true)
    private Vec3 ca$fixInput(Vec3 input) {
        if (!KillAura.fix || !((Object) this instanceof LocalPlayer p)) return input;
        return KillAura.fixInput(input, p.getYRot());
    }
}