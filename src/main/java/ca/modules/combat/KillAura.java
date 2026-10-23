package ca.modules.combat;

import ca.Setting;
import ca.modules.Module;
import ca.modules.player.AutoTool;
import ca.util.MouseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {

    public static boolean spoof, fix;
    public static float spoofYaw, spoofPitch;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Dropdown rotation = new Setting.Dropdown("Rotation", "Camera", "Silent", "Camera");
    public final Setting.Switch moveFix = new Setting.Switch("Move fix (silent)", false);
    public final Setting.Slider aimRange = new Setting.Slider("Aim range", 1, 8, 4);
    public final Setting.Slider attackRange = new Setting.Slider("Attack range", 1, 6, 2.9);
    public final Setting.Slider fov = new Setting.Slider("FOV", 10, 360, 360);
    public final Setting.Dropdown mode = new Setting.Dropdown("Target mode", "Single", "Single", "Switch");
    public final Setting.Dropdown priority = new Setting.Dropdown("Priority", "Distance", "Distance", "Health", "Angle");
    public final Setting.Dropdown aimAt = new Setting.Dropdown("Aim at", "Closest", "Closest", "Head", "Body");
    public final Setting.Switch players = new Setting.Switch("Players", true);
    public final Setting.Switch mobs = new Setting.Switch("Hostile mobs", false);
    public final Setting.Switch allies = new Setting.Switch("Ignore teammates", true);
    public final Setting.Switch invisible = new Setting.Switch("Ignore invisible", true);
    public final Setting.Switch visibleOnly = new Setting.Switch("Visible only", true);
    public final Setting.Dropdown attackMethod = new Setting.Dropdown("Attack method", "Packet", "Packet", "Click");
    public final Setting.Switch holdClick = new Setting.Switch("Only while holding attack", false);
    public final Setting.Switch cooldown = new Setting.Switch("Attack cooldown", true);
    public final Setting.Slider cps = new Setting.Slider("CPS", 1, 20, 10);
    public final Setting.Slider jitter = new Setting.Slider("Jitter (%)", 0, 60, 30);
    public final Setting.Slider reactionTicks = new Setting.Slider("Reaction (ticks)", 0, 10, 2);
    public final Setting.Slider confirmTicks = new Setting.Slider("Confirm ticks", 0, 5, 0);
    public final Setting.Slider speed = new Setting.Slider("Rotation speed (deg/tick)", 10, 180, 60);
    public final Setting.Slider smooth = new Setting.Slider("Rotation smoothing", 0.1, 1, 0.6);
    public final Setting.Switch autoWeapon = new Setting.Switch("Auto weapon", true);

    private LivingEntity target, lastTarget;
    private float sentYaw, sentPitch;
    private boolean have;
    private double curYaw, curPitch, accYaw, accPitch;
    private int seen, reaction;
    private double clicks;
    private double blend;
    private Vec3 offset = Vec3.ZERO;

    public KillAura() {
        super("Kill Aura", "Combat");
        this.enabled = true;
        settings.add(toggle);
        settings.add(rotation);
        settings.add(moveFix);
        settings.add(aimRange);
        settings.add(attackRange);
        settings.add(fov);
        settings.add(mode);
        settings.add(priority);
        settings.add(aimAt);
        settings.add(players);
        settings.add(mobs);
        settings.add(allies);
        settings.add(invisible);
        settings.add(visibleOnly);
        settings.add(attackMethod);
        settings.add(holdClick);
        settings.add(cooldown);
        settings.add(cps);
        settings.add(jitter);
        settings.add(reactionTicks);
        settings.add(confirmTicks);
        settings.add(speed);
        settings.add(smooth);
        settings.add(autoWeapon);
        reroll();
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || mc.level == null || mc.gameMode == null || !p.isAlive() || p.isSpectator()) {
            reset();
            return;
        }
        boolean silent = rotation.value.equals("Silent");

        target = mc.mouseHandler.isMouseGrabbed() ? pickTarget(mc) : null;
        if (target != lastTarget) {
            lastTarget = target;
            seen = 0;
            reaction = target == null ? 0 : rollReaction();
        }

        if (target == null) {
            seen = 0;
            if (have) {
                double deg = degPerCount(mc);
                if (Math.hypot(wrap(p.getYRot() - curYaw), p.getXRot() - curPitch) < deg * 1.5) reset();
                else step(mc, true, p.getYRot(), p.getXRot());
            }
            return;
        }

        attack(mc, silent);

        Vec3 eye = p.getEyePosition();
        Vec3 d = aimPoint(eye, target).subtract(eye);
        step(mc, silent,
                Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0,
                -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))));
    }

    private LivingEntity pickTarget(Minecraft mc) {
        var p = mc.player;
        if (mode.value.equals("Single") && target != null && valid(mc, target)) return target;
        LivingEntity best = null;
        double bs = Double.MAX_VALUE;
        for (Entity e : mc.level.getEntities(p, p.getBoundingBox().inflate(aimRange.value + 1), x -> x instanceof LivingEntity)) {
            LivingEntity t = (LivingEntity) e;
            if (!valid(mc, t)) continue;
            double s = switch (priority.value) {
                case "Health" -> t.getHealth();
                case "Angle" -> angle(p, t.getBoundingBox().getCenter());
                default -> p.getEyePosition().distanceTo(t.getBoundingBox().getCenter());
            };
            if (t == target) s *= 0.85;
            if (s < bs) {
                bs = s;
                best = t;
            }
        }
        return best;
    }

    private boolean valid(Minecraft mc, LivingEntity t) {
        var p = mc.player;
        if (t == p || !t.isAlive() || t.isSpectator()) return false;
        if (!((players.value && t instanceof Player) || (mobs.value && t instanceof Enemy))) return false;
        if (invisible.value && t.isInvisible()) return false;
        if (allies.value && p.isAlliedTo(t)) return false;
        Vec3 eye = p.getEyePosition();
        Vec3 near = closest(t.getBoundingBox(), eye);
        if (eye.distanceTo(near) > aimRange.value) return false;
        if (visibleOnly.value && !p.hasLineOfSight(t)) return false;
        return angle(p, near) <= fov.value / 2;
    }

    private static double angle(Player p, Vec3 point) {
        Vec3 look = p.getViewVector(1f);
        Vec3 dir = point.subtract(p.getEyePosition()).normalize();
        return Math.toDegrees(Math.acos(Math.clamp(look.dot(dir), -1.0, 1.0)));
    }

    private static Vec3 closest(AABB b, Vec3 v) {
        return new Vec3(Mth.clamp(v.x, b.minX, b.maxX), Mth.clamp(v.y, b.minY, b.maxY), Mth.clamp(v.z, b.minZ, b.maxZ));
    }

    private Vec3 aimPoint(Vec3 eye, LivingEntity t) {
        AABB b = t.getBoundingBox();
        Vec3 c = b.getCenter();
        Vec3 base;
        switch (aimAt.value) {
            case "Head" -> base = t.getEyePosition();
            case "Body" -> base = c;
            default -> {
                Vec3 near = closest(b, eye);
                base = near.add(c.subtract(near).scale(blend));
            }
        }
        Vec3 q = base.add(offset);
        return new Vec3(Mth.clamp(q.x, b.minX + 0.02, b.maxX - 0.02),
                Mth.clamp(q.y, b.minY + 0.02, b.maxY - 0.02),
                Mth.clamp(q.z, b.minZ + 0.02, b.maxZ - 0.02));
    }

    private boolean hits(Minecraft mc, LivingEntity t, float yaw, float pitch) {
        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(attackRange.value));
        var hit = t.getBoundingBox().clip(eye, end);
        if (hit.isEmpty()) return false;
        var b = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return b.getType() == HitResult.Type.MISS || eye.distanceToSqr(b.getLocation()) > eye.distanceToSqr(hit.get());
    }

    private void attack(Minecraft mc, boolean silent) {
        var p = mc.player;
        var r = ThreadLocalRandom.current();

        clicks = Math.min(1.0, clicks + cps.value / 20.0 * Math.max(0.2, 1 + r.nextGaussian() * jitter.value / 100));

        if (silent && !have) return;
        if (p.isUsingItem()) return;
        if (holdClick.value && !mc.options.keyAttack.isDown()) return;

        float yaw = silent ? sentYaw : p.getYRot();
        float pitch = silent ? sentPitch : p.getXRot();
        if (!hits(mc, target, yaw, pitch)) {
            seen = 0;
            return;
        }
        seen++;
        if (seen <= confirmTicks.value) return;
        if (reaction > 0) {
            reaction--;
            return;
        }
        boolean click = attackMethod.value.equals("Click") && !silent;
        if (click && !(mc.hitResult instanceof EntityHitResult er && er.getEntity() == target)) return;
        if (cooldown.value && p.getAttackStrengthScale(0.5f) < 0.9f) return;
        if (clicks < 1.0 - 1e-9) return;

        clicks -= 1.0;
        if (autoWeapon.value) AutoTool.equipWeapon();

        if (click) {
            MouseUtil.clickLeft();
        } else {
            mc.gameMode.attack(p, target);
            p.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        }
        reroll();
    }

    private int rollReaction() {
        return (int) Math.round(reactionTicks.value * (0.5 + ThreadLocalRandom.current().nextDouble()));
    }

    private void step(Minecraft mc, boolean silent, double ty, double tp) {
        var p = mc.player;
        if (!silent || !have) {
            curYaw = p.getYRot();
            curPitch = p.getXRot();
            if (silent && !have) accYaw = accPitch = 0;
        }
        double dy = wrap(ty - curYaw), dp = tp - curPitch;
        double total = Math.hypot(dy, dp);
        double max = speed.value * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.2);
        double k = Math.min(1.0, max / Math.max(total, 1e-6)) * smooth.value;
        double unit = unitPerCount(mc);
        double deg = degPerCount(mc);
        accYaw += dy * k / deg;
        accPitch += dp * k / deg;
        int cx = (int) accYaw, cy = (int) accPitch;
        accYaw -= cx;
        accPitch -= cy;

        if (silent) {
            curYaw += cx * deg;
            curPitch = Math.clamp(curPitch + cy * deg, -90.0, 90.0);
            spoofYaw = (float) curYaw;
            spoofPitch = (float) curPitch;
            sentYaw = spoofYaw;
            sentPitch = spoofPitch;
            spoof = true;
            have = true;
            fix = moveFix.value && Math.abs(wrap(curYaw - p.getYRot())) > 1e-3;
        } else {
            if (cx != 0 || cy != 0) p.turn(cx * unit, cy * unit);
            spoof = fix = have = false;
        }
    }

    private static double unitPerCount(Minecraft mc) {
        return Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3) * 8.0;
    }

    private static double degPerCount(Minecraft mc) {
        return unitPerCount(mc) * 0.15;
    }

    private void reroll() {
        var r = ThreadLocalRandom.current();
        blend = 0.2 + r.nextDouble() * 0.3;
        offset = new Vec3(Math.clamp(r.nextGaussian() * 0.05, -0.12, 0.12),
                Math.clamp(r.nextGaussian() * 0.07, -0.15, 0.15),
                Math.clamp(r.nextGaussian() * 0.05, -0.12, 0.12));
    }

    private void reset() {
        spoof = fix = have = false;
        accYaw = accPitch = 0;
        lastTarget = null;
        reaction = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        seen = 0;
        reset();
    }

    private static double wrap(double deg) {
        return ((deg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }

    public static Vec3 fixInput(Vec3 in, float camYaw) {
        double c = Math.max(Math.abs(in.x), Math.abs(in.z));
        if (c < 1e-4) return in;
        double d = Math.toRadians(camYaw - spoofYaw);
        double x = in.x * Math.cos(d) - in.z * Math.sin(d);
        double z = in.z * Math.cos(d) + in.x * Math.sin(d);
        long k = Math.round(Math.atan2(z, x) / (Math.PI / 4));
        double a = k * Math.PI / 4;
        double m = k % 2 == 0 ? c : c * Math.sqrt(2);
        double sx = Math.cos(a) * m, sz = Math.sin(a) * m;
        double back = Math.toRadians(spoofYaw - camYaw);
        return new Vec3(sx * Math.cos(back) - sz * Math.sin(back), in.y, sz * Math.cos(back) + sx * Math.sin(back));
    }
}