package ca.modules.combat;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class AimLock extends AimModule {

    public AimLock() {
        super("Aim Lock");
    }

    /** Locks onto the closest player inside the range. */
    @Override
    protected double score(Minecraft mc, Player p, Vec3 point) {
        return mc.player.distanceTo(p);
    }
}
