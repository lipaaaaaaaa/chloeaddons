package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;

public class NoBreakParticles extends Module {

    // static so the mixin can read it
    public static final Setting.Switch TOGGLE = new Setting.Switch("Enabled", false);

    public NoBreakParticles() {
        super("No break particles", "Visuals");
        this.enabled = true;
        settings.add(TOGGLE);
    }
}