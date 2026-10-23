package ca.modules;

import ca.Setting;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    public final String name;
    public final String category;
    public boolean enabled;
    public final List<Setting<?>> settings = new ArrayList<>();

    public Module(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}

    /** Called during the extraction phase to gather data for rendering. */
    public void onExtract(LevelExtractionContext context) {}

    /** Called during the drawing phase to render geometry. */
    public void onRender3D(LevelRenderContext context) {}

    /** Called every frame while the HUD is drawn. */
    public void onRenderHud(GuiGraphicsExtractor graphics) {}
}