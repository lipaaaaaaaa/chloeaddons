package ca.modules.combat;

import ca.Rotation;
import ca.Setting;
import ca.modules.Module;
import ca.modules.player.AutoTool;
import ca.util.MouseUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

public class KillAura extends Module {

    private static final double MOUSE_UNIT = 0.216;

    public final Setting.Switch toggle =
            new Setting.Switch("Enabled", false);

    public final Setting.Dropdown rotation =
            new Setting.Dropdown("Rotation", "Camera", "Silent", "Camera");

    public final Setting.Switch moveFix =
            new Setting.Switch("Move fix (silent)", false);

    public final Setting.Switch showServerRotation =
            new Setting.Switch("Show outgoing rotation in F5", false);

    public final Setting.Switch blockMouse =
            new Setting.Switch("Block attack/use clicks (silent)", false);

    public final Setting.Dropdown hitSound =
            new Setting.Dropdown("Hit sound", "Confirmed", "Off", "Sent", "Confirmed");

    public final Setting.Slider hitVolume =
            new Setting.Slider("Hit sound volume", 0.1, 1, 1);

    public final Setting.Slider hitPitch =
            new Setting.Slider("Hit sound pitch", 0.5, 2, 1.5);

    public final Setting.Slider aimRange =
            new Setting.Slider("Aim range", 1, 8, 4);

    public final Setting.Slider attackRange =
            new Setting.Slider("Attack range", 1, 6, 2.9);

    public final Setting.Slider fov =
            new Setting.Slider("FOV", 10, 360, 360);

    public final Setting.Dropdown mode =
            new Setting.Dropdown("Target mode", "Single", "Single", "Switch");

    public final Setting.Dropdown priority =
            new Setting.Dropdown("Priority", "Distance", "Distance", "Health", "Angle");

    public final Setting.Dropdown aimAt =
            new Setting.Dropdown("Aim at", "Closest", "Closest", "Head", "Body");

    public final Setting.Switch players =
            new Setting.Switch("Players", true);

    public final Setting.Switch mobs =
            new Setting.Switch("Hostile mobs", false);

    public final Setting.Switch allies =
            new Setting.Switch("Ignore teammates", true);

    public final Setting.Switch invisible =
            new Setting.Switch("Ignore invisible", true);

    public final Setting.Switch visibleOnly =
            new Setting.Switch("Visible only", true);

    public final Setting.Switch holdClick =
            new Setting.Switch("Only while holding attack", false);

    public final Setting.Switch cooldown =
            new Setting.Switch("Attack cooldown", true);

    public final Setting.Slider cps =
            new Setting.Slider("CPS", 1, 20, 10);

    public final Setting.Slider jitter =
            new Setting.Slider("Jitter (%)", 0, 60, 30);

    public final Setting.Slider reactionTicks =
            new Setting.Slider("Reaction (ticks)", 0, 10, 2);

    public final Setting.Slider confirmTicks =
            new Setting.Slider("Confirm ticks", 0, 5, 0);

    public final Setting.Slider speed =
            new Setting.Slider("Rotation speed (deg/tick)", 10, 180, 60);

    public final Setting.Slider smooth =
            new Setting.Slider("Rotation smoothing", 0.1, 1, 0.6);

    public final Setting.Switch autoWeapon =
            new Setting.Switch("Auto weapon", true);

    private record Pending(
            LivingEntity target,
            boolean silent,
            boolean click,
            boolean cooldown,
            float yaw,
            float pitch
    ) {
        Pending(
                LivingEntity target,
                boolean silent,
                boolean click,
                float yaw,
                float pitch
        ) {
            this(target, silent, click, true, yaw, pitch);
        }
    }

    private LivingEntity target;
    private LivingEntity lastTarget;

    // Hit confirmation: the target we just attacked and its hurtTime at that moment
    private LivingEntity watch;
    private int watchTicks;
    private int watchHurt;
    private Pending pending;

    private boolean have;

    private double curYaw;
    private double curPitch;

    private double accYaw;
    private double accPitch;

    private int seen;
    private int reaction;
    private int targetLostTicks;

    private double clicks;
    private double blend;

    private Vec3 offset = Vec3.ZERO;

