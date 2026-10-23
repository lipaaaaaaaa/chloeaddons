package ca.mixin;

import ca.modules.player.OldSneak;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Since 1.21.9 the pose dimensions live in Avatar (the superclass of Player and Mannequin), not in Player
@Mixin(Avatar.class)
public class OldSneakMixin {
    @Inject(method = "getDefaultDimensions", at = @At("RETURN"), cancellable = true)
    private void ca$oldSneak(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.CROUCHING && OldSneak.isOn()) cir.setReturnValue(OldSneak.DIMENSIONS);
    }
}