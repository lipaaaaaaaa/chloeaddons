package ca.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

public final class MouseUtil {
    private MouseUtil() {}

    @SuppressWarnings("resource")
    private static Minecraft mc() { return Minecraft.getInstance(); }

    // --- Actions (follow the keybind, so they work even if attack/use are rebound) ---

    /** Attack / break key is held. */
    public static boolean left() { return mc().options.keyAttack.isDown(); }

    /** Use / interact / place key is held. */
    public static boolean right() { return mc().options.keyUse.isDown(); }

    /** Pick block key is held. */
    public static boolean middle() { return mc().options.keyPickItem.isDown(); }

    /** Hold or release attack / break. */
    public static void holdLeft(boolean down) { mc().options.keyAttack.setDown(down); }

    /** Hold or release use / interact. */
    public static void holdRight(boolean down) { mc().options.keyUse.setDown(down); }

    /** One attack / break click, handled by the game on the next tick. */
    public static void clickLeft() { click(mc().options.keyAttack); }

    /** One use / interact click, handled by the game on the next tick. */
    public static void clickRight() { click(mc().options.keyUse); }

    // --- Physical buttons (whatever the user is actually pressing) ---

    public static boolean leftRaw() { return mc().mouseHandler.isLeftPressed(); }
    public static boolean rightRaw() { return mc().mouseHandler.isRightPressed(); }
    public static boolean middleRaw() { return mc().mouseHandler.isMiddlePressed(); }

    // --- Any keybind ---

    public static boolean down(KeyMapping key) { return key.isDown(); }

    public static void hold(KeyMapping key, boolean down) { key.setDown(down); }

    public static void click(KeyMapping key) { KeyMapping.click(InputConstants.getKey(key.saveString())); }

    /** Find a keybind by its id, e.g. "key.sneak", "key.jump", "key.inventory". Null if none. */
    public static KeyMapping key(String name) {
        for (KeyMapping k : mc().options.keyMappings) if (k.getName().equals(name)) return k;
        return null;
    }
}