    public KillAura() {
        super("Kill Aura", "Combat");

        this.enabled = true;

        settings.add(toggle);
        settings.add(rotation);
        settings.add(moveFix);
        settings.add(showServerRotation);
        settings.add(blockMouse);
        settings.add(hitSound);
        settings.add(hitVolume);
        settings.add(hitPitch);

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
        // The render mixin reads this shared flag.
        Rotation.showPacketRotation = showServerRotation.value;

        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;

        if (p == null
                || mc.level == null
                || mc.gameMode == null
                || !p.isAlive()
                || p.isSpectator()) {

            pending = null;
            target = null;
            watch = null;
            reset();
            return;
        }

        checkHit();

        // Flush an attack queued during the previous tick.
        flushPending(mc);

        boolean silent = rotation.value.equals("Silent");

        target = mc.mouseHandler.isMouseGrabbed()
                ? pickTarget(mc)
                : null;

        if (target != lastTarget) {
            lastTarget = target;
            seen = 0;
            targetLostTicks = 0;
            reaction = target == null ? 0 : rollReaction();

            if (target != null) {
                reroll();
            }
        }

        if (target == null) {
            seen = 0;

            if (have && ++targetLostTicks > 3) {
                if (Math.hypot(
                        wrap(p.getYRot() - curYaw),
                        p.getXRot() - curPitch
                ) < MOUSE_UNIT * 1.5) {
                    reset();
                } else {
                    step(
                            mc,
                            true,
                            p.getYRot(),
                            p.getXRot()
                    );
                }
            }

            dropClicks(mc);

            return;
        }

        targetLostTicks = 0;

        Vec3 eye = p.getEyePosition();
        Vec3 d = aimPoint(eye, target).subtract(eye);

        double targetYaw =
                Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0;

        double targetPitch =
                -Math.toDegrees(Math.atan2(
                        d.y,
                        Math.hypot(d.x, d.z)
                ));

        step(mc, silent, targetYaw, targetPitch);

        dropClicks(mc);

        attack(mc, silent);
    }

    private LivingEntity pickTarget(Minecraft mc) {
        var p = mc.player;

        if (mode.value.equals("Single")
                && target != null
                && valid(mc, target)) {
            return target;
        }

        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.level.getEntities(
                p,
                p.getBoundingBox().inflate(aimRange.value + 1),
                x -> x instanceof LivingEntity
        )) {
            LivingEntity t = (LivingEntity) e;

            if (!valid(mc, t)) {
                continue;
            }

            double score = switch (priority.value) {
                case "Health" -> t.getHealth();
                case "Angle" -> angle(
                        p,
                        t.getBoundingBox().getCenter()
                );
                default -> p.getEyePosition()
                        .distanceTo(t.getBoundingBox().getCenter());
            };

            if (t == target) {
                score *= 0.85;
            }

            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }

