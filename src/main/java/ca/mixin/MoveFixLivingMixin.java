package ca.mixin;

import ca.modules.combat.KillAura;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Move fix: the sprint jump boost points along the silent yaw, like the server expects. */
@Mixin(LivingEntity.class)
public class MoveFixLivingMixin {

    @ModifyExpressionValue(method = "jumpFromGround",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float ca$jumpYaw(float original) {
        return KillAura.fix && (Object) this instanceof LocalPlayer ? KillAura.spoofYaw : original;
    }
}