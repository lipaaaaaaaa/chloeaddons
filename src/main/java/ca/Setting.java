package ca;

import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.List;

public abstract class Setting<T> {
    public final String name;
    public final T def;
    public T value;

    Setting(String name, T def) {
        this.name = name;
        this.def = def;
        this.value = def;
    }

    @SuppressWarnings("unchecked")
    void load(JsonElement e) {
        if (e.isJsonNull()) return;
        value = (T) Config.GSON.fromJson(e, def.getClass());
    }

    public static class Switch extends Setting<Boolean> {
        public Switch(String name, boolean def) { super(name, def); }
    }

    public static class Slider extends Setting<Double> {
        public final double min, max;
        public Slider(String name, double min, double max, double def) {
            super(name, def);
            this.min = min;
            this.max = max;
        }

        @Override
        void load(JsonElement e) {
            super.load(e);
            value = Math.clamp(value, min, max);
        }
    }

    public static class NumberField extends Setting<Double> {
        public NumberField(String name, double def) { super(name, def); }
    }

    public static class TextField extends Setting<String> {
        public TextField(String name, String def) { super(name, def); }
    }

    public static class Dropdown extends Setting<String> {
        public final String[] options;
        public Dropdown(String name, String def, String... options) {
            super(name, def);
            this.options = options;
        }
    }

    // NEW: Color setting
    public static class Color extends Setting<Integer> {
        public Color(String name, int def) { super(name, def); }
    }

    // List of block ids like "minecraft:bedrock"
    public static class BlockList extends Setting<ArrayList<String>> {
        public BlockList(String name, String... def) { super(name, new ArrayList<>(List.of(def))); }
    }
}