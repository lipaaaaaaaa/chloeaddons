package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;

/** Hides the first-person swing. Hits and swing packets are untouched, only what you see changes. */
public class NoSwing extends Module {

    private static NoSwing instance;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);

    public NoSwing() {
        super("No Swing", "Visuals");
        instance = this;
        settings.add(toggle);
    }

    /** Read by ViewmodelMixin through Viewmodel.attack. */
    public static boolean isOn() { return instance != null && instance.toggle.value; }
}