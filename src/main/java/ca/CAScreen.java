package ca;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class CAScreen extends Screen {
    private static final int W = 440, SW = 205;
    private static final int BG = 0xFF334D86, HEAD = 0xFF2A4075, PANEL = 0xFF2D4579, TITLE_BG = 0xFF273C6E,
            EDGE = 0xFF4A66A8, CTRL = 0xFF21346A, ACCENT = 0xFF8CB4FF, TEXT = 0xFFF4F7FF, DIM = 0xFFAFC0E8;
    private static final int GAP = 3, TITLE_H = 18, HEADER_H = 24;

    private record Hit(int x, int y, int w, int h, Object target, int opt) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    // Click target inside a block list: add a suggestion or remove a current entry
    private record Pick(Setting.BlockList list, String id, boolean add) {}

    // Click target for the search bar
    private record Search() {}
    private static final Search SEARCH = new Search();

    // Where you were when the screen was last closed (also saved to the config)
    static String lastTab = "";
    static int lastScroll;

    private final List<Hit> hits = new ArrayList<>();
    private final StringBuilder buf = new StringBuilder();
    private String tab = Settings.TABS.containsKey(lastTab) ? lastTab
            : Settings.TABS.isEmpty() ? "" : Settings.TABS.keySet().iterator().next();
    private Setting<?> focus;
    private Setting.Dropdown open;
    private Setting.Slider drag;
    private Hit dragHit;
    private int ox, oy, ow;

    private int[] toastBox;
    private boolean dragToast;
    private int toastGx, toastGy;

    // Window height, follows the screen so small windows still fit
    private int H = 280;

    // Vertical scroll of the settings area, and the most it can scroll (measured last frame)
    // maxScroll starts high so the restored scroll is not cut to 0 before the first frame measures the content
    private int scroll = lastScroll, maxScroll = Integer.MAX_VALUE;

    // Hidden vanilla EditBox, only used as the text input sink. We draw the field ourselves.
    private EditBox input;

    // Same idea for the search bar
    private EditBox search;
    private boolean searching;

    // Color Picker State (renamed from cx/cy to avoid shadowing local int[] cy)
    private Setting.Color openColor;
    private int colorX, colorY;
    private boolean draggingSV, draggingHue, draggingAlpha;
    private float hue = 0, sat = 1, val = 1, alpha = 1;

    public CAScreen() { super(Component.literal("CA")); }

    @Override
    protected void init() {
        focus = null;
        searching = false;
        input = new EditBox(font, -1000, -1000, 100, 12, Component.empty());
        input.setMaxLength(64);
        input.setResponder(v -> {
            // numeric fields only accept digits, dot and minus
            String clean = focus instanceof Setting.NumberField || focus instanceof Setting.Slider
                    ? v.replaceAll("[^0-9.\\-]", "") : v;
            if (!clean.equals(v)) {
                input.setValue(clean); // re-enters this responder with the cleaned text
                return;
            }
            buf.setLength(0);
            buf.append(v);
            commit();
        });
        addWidget(input);

        search = new EditBox(font, -1000, -1000, 100, 12, Component.empty());
        search.setMaxLength(32);
        search.setResponder(v -> {
            scroll = 0;
            open = null;
            openColor = null;
        });
        addWidget(search);
    }

    @Override
    public void removed() {
        lastTab = tab;
        lastScroll = scroll;
        Config.save();
    }

    private void focusOn(Setting<?> s, String initial) {
        focus = s;
        input.setValue(initial);
        buf.setLength(0);
        buf.append(initial);
        setFocused(input);
        input.setFocused(true);
    }

    private void blur() {
        focus = null;
        if (input != null) {
            input.setFocused(false);
            setFocused(null);
        }
    }

    private void stopSearch() {
        searching = false;
        search.setFocused(false);
        setFocused(null);
    }

    private int rowH(Setting<?> s) {
        if (s instanceof Setting.BlockList b) return 24 + 11 * (matches(b).size() + b.value.size()) + GAP;
        if (s instanceof Setting.Switch || s instanceof Setting.Color) return 12 + GAP;
        if (s instanceof Setting.Slider) return 19 + GAP;
        return 22 + GAP;
    }

    private static String fmt(Object v) {
        return v instanceof Double d && d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(v);
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    // A feature is "on" when its Enabled switch is on
    private static boolean on(Settings.Section sec) {
        for (var s : sec.settings())
            if (s instanceof Setting.Switch sw && sw.name.equals("Enabled")) return sw.value;
        return false;
    }

    // Up to 6 block ids matching the search text, prefix matches first
    private List<String> matches(Setting.BlockList b) {
        if (focus != b || buf.isEmpty()) return List.of();
        String q = buf.toString().toLowerCase();
        return BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getPath().contains(q)
                        && !b.value.contains(id.toString())
                        && !BuiltInRegistries.BLOCK.getValue(id).defaultBlockState().isAir())
                .sorted(Comparator.comparing((Identifier id) -> !id.getPath().startsWith(q))
                        .thenComparing(Identifier::getPath))
                .limit(6)
                .map(Identifier::toString)
                .toList();
    }

    private void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) { g.fill(x, y, x + w, y + h, c); }

    private void edge(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        rect(g, x, y, w, 1, c);
        rect(g, x, y + h - 1, w, 1, c);
        rect(g, x, y, 1, h, c);
        rect(g, x + w - 1, y, 1, h, c);
    }

    // Same as rect/edge, but only the part between top and bottom is drawn (used for scrolling)
    private void clipRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c, int top, int bottom) {
        int y1 = Math.max(y, top), y2 = Math.min(y + h, bottom);
        if (y2 > y1) rect(g, x, y1, w, y2 - y1, c);
    }

    private void clipEdge(GuiGraphicsExtractor g, int x, int y, int w, int h, int c, int top, int bottom) {
        clipRect(g, x, y, w, 1, c, top, bottom);
        clipRect(g, x, y + h - 1, w, 1, c, top, bottom);
        clipRect(g, x, y, 1, h, c, top, bottom);
        clipRect(g, x + w - 1, y, 1, h, c, top, bottom);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int c) { g.text(font, s, x, y, c, true); }

    private void hit(int x, int y, int w, int h, Object t, int o) { hits.add(new Hit(x, y, w, h, t, o)); }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        hits.clear();
        H = Math.max(120, Math.min(height - 20, 330));
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        String q = search.getValue().trim().toLowerCase();

        rect(g, 0, 0, width, height, 0x80000000);
        rect(g, x0, y0, W, H, BG);
        rect(g, x0 + 1, y0 + 1, W - 2, HEADER_H - 1, HEAD);
        rect(g, x0 + 1, y0 + HEADER_H, W - 2, 1, EDGE);
        edge(g, x0, y0, W, H, ACCENT);
        text(g, "CA", x0 + 10, y0 + 8, TEXT);

        int tx = x0 + 50;
        for (String t : Settings.TABS.keySet()) {
            int w = font.width(t) + 14;
            boolean sel = q.isEmpty() && t.equals(tab);
            text(g, t, tx + 7, y0 + 8, sel ? TEXT : DIM);
            if (sel) rect(g, tx + 7, y0 + 18, w - 14, 1, ACCENT);
            hit(tx, y0 + 3, w, 18, t, 0);
            tx += w;
        }

        // Search bar, top right edge
        int sbw = 100, sbh = 14, sbx = x0 + W - sbw - 8, sby = y0 + 5;
        rect(g, sbx, sby, sbw, sbh, CTRL);
        edge(g, sbx, sby, sbw, sbh, searching ? ACCENT : EDGE);
        String shown = searching ? search.getValue() + "_" : search.getValue();
        while (shown.length() > 1 && font.width(shown) > sbw - 8) shown = shown.substring(1);
        if (shown.isEmpty()) text(g, "search...", sbx + 4, sby + 3, DIM);
        else text(g, shown, sbx + 4, sby + 3, TEXT);
        hit(sbx, sby, sbw, sbh, SEARCH, 0);

        int viewTop = y0 + 30, viewBottom = y0 + H - 8, viewH = viewBottom - viewTop;
        scroll = Math.clamp(scroll, 0, maxScroll);

        // Features to show: the current tab, or every feature whose name matches the search
        List<Settings.Section> secs = new ArrayList<>();
        if (q.isEmpty()) {
            if (Settings.TABS.containsKey(tab)) secs.addAll(Settings.TABS.get(tab));
        } else {
            for (var list : Settings.TABS.values())
                for (var sec : list)
                    if (sec.title().toLowerCase().contains(q)) secs.add(sec);
        }

        // NOTE: this is the local int[] that was shadowing the old cy field.
        // We kept the name 'cy' here, which is fine now that the field is 'colorY'.
        int base = viewTop - scroll;
        int[] cy = {base, base};
        for (var sec : secs) {
            // put each section in the shorter column
            int col = cy[0] <= cy[1] ? 0 : 1, sx = x0 + 10 + col * (SW + 10), sy = cy[col];
            int h = TITLE_H + 5 + sec.settings().stream().mapToInt(this::rowH).sum() + GAP;

            clipRect(g, sx, sy, SW, h, PANEL, viewTop, viewBottom);
            clipRect(g, sx + 1, sy + 1, SW - 2, TITLE_H - 1, TITLE_BG, viewTop, viewBottom);
            clipEdge(g, sx, sy, SW, h, EDGE, viewTop, viewBottom);
            clipRect(g, sx + 1, sy + TITLE_H, SW - 2, 1, EDGE, viewTop, viewBottom);
            if (sy >= viewTop && sy + TITLE_H <= viewBottom)
                text(g, sec.title(), sx + 8, sy + 5, on(sec) ? ACCENT : TEXT);

            int ry = sy + TITLE_H + 5;
            for (var s : sec.settings()) {
                int rh = rowH(s);
                // only rows that fit completely inside the view are drawn and clickable
                if (ry >= viewTop && ry + rh - GAP <= viewBottom) row(g, s, sx + 8, ry, SW - 16);
                ry += rh;
            }
            cy[col] += h + 6;
        }
        if (secs.isEmpty() && !q.isEmpty()) text(g, "No results", x0 + 10, viewTop + 4, DIM);
        maxScroll = Math.max(0, Math.max(cy[0], cy[1]) - base - viewH);

        // Scrollbar
        if (maxScroll > 0) {
            int th = Math.max(12, viewH * viewH / (viewH + maxScroll));
            int ty = viewTop + (viewH - th) * scroll / maxScroll;
            rect(g, x0 + W - 5, viewTop, 3, viewH, CTRL);
            rect(g, x0 + W - 5, ty, 3, th, ACCENT);
        }

        // Render Dropdown
        if (open != null) {
            int h = open.options.length * 12;
            rect(g, ox, oy, ow, h, CTRL);
            edge(g, ox, oy, ow, h, ACCENT);
            for (int k = 0; k < open.options.length; k++) {
                text(g, open.options[k], ox + 3, oy + k * 12 + 2, open.options[k].equals(open.value) ? ACCENT : TEXT);
                hit(ox, oy + k * 12, ow, 12, open, k);
            }
        }

        // Render Color Picker Popup
        if (openColor != null) {
            int size = 60;

            // Background panel so it doesn't blend into the GUI
            rect(g, colorX - 3, colorY - 3, size + 18, size + 18, PANEL);
            edge(g, colorX - 3, colorY - 3, size + 18, size + 18, ACCENT);

            // Draw SV Square
            for (int s = 0; s < size; s += 2) {
                for (int v = 0; v < size; v += 2) {
                    int rgb = Color.HSBtoRGB(hue, s / (float) size, 1f - (v / (float) size));
                    rect(g, colorX + s, colorY + v, 2, 2, 0xFF000000 | rgb);
                }
            }

            // Draw Hue Bar
            for (int hh = 0; hh < size; hh++) {
                rect(g, colorX + size + 2, colorY + hh, 10, 1, 0xFF000000 | Color.HSBtoRGB(hh / (float) size, 1f, 1f));
            }

            // Draw Alpha Bar
            for (int a = 0; a < size; a++) {
                int aVal = (int) ((a / (float) size) * 255);
                int col = (aVal << 24) | (Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF);
                rect(g, colorX + a, colorY + size + 4, 1, 8, col);
            }

            // Draw Cursors
            rect(g, colorX + (int) (sat * size) - 1, colorY + (int) ((1f - val) * size) - 1, 3, 3, 0xFFFFFFFF);
            rect(g, colorX + size + 2, colorY + (int) (hue * size) - 1, 10, 2, 0xFFFFFFFF);
            rect(g, colorX + (int) (alpha * size) - 1, colorY + size + 4, 2, 8, 0xFFFFFFFF);

            // Register Hitboxes for dragging
            hit(colorX, colorY, size, size, "SV", 0);
            hit(colorX + size + 2, colorY, 10, size, "HUE", 0);
            hit(colorX, colorY + size + 4, size, 8, "ALPHA", 0);
        }

        toastBox = q.isEmpty() && tab.equals("Misc") ? CA.drawToast(g, "ModuleName", true, 1f) : null;
    }

    private void row(GuiGraphicsExtractor g, Setting<?> s, int x, int y, int w) {
        switch (s) {
            case Setting.Switch v -> {
                rect(g, x, y + 1, 9, 9, CTRL);
                edge(g, x, y + 1, 9, 9, EDGE);
                if (v.value) rect(g, x + 2, y + 3, 5, 5, ACCENT);
                text(g, v.name, x + 15, y + 2, TEXT);
                hit(x, y, w, 12, v, 0);
            }
            case Setting.Slider v -> {
                String val = focus == v ? buf + "_" : fmt(v.value);
                int vw = font.width(val);
                text(g, v.name, x, y, TEXT);
                text(g, val, x + w - vw, y, focus == v ? ACCENT : DIM);
                rect(g, x, y + 11, w, 5, CTRL);
                rect(g, x, y + 11, (int) (w * (v.value - v.min) / (v.max - v.min)), 5, ACCENT);
                hit(x, y + 10, w, 8, v, 0);
                // click the value to type it (opt 1)
                hit(x + w - vw - 2, y - 1, vw + 2, 10, v, 1);
            }
            case Setting.Dropdown v -> {
                text(g, v.name, x, y, TEXT);
                rect(g, x, y + 10, w, 11, CTRL);
                edge(g, x, y + 10, w, 11, EDGE);
                text(g, v.value, x + 3, y + 12, DIM);
                rect(g, x + w - 11, y + 10, 11, 11, ACCENT);
                hit(x, y + 10, w, 11, v, -1);
                if (open == v) { ox = x; oy = y + 21; ow = w; }
            }
            case Setting.TextField v -> field(g, s, v.name, v.value, x, y, w);
            case Setting.NumberField v -> field(g, s, v.name, fmt(v.value), x, y, w);
            case Setting.Color v -> {
                text(g, v.name, x, y + 2, TEXT);
                // Render color preview box
                rect(g, x + w - 24, y + 1, 20, 10, v.value);
                edge(g, x + w - 24, y + 1, 20, 10, EDGE);
                hit(x, y, w, 12, v, 0);
            }
            case Setting.BlockList v -> {
                text(g, v.name, x, y, TEXT);
                rect(g, x, y + 10, w, 11, CTRL);
                edge(g, x, y + 10, w, 11, focus == v ? ACCENT : EDGE);
                text(g, focus == v ? buf + "_" : "search blocks...", x + 3, y + 12, DIM);
                hit(x, y + 10, w, 11, v, 0);
                int ly = y + 24;
                for (String id : matches(v)) {
                    text(g, "+ " + shortId(id), x + 3, ly, ACCENT);
                    hit(x, ly - 1, w, 11, new Pick(v, id, true), 0);
                    ly += 11;
                }
                for (String id : v.value) {
                    text(g, "x " + shortId(id), x + 3, ly, TEXT);
                    hit(x, ly - 1, w, 11, new Pick(v, id, false), 0);
                    ly += 11;
                }
            }
            default -> {}
        }
    }

    private void field(GuiGraphicsExtractor g, Setting<?> s, String name, String shown, int x, int y, int w) {
        text(g, name, x, y, TEXT);
        rect(g, x, y + 10, w, 11, CTRL);
        edge(g, x, y + 10, w, 11, focus == s ? ACCENT : EDGE);
        text(g, focus == s ? buf + "_" : shown, x + 3, y + 12, DIM);
        hit(x, y + 10, w, 11, s, 0);
    }

    private void slide(double mx) {
        double t = Math.clamp((mx - dragHit.x()) / dragHit.w(), 0, 1);
        drag.value = Math.round((drag.min + t * (drag.max - drag.min)) * 100) / 100.0;
    }

    private void commit() {
        if (focus instanceof Setting.TextField t) t.value = buf.toString();
        else if (focus instanceof Setting.NumberField n) {
            try { n.value = Double.parseDouble(buf.toString()); } catch (NumberFormatException ignored) {}
        } else if (focus instanceof Setting.Slider sl) {
            try { sl.value = Math.clamp(Double.parseDouble(buf.toString()), sl.min, sl.max); } catch (NumberFormatException ignored) {}
        }
    }

    private void updateColor(double mx, double my) {
        int size = 60;
        if (draggingSV) {
            sat = Math.clamp((float) (mx - colorX) / size, 0, 1);
            val = 1f - Math.clamp((float) (my - colorY) / size, 0, 1);
        }
        if (draggingHue) {
            hue = Math.clamp((float) (my - colorY) / size, 0, 1);
        }
        if (draggingAlpha) {
            alpha = Math.clamp((float) (mx - colorX) / size, 0, 1);
        }

        int rgb = Color.HSBtoRGB(hue, sat, val);
        int a = (int) (alpha * 255);
        openColor.value = (a << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll <= 0) return super.mouseScrolled(mx, my, sx, sy);
        scroll = Math.clamp(scroll - (int) Math.round(sy * 14), 0, maxScroll);
        // popups are anchored to a row, so close them when the content moves
        open = null;
        openColor = null;
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (toastBox != null && e.x() >= toastBox[0] && e.x() < toastBox[0] + toastBox[2]
                && e.y() >= toastBox[1] && e.y() < toastBox[1] + 32) {
            dragToast = true;
            toastGx = (int) e.x() - toastBox[0];
            toastGy = (int) e.y() - toastBox[1];
            return true;
        }

        Hit h = null;
        for (int i = hits.size() - 1; i >= 0 && h == null; i--) if (hits.get(i).in(e.x(), e.y())) h = hits.get(i);

        // Handle Color Picker interactions first
        if (openColor != null) {
            if (h != null && h.target() instanceof String s && (s.equals("SV") || s.equals("HUE") || s.equals("ALPHA"))) {
                if (s.equals("SV")) draggingSV = true;
                if (s.equals("HUE")) draggingHue = true;
                if (s.equals("ALPHA")) draggingAlpha = true;
                updateColor(e.x(), e.y());
                return true;
            } else {
                openColor = null; // Close if clicked outside
            }
        }

        var prev = open;
        if (h == null || !(h.target() instanceof Setting.Dropdown)) open = null;
        if (searching && (h == null || !(h.target() instanceof Search))) stopSearch();
        if (h == null || h.target() != focus) blur();
        if (h == null) return super.mouseClicked(e, dbl);

        switch (h.target()) {
            case String t -> {
                if (!t.equals(tab)) scroll = 0;
                tab = t;
                search.setValue("");
            }
            case Search ignored -> {
                searching = true;
                setFocused(search);
                search.setFocused(true);
            }
            case Pick p -> {
                if (p.add()) {
                    p.list().value.add(p.id());
                    focusOn(p.list(), "");
                } else {
                    p.list().value.remove(p.id());
                }
            }
            case Setting.Switch s -> s.value = !s.value;
            case Setting.Slider s -> {
                if (h.opt() == 1) {
                    // typing mode: start empty, value updates as you type
                    focusOn(s, "");
                } else {
                    drag = s;
                    dragHit = h;
                    slide(e.x());
                }
            }
            case Setting.Dropdown s -> {
                if (h.opt() >= 0) { s.value = s.options[h.opt()]; open = null; }
                else open = prev == s ? null : s;
            }
            case Setting.Color s -> {
                Color c = new Color(s.value, true);
                float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
                hue = hsb[0]; sat = hsb[1]; val = hsb[2];
                alpha = c.getAlpha() / 255f;
                openColor = s;
                // Position popup to the left/above of the clicked row
                colorX = (int) e.x() - 30;
                colorY = (int) e.y() - 90;
            }
            case Setting.BlockList s -> focusOn(s, "");
            case Setting<?> s -> focusOn(s, fmt(s.value));
            default -> {}
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        if (dragToast) {
            Settings.NOTIFICATION_X.value = Math.round(Math.clamp((e.x() - toastGx) / (width - toastBox[2]), 0, 1) * 10000) / 100.0;
            Settings.NOTIFICATION_Y.value = Math.round(Math.clamp((e.y() - toastGy) / (height - 32), 0, 1) * 10000) / 100.0;
            return true;
        }
        if (draggingSV || draggingHue || draggingAlpha) {
            updateColor(e.x(), e.y());
            return true;
        }
        if (drag == null) return super.mouseDragged(e, dx, dy);
        slide(e.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent e) {
        dragToast = false;
        draggingSV = false;
        draggingHue = false;
        draggingAlpha = false;
        drag = null;
        return super.mouseReleased(e);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int k = e.key();
        boolean leave = k == InputConstants.KEY_RETURN || k == InputConstants.KEY_NUMPADENTER || k == InputConstants.KEY_ESCAPE;
        if (searching && leave) {
            stopSearch();
            return true;
        }
        if (focus != null && leave) {
            blur();
            return true;
        }
        // typing, backspace etc. go to the focused EditBox through the screen
        return super.keyPressed(e);
    }
}