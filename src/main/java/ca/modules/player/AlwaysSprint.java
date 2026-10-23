package ca.modules.player;

import ca.Setting;
import ca.modules.Module;
import net.minecraft.client.Minecraft;

public class AlwaysSprint extends Module {
    public AlwaysSprint() {
        super("Always Sprint", "Player");
        settings.add(new Setting.Switch("Enabled", false));
    }

    @Override
    public void onTick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.options.keySprint.setDown(mc.options.keyUp.isDown());
    }
}