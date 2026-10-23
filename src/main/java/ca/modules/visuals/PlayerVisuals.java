package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import ca.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PlayerVisuals extends Module {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static PlayerVisuals instance;
    private static RenderType boxType;

    private final Setting.Switch enabled = new Setting.Switch("Enabled", false);

    private final Setting.Switch esp = new Setting.Switch("ESP box", true);
    private final Setting.Color espColor = new Setting.Color("ESP color", 0xFFFFFFFF);
    private final Setting.Switch showItem = new Setting.Switch("Held item", true);
    private final Setting.Switch showDistance = new Setting.Switch("Distance", true);
    private final Setting.Switch armor = new Setting.Switch("Armor icons", true);
    private final Setting.Switch handIcons = new Setting.Switch("Hand icons", true);
    private final Setting.Switch hideNames = new Setting.Switch("Hide names", false);
    private final Setting.Dropdown espAttackable = new Setting.Dropdown("ESP attackable", "Indicator", "Off", "Indicator", "Invert");

    private final Setting.Switch box3d = new Setting.Switch("3D box", true);
    private final Setting.Color box3dColor = new Setting.Color("3D box color", 0xFFE5155E);
    private final Setting.Switch box3dFill = new Setting.Switch("3D fill", false);
    private final Setting.Slider box3dFillAlpha = new Setting.Slider("3D fill alpha", 0, 255, 60);
    private final Setting.Dropdown box3dAttackable = new Setting.Dropdown("3D box attackable", "Invert", "Off", "Indicator", "Invert");

    private final List<Box3D> boxes = new ArrayList<>();

    public PlayerVisuals() {
        super("Player Visuals", "Visuals");
        instance = this;
        settings.add(enabled);
        settings.add(esp);
        settings.add(espColor);
        settings.add(showItem);
        settings.add(showDistance);
        settings.add(armor);
        settings.add(handIcons);
        settings.add(hideNames);
        settings.add(espAttackable);
        settings.add(box3d);
        settings.add(box3dColor);
        settings.add(box3dFill);
        settings.add(box3dFillAlpha);
        settings.add(box3dAttackable);
        LevelRenderEvents.COLLECT_SUBMITS.register(this::renderBoxes);
    }

    /** Called from the renderer while the player model state is built. */
    public static void remember(LivingEntity entity, LivingEntityRenderState state) {
        if (instance == null || !instance.enabled.value || !(entity instanceof Player)) return;
        if (instance.hideNames.value) state.nameTag = null;
    }

    /** Lines render type for the 3D box, without depth testing so it shows through walls. */
    private static RenderType boxType() {
        if (boxType == null) {
            RenderPipeline pipeline = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation("pipeline/ca_box_lines")
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .withDepthStencilState(Optional.empty())
                    .build();
            boxType = RenderType.create("ca_box_lines", RenderSetup.builder(pipeline).createRenderSetup());
        }
        return boxType;
    }

    @Override
    public void onExtract(LevelExtractionContext context) {
        boxes.clear();
        if (!enabled.value || !box3d.value) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = context.deltaTracker().getGameTimeDeltaPartialTick(true);
        for (Player p : context.level().players()) {
            if (p == mc.player || p.isRemoved() || p.isDeadOrDying()) continue;
            boolean attackable = p.isAttackable() && !mc.player.isAlliedTo(p);
            boxes.add(new Box3D(
                    Mth.lerp(pt, p.xOld, p.getX()),
                    Mth.lerp(pt, p.yOld, p.getY()),
                    Mth.lerp(pt, p.zOld, p.getZ()),
                    p.getBbWidth() / 2f,
                    p.getBbHeight(),
                    paint(box3dColor.value, attackable, box3dAttackable.value)));
        }
    }

    private void renderBoxes(LevelRenderContext context) {
        if (boxes.isEmpty()) return;
        PoseStack pose = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var ordered = context.submitNodeCollector().order(0);
        for (Box3D box : boxes) {
            ordered.submitShapeOutline(pose,
                    Shapes.create(new AABB(box.x - box.hw, box.y, box.z - box.hw, box.x + box.hw, box.y + box.h, box.z + box.hw)),
                    boxType(),
                    box.color,
                    2f,
                    false);
        }
        pose.popPose();
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        if (!enabled.value || !box3d.value || !box3dFill.value || boxes.isEmpty()) return;
        int a = box3dFillAlpha.value.intValue() << 24;
        List<RenderUtils.Box> out = new ArrayList<>();
        for (Box3D b : boxes) {
            out.add(new RenderUtils.Box(
                    new AABB(b.x - b.hw, b.y, b.z - b.hw, b.x + b.hw, b.y + b.h, b.z + b.hw),
                    a | (b.color & 0xFFFFFF)));
        }
        RenderUtils.drawBoxes(context, out);
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g) {
        if (!enabled.value) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gui.screen() != null) return;
        boolean info = showItem.value || showDistance.value || armor.value || handIcons.value;
        if (!esp.value && !info) return;

        for (Player p : mc.level.players()) {
            if (p == mc.player || p.isRemoved() || p.isDeadOrDying()) continue;
            Box2D box = project(mc, p);
            if (box == null) continue;
            boolean attackable = p.isAttackable() && !mc.player.isAlliedTo(p);
            int color = paint(espColor.value, attackable, espAttackable.value);
            if (esp.value) {
                outline(g, box, color);
                if (attackable && espAttackable.value.equals("Indicator")) corners(g, box, 0xFFFFFFFF);
            }
            if (info) label(g, mc, p, box, color, attackable && espAttackable.value.equals("Indicator"));
        }
    }

    /** Small card above the box, with an icon row above it. Shrinks with the box so far players do not get huge cards. */
    private void label(GuiGraphicsExtractor g, Minecraft mc, Player p, Box2D box, int accent, boolean mark) {
        List<Part> parts = new ArrayList<>();
        if (mark) parts.add(new Part("!", 0xFFFFFFFF));
        if (showItem.value && !p.getMainHandItem().isEmpty()) {
            parts.add(new Part(p.getMainHandItem().getHoverName().getString(), 0xFFFFFFFF));
        }
        if (showDistance.value) parts.add(new Part(Mth.floor(mc.player.distanceTo(p)) + "m", 0xFFAAAAAA));

        List<ItemStack> icons = new ArrayList<>();
        if (handIcons.value) {
            icons.add(p.getMainHandItem());
            icons.add(p.getOffhandItem());
        }
        if (armor.value) for (EquipmentSlot s : ARMOR) icons.add(p.getItemBySlot(s));
        icons.removeIf(ItemStack::isEmpty);
        if (parts.isEmpty() && icons.isEmpty()) return;

        int gap = 6, padX = 4, padY = 2;
        int h = mc.font.lineHeight + padY * 2;
        float scale = Math.clamp((box.y2 - box.y1) / 90f, 0.45f, 1f);

        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((box.x1 + box.x2) / 2f, box.y1 - 3f);
        pose.scale(scale, scale);

        if (!parts.isEmpty()) {
            int textW = gap * (parts.size() - 1);
            for (Part part : parts) textW += mc.font.width(part.text);
            int w = textW + padX * 2;
            int x = -w / 2, y = -h;
            g.fill(x, y, x + w, y + h, 0xB0141418);
            g.fill(x, y, x + w, y + 1, accent);
            int tx = x + padX;
            for (Part part : parts) {
                g.text(mc.font, part.text, tx, y + padY, part.color, false);
                tx += mc.font.width(part.text) + gap;
            }
        }

        int ix = -icons.size() * 9;
        int iy = parts.isEmpty() ? -16 : -h - 18;
        for (ItemStack s : icons) {
            g.item(s, ix, iy);
            ix += 18;
        }
        pose.popMatrix();
    }

    private static int paint(int argb, boolean attackable, String mode) {
        if (!attackable || !mode.equals("Invert")) return argb;
        return (argb & 0xFF000000) | (~argb & 0x00FFFFFF);
    }

    private static void outline(GuiGraphicsExtractor g, Box2D b, int color) {
        g.fill(b.x1, b.y1, b.x2, b.y1 + 1, color);
        g.fill(b.x1, b.y2 - 1, b.x2, b.y2, color);
        g.fill(b.x1, b.y1, b.x1 + 1, b.y2, color);
        g.fill(b.x2 - 1, b.y1, b.x2, b.y2, color);
    }

    private static void corners(GuiGraphicsExtractor g, Box2D b, int color) {
        int w = Math.max(4, (b.x2 - b.x1) / 5);
        int h = Math.max(4, (b.y2 - b.y1) / 6);
        g.fill(b.x1, b.y1, b.x1 + w, b.y1 + 1, color);
        g.fill(b.x1, b.y1, b.x1 + 1, b.y1 + h, color);
        g.fill(b.x2 - w, b.y1, b.x2, b.y1 + 1, color);
        g.fill(b.x2 - 1, b.y1, b.x2, b.y1 + h, color);
        g.fill(b.x1, b.y2 - 1, b.x1 + w, b.y2, color);
        g.fill(b.x1, b.y2 - h, b.x1 + 1, b.y2, color);
        g.fill(b.x2 - w, b.y2 - 1, b.x2, b.y2, color);
        g.fill(b.x2 - 1, b.y2 - h, b.x2, b.y2, color);
    }

    private static Box2D project(Minecraft mc, Player p) {
        Camera cam = mc.gameRenderer.mainCamera();
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double x = Mth.lerp(pt, p.xOld, p.getX());
        double y = Mth.lerp(pt, p.yOld, p.getY());
        double z = Mth.lerp(pt, p.zOld, p.getZ());
        float hw = p.getBbWidth() / 2f;
        float h = p.getBbHeight();
        Vec3 camPos = cam.position();
        Quaternionf rot = new Quaternionf(cam.rotation()).conjugate();
        float fov = cam.getFov();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        float f = (float) (sh / 2.0 / Math.tan(Math.toRadians(fov) / 2.0));
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        boolean any = false;
        for (int i = 0; i < 8; i++) {
            Vector3f v = new Vector3f(
                    (float) (x + ((i & 1) == 0 ? -hw : hw) - camPos.x),
                    (float) (y + ((i & 2) == 0 ? 0 : h) - camPos.y),
                    (float) (z + ((i & 4) == 0 ? -hw : hw) - camPos.z)).rotate(rot);
            if (v.z >= 0) continue;
            int sx = (int) (sw / 2f + v.x * f / -v.z);
            int sy = (int) (sh / 2f - v.y * f / -v.z);
            minX = Math.min(minX, sx);
            minY = Math.min(minY, sy);
            maxX = Math.max(maxX, sx);
            maxY = Math.max(maxY, sy);
            any = true;
        }
        if (!any || maxX < 0 || maxY < 0 || minX > sw || minY > sh) return null;
        return new Box2D(minX, minY, maxX, maxY);
    }

    private record Box2D(int x1, int y1, int x2, int y2) {}
    private record Box3D(double x, double y, double z, float hw, float h, int color) {}
    private record Part(String text, int color) {}
}