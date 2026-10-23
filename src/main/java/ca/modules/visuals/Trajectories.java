package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import ca.util.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows where the projectile in your hand will go: snowball, egg, ender pearl, experience bottle, splash and
 * lingering potions, a drawn bow, a charged crossbow and a trident being drawn. The path is simulated with the
 * vanilla launch speed, gravity and drag, and drawn as small dots.
 */
public class Trajectories extends Module {
    private static final int MAX_TICKS = 200;

    private final Setting.Switch enabled = new Setting.Switch("Enabled", false);
    private final Setting.Color lineColor = new Setting.Color("Line color", 0xFFFFFFFF);
    private final Setting.Switch landing = new Setting.Switch("Landing marker", true);
    private final Setting.Color landColor = new Setting.Color("Landing color", 0xFF55FF55);
    private final Setting.Color hitColor = new Setting.Color("Entity hit color", 0xFFFF5555);

    private final List<RenderUtils.Box> boxes = new ArrayList<>();

    /** Launch speed, gravity per tick, extra pitch used when throwing and the drag in water. */
    private record Shot(double power, double gravity, float pitchOffset, double waterDrag) {}

    public Trajectories() {
        super("Trajectories", "Visuals");
        settings.add(enabled);
        settings.add(lineColor);
        settings.add(landing);
        settings.add(landColor);
        settings.add(hitColor);
    }

    private static Shot shotFor(Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        boolean using = p.isUsingItem() && p.getUsedItemHand() == hand;
        if (s.is(Items.SNOWBALL) || s.is(Items.EGG) || s.is(Items.ENDER_PEARL)) return new Shot(1.5, 0.03, 0, 0.8);
        if (s.is(Items.EXPERIENCE_BOTTLE)) return new Shot(0.7, 0.07, -20, 0.8);
        if (s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION)) return new Shot(0.5, 0.05, -20, 0.8);
        if (s.is(Items.TRIDENT) && using) return new Shot(2.5, 0.05, 0, 0.99);
        if (s.is(Items.CROSSBOW) && CrossbowItem.isCharged(s)) return new Shot(3.15, 0.05, 0, 0.6);
        if (s.is(Items.BOW) && using) {
            float f = p.getTicksUsingItem() / 20f;
            f = (f * f + f * 2f) / 3f;
            if (f < 0.1f) return null;
            return new Shot(Math.min(f, 1f) * 3.0, 0.05, 0, 0.6);
        }
        return null;
    }

    @Override
    public void onExtract(LevelExtractionContext context) {
        boxes.clear();
        Minecraft mc = Minecraft.getInstance();
        var p = mc.player;
        var level = mc.level;
        if (!enabled.value || p == null || level == null || p.isSpectator()) return;

        Shot shot = shotFor(p, InteractionHand.MAIN_HAND);
        if (shot == null) shot = shotFor(p, InteractionHand.OFF_HAND);
        if (shot == null) return;

        float pt = context.deltaTracker().getGameTimeDeltaPartialTick(true);
        double xRot = p.getViewXRot(pt), yRot = p.getViewYRot(pt);
        double xr = Math.toRadians(xRot), yr = Math.toRadians(yRot);
        Vec3 m = p.getDeltaMovement();
        Vec3 v = new Vec3(-Math.sin(yr) * Math.cos(xr),
                -Math.sin(Math.toRadians(xRot + shot.pitchOffset())),
                Math.cos(yr) * Math.cos(xr)).normalize().scale(shot.power())
                .add(m.x, p.onGround() ? 0 : m.y, m.z);
        Vec3 pos = new Vec3(Mth.lerp(pt, p.xOld, p.getX()),
                Mth.lerp(pt, p.yOld, p.getY()) + p.getEyeHeight() - 0.1,
                Mth.lerp(pt, p.zOld, p.getZ()));

        Vec3 end = null;
        boolean hitEntity = false;
        for (int t = 0; t < MAX_TICKS; t++) {
            Vec3 next = pos.add(v);
            BlockHitResult bh = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            boolean hitBlock = bh.getType() != HitResult.Type.MISS;
            Vec3 to = hitBlock ? bh.getLocation() : next;

            // The first entity on the way, if it is closer than the block
            Entity hit = null;
            double best = pos.distanceToSqr(to);
            for (Entity e : level.getEntities(p, new AABB(pos, to).inflate(1.0), x -> !x.isSpectator() && x.isPickable())) {
                var c = e.getBoundingBox().inflate(0.3).clip(pos, to);
                if (c.isPresent() && pos.distanceToSqr(c.get()) < best) {
                    best = pos.distanceToSqr(c.get());
                    hit = e;
                    to = c.get();
                }
            }

            dots(pos, to);
            if (hit != null || hitBlock) {
                end = to;
                hitEntity = hit != null;
                break;
            }
            pos = next;
            boolean water = !level.getFluidState(BlockPos.containing(pos)).isEmpty();
            v = v.scale(water ? shot.waterDrag() : 0.99).add(0, -shot.gravity(), 0);
        }

        if (landing.value && end != null) {
            double r = 0.15;
            int c = hitEntity ? hitColor.value : landColor.value;
            boxes.add(new RenderUtils.Box(new AABB(end.x - r, end.y - r, end.z - r, end.x + r, end.y + r, end.z + r), c));
        }
    }

    /** Small dots along one step of the path, about every quarter block. */
    private void dots(Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        int n = Math.max(1, (int) (d.length() / 0.25));
        double r = 0.03;
        for (int i = 0; i < n; i++) {
            Vec3 q = a.add(d.scale((double) i / n));
            boxes.add(new RenderUtils.Box(new AABB(q.x - r, q.y - r, q.z - r, q.x + r, q.y + r, q.z + r), lineColor.value));
        }
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        if (enabled.value && !boxes.isEmpty()) RenderUtils.drawBoxes(context, boxes);
    }
}