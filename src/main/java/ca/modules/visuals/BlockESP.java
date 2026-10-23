package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import ca.util.RenderUtils;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BlockESP extends Module {

    private static final int MAX_BOXES = 10000;
    private static final long SCAN_INTERVAL_NS = 250_000_000L;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", true);
    public final Setting.BlockList blocks = new Setting.BlockList("Blocks",
            "minecraft:chest", "minecraft:ender_chest", "minecraft:spawner");
    public final Setting.Color color = new Setting.Color("Color", 0xFFFF0000);
    public final Setting.Slider alpha = new Setting.Slider("Alpha", 0, 1, 0.4);

    // Cached data for rendering
    private final List<RenderUtils.Box> blocksToRender = new ArrayList<>();
    private long lastScan;

    public BlockESP() {
        super("BlockESP", "Visuals");
        this.enabled = true;
        settings.add(toggle);
        settings.add(blocks);
        settings.add(color);
        settings.add(alpha);
    }

    @Override
    public void onExtract(LevelExtractionContext context) {
        if (!toggle.value) return;

        long now = System.nanoTime();
        if (now - lastScan < SCAN_INTERVAL_NS) return;
        lastScan = now;
        blocksToRender.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Set<Block> wanted = new HashSet<>();
        for (String id : blocks.value) {
            Identifier rl = Identifier.tryParse(id);
            if (rl != null) BuiltInRegistries.BLOCK.getOptional(rl).ifPresent(wanted::add);
        }
        if (wanted.isEmpty()) return;

        int argb = ((int) (alpha.value * 255) << 24) | (color.value & 0xFFFFFF);
        int chunkRadius = 4; // 64 blocks
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;

        for (int cx = pcx - chunkRadius; cx <= pcx + chunkRadius; cx++) {
            for (int cz = pcz - chunkRadius; cz <= pcz + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section.hasOnlyAir() || !section.maybeHas(st -> wanted.contains(st.getBlock()))) continue;

                    int bx = cx << 4, by = (chunk.getMinSectionY() + i) << 4, bz = cz << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                if (!wanted.contains(section.getBlockState(x, y, z).getBlock())) continue;
                                int wx = bx + x, wy = by + y, wz = bz + z;
                                blocksToRender.add(new RenderUtils.Box(
                                        new AABB(wx, wy, wz, wx + 1, wy + 1, wz + 1).inflate(0.002), argb));
                                if (blocksToRender.size() >= MAX_BOXES) return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onRender3D(LevelRenderContext context) {
        if (!enabled || !toggle.value || blocksToRender.isEmpty()) return;
        RenderUtils.drawBoxes(context, blocksToRender);
    }
}