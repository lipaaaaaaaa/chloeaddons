package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;

import java.lang.reflect.Field;

public class Fullbright extends Module {

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);

    private Double saved;

    public Fullbright() {
        super("Fullbright", "Visuals");
        this.enabled = true;
        settings.add(toggle);
    }

    @Override
    public void onExtract(LevelExtractionContext context) {
        OptionInstance<Double> gamma = Minecraft.getInstance().options.gamma();
        if (toggle.value) {
            if (saved == null) saved = gamma.get();
            set(gamma, 1000.0);
        } else if (saved != null) {
            set(gamma, saved);
            saved = null;
        }
    }

    // OptionInstance range-checks in the UI only, the backing field takes any value
    private static void set(OptionInstance<Double> opt, double v) {
        try {
            Field f = OptionInstance.class.getDeclaredField("value");
            f.setAccessible(true);
            f.set(opt, v);
        } catch (ReflectiveOperationException ignored) {}
    }
}