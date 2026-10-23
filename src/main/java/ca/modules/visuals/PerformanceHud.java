package ca.modules.visuals;

import ca.HudEditScreen;
import ca.Setting;
import ca.modules.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayDeque;
import java.util.Locale;

/** Small pill with FPS, TPS and Ping. Position is set with /ca hud. TPS comes from TpsMixin. */
public class PerformanceHud extends Module {

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);
    public final Setting.Switch showFps = new Setting.Switch("FPS display", true);
    public final Setting.Switch showTps = new Setting.Switch("TPS display", true);
    public final Setting.Switch showPing = new Setting.Switch("Ping display", true);
    // Pill position in percent of the free screen space, moved with /ca hud
    public final Setting.Slider hudX = new Setting.Slider("HUD X", 0, 100, 1);
    public final Setting.Slider hudY = new Setting.Slider("HUD Y", 0, 100, 1);

    // TPS: the server sends the world time about every 20 ticks. Ticks passed / real seconds passed = TPS.
    private static final ArrayDeque<Double> history = new ArrayDeque<>();
    private static long lastNanos, lastGameTime;

    public PerformanceHud() {
        super("Performance HUD", "Visuals");
        settings.add(toggle);
        settings.add(showFps);
        settings.add(showTps);
        settings.add(showPing);
        settings.add(hudX);
        settings.add(hudY);
    }

    /** Called by TpsMixin every time the server sends the world time. */
    public static void onTime(long gameTime) {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            double dt = (now - lastNanos) / 1e9;
            long ticks = gameTime - lastGameTime;
            if (ticks < 0 || dt >= 30) {
                history.clear(); // new world or a long gap, old samples are useless
            } else if (ticks > 0 && dt > 0.05) {
                history.addLast(Math.clamp(ticks / dt, 0, 20));
                if (history.size() > 5) history.removeFirst();
            }
        }
        lastNanos = now;
        lastGameTime = gameTime;
    }

    private static String tps() {
        if (history.isEmpty()) return "--";
        double sum = 0;
        for (double d : history) sum += d;
        return String.format(Locale.ROOT, "%.1f", sum / history.size());
    }

    private static String ping(Minecraft mc) {
        var conn = mc.getConnection();
        var info = conn == null || mc.player == null ? null : conn.getPlayerInfo(mc.player.getUUID());
        return info == null ? "--" : info.getLatency() + "ms";
    }

    private static void add(MutableComponent text, String label, String value) {
        if (!text.getSiblings().isEmpty()) text.append(Component.literal("  "));
        text.append(Component.literal(label).withColor(0xAFC0E8));
        text.append(Component.literal(value).withColor(0xFFFFFF));
    }

    @Override
    public void onTick() {
        if (Minecraft.getInstance().level == null) {
            history.clear();
            lastNanos = 0;
        }
    }

    /** Draws the pill and returns its box: x, y, width, height. Null when every display is off. */
    public int[] draw(GuiGraphicsExtractor g) {
        var mc = Minecraft.getInstance();
        var f = mc.font;
        MutableComponent text = Component.empty();
        if (showFps.value) add(text, "FPS ", String.valueOf(mc.getFps()));
        if (showTps.value) add(text, "TPS ", tps());
        if (showPing.value) add(text, "Ping ", ping(mc));
        if (text.getSiblings().isEmpty()) return null;

        int h = 22, r = h / 2;
        int w = 8 + f.width(text) + 8;
        int x = (int) ((g.guiWidth() - w) * hudX.value / 100);
        int y = (int) ((g.guiHeight() - h) * hudY.value / 100);
        for (int i = 0; i < h; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, 0xB414286E);
        }
        g.text(f, text, x + 8, y + (h - 8) / 2, 0xFFFFFFFF, true);
        return new int[] {x, y, w, h};
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g) {
        if (Minecraft.getInstance().player == null || HudEditScreen.open) return;
        draw(g);
    }
}