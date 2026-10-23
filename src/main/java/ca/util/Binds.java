package ca.util;

import ca.Config;
import ca.Setting;
import ca.Settings;
import ca.modules.Module;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

/** Module keybinds. The key names live in the "Binds" tab, so they are saved with the rest of the config. */
public final class Binds {
    private static final Map<Module, Boolean> was = new HashMap<>();

    private Binds() {}

    public static void tick(Minecraft mc) {
        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) {
            was.clear();
            return;
        }
        Settings.BINDS.forEach((m, field) -> {
            boolean down = false;
            String name = field.value.trim();
            if (!name.isEmpty()) {
                try {
                    int code = InputConstants.getKey("key.keyboard." + name.toLowerCase()).getValue();
                    // Your mappings only accept the key code (no Window parameter)
                    down = InputConstants.isKeyDown(code);
                } catch (RuntimeException ignored) {}
            }
            if (down && !was.getOrDefault(m, false)) {
                toggle(m);
            }
            was.put(m, down);
        });
    }

    private static void toggle(Module m) {
        for (Setting<?> s : m.settings) {
            if (s instanceof Setting.Switch sw && sw.name.equals("Enabled")) {
                sw.value = !sw.value;
                return;
            }
        }
        m.enabled = !m.enabled;
    }

    /** Used by /ca bind. "none" clears the bind. Returns a message for the player. */
    public static String bind(String module, String key) {
        for (var e : Settings.BINDS.entrySet()) {
            if (!e.getKey().name.equalsIgnoreCase(module)) continue;
            e.getValue().value = key.equalsIgnoreCase("none") ? "" : key;
            Config.save();
            return e.getKey().name + (e.getValue().value.isEmpty() ? " unbound" : " bound to " + key);
        }
        return "No module named " + module;
    }
}