package ca.modules.visuals;

import ca.Setting;
import ca.modules.Module;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.entity.HumanoidArm;

/**
 * First-person item viewmodel. All the work is done by ViewmodelMixin, this class holds the settings
 * and the small helpers the mixin calls.
 */
public class Viewmodel extends Module {

    private static Viewmodel instance;

    public final Setting.Switch toggle = new Setting.Switch("Enabled", false);

    // Position, size and rotation of the held item
    public final Setting.Slider offX = new Setting.Slider("Offset X", -1, 1, 0);
    public final Setting.Slider offY = new Setting.Slider("Offset Y", -1, 1, 0);
    public final Setting.Slider offZ = new Setting.Slider("Offset Z", -1, 1, 0);
    public final Setting.Slider scale = new Setting.Slider("Scale", 0.1, 2, 1);
    public final Setting.Slider rotX = new Setting.Slider("Rotation X", -180, 180, 0);
    public final Setting.Slider rotY = new Setting.Slider("Rotation Y", -180, 180, 0);
    public final Setting.Slider rotZ = new Setting.Slider("Rotation Z", -180, 180, 0);

    // Swing
    public final Setting.Slider speed = new Setting.Slider("Swing speed", 1, 4, 1);
    public final Setting.Slider lunge = new Setting.Slider("Swing lunge (%)", 0, 100, 100);
    public final Setting.Switch centerPivot = new Setting.Switch("Center pivot", false);
    public final Setting.Slider pivotX = new Setting.Slider("Pivot X", -0.5, 0.5, 0);
    public final Setting.Slider pivotY = new Setting.Slider("Pivot Y", -0.5, 0.5, 0.15);
    public final Setting.Slider pivotZ = new Setting.Slider("Pivot Z", -0.5, 0.5, -0.1);

    public final Setting.Switch blockhit = new Setting.Switch("1.7 blockhit", false);

    public Viewmodel() {
        super("Viewmodel", "Visuals");
        instance = this;
        settings.add(toggle);
        settings.add(offX);
        settings.add(offY);
        settings.add(offZ);
        settings.add(scale);
        settings.add(rotX);
        settings.add(rotY);
        settings.add(rotZ);
        settings.add(speed);
        settings.add(lunge);
        settings.add(centerPivot);
        settings.add(pivotX);
        settings.add(pivotY);
        settings.add(pivotZ);
        settings.add(blockhit);
    }

    // Read by ViewmodelMixin

    public static boolean on() { return instance != null && instance.toggle.value; }

    public static Viewmodel get() { return instance; }

    public static boolean blockhitOn() { return on() && instance.blockhit.value; }

    /** Swing progress after Swing speed and No Swing. 0 and 1 both mean "arm at rest". */
    public static float attack(float a) {
        if (NoSwing.isOn()) return 0;
        if (!on() || a <= 0) return a;
        return Math.min(1, a * instance.speed.value.floatValue());
    }

    /** Offset, rotation and scale, applied right after the normal item position. */
    public static void transform(PoseStack ps, HumanoidArm arm) {
        if (!on()) return;
        int inv = arm == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(inv * instance.offX.value.floatValue(), instance.offY.value.floatValue(), instance.offZ.value.floatValue());
        ps.rotateDegrees(Axis.XP, instance.rotX.value.floatValue());
        ps.rotateDegrees(Axis.YP, inv * instance.rotY.value.floatValue());
        ps.rotateDegrees(Axis.ZP, inv * instance.rotZ.value.floatValue());
        float s = instance.scale.value.floatValue();
        ps.scale(s, s, s);
    }

    /** The 1.7 / 1.8 sword block pose, converted to the modern item orientation. */
    public static void blockPose(PoseStack ps, int inv) {
        ps.rotateDegrees(Axis.YP, inv * 45.0F);
        ps.translate(inv * -0.2F, 0.08F, 0.0F);
        ps.rotateDegrees(Axis.YP, inv * 30.0F);
        ps.rotateDegrees(Axis.XP, -80.0F);
        ps.rotateDegrees(Axis.YP, inv * 60.0F);
        ps.rotateDegrees(Axis.YP, inv * -45.0F);
    }
}