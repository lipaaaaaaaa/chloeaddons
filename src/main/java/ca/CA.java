package ca;

import ca.modules.Module;
import ca.util.Binds;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class CA implements ClientModInitializer {
    private static boolean pending, pendingHud;
    private static final Map<Module, Boolean> last = new HashMap<>();
    private static String toastName;
    private static boolean toastOn;
    private static long toastStart;

    /** An "Enabled" switch is the real toggle. Modules without one still use the boolean. */
    private static boolean live(Module m) {
        boolean v = m.enabled;
        for (Setting<?> s : m.settings) {
            if (s instanceof Setting.Switch sw && sw.name.equals("Enabled")) {
                v = m.enabled = sw.value;
                break;
            }
        }
        Boolean p = last.put(m, v);
        if (p != null && p != v) {
            toastName = m.name;
            toastOn = v;
            toastStart = System.currentTimeMillis();
            if (!v) m.onDisable();
        }
        return v;
    }

    static int[] drawToast(GuiGraphicsExtractor g, String name, boolean on, float p) {
        var f = Minecraft.getInstance().font;
        var text = Component.literal(name + " is now ").withColor(0xAAAAAA)
                .append(Component.literal(on ? "Enabled" : "Disabled").withColor(on ? 0x55FF55 : 0xFF5555));
        int w = f.width(text) + 30;
        int x = (int) ((g.guiWidth() - w) * Settings.NOTIFICATION_X.value / 100);
        int y = (int) ((g.guiHeight() - 32) * Settings.NOTIFICATION_Y.value / 100) - (int) ((1 - p) * 8);
        int a = (int) (255 * p);
        g.fill(x, y, x + w, y + 32, ((int) (Settings.NOTIFICATION_ALPHA.value * p) << 24) | 0x14286E);
        if (a > 8) {
            g.text(f, "ChloeAddons", x + 5, y + 5, (a << 24) | 0xFFFFFF, true);
            g.text(f, text, x + w - 5 - f.width(text), y + 18, (a << 24) | 0xFFFFFF, true);
        }
        return new int[] {x, y, w};
    }

    private static int open(CommandContext<?> c) {
        pending = true;
        return 1;
    }

    @Override
    public void onInitializeClient() {
        Settings.init();
        Config.load();

        var cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ca", "main"));
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ca.open", InputConstants.KEY_RSHIFT, cat));

        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> {
            var bind = ClientCommands.literal("bind").then(
                    ClientCommands.argument("args", StringArgumentType.greedyString()).executes(c -> {
                        // "/ca bind Aim Assist F": the last word is the key, the rest is the module name
                        String a = StringArgumentType.getString(c, "args").trim();
                        int i = a.lastIndexOf(' ');
                        c.getSource().sendFeedback(Component.literal(i < 0
                                ? "Usage: /ca bind <module> <key>"
                                : Binds.bind(a.substring(0, i).trim(), a.substring(i + 1))));
                        return 1;
                    }));
            var hud = ClientCommands.literal("hud").executes(c -> {
                pendingHud = true;
                return 1;
            });
            var main = d.register(ClientCommands.literal("chloeaddons").executes(CA::open)
                    .then(bind).then(hud));
            for (var alias : new String[] {"ca", "chloe"})
                d.register(ClientCommands.literal(alias).executes(CA::open).redirect(main));
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (key.consumeClick()) pending = true;
            if (pending) {
                pending = false;
                mc.gui.setScreen(new CAScreen());
            }
            if (pendingHud) {
                pendingHud = false;
                mc.gui.setScreen(new HudEditScreen());
            }
        });

        // Start of tick, like vanilla input handling, so actions go out before the movement packet
        ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            Binds.tick(mc);
            for (var m : ca.modules.ModuleManager.INSTANCE.getModules()) {
                if (live(m)) m.onTick();
            }
        });

        // 1. EXTRACTION PHASE: Gather block data from the world.
        // This runs on the render thread before the draw phase.
        LevelExtractionEvents.END_EXTRACTION.register(context -> {
            for (var m : ca.modules.ModuleManager.INSTANCE.getModules()) {
                if (live(m)) m.onExtract(context);
            }
        });

        // 2. DRAWING PHASE: Render the cached data.
        // END_MAIN runs with no render pass open, so buffer upload and our own pass are allowed.
        LevelRenderEvents.END_MAIN.register(context -> {
            for (var m : ca.modules.ModuleManager.INSTANCE.getModules()) {
                if (live(m)) m.onRender3D(context);
            }
        });

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("ca", "hud"), (g, delta) -> {
            for (var m : ca.modules.ModuleManager.INSTANCE.getModules()) {
                if (live(m)) m.onRenderHud(g);
            }
            long el = System.currentTimeMillis() - toastStart;
            if (el < 500) {
                float t = el / 500f, s = Math.min(1f, Math.min(t, 1f - t) / 0.3f);
                drawToast(g, toastName, toastOn, s * s * (3 - 2 * s));
            }
        });

        // 3. Hide the vanilla block outline when the custom one asks for it.
        LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> {
            for (var m : ca.modules.ModuleManager.INSTANCE.getModules())
                if (live(m) && m instanceof ca.modules.visuals.CustomBlockOutline c && c.hidesVanilla()) return false;
            return true;
        });
    }
}