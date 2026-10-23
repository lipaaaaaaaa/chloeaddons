package ca.mixin;

import ca.Rotation;
import net.minecraft.client.player.LocalPlayer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Move fix: the sprint jump boost points along the silent yaw, like the server expects. */
@Mixin(LivingEntity.class)
public class MoveFixLivingMixin {

    @ModifyExpressionValue(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float ca$jumpYaw(float original) {
        return Rotation.spoof && (Object) this instanceof LocalPlayer ? Rotation.yaw : original;
    }
}
