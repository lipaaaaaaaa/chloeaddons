package ca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Config {
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("CA.json");

    static void load() {
        try (var r = Files.newBufferedReader(FILE)) {
            var o = GSON.fromJson(r, JsonObject.class);
            Settings.all().forEach((k, s) -> {
                if (!o.has(k)) return;
                try { s.load(o.get(k)); } catch (RuntimeException ignored) {}
            });
            // Where the settings screen was last left
            if (o.has("_ui") && o.get("_ui").isJsonObject()) {
                var ui = o.getAsJsonObject("_ui");
                try {
                    if (ui.has("tab")) CAScreen.lastTab = ui.get("tab").getAsString();
                    if (ui.has("scroll")) CAScreen.lastScroll = Math.max(0, ui.get("scroll").getAsInt());
                } catch (RuntimeException ignored) {}
            }
        } catch (IOException | RuntimeException ignored) {}
        Settings.applyExploits();
    }

    public static void save() {
        var o = new JsonObject();
        Settings.all().forEach((k, s) -> o.add(k, GSON.toJsonTree(s.value)));
        var ui = new JsonObject();
        ui.addProperty("tab", CAScreen.lastTab);
        ui.addProperty("scroll", CAScreen.lastScroll);
        o.add("_ui", ui);
        try { Files.writeString(FILE, GSON.toJson(o)); } catch (IOException ignored) {}
        Settings.applyExploits();
    }
}