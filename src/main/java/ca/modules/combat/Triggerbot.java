package ca.modules.combat;

import ca.Setting;
import ca.modules.Module;
import ca.modules.player.AutoTool;
import ca.util.MouseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import java.util.concurrent.ThreadLocalRandom;

public class Triggerbot extends Module {

    private static final double REACH_SQ = 2.9 * 2.9;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Slider cps = new Setting.Slider("CPS", 1, 20, 10);
    public final Setting.Slider jitter = new Setting.Slider("Jitter (%)", 0, 60, 30);

    public final Setting.Slider confirmTicks = new Setting.Slider("Confirm Ticks", 0, 5, 0);

    private int seen;
    private long lastAttack;
    private long delay;
    private Player lastTarget;

    public Triggerbot() {
        super("Triggerbot", "Combat");
        this.enabled = true;
        settings.add(toggle);
        settings.add(cps);
        settings.add(jitter);
        settings.add(confirmTicks);
    }

    @Override
    public void onTick() {
        if (!toggle.value) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || !mc.mouseHandler.isMouseGrabbed()) return;

        // Only attack with the crosshair really on the player, never off-target
        if (!(mc.hitResult instanceof EntityHitResult hit && hit.getEntity() instanceof Player p)
                || mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > REACH_SQ) {
            seen = 0;
            return;
        }
        seen = p == lastTarget && seen > 0 ? seen + 1 : 1;
        lastTarget = p;
        if (seen <= confirmTicks.value) return;

        AutoTool.equipWeapon();

        // After the equip: swapping items resets the cooldown. Only a charged hit applies the sprint slowdown.
        if (mc.player.getAttackStrengthScale(0.5f) < 0.9f) return;

        long now = System.nanoTime();
        if (now - lastAttack < delay) return;
        lastAttack = now;
        // New random interval after every hit: gaussian around 1/cps, at least 40% of it
        double f = 1 + ThreadLocalRandom.current().nextGaussian() * jitter.value / 200;
        delay = (long) (1_000_000_000L / cps.value * Math.max(0.4, f));

        MouseUtil.clickLeft();
    }
}