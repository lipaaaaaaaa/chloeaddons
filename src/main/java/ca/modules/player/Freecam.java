package ca.modules.player;

import ca.Setting;
import ca.modules.Module;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public class Freecam extends Module {
    public static boolean active;
    public static double x, y, z;
    public static float yaw, pitch;
    private static final Setting.Slider SPEED = new Setting.Slider("Speed", 1, 50, 11);
    private static final boolean[] saved = new boolean[10];
    private static long last;

    public Freecam() {
        super("Freecam", "Player");
        settings.add(new Setting.Switch("Enabled", false));
        settings.add(SPEED);
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            if (!active) return;
            var k = keys(mc.options);
            for (int i = 0; i < k.length; i++) k[i].setDown(saved[i]);
        });
    }

    private static KeyMapping[] keys(Options o) {
        return new KeyMapping[] {o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint, o.keyAttack, o.keyUse, o.keyPickItem};
    }

    public static void step() {
        var o = Minecraft.getInstance().options;
        long now = System.nanoTime();
        double dt = Math.min((now - last) / 1e9, 0.1);
        last = now;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double len = Math.hypot(f, s);
        double v = SPEED.value * (o.keySprint.isDown() ? 2 : 1) * dt;
        double sin = Math.sin(Math.toRadians(yaw)), cos = Math.cos(Math.toRadians(yaw));
        if (len > 0) {
            x += (-sin * f + cos * s) / len * v;
            z += (cos * f + sin * s) / len * v;
        }
        y += ((o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0)) * v;
    }

    @Override
    public void onTick() {
        var mc = Minecraft.getInstance();
        if (mc.player == null) {
            if (active) onDisable();
            return;
        }
        if (!active) {
            var eye = mc.player.getEyePosition();
            x = eye.x;
            y = eye.y;
            z = eye.z;
            yaw = mc.player.getYRot();
            pitch = mc.player.getXRot();
            last = System.nanoTime();
            mc.smartCull = false;
            active = true;
        }
        var k = keys(mc.options);
        for (int i = 0; i < k.length; i++) {
            saved[i] = k[i].isDown();
            k[i].setDown(false);
            while (k[i].consumeClick()) {}
        }
    }

    @Override
    public void onDisable() {
        active = false;
        Minecraft.getInstance().smartCull = true;
        KeyMapping.setAll();
    }
}