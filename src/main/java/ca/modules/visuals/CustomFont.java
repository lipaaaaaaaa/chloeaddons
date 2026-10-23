package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import net.minecraft.resources.Identifier;

public class CustomFont extends Module {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("ca", "custom");
    private static CustomFont instance;

    private final Setting.Switch enabled = new Setting.Switch("Enabled", false);

    public CustomFont() {
        super("Custom Font", "Visuals");
        instance = this;
        settings.add(enabled);
    }

    public static boolean active() {
        return instance != null && instance.enabled.value;
    }
}