package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import ca.util.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;

public class CustomBlockOutline extends Module {

    public final Setting.Switch enabled = new Setting.Switch("Enabled", true);
    public final Setting.Dropdown mode = new Setting.Dropdown("Mode", "Both", "Outline", "Fill", "Both");
    public final Setting.Color color = new Setting.Color("Color", 0xFFFF0000);
    public final Setting.Slider alpha = new Setting.Slider("Alpha", 0f, 1f, 0.6f);
    public final Setting.Switch depthCheck = new Setting.Switch("Depth Check", true);
    public final Setting.Switch hideVanilla = new Setting.Switch("Hide Vanilla Outline", true);

    public CustomBlockOutline() {
        super("Custom Block Outline", "Visuals");
        super.enabled = true;
        settings.add(enabled);
        settings.add(mode);
        settings.add(color);
        settings.add(alpha);
        settings.add(depthCheck);
        settings.add(hideVanilla);
    }

    public boolean hidesVanilla() {
        return super.enabled && enabled.value && hideVanilla.value;
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        if (!enabled.value) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();

        int argb = ((int) (alpha.value * 255) << 24) | (color.value & 0xFFFFFF);

        boolean drawOutline = mode.value.equals("Outline") || mode.value.equals("Both");
        boolean drawFill = mode.value.equals("Fill") || mode.value.equals("Both");

        RenderUtils.drawShape(context, mc.level.getBlockState(pos).getShape(mc.level, pos, CollisionContext.of(mc.player)),
                pos, argb, drawOutline, drawFill, depthCheck.value);
    }
}