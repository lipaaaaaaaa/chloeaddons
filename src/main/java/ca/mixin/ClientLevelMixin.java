package ca.mixin;

import ca.modules.visuals.NoBreakParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void ca$noDestroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (NoBreakParticles.TOGGLE.value) ci.cancel();
    }
}