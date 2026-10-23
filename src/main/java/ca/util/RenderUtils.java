package ca.util;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class RenderUtils {

    public record Box(AABB box, int color) {}

    private static final RenderPipeline FILLED_THROUGH_WALLS = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ca", "pipeline/block_esp"))
                    .withDepthStencilState(Optional.empty())
                    .build()
    );

    private static final RenderPipeline FILLED_DEPTH_TESTED = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("ca", "pipeline/block_fill_depth"))
                    .build()
    );

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f();
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final StagedVertexBuffer STAGED = new StagedVertexBuffer(() -> "CA BlockESP", RenderType.SMALL_BUFFER_SIZE);

    // Cube as a single triangle strip, corners as 0/1 offsets (x, y, z)
    private static final int[][] STRIP = {
            {0, 1, 1}, {1, 1, 1}, {0, 0, 1}, {1, 0, 1}, {1, 0, 0}, {1, 1, 1}, {1, 1, 0},
            {0, 1, 1}, {0, 1, 0}, {0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {0, 1, 0}, {1, 1, 0}
    };

    public static void drawBoxes(LevelRenderContext context, List<Box> boxes) {
        if (!RenderSystem.isOnRenderThread()) return;

        var format = FILLED_THROUGH_WALLS.getVertexFormatBinding(0);
        if (format == null) return;

        PrimitiveTopology topology = FILLED_THROUGH_WALLS.getPrimitiveTopology();
        boolean quads = topology == PrimitiveTopology.QUADS;
        StagedVertexBuffer.Draw draw = STAGED.appendDraw(format, topology,
                quads ? RenderSystem.getProjectionType().vertexSorting() : null);

        PoseStack matrices = context.poseStack();
        var camera = context.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4fc matrix = matrices.last().pose();
        VertexConsumer buffer = STAGED.getVertexBuilder(draw);

        for (Box b : boxes) {
            int color = b.color();
            float a = ((color >> 24) & 0xFF) / 255f;
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float bl = (color & 0xFF) / 255f;

            if (quads) renderFilledBox(buffer, matrix, b.box(), r, g, bl, a);
            else renderStripBox(buffer, matrix, b.box(), r, g, bl, a);
        }

        matrices.popPose();

        STAGED.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED.getExecuteInfo(draw);
        if (info != null) execute(info, FILLED_THROUGH_WALLS);
        STAGED.endFrame();
    }

    public static void drawBlockOutlineAndFill(LevelRenderContext context, AABB box, int color,
                                               boolean drawOutline, boolean drawFill, boolean depthCheck) {
        if (!RenderSystem.isOnRenderThread()) return;

        RenderPipeline pipeline = depthCheck ? FILLED_DEPTH_TESTED : FILLED_THROUGH_WALLS;

        if (drawFill) drawFilledBoxWithPipeline(context, List.of(box), color, pipeline);
        if (drawOutline) drawFilledBoxWithPipeline(context, edges(box), color, pipeline);
    }

    public static void drawShape(LevelRenderContext context, VoxelShape shape, BlockPos pos, int color,
                                 boolean drawOutline, boolean drawFill, boolean depthCheck) {
        if (!RenderSystem.isOnRenderThread() || shape.isEmpty()) return;

        RenderPipeline pipeline = depthCheck ? FILLED_DEPTH_TESTED : FILLED_THROUGH_WALLS;

        if (drawFill) drawFilledBoxWithPipeline(context,
                shape.toAabbs().stream().map(b -> b.move(pos).inflate(0.002)).toList(), color, pipeline);
        if (drawOutline) {
            List<AABB> out = new java.util.ArrayList<>();
            shape.forAllEdges((x1, y1, z1, x2, y2, z2) ->
                    out.add(new AABB(x1, y1, z1, x2, y2, z2).move(pos).inflate(0.01)));
            drawFilledBoxWithPipeline(context, out, color, pipeline);
        }
    }

    // Outline as 12 thin boxes, so it reuses the filled pipelines
    private static List<AABB> edges(AABB b) {
        double t = 0.01;
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        List<AABB> out = new java.util.ArrayList<>(12);
        for (double y : ys) for (double z : zs) out.add(new AABB(xs[0], y, z, xs[1], y, z).inflate(t));
        for (double x : xs) for (double z : zs) out.add(new AABB(x, ys[0], z, x, ys[1], z).inflate(t));
        for (double x : xs) for (double y : ys) out.add(new AABB(x, y, zs[0], x, y, zs[1]).inflate(t));
        return out;
    }

    private static void drawFilledBoxWithPipeline(LevelRenderContext context, List<AABB> boxes, int color, RenderPipeline pipeline) {
        var format = pipeline.getVertexFormatBinding(0);
        if (format == null) return;

        PrimitiveTopology topology = pipeline.getPrimitiveTopology();
        boolean quads = topology == PrimitiveTopology.QUADS;
        StagedVertexBuffer.Draw draw = STAGED.appendDraw(format, topology,
                quads ? RenderSystem.getProjectionType().vertexSorting() : null);

        PoseStack matrices = context.poseStack();
        var camera = context.levelState().cameraRenderState.pos;

        matrices.pushPose();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        Matrix4fc matrix = matrices.last().pose();
        VertexConsumer buffer = STAGED.getVertexBuilder(draw);

        float a = ((color >> 24) & 0xFF) / 255f;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float bl = (color & 0xFF) / 255f;

        for (AABB box : boxes) {
            if (quads) renderFilledBox(buffer, matrix, box, r, g, bl, a);
            else renderStripBox(buffer, matrix, box, r, g, bl, a);
        }

        matrices.popPose();

        STAGED.upload();
        StagedVertexBuffer.ExecuteInfo info = STAGED.getExecuteInfo(draw);
        if (info != null) execute(info, pipeline);
        STAGED.endFrame();
    }

    @SuppressWarnings("resource")
    private static void execute(StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        var transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var colorTexture = target.getColorTextureView();
        if (colorTexture == null) return;

        try (var pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "CA Custom Render", colorTexture, Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {

            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);

            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    private static void renderStripBox(VertexConsumer buffer, Matrix4fc m, AABB box,
                                       float r, float g, float b, float a) {
        float[] min = {(float) box.minX, (float) box.minY, (float) box.minZ};
        float[] max = {(float) box.maxX, (float) box.maxY, (float) box.maxZ};

        for (int i = -1; i <= STRIP.length; i++) {
            int[] c = STRIP[Math.clamp(i, 0, STRIP.length - 1)];
            buffer.addVertex(m,
                    c[0] == 1 ? max[0] : min[0],
                    c[1] == 1 ? max[1] : min[1],
                    c[2] == 1 ? max[2] : min[2]).setColor(r, g, b, a);
        }
    }

    private static void renderFilledBox(VertexConsumer buffer, Matrix4fc m, AABB box,
                                        float r, float g, float b, float a) {
        float x1 = (float) box.minX, y1 = (float) box.minY, z1 = (float) box.minZ;
        float x2 = (float) box.maxX, y2 = (float) box.maxY, z2 = (float) box.maxZ;

        buffer.addVertex(m, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y2, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z1).setColor(r, g, b, a);
        buffer.addVertex(m, x2, y1, z2).setColor(r, g, b, a);
        buffer.addVertex(m, x1, y1, z2).setColor(r, g, b, a);
    }
}