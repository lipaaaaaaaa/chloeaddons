package ca;

import ca.modules.Module;
import ca.modules.ModuleManager;
import ca.modules.player.Scaffold;
import ca.modules.visuals.PerformanceHud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** /ca hud: drag the Scaffold block pill and the Performance HUD, close the screen to save their positions. */
public class HudEditScreen extends Screen {
    /** True while the screen is open, so the real HUD does not draw a second copy under it. */
    public static boolean open;

    private final Scaffold scaffold = find(Scaffold.class);
    private final PerformanceHud perf = find(PerformanceHud.class);
    private int[] scaffoldBox, perfBox;
    // 0 = nothing, 1 = scaffold pill, 2 = performance HUD
    private int dragging;
    private int gx, gy;

    public HudEditScreen() { super(Component.literal("HUD")); }

    private static <T extends Module> T find(Class<T> type) {
        for (var m : ModuleManager.INSTANCE.getModules()) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    @Override
    protected void init() { open = true; }

    @Override
    public void removed() {
        open = false;
        Config.save();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.fill(0, 0, width, height, 0x66000000);
        String hint = "Drag the pills, press Esc to save";
        g.text(font, hint, (width - font.width(hint)) / 2, height / 2 - 4, 0xFFFFFFFF, true);
        if (scaffold != null) scaffoldBox = scaffold.drawPill(g, scaffold.previewStack());
        if (perf != null) perfBox = perf.draw(g);
    }

    private static boolean in(int[] box, MouseButtonEvent e) {
        return box != null && e.x() >= box[0] && e.x() < box[0] + box[2] && e.y() >= box[1] && e.y() < box[1] + box[3];
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        int[] box = null;
        if (in(perfBox, e)) {
            dragging = 2;
            box = perfBox;
        } else if (in(scaffoldBox, e)) {
            dragging = 1;
            box = scaffoldBox;
        }
        if (box != null) {
            gx = (int) e.x() - box[0];
            gy = (int) e.y() - box[1];
            return true;
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragging != 0) {
            int[] box = dragging == 1 ? scaffoldBox : perfBox;
            Setting.Slider sx = dragging == 1 ? scaffold.hudX : perf.hudX;
            Setting.Slider sy = dragging == 1 ? scaffold.hudY : perf.hudY;
            if (box != null) {
                sx.value = Math.round(Math.clamp((e.x() - gx) / (width - box[2]), 0, 1) * 10000) / 100.0;
                sy.value = Math.round(Math.clamp((e.y() - gy) / (height - box[3]), 0, 1) * 10000) / 100.0;
                return true;
            }
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragging = 0;
        return super.mouseReleased(e);
    }
}