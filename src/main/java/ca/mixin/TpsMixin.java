package ca.mixin;

import ca.modules.visuals.PerformanceHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class TpsMixin {
    // TAIL, not HEAD: the handler first re-queues the packet onto the main thread, HEAD would run twice
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void ca$onSetTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        PerformanceHud.onTime(packet.gameTime());
    }
}