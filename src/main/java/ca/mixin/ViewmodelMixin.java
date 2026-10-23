package ca.mixin;

import ca.modules.visuals.Viewmodel;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class ViewmodelMixin {

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight) {}

    @Shadow
    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm arm, float attackValue) {}

    @Shadow
    private void swingArm(float animation, PoseStack poseStack, int invert, HumanoidArm arm) {}

    /** Offset, rotation and scale: every normal held item goes through this method. */
    @Inject(method = "applyItemArmTransform", at = @At("TAIL"))
    private void ca$offset(PoseStack poseStack, HumanoidArm arm, float inverseArmHeight, CallbackInfo ci) {
        Viewmodel.transform(poseStack, arm);
    }

    /** Swing with a smaller lunge and an optional pivot closer to the middle of the item. */
    @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
    private void ca$swing(float animation, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
        var vm = Viewmodel.get();
        if (!Viewmodel.on()) return;
        ci.cancel();

        float lunge = vm.lunge.value.floatValue() / 100.0F;
        float root = Mth.sqrt(animation);
        float x = -0.4F * Mth.sin((double) (root * (float) Math.PI)) * lunge;
        float y = 0.2F * Mth.sin((double) (root * ((float) Math.PI * 2.0F))) * lunge;
        float z = -0.2F * Mth.sin((double) (animation * (float) Math.PI)) * lunge;
        poseStack.translate((float) invert * x, y, z);

        if (vm.centerPivot.value) {
            float px = invert * vm.pivotX.value.floatValue(), py = vm.pivotY.value.floatValue(), pz = vm.pivotZ.value.floatValue();
            poseStack.translate(px, py, pz);
            applyItemArmAttackTransform(poseStack, arm, animation);
            poseStack.translate(-px, -py, -pz);
        } else {
            applyItemArmAttackTransform(poseStack, arm, animation);
        }
    }

    /** Swing speed, No Swing and the 1.7 blockhit pose. Wraps the whole per-hand render. */
    @WrapMethod(method = "submitArmWithItem")
    private void ca$hand(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state, float partialTicks,
                         float xRot, InteractionHand hand, float attack, ItemStack itemStack, float inverseArmHeight,
                         PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
                         Operation<Void> original) {
        float a = Viewmodel.attack(attack);

        var mc = Minecraft.getInstance();
        var avatar = playerState.avatarRenderState;
        if (Viewmodel.blockhitOn() && avatar != null && hand == InteractionHand.MAIN_HAND && !state.isScoping
                && !avatar.isUsingItem && itemStack.is(ItemTags.SWORDS) && !state.offHandItem.is(Items.SHIELD)
                && mc.options.keyUse.isDown() && mc.mouseHandler.isMouseGrabbed()) {
            HumanoidArm arm = avatar.mainArm;
            int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.pushPose();
            applyItemArmTransform(poseStack, arm, inverseArmHeight);
            swingArm(a, poseStack, invert, arm); // swing while blocking: blockhit
            Viewmodel.blockPose(poseStack, invert);
            state.mainHandRenderState.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
            return;
        }

        original.call(playerState, state, partialTicks, xRot, hand, a, itemStack, inverseArmHeight, poseStack,
                collector, lightCoords);
    }
}