package ca.modules.player;

import ca.HudEditScreen;
import ca.Rotation;
import ca.Setting;
import ca.modules.Module;
import ca.util.MouseUtil;
import ca.util.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Bridging helper. You hold the movement keys. Every tick it picks the block it needs to place (straight or
 * diagonal staircase), aims at the face it will click, sneaks at the edge (safewalk), keeps you on the middle of
 * the block (centering) and places only when the aim really hits that face.
 *
 * Camera mode: only the pitch moves, yaw stays yours.
 * Silent rotate: the camera is never touched, only the pitch written into Rotation changes
 * (SendRotationSyncMixin + RotationPacketMixin put it on the wire together with the last Rot
 * packet we actually sent). The yaw stays ours, so the server predicts the same movement.
 * The block is placed at the START of the next tick, before that tick's movement packet and with the rotation
 * the server already got, like a real click.
 */
public class Scaffold extends Module {

    /** Last rotation we handed to the packet pipeline (what the server has on file). */
    private float rotYaw, rotPitch;
    private boolean rotHave;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Switch silent = new Setting.Switch("Silent rotate", false);
    public final Setting.Switch swap = new Setting.Switch("Auto swap", true);
    public final Setting.Switch safewalk = new Setting.Switch("Safewalk (sneak at edge)", true);
    public final Setting.Switch center = new Setting.Switch("Block centering", true);
    public final Setting.Slider cps = new Setting.Slider("CPS", 1, 20, 9);
    public final Setting.Slider wave = new Setting.Slider("CPS wave", 0, 8, 3);
    public final Setting.Slider wavePeriod = new Setting.Slider("CPS wave period (s)", 0.5, 10, 2.5);
    public final Setting.Slider clickNoise = new Setting.Slider("Click noise (%)", 0, 40, 12);
    public final Setting.Slider pitchJitter = new Setting.Slider("Pitch jitter", 0, 4, 1.2);
    public final Setting.Slider rotSpeed = new Setting.Slider("Rotate speed", 2, 30, 10);
    public final Setting.Slider edgeDist = new Setting.Slider("Edge distance", 0.1, 0.8, 0.3);
    public final Setting.Switch showHud = new Setting.Switch("Show block HUD", true);
    public final Setting.Switch showNext = new Setting.Switch("Show next block", true);
    // Pill position in percent of the free screen space, moved with /ca hud
    public final Setting.Slider hudX = new Setting.Slider("HUD X", 0, 100, 50);
    public final Setting.Slider hudY = new Setting.Slider("HUD Y", 0, 100, 80);

    /** The block to fill, the block it is placed against and the face that gets clicked. */
    private record Target(BlockPos support, Direction face, BlockPos place) {}

    // True only while our own silent placement runs, so the manual block below lets it through
    private static boolean placing;

    private boolean active, sneaking, haveSent;
    private Target target;
    private int levelY = Integer.MIN_VALUE;
    private KeyMapping centerKey;
    private double dx, dz;
    private long lastPlace, delay, lastFrame, lastEdge;
    private final double phase = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
    // Re-rolled after every placement so no two placements share the same rotation
    private double pitchRoll;
    private double accPitch;
    private float camPitch;
    // Silent rotation: offset from the camera, and the rotation that goes out in this tick's packet
    private double offPitch;
    private float sentYaw, sentPitch;

