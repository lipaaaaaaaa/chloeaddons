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
        var p = mc.player;
        if (p == null || p.isSprinting() || !mc.options.keyUp.isDown() || p.isShiftKeyDown() || p.isUsingItem() || p.horizontalCollision) return;
        if (p.getFoodData().getFoodLevel() > 6 || p.getAbilities().mayfly) p.setSprinting(true);
    }
}