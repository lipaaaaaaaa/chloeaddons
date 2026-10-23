package ca.modules.player;

import ca.Setting;
import ca.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityDimensions;

/**
 * 1.7.10 / 1.8.9 sneaking: the hitbox stays 1.8 tall and only the eyes drop by 0.08 (1.62 to 1.54).
 * Modern versions shrink the sneaking hitbox to 1.5 and drop the eyes to 1.27, which is what makes
 * aim and hits feel off on servers that run the old rules. OldSneakMixin swaps the dimensions, and
 * since the eye height comes from the dimensions, the camera, raycasts and collision all follow.
 * Applies to every player, so sneaking opponents have the same hitbox the server uses.
 */
public class OldSneak extends Module {

    public static final EntityDimensions DIMENSIONS = EntityDimensions.scalable(0.6F, 1.8F).withEyeHeight(1.54F);

    private static OldSneak instance;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);

    private boolean applied;

    public OldSneak() {
        super("Old Sneak", "Player");
        instance = this;
        settings.add(toggle);
    }

    /** Read by OldSneakMixin. */
    public static boolean isOn() { return instance != null && instance.toggle.value; }

    @Override
    public void onTick() {
        // First tick after enabling: players that are already sneaking need their size recalculated
        if (!applied) {
            applied = true;
            refresh();
        }
    }

    @Override
    public void onDisable() {
        applied = false;
        refresh();
    }

    private static void refresh() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var p : level.players()) p.refreshDimensions();
    }
}