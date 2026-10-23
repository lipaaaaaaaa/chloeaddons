package ca.mixin;

import ca.modules.visuals.CustomFont;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FontDescription;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@SuppressWarnings("unused")
@Mixin(Font.class)
public class FontMixin {
    @ModifyVariable(method = "getGlyphSource", at = @At("HEAD"), argsOnly = true)
    private FontDescription ca$font(FontDescription description) {
        if (CustomFont.active() && description.equals(FontDescription.DEFAULT)) {
            return new FontDescription.Resource(CustomFont.ID);
        }
        return description;
    }
}