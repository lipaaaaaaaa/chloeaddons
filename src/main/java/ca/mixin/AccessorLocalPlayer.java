
package ca.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** yRotLast/xRotLast are private in LocalPlayer; mixins need them to track the sent rotation. */
@Mixin(LocalPlayer.class)
public interface AccessorLocalPlayer {

    @Accessor("yRotLast")
    float ca$getYRotLast();

    @Accessor("yRotLast")
    void ca$setYRotLast(float value);

    @Accessor("xRotLast")
    float ca$getXRotLast();

    @Accessor("xRotLast")
    void ca$setXRotLast(float value);
}