        return best;
    }

    private boolean valid(Minecraft mc, LivingEntity t) {
        var p = mc.player;

        if (t == p || !t.isAlive() || t.isSpectator()) {
            return false;
        }

        if (!((players.value && t instanceof Player)
                || (mobs.value && t instanceof Enemy))) {
            return false;
        }

        if (invisible.value && t.isInvisible()) {
            return false;
        }

        if (allies.value && p.isAlliedTo(t)) {
            return false;
        }

        Vec3 eye = p.getEyePosition();
        Vec3 near = closest(t.getBoundingBox(), eye);

        if (eye.distanceTo(near) > aimRange.value) {
            return false;
        }

        if (visibleOnly.value) {
            HitResult blockHit = mc.level.clip(
                    new ClipContext(
                            eye,
                            near,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            p
                    )
            );

            if (blockHit.getType() != HitResult.Type.MISS) {
                return false;
            }
        }

        return angle(p, near) <= fov.value / 2.0;
    }

    private static double angle(Player p, Vec3 point) {
        Vec3 look = p.getViewVector(1f);
        Vec3 dir = point.subtract(p.getEyePosition()).normalize();

        return Math.toDegrees(Math.acos(
                Mth.clamp(look.dot(dir), -1.0, 1.0)
        ));
    }

    private static Vec3 closest(AABB box, Vec3 point) {
        return new Vec3(
                Mth.clamp(point.x, box.minX, box.maxX),
                Mth.clamp(point.y, box.minY, box.maxY),
                Mth.clamp(point.z, box.minZ, box.maxZ)
        );
    }

    private Vec3 aimPoint(Vec3 eye, LivingEntity t) {
        AABB box = t.getBoundingBox();
        Vec3 center = box.getCenter();

        Vec3 base;

        switch (aimAt.value) {
            case "Head" -> base = t.getEyePosition();
            case "Body" -> base = center;
            default -> {
                Vec3 near = closest(box, eye);
                base = near.add(
                        center.subtract(near).scale(blend)
                );
            }
        }

        Vec3 point = base.add(offset);

        return new Vec3(
                Mth.clamp(point.x, box.minX + 0.02, box.maxX - 0.02),
                Mth.clamp(point.y, box.minY + 0.02, box.maxY - 0.02),
                Mth.clamp(point.z, box.minZ + 0.02, box.maxZ - 0.02)
        );
    }

    private boolean hits(
            Minecraft mc,
            LivingEntity t,
            float yaw,
            float pitch
    ) {
        var p = mc.player;

        Vec3 eye = p.getEyePosition();

        Vec3 end = eye.add(
                Vec3.directionFromRotation(pitch, yaw)
                        .scale(attackRange.value)
        );

        var entityHit = t.getBoundingBox().clip(eye, end);

        if (entityHit.isEmpty()) {
            return false;
        }

        var blockHit = mc.level.clip(
                new ClipContext(
                        eye,
                        end,
                        ClipContext.Block.OUTLINE,
                        ClipContext.Fluid.NONE,
                        p
                )
        );

        return blockHit.getType() == HitResult.Type.MISS
                || eye.distanceToSqr(blockHit.getLocation())
                > eye.distanceToSqr(entityHit.get());
    }

    private void attack(Minecraft mc, boolean silent) {
        var p = mc.player;
        var random = ThreadLocalRandom.current();

        clicks = Math.min(
                1.0,
                clicks + cps.value / 20.0
                        * Math.max(
                        0.2,
                        1.0 + random.nextGaussian()
                                * jitter.value / 100.0
                )
        );

        if (pending != null || (silent && !have)) {
            return;
        }

        if (p.isUsingItem() || mc.options.keyUse.isDown()) {
            return;
        }

        if (holdClick.value && !mc.options.keyAttack.isDown()) {
            return;
        }

        float yaw = silent ? Rotation.yaw : p.getYRot();
        float pitch = silent ? Rotation.pitch : p.getXRot();

        if (!hits(mc, target, yaw, pitch)) {
            seen = 0;
            return;
        }

        seen++;

        if (seen <= confirmTicks.value) {
            return;
        }

        if (reaction > 0) {
            reaction--;
            return;
        }

        boolean click = !silent;

        if (click
                && !(mc.hitResult instanceof EntityHitResult er
                && er.getEntity() == target)) {
            return;
        }

        if (cooldown.value
                && p.getAttackStrengthScale(0.5f) < 0.9f) {
            return;
        }

        if (clicks < 1.0 - 1e-9) {
            return;
        }

        clicks -= 1.0;

        pending = new Pending(
                target,
                silent,
                click,
                cooldown.value,
                yaw,
                pitch
        );
    }

    /**
     * While a silent rotation is active, vanilla clicks act on the camera, not on the
     * rotation the server sees. Drop queued attack/use clicks so they cannot go out
     * with a mismatched rotation. The key's held state is left alone, so
     * "Only while holding attack" still works.
     */
    private void dropClicks(Minecraft mc) {
        if (!blockMouse.value || !Rotation.spoof) {
            return;
        }

        while (mc.options.keyAttack.consumeClick()) {
            // discard
        }

        while (mc.options.keyUse.consumeClick()) {
            // discard
        }
    }

    private void flushPending(Minecraft mc) {
        if (pending == null) {
            return;
        }

        var p = mc.player;
        var q = pending;

        pending = null;

        if (p == null
                || mc.gameMode == null
                || !p.isAlive()) {
            return;
        }

        if (p.isUsingItem() || mc.options.keyUse.isDown()) {
            return;
        }

        LivingEntity t = q.target();

        if (!t.isAlive()
                || t.distanceToSqr(p)
                > attackRange.value * attackRange.value + 4.0) {
            return;
        }

        if (q.silent()) {
            if (!Rotation.packetRotationKnown
                    || !hits(
                    mc,
                    t,
                    Rotation.yawSent,
                    Rotation.pitchSent
            )) {
                return;
            }
        } else if (q.click()) {
            if (!(mc.hitResult instanceof EntityHitResult er
                    && er.getEntity() == t)) {
                return;
            }
        }

        if (q.cooldown()
                && p.getAttackStrengthScale(0.5f) < 0.9f) {
            return;
        }

        if (autoWeapon.value) {
            AutoTool.equipWeapon();
        }

        if (q.click()) {
            MouseUtil.clickLeft();
        } else {
            mc.gameMode.attack(p, t);

            var hand = p.getItemInHand(
                    InteractionHand.MAIN_HAND
            );

            p.swing(
                    InteractionHand.MAIN_HAND,
                    hand.getAttackAnimation(),
                    false
            );

            mc.getConnection().send(
                    ServerboundPunchPacket.INSTANCE
            );
        }

        onAttackSent(t);

        reroll();
    }

    /**
     * "Sent" plays right away. "Confirmed" waits for the target's hurtTime to jump,
     * which only happens when the server actually registered the damage, so no
     * sound means the hit did not go through.
     */
    private void onAttackSent(LivingEntity t) {
        if (hitSound.value.equals("Sent")) {
            playHit();
        } else if (hitSound.value.equals("Confirmed")) {
            watch = t;
            watchTicks = 8;
            watchHurt = t.hurtTime;
        }
    }

    private void checkHit() {
        if (watch == null) {
            return;
        }

        int hurt = watch.hurtTime;

        if (hurt > watchHurt) {
            watch = null;
            playHit();
            return;
        }

        watchHurt = hurt;

        if (--watchTicks <= 0) {
            watch = null;
        }
    }

    private void playHit() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(
                        SoundEvents.ARROW_HIT_PLAYER,
                        hitPitch.value.floatValue(),
                        hitVolume.value.floatValue()
                )
        );
    }

    private int rollReaction() {
        return (int) Math.round(
                reactionTicks.value
                        * (0.5 + ThreadLocalRandom.current().nextDouble())
        );
    }

    private void step(
            Minecraft mc,
            boolean silent,
            double targetYaw,
            double targetPitch
    ) {
        var p = mc.player;

        if (!silent || !have) {
            curYaw = p.getYRot();
            curPitch = p.getXRot();

            if (silent && !have) {
                accYaw = 0;
                accPitch = 0;
            }
        }

        double dy = wrap(targetYaw - curYaw);
        double dp = targetPitch - curPitch;

        double total = Math.hypot(dy, dp);

        double max = speed.value
                * (0.8 + ThreadLocalRandom.current().nextDouble() * 0.2);

        double k = Math.min(
                1.0,
                max / Math.max(total, 1e-6)
        ) * smooth.value;

        accYaw += dy * k / MOUSE_UNIT;
        accPitch += dp * k / MOUSE_UNIT;

        int cx = (int) accYaw;
        int cy = (int) accPitch;

        accYaw -= cx;
        accPitch -= cy;

        if (silent) {
            if (cx != 0 || cy != 0) {
                curYaw += cx * MOUSE_UNIT;

                curPitch = Mth.clamp(
                        curPitch + cy * MOUSE_UNIT,
                        -90.0,
                        90.0
                );
            }

            Rotation.spoof = true;
            Rotation.moveFix = moveFix.value;

            Rotation.yaw = (float) curYaw;
            Rotation.pitch = (float) curPitch;

            have = true;
        } else {
            if (cx != 0 || cy != 0) {
                p.turn(
                        cx * unitPerCount(mc),
                        cy * unitPerCount(mc)
                );
            }

            Rotation.spoof = false;
            Rotation.moveFix = false;

            have = false;
        }
    }

    private static double unitPerCount(Minecraft mc) {
        return Math.pow(
                mc.options.sensitivity().get() * 0.6 + 0.2,
                3
        ) * 8.0;
    }

    private void reroll() {
        var random = ThreadLocalRandom.current();

        blend = 0.2 + random.nextDouble() * 0.3;

        offset = new Vec3(
                Mth.clamp(
                        random.nextGaussian() * 0.05,
                        -0.12,
                        0.12
                ),
                Mth.clamp(
                        random.nextGaussian() * 0.07,
                        -0.15,
                        0.15
                ),
                Mth.clamp(
                        random.nextGaussian() * 0.05,
                        -0.12,
                        0.12
                )
        );
    }

    private void reset() {
        Rotation.spoof = false;
        Rotation.moveFix = false;

        have = false;

        accYaw = 0;
        accPitch = 0;

        lastTarget = null;
        reaction = 0;
        targetLostTicks = 0;
    }

    @Override
    public void onDisable() {
        target = null;
        pending = null;
        seen = 0;

        Rotation.showPacketRotation = false;

        reset();
    }

    private static double wrap(double deg) {
        return ((deg + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }
}