package ca.modules.combat;

import ca.Setting;
import ca.modules.Module;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/** Shared rotation logic for Aim Lock and Aim Assist. Subclasses only decide who is a valid target. */
public abstract class AimModule extends Module {

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Slider range = new Setting.Slider("Range", 1, 12, 4);
    public final Setting.Dropdown aimAt = new Setting.Dropdown("Aim At", "Auto", "Auto", "Head", "Body");
    public final Setting.Switch visibleOnly = new Setting.Switch("Visible Only", true);
    public final Setting.Slider duration = new Setting.Slider("Rotation Time (ms)", 50, 1000, 200);
    public final Setting.Slider retryDelay = new Setting.Slider("Retry Delay (ms)", 0, 500, 120);

    private Player target;
    private long start;
    private double prev, accYaw, accPitch;
    // True once the crosshair has been on the target, so we stop aiming until it drifts off again
    private boolean settled;
    private long lostAt;
    // Re-rolled for every aim so no two rotations look the same
    private double dur, retry;
    private Vec3 offset = Vec3.ZERO;

    protected AimModule(String name) {
        super(name, "Combat");
        this.enabled = true;
        settings.add(toggle);
        settings.add(range);
        settings.add(aimAt);
        settings.add(visibleOnly);
        settings.add(duration);
        settings.add(retryDelay);
    }

    /** Lower is better. Return NaN if the player is not a candidate. */
    protected abstract double score(Minecraft mc, Player p, Vec3 point);

    // Partial tick of the current frame, so positions are interpolated instead of stepping every tick
    protected float pt;

    protected double angleTo(Minecraft mc, Vec3 point) {
        Vec3 look = mc.player.getViewVector(pt);
        Vec3 dir = point.subtract(mc.player.getEyePosition(pt)).normalize();
        return Math.toDegrees(Math.acos(Math.clamp(look.dot(dir), -1.0, 1.0)));
    }

    /** Whether an already locked target should be kept. Stops the lock flipping between players. */
    protected boolean keep(Minecraft mc, Player p) { return true; }

    protected boolean active() { return toggle.value; }

    protected Vec3 head(Player p) { return p.getEyePosition(pt); }
    protected Vec3 body(Player p) { return p.getPosition(pt).add(0, p.getBbHeight() / 2, 0); }
    protected Vec3 legs(Player p) { return p.getPosition(pt).add(0, p.getBbHeight() * 0.2, 0); }

    /** Airborne and falling, so the next hit is a crit. */
    private static boolean critting(Minecraft mc) {
        return !mc.player.onGround() && mc.player.getDeltaMovement().y < 0;
    }

    /** Auto: aim low while critting, otherwise whichever part is closest to the crosshair. */
    private Vec3 auto(Minecraft mc, Player p) {
        if (critting(mc)) return legs(p);
        Vec3 best = head(p);
        double a = angleTo(mc, best);
        for (Vec3 v : new Vec3[] {body(p), legs(p)}) {
            double b = angleTo(mc, v);
            if (b < a) {
                a = b;
                best = v;
            }
        }
        return best;
    }

    private Vec3 point(Minecraft mc, Player p) {
        Vec3 base = switch (aimAt.value) {
            case "Head" -> head(p);
            case "Body" -> body(p);
            default -> auto(mc, p);
        };
        return base.add(offset);
    }

    private void restart(long now) {
        var r = ThreadLocalRandom.current();
        start = now;
        prev = 0;
        accYaw = accPitch = 0;
        dur = duration.value * (0.8 + r.nextDouble() * 0.45);
        retry = retryDelay.value * (0.7 + r.nextDouble() * 0.6);
        // Humans never hit the exact centre: small random miss inside the hitbox
        offset = new Vec3(
                Math.clamp(r.nextGaussian() * 0.08, -0.2, 0.2),
                Math.clamp(r.nextGaussian() * 0.1, -0.2, 0.2),
                Math.clamp(r.nextGaussian() * 0.08, -0.2, 0.2));
    }

    private boolean eligible(Minecraft mc, Player p) {
        return p != mc.player && p.isAlive() && !p.isSpectator()
                && mc.player.distanceTo(p) <= range.value
                && (!visibleOnly.value || mc.player.hasLineOfSight(p));
    }

    /** Whether the crosshair ray currently passes through the target's hitbox. */
    private boolean onTarget(Minecraft mc, Player p) {
        Vec3 eye = mc.player.getEyePosition(pt);
        Vec3 end = eye.add(mc.player.getViewVector(pt).scale(range.value));
        return p.getBoundingBox().clip(eye, end).isPresent();
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        if (!active()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.mouseHandler.isMouseGrabbed()) return;

        pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        Player best = null;
        if (target != null && eligible(mc, target) && keep(mc, target)) {
            best = target;
        } else {
            double bestScore = Double.MAX_VALUE;
            for (Player p : mc.level.players()) {
                if (!eligible(mc, p)) continue;
                double s = score(mc, p, point(mc, p));
                if (!Double.isNaN(s) && s < bestScore) {
                    bestScore = s;
                    best = p;
                }
            }
        }

        long now = System.nanoTime();
        if (best == null) {
            target = null;
            return;
        }
        if (best != target) {
            target = best;
            settled = false;
            lostAt = 0;
            restart(now);
        }

        // Already on the target: stop aiming. When it drifts off, wait a short reaction time, then retry.
        if (onTarget(mc, target)) {
            settled = true;
            lostAt = 0;
            return;
        }
        if (settled) {
            if (lostAt == 0) lostAt = now;
            if (now - lostAt < retry * 1_000_000L) return;
            settled = false;
            lostAt = 0;
            restart(now);
        }

        // Eased progress (slow start, fast middle, slow end like a real flick). The step is a share of
        // whatever angle is still left, so a moving target is followed and the rotation ends on it.
        double t = Math.clamp((now - start) / (dur * 1_000_000.0), 0.0, 1.0);
        double e = t * t * (3 - 2 * t);
        double f = prev >= 1 ? 1 : (e - prev) / (1 - prev);
        prev = e;
        if (f <= 0) return;

        Vec3 d = point(mc, target).subtract(mc.player.getEyePosition(pt));
        double dyaw = wrap(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0 - mc.player.getYRot());
        double dpitch = -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))) - mc.player.getXRot();

        // Turn in whole mouse counts, the same units real mouse movement uses
        double k = Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3) * 8.0;
        double degPerCount = k * 0.15;
        accYaw += dyaw * f / degPerCount;
        accPitch += dpitch * f / degPerCount;
        int cx = (int) accYaw, cy = (int) accPitch;
        accYaw -= cx;
        accPitch -= cy;
        if (cx != 0 || cy != 0) mc.player.turn(cx * k, cy * k);
    }

    private static double wrap(double deg) {
        return ((deg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

}