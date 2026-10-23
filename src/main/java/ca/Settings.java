package ca;

import ca.modules.Module;
import ca.modules.ModuleManager;
import com.nikoverflow.exploitpreventer.ExploitPreventer;
import com.nikoverflow.exploitpreventer.module.Modules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settings {
    public record Section(String title, List<Setting<?>> settings) {}

    // ExploitPreventer settings (kept as a hardcoded module for compatibility)
    public static final Setting.Switch TRANSLATION_KEY = new Setting.Switch("Translation key", true);
    public static final Setting.Switch FINGERPRINTING = new Setting.Switch("Fingerprinting", true);
    public static final Setting.Switch LOCAL_HTTP = new Setting.Switch("Local HTTP request", true);
    public static final Setting.Slider NOTIFICATION_ALPHA = new Setting.Slider("Notification alpha", 0, 255, 120);
    public static final Setting.Slider NOTIFICATION_X = new Setting.Slider("Notification X", 0, 100, 99);
    public static final Setting.Slider NOTIFICATION_Y = new Setting.Slider("Notification Y", 0, 100, 2);

    // Packet protection, enforced by ExploitGuardMixin (not part of ExploitPreventer)
    public static final Setting.Switch BLOCK_LOG4J = new Setting.Switch("Block log4j", true);
    public static final Setting.Switch BLOCK_DEMO = new Setting.Switch("Block demo screen", true);
    public static final Setting.Switch BLOCK_EXPLOSIONS = new Setting.Switch("Block huge explosions", true);
    public static final Setting.Slider MAX_KNOCKBACK = new Setting.Slider("Max explosion knockback", 1, 100, 20);
    public static final Setting.Switch BLOCK_PARTICLES = new Setting.Switch("Block particle floods", true);
    public static final Setting.Slider MAX_PARTICLES = new Setting.Slider("Max particle count", 10, 2000, 500);
    public static final Setting.Switch BLOCK_ITEMS = new Setting.Switch("Block invalid potions", true);
    public static final Setting.Switch NOTIFY = new Setting.Switch("Notify when blocked", true);

    public static final Map<String, List<Section>> TABS = new LinkedHashMap<>();

    // One key name per module, empty means unbound. Shown in the Binds tab.
    public static final Map<Module, Setting.TextField> BINDS = new LinkedHashMap<>();

    // Called by CA.java during initialization
    public static void init() {
        TABS.clear();
        BINDS.clear();
        TABS.put("Misc", new ArrayList<>(List.of(
                new Section("Exploit prevention", List.of(TRANSLATION_KEY, FINGERPRINTING, LOCAL_HTTP)),
                new Section("Packet protection", List.of(BLOCK_LOG4J, BLOCK_DEMO, BLOCK_EXPLOSIONS, MAX_KNOCKBACK,
                        BLOCK_PARTICLES, MAX_PARTICLES, BLOCK_ITEMS, NOTIFY)),
                new Section("Notifications", List.of(NOTIFICATION_ALPHA, NOTIFICATION_X, NOTIFICATION_Y))
        )));

        // Dynamically load modules from ModuleManager
        for (Module m : ModuleManager.INSTANCE.getModules()) {
            TABS.computeIfAbsent(m.category, k -> new ArrayList<>())
                    .add(new Section(m.name, m.settings));
            BINDS.put(m, new Setting.TextField(m.name, ""));
        }

        TABS.put("Binds", List.of(new Section("Keybinds (e.g. F, G, r, f6, right.shift)", new ArrayList<>(BINDS.values()))));
    }

    static Map<String, Setting<?>> all() {
        var m = new LinkedHashMap<String, Setting<?>>();
        TABS.forEach((t, secs) -> secs.forEach(sec -> sec.settings().forEach(
                s -> m.put(t + "." + sec.title() + "." + s.name, s))));
        return m;
    }

    static void applyExploits() {
        var api = ExploitPreventer.getAPI();
        if (api == null) return;
        var m = api.getModuleManager();
        m.getModule(Modules.TRANSLATION_KEY).setEnabled(TRANSLATION_KEY.value);
        m.getModule(Modules.FINGERPRINTING).setEnabled(FINGERPRINTING.value);
        m.getModule(Modules.LOCAL_HTTP_REQUEST).setEnabled(LOCAL_HTTP.value);
    }
}