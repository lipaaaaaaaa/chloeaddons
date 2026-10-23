package ca.modules.combat;

import ca.Setting;
import ca.modules.Module;
import net.minecraft.client.Minecraft;

import java.util.concurrent.ThreadLocalRandom;

public class JumpReset extends Module {

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Slider chance = new Setting.Slider("Chance (%)", 0, 100, 100);

    private int lastHurt;
    private boolean pressed;

    public JumpReset() {
        super("Jump Reset", "Combat");
        this.enabled = true;
        settings.add(toggle);
        settings.add(chance);
    }

    @Override
    public void onTick() {
        if (!toggle.value) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Release the key one tick after pressing it
        if (pressed) release(mc);

        // A new hit is when hurtTime jumps up. Jump on that same tick, before the movement packet goes out.
        int hurt = mc.player.hurtTime;
        boolean hit = hurt > lastHurt;
        lastHurt = hurt;

        if (!hit || !mc.player.onGround() || !mc.mouseHandler.isMouseGrabbed()) return;
        if (ThreadLocalRandom.current().nextDouble() * 100 >= chance.value) return;

        mc.options.keyJump.setDown(true);
        pressed = true;
    }

    @Override
    public void onDisable() {
        release(Minecraft.getInstance());
    }

    private void release(Minecraft mc) {
        mc.options.keyJump.setDown(false);
        pressed = false;
    }
}
