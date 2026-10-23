package ca.modules.combat;

import ca.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class AimAssist extends AimModule {

    public final Setting.Slider circleSize = new Setting.Slider("Circle Size (deg)", 1, 60, 15);
    public final Setting.Switch drawCircle = new Setting.Switch("Draw Circle", true);
    public final Setting.Color circleColor = new Setting.Color("Circle Color", 0xAAFFFFFF);

    public AimAssist() {
        super("Aim Assist");
        settings.add(circleSize);
        settings.add(drawCircle);
        settings.add(circleColor);
    }

    /** Smallest angle from the crosshair to the head, body or feet, so any part inside the circle counts. */
    private double nearest(Minecraft mc, Player p) {
        return Math.min(angleTo(mc, head(p)),
                Math.min(angleTo(mc, body(p)), angleTo(mc, p.getPosition(pt))));
    }

    /** Only players with a part inside the circle count, the one nearest the crosshair wins. */
    @Override
    protected double score(Minecraft mc, Player p, Vec3 point) {
        double angle = nearest(mc, p);
        return angle <= circleSize.value ? angle : Double.NaN;
    }

    /** Once locked, the target is kept until it is well outside the circle. */
    @Override
    protected boolean keep(Minecraft mc, Player p) {
        return nearest(mc, p) <= circleSize.value * 2;
    }

    @Override
    public void onRenderHud(GuiGraphicsExtractor g) {
        if (!toggle.value || !drawCircle.value) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.mouseHandler.isMouseGrabbed()) return;

        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        // Angle to pixels with the same projection the game uses (fov option is vertical)
        double vfov = Math.toRadians(mc.options.fov().get());
        double r = Math.tan(Math.toRadians(circleSize.value)) * (h / 2.0) / Math.tan(vfov / 2.0);

        int steps = Math.max(48, (int) (r * 2));
        for (int i = 0; i < steps; i++) {
            double a = 2 * Math.PI * i / steps;
            int x = (int) Math.round(w / 2.0 + r * Math.cos(a));
            int y = (int) Math.round(h / 2.0 + r * Math.sin(a));
            g.fill(x, y, x + 1, y + 1, circleColor.value);
        }
    }
}