    public Scaffold() {
        super("Scaffold", "Player");
        this.enabled = true;
        settings.add(toggle);
        settings.add(silent);
        settings.add(swap);
        settings.add(safewalk);
        settings.add(center);
        settings.add(cps);
        settings.add(wave);
        settings.add(wavePeriod);
        settings.add(clickNoise);
        settings.add(pitchJitter);
        settings.add(rotSpeed);
        settings.add(edgeDist);
        settings.add(showHud);
        settings.add(showNext);
        settings.add(hudX);
        settings.add(hudY);
        // Manual placements use the camera rotation while the server has the spoofed one, so block them.
        // This event also fires on the integrated server, so only the client side may be touched.
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                level.isClientSide() && Rotation.spoof && !placing ? InteractionResult.FAIL : InteractionResult.PASS);
    }

    @Override
    public void onTick() {
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        clearCenter();
        if (p == null || mc.level == null || !mc.mouseHandler.isMouseGrabbed()) {
            active = false;
            target = null;
            levelY = Integer.MIN_VALUE;
            reset();
            release(mc);
            return;
        }
        if (p.onGround()) levelY = Mth.floor(p.getY() - 0.5);

        // Movement direction from the keys, so it still works while sneaking holds the speed at zero
        var o = mc.options;
        double f = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double s = (o.keyLeft.isDown() ? 1 : 0) - (o.keyRight.isDown() ? 1 : 0);
        double yaw = Math.toRadians(p.getYRot());
        dx = f * -Math.sin(yaw) + s * Math.cos(yaw);
        dz = f * Math.cos(yaw) + s * Math.sin(yaw);
        double len = Math.hypot(dx, dz);

        // Nothing under the spot we are about to step on. Faster means we look further ahead.
        var v = p.getDeltaMovement();
        double look = edgeDist.value + Math.hypot(v.x, v.z);
        AABB b = p.getBoundingBox();
        boolean gap = len > 0 && mc.level.noCollision(p,
                new AABB(b.minX, b.minY - 0.5, b.minZ, b.maxX, b.minY, b.maxZ)
                        .move(dx / len * look, 0, dz / len * look));
        long now = System.nanoTime();
        if (gap) lastEdge = now;

        if (gap && swap.value && !(p.getMainHandItem().getItem() instanceof BlockItem)) {
            int best = -1, count = 0;
            for (int i = 0; i < 9; i++) {
                var st = p.getInventory().getItem(i);
                if (st.getItem() instanceof BlockItem && st.getCount() > count) {
                    best = i;
                    count = st.getCount();
                }
            }
            if (best >= 0) p.getInventory().setSelectedSlot(best);
        }
        target = gap && p.getMainHandItem().getItem() instanceof BlockItem
                ? find(mc, dx / len, dz / len, look) : null;
        active = target != null;

        // Safewalk: sneak only for the ticks where the next step would leave the block
        if (safewalk.value && gap && p.onGround()) {
            o.keyShift.setDown(true);
            sneaking = true;
            // Sprint must not carry into a sneak tick (AlwaysSprint reads last tick's sneak state)
            p.setSprinting(false);
        } else {
            release(mc);
        }

        // Centering: tap strafe keys (inputs the server can reproduce) to stay on the middle of the block
        if (center.value && len > 0 && s == 0 && p.onGround() && now - lastEdge < 1_000_000_000L) {
            double ex = 0, ez = 0;
            if (Math.abs(dx) > 0.92 * len) ez = Mth.floor(p.getZ()) + 0.5 - p.getZ();
            else if (Math.abs(dz) > 0.92 * len) ex = Mth.floor(p.getX()) + 0.5 - p.getX();
            double side = ex * Math.cos(yaw) + ez * Math.sin(yaw);
            if (side > 0.08) centerKey = o.keyLeft;
            else if (side < -0.08) centerKey = o.keyRight;
            if (centerKey != null) centerKey.setDown(true);
        }

        if (active) camPitch = camAim(mc, target);
        if (silent.value && active && haveSent && now - lastPlace >= delay) placeSilent(mc, now);
        updateSilent(mc);

        // Camera mode: let the game do a real click. Silent mode places in placeSilent.
        if (!silent.value && active && now - lastPlace >= delay
                && mc.hitResult instanceof BlockHitResult h && match(h, target)) {
            MouseUtil.clickRight();
            scheduleNext(now);
        }
    }

    /**
     * Silent placement. Runs at the start of the tick, before this tick's movement packet, with the rotation the
     * server got in the previous tick's packet. Placing after the movement packet gets flagged as Post by Grim.
     */
    private void placeSilent(Minecraft mc, long now) {
        // The server processes this interaction with the look from the PREVIOUS tick's Rot
        // packet, so the raycast must use exactly that rotation (rotYaw/rotPitch), never
        // the camera and never this tick's not-yet-sent value. Otherwise Grim sees a click
        // outside the view angle it has on file -> groundspoof / Simulation flags.
        if (!rotHave) return;
        BlockHitResult h = clip(mc, rotYaw, rotPitch);
        if (!match(h, target) || mc.gameMode == null) return;
        placing = true;
        try {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, h);
        } finally {
            placing = false;
        }
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
        scheduleNext(now);
    }

    /** CPS wave: the rate drifts up and down slowly, plus per click noise. Also re-rolls the rotation noise. */
    private void scheduleNext(long now) {
        var r = ThreadLocalRandom.current();
        lastPlace = now;
        double c = Math.max(1, cps.value
                + Math.sin(now / 1e9 * 2 * Math.PI / wavePeriod.value + phase) * wave.value);
        delay = (long) (1e9 / c * Math.max(0.5, 1 + r.nextGaussian() * clickNoise.value / 100));
        pitchRoll = Math.clamp(r.nextGaussian() * pitchJitter.value, -4, 4);
    }

    /**
     * The block that has to be filled next. Looks at the 3x3 blocks around the feet at floor level, keeps the ones
     * that are empty and have a solid side to click, and takes the one closest to where we step next. This is what
     * makes diagonals work: it first fills the block beside the corner, then the one after it.
     */
    private Target find(Minecraft mc, double ux, double uz, double look) {
        var p = mc.player;
        var level = mc.level;
        if (levelY == Integer.MIN_VALUE) return null;
        double ax = p.getX() + ux * look, az = p.getZ() + uz * look;
        BlockPos c0 = new BlockPos(Mth.floor(p.getX()), levelY, Mth.floor(p.getZ()));
        Target best = null;
        double bd = 1.5;
        for (int i = -1; i <= 1; i++) {
            for (int k = -1; k <= 1; k++) {
                BlockPos c = c0.offset(i, 0, k);
                double d = Math.pow(c.getX() + 0.5 - ax, 2) + Math.pow(c.getZ() + 0.5 - az, 2);
                if (d >= bd || !level.getBlockState(c).canBeReplaced()) continue;
                // Click the solid side that lies furthest behind us
                Direction side = null;
                double sd = Double.MAX_VALUE;
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos n = c.relative(dir);
                    if (!level.getBlockState(n).isFaceSturdy(level, n, dir.getOpposite())) continue;
                    double dot = dir.getStepX() * ux + dir.getStepZ() * uz;
                    if (dot < sd) {
                        sd = dot;
                        side = dir;
                    }
                }
                if (side == null) continue;
                best = new Target(c.relative(side), side.getOpposite(), c);
                bd = d;
            }
        }
        return best;
    }

    private static Vec3 faceCenter(Target t) {
        var f = t.face();
        return Vec3.atCenterOf(t.support()).add(f.getStepX() * 0.5, f.getStepY() * 0.5, f.getStepZ() * 0.5);
    }

    /** Exact yaw and pitch from the eye to the middle of the face. */
    private static float[] aim(Minecraft mc, Target t) {
        Vec3 d = faceCenter(t).subtract(mc.player.getEyePosition());
        return new float[] {
                (float) Math.toDegrees(Math.atan2(-d.x, d.z)),
                (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)))
        };
    }

    /** Looks along the given rotation without touching the player. */
    private static BlockHitResult clip(Minecraft mc, float yaw, float pitch) {
        var p = mc.player;
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(Vec3.directionFromRotation(pitch, yaw).scale(p.blockInteractionRange()));
        var r = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return r.getType() == HitResult.Type.BLOCK ? r : null;
    }

    private static boolean match(BlockHitResult h, Target t) {
        return h != null && t != null && h.getBlockPos().equals(t.support()) && h.getDirection() == t.face();
    }

    /** Camera mode: with the yaw you have, the pitch whose ray lands closest to the middle of the face. */
    private float camAim(Minecraft mc, Target t) {
        float yaw = mc.player.getYRot();
        Vec3 c = faceCenter(t);
        float best = aim(mc, t)[1];
        double bd = Double.MAX_VALUE;
        for (float pt = 50; pt <= 89.5f; pt += 0.5f) {
            BlockHitResult h = clip(mc, yaw, pt);
            if (!match(h, t)) continue;
            double d = h.getLocation().distanceToSqr(c);
            if (d < bd) {
                bd = d;
                best = pt;
            }
        }
        float noisy = (float) Math.clamp(best + pitchRoll, -90.0, 89.9);
        return bd == Double.MAX_VALUE || match(clip(mc, yaw, noisy), t) ? noisy : best;
    }

    /**
     * Eases the offset between the camera and the rotation sent to the server, in whole
     * mouse counts, then hands it to Rotation. We also remember the last rotation that was
     * really on the wire (rotYaw/rotPitch): placements are validated against THAT, because
     * the interaction packet is processed by the server with the look from the previous tick.
     */
    private void updateSilent(Minecraft mc) {
        var p = mc.player;
        if (!silent.value) {
            reset();
            Rotation.spoof = false;
            return;
        }
        // Pitch only: the yaw stays the camera yaw, so the server predicts the same movement as the client
        double tp = active ? camPitch - p.getXRot() : 0;
        double deg = degPerCount(mc);
        offPitch = Math.round((offPitch + (tp - offPitch) * 0.5) / deg) * deg;
        boolean on = offPitch != 0;
        Rotation.spoof = on;
        if (on) {
            Rotation.yaw = p.getYRot();
            Rotation.pitch = (float) Math.clamp(p.getXRot() + offPitch, -90.0, 90.0);
        }
        rotYaw = on ? Rotation.yaw : p.getYRot();
        rotPitch = on ? Rotation.pitch : p.getXRot();
        rotHave = true;
        haveSent = true;
        sentYaw = rotYaw;
        sentPitch = rotPitch;
    }

    private void reset() {
        offPitch = 0;
        haveSent = false;
        rotHave = false;
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = Math.min((now - lastFrame) / 1e9, 0.1);
        lastFrame = now;

        // Blue box where the next block goes
        if (showNext.value && target != null && mc.player != null) {
            RenderUtils.drawShape(context, Shapes.block(), target.place(), 0x703C78FF, true, true, true);
        }

        if (silent.value || !active || mc.player == null) return;

        // Ease toward the target a bit each frame instead of snapping
        double f = 1 - Math.exp(-dt * rotSpeed.value);
        double deg = degPerCount(mc);
        accPitch += (camPitch - mc.player.getXRot()) * f / deg;
        int cy = (int) accPitch;
        accPitch -= cy;
        if (cy != 0) mc.player.turn(0, cy * deg / 0.15);
    }

    /** The stack the HUD shows: the block in hand, else the stack Auto swap would pick. */
    private ItemStack bridgeStack(Minecraft mc) {
        var p = mc.player;
        if (p == null) return ItemStack.EMPTY;
        var hand = p.getMainHandItem();
        if (hand.getItem() instanceof BlockItem) return hand;
        if (!swap.value) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        for (int i = 0; i < 9; i++) {
            var st = p.getInventory().getItem(i);
            if (st.getItem() instanceof BlockItem && st.getCount() > best.getCount()) best = st;
        }
        return best;
    }

    /** Same as the HUD stack, or a sample block when there is none (used while placing the pill with /ca hud). */
    public ItemStack previewStack() {
        var s = bridgeStack(Minecraft.getInstance());
        return s.isEmpty() ? new ItemStack(Items.GRASS_BLOCK, 64) : s;
    }

    /** Draws the pill (block icon and count) and returns its box: x, y, width, height. */
    public int[] drawPill(GuiGraphicsExtractor g, ItemStack stack) {
        var f = Minecraft.getInstance().font;
        String count = String.valueOf(stack.getCount());
        int h = 22, r = h / 2;
        int w = 8 + 16 + 6 + f.width(count) + 8;
        int x = (int) ((g.guiWidth() - w) * hudX.value / 100);
        int y = (int) ((g.guiHeight() - h) * hudY.value / 100);
        for (int i = 0; i < h; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, 0xB414286E);
        }
        g.item(stack, x + 8, y + 3);
        g.text(f, count, x + 8 + 16 + 6, y + (h - 8) / 2, 0xFFFFFFFF, true);
        return new int[] {x, y, w, h};
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        if (!showHud.value || mc.player == null || HudEditScreen.open) return;
        ItemStack st = bridgeStack(mc);
        if (!st.isEmpty()) drawPill(g, st);
    }

    /** Degrees one mouse count turns the camera at the current sensitivity. */
    private static double degPerCount(Minecraft mc) {
        return Math.pow(mc.options.sensitivity().get() * 0.6 + 0.2, 3) * 8.0 * 0.15;
    }

    @Override
    public void onDisable() {
        active = false;
        target = null;
        reset();
        // onTick stops running once disabled, so nothing else would ever clear a silent rotation
        Rotation.spoof = false;
        release(Minecraft.getInstance());
        clearCenter();
    }

    private void release(Minecraft mc) {
        if (sneaking) mc.options.keyShift.setDown(false);
        sneaking = false;
    }

    private void clearCenter() {
        if (centerKey != null) centerKey.setDown(false);
        centerKey = null;
    }
}