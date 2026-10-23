package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;

/**
 * Fixes the first-person camera dipping twice when sneaking.
 * Vanilla eases the camera eye height toward the new pose height every tick;
 * CameraSneakMixin replaces that easing with one controllable step.
 */
public class SneakFix extends Module {

    /** Read by CameraSneakMixin. Only true while the module is enabled. */
    public static volatile boolean active;
    public static volatile float factor = 1.0F;

    public final Setting.Switch toggle =
            new Setting.Switch("Enabled", true);

    /** 1 = camera follows the pose height instantly, lower = slower single ease. */
    public final Setting.Slider ease =
            new Setting.Slider("Ease speed", 0.1, 1, 1);

    public SneakFix() {
        super("Sneak Fix", "Visuals");

        this.enabled = true;

        settings.add(toggle);
        settings.add(ease);
    }

    @Override
    public void onTick() {
        factor = (float) ease.value.doubleValue();
        active = true;
    }

    @Override
    public void onDisable() {
        active = false;
    }
}
