package ca.mixin;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the move fix rewrite the move vector the input object just produced. */
@Mixin(ClientInput.class)
public interface AccessorClientInput {

    @Accessor("moveVector")
    void ca$setMoveVector(Vec2 value);
}