package com.github.devjake123.jakeseconomy.client.screen;

import com.github.devjake123.jakeseconomy.client.ClientPriceHistoryCache;
import com.github.devjake123.jakeseconomy.client.network.MarketPacketSender;
import com.github.devjake123.jakeseconomy.economy.CurrencyFormatter;
import com.github.devjake123.jakeseconomy.economy.PricePoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Trend graph screen for a single market item.
 *
 * Shows a line graph of hourly price snapshots with Day / Week / Month tab views.
 * The data is fetched from the server via {@link MarketPacketSender#sendPriceHistoryRequest}
 * on screen open and cached in {@link ClientPriceHistoryCache}.
 *
 * Hovering anywhere over the graph area snaps to the nearest hourly data point and
 * shows a tooltip with the relative timestamp and exact price.
 */
public class MarketGraphScreen extends Screen {

    private final String itemId;
    private final String displayName;
    private final Screen parent;

    // ── View tabs ──────────────────────────────────────────────────────────────
    private enum View { DAY, WEEK, MONTH }
    private View currentView = View.DAY;

    // ── Panel / graph geometry (computed in init()) ────────────────────────────
    private int guiLeft, guiTop, panelW, panelH;

    // Fixed margins inside the panel for the graph canvas
    private static final int M_LEFT  = 48; // room for Y-axis price labels
    private static final int M_RIGHT = 10;
    private static final int M_TOP   = 42; // room for header + tab row
    private static final int M_BOT   = 22; // room for X-axis time labels

    private int graphX, graphY, graphW, graphH;

    // ── Hover state (recomputed every render frame) ────────────────────────────
    private PricePoint hoveredPoint = null;
    private int hoveredPx, hoveredPy;

    // ── Timestamp formatters (UTC so they're consistent server/client) ─────────
    private static final DateTimeFormatter FMT_HOUR = DateTimeFormatter
            .ofPattern("HH:mm", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FMT_DAY  = DateTimeFormatter
            .ofPattern("EEE",   Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter
            .ofPattern("MMM d", Locale.ROOT).withZone(ZoneOffset.UTC);

    // ── Tab configuration ──────────────────────────────────────────────────────
    private static final String[] TAB_LABELS = { "Day", "Week", "Month" };
    private static final int TAB_W = 50;
    private static final int TAB_H = 14;
    private static final int TAB_GAP = 4;

    // ── Colours ────────────────────────────────────────────────────────────────
    private static final int COL_LINE       = 0xFF4488FF;
    private static final int COL_GRID       = 0xFF1C1C1C;
    private static final int COL_AXIS       = 0xFF555555;
    private static final int COL_LABEL      = 0xFF666666;
    private static final int COL_HOVER_DOT  = 0xFFFFFFFF;
    private static final int COL_HOVER_FILL = 0xFF2266CC;
    private static final int COL_HOVER_LINE = 0x44FFFFFF;

    public MarketGraphScreen(String itemId, String displayName, Screen parent) {
        super(Component.literal("Price History"));
        this.itemId      = itemId;
        this.displayName = displayName;
        this.parent      = parent;
    }

    @Override
    protected void init() {
        panelW  = Math.min(320, width  - 20);
        panelH  = Math.min(230, height - 40);
        guiLeft = (width  - panelW) / 2;
        guiTop  = (height - panelH) / 2;
        graphX  = guiLeft + M_LEFT;
        graphY  = guiTop  + M_TOP;
        graphW  = panelW  - M_LEFT - M_RIGHT;
        graphH  = panelH  - M_TOP  - M_BOT;

        // Request fresh history from the server each time the screen opens
        MarketPacketSender.sendPriceHistoryRequest(itemId);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {

        // ── Panel ──────────────────────────────────────────────────────────────
        g.fill(guiLeft, guiTop, guiLeft + panelW, guiTop + panelH, 0xF0101010);
        g.fill(guiLeft,              guiTop,              guiLeft + panelW, guiTop + 1,              0xFFAAAAAA);
        g.fill(guiLeft,              guiTop + panelH - 1, guiLeft + panelW, guiTop + panelH,         0xFFAAAAAA);
        g.fill(guiLeft,              guiTop,              guiLeft + 1,      guiTop + panelH,          0xFFAAAAAA);
        g.fill(guiLeft + panelW - 1, guiTop,              guiLeft + panelW, guiTop + panelH,          0xFFAAAAAA);

        // ── Header ─────────────────────────────────────────────────────────────
        drawFlatButton(g, guiLeft + 4, guiTop + 4, 60, 14, "\u2190 Back", mouseX, mouseY, 0xFF222222, 0xFF333333);
        String title = displayName + " \u2014 Price History";
        g.drawString(font, title, guiLeft + panelW / 2 - font.width(title) / 2, guiTop + 6, 0xFFFFFFFF);

        // ── Tab row ────────────────────────────────────────────────────────────
        int totalTabsW  = TAB_W * 3 + TAB_GAP * 2;
        int tabsStartX  = guiLeft + panelW / 2 - totalTabsW / 2;
        int tabY        = guiTop + 22;
        View[] views    = View.values();
        for (int i = 0; i < views.length; i++) {
            int tx     = tabsStartX + i * (TAB_W + TAB_GAP);
            boolean active = (currentView == views[i]);
            int bg    = active ? 0xFF1A4488 : 0xFF222222;
            int hover = active ? 0xFF1A4488 : 0xFF333333;
            drawFlatButton(g, tx, tabY, TAB_W, TAB_H, TAB_LABELS[i], mouseX, mouseY, bg, hover);
        }

        // ── Graph area background ──────────────────────────────────────────────
        g.fill(graphX, graphY, graphX + graphW, graphY + graphH, 0xFF080808);

        // Y-axis and X-axis lines
        g.fill(graphX - 1, graphY,            graphX,                  graphY + graphH + 1, COL_AXIS);
        g.fill(graphX,     graphY + graphH,    graphX + graphW,         graphY + graphH + 1, COL_AXIS);

        // ── Graph content ──────────────────────────────────────────────────────
        List<PricePoint> data = getViewData();
        hoveredPoint = null;

        if (!ClientPriceHistoryCache.has(itemId)) {
            // Waiting for server response
            String msg = "Loading\u2026";
            g.drawString(font, msg, graphX + graphW / 2 - font.width(msg) / 2,
                    graphY + graphH / 2 - 4, 0xFF888888);

        } else if (data.size() < 2) {
            // Not enough data yet
            String msg1 = "No price history yet.";
            String msg2 = currentView == View.DAY
                    ? "Data is recorded every 20 minutes."
                    : "Data is recorded hourly.";
            int cx = graphX + graphW / 2;
            int cy = graphY + graphH / 2;
            g.drawString(font, msg1, cx - font.width(msg1) / 2, cy - 10, 0xFF888888);
            g.drawString(font, msg2, cx - font.width(msg2) / 2, cy +  2,  0xFF555555);

        } else {
            renderGraph(g, data, mouseX, mouseY);
        }

        // ── Hover overlay (drawn after graph so it sits on top) ────────────────
        if (hoveredPoint != null) {
            // Faint vertical guide line
            g.fill(hoveredPx, graphY, hoveredPx + 1, graphY + graphH, COL_HOVER_LINE);
            // Highlight dot: 5×5 white border, 3×3 blue fill
            g.fill(hoveredPx - 2, hoveredPy - 2, hoveredPx + 3, hoveredPy + 3, COL_HOVER_DOT);
            g.fill(hoveredPx - 1, hoveredPy - 1, hoveredPx + 2, hoveredPy + 2, COL_HOVER_FILL);

            // Tooltip: relative time + exact price
            String relTime  = relativeTime(hoveredPoint.timestamp());
            String priceStr = CurrencyFormatter.format(Math.round(hoveredPoint.price()), false);
            List<Component> tooltipLines = new ArrayList<>();
            tooltipLines.add(Component.literal(relTime) .withStyle(s -> s.withColor(0xAAAAAA)));
            tooltipLines.add(Component.literal(priceStr).withStyle(s -> s.withColor(0xFFDD55)));
            g.renderTooltip(font, tooltipLines, Optional.empty(), mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    // ── Graph rendering ───────────────────────────────────────────────────────

    private void renderGraph(GuiGraphics g, List<PricePoint> points, int mouseX, int mouseY) {
        int n = points.size();

        // Compute price range with 5 % padding so the line never hugs the very edge
        double minP = Double.MAX_VALUE, maxP = -Double.MAX_VALUE;
        for (PricePoint p : points) {
            if (p.price() < minP) minP = p.price();
            if (p.price() > maxP) maxP = p.price();
        }
        if (maxP <= minP) maxP = minP + 1;
        double pad   = (maxP - minP) * 0.05;
        double lo    = minP - pad;
        double hi    = maxP + pad;
        double range = hi - lo;

        // Map each data point to canvas pixel coordinates
        int[] px = new int[n];
        int[] py = new int[n];
        for (int i = 0; i < n; i++) {
            px[i] = graphX + (int) Math.round(i * (graphW - 1.0) / (n - 1));
            py[i] = graphY + graphH - 1
                    - (int) Math.round((points.get(i).price() - lo) / range * (graphH - 1));
        }

        // ── Horizontal gridlines + Y-axis labels ──────────────────────────────
        int gridCount = 4;
        for (int gi = 0; gi <= gridCount; gi++) {
            int gy = graphY + (int) Math.round(gi * (graphH - 1.0) / gridCount);
            g.fill(graphX, gy, graphX + graphW, gy + 1, COL_GRID);
            double labelPrice = hi - gi * range / gridCount;
            String lbl = CurrencyFormatter.format(Math.round(labelPrice), true);
            g.drawString(font, lbl, graphX - font.width(lbl) - 3, gy - 3, COL_LABEL);
        }

        // ── Line ──────────────────────────────────────────────────────────────
        for (int i = 0; i < n - 1; i++) {
            drawLine(g, px[i], py[i], px[i + 1], py[i + 1], COL_LINE);
        }

        // ── X-axis labels: left (oldest), centre, right (newest = "Now") ──────
        int xLabelY = graphY + graphH + 4;
        if (n >= 2) {
            String lblLeft   = formatTime(points.get(0).timestamp());
            String lblMid    = formatTime(points.get(n / 2).timestamp());
            String lblRight  = "Now";
            g.drawString(font, lblLeft,  graphX,                                xLabelY, COL_LABEL);
            g.drawString(font, lblMid,   graphX + graphW / 2 - font.width(lblMid) / 2, xLabelY, COL_LABEL);
            g.drawString(font, lblRight, graphX + graphW - font.width(lblRight), xLabelY, COL_LABEL);
        }

        // ── Hover detection ───────────────────────────────────────────────────
        if (mouseX >= graphX && mouseX < graphX + graphW
                && mouseY >= graphY && mouseY < graphY + graphH) {
            // Map mouse X → nearest point index using linear interpolation
            int idx = (int) Math.round((double)(mouseX - graphX) * (n - 1) / (graphW - 1));
            idx = Math.clamp(idx, 0, n - 1);
            hoveredPoint = points.get(idx);
            hoveredPx    = px[idx];
            hoveredPy    = py[idx];
        }
    }

    /**
     * Draws a line between two points using a Bresenham-style walk.
     * Steps along the longer axis and interpolates the shorter one, ensuring
     * no pixel gaps even for steep slopes.
     */
    private void drawLine(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        if (x0 == x1 && y0 == y1) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            return;
        }
        int adx = Math.abs(x1 - x0);
        int ady = Math.abs(y1 - y0);

        if (adx >= ady) {
            // Step along X — swap so we always walk left → right
            if (x0 > x1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            int dx = x1 - x0, dy = y1 - y0;
            for (int x = x0; x <= x1; x++) {
                int y = y0 + (int) Math.round((double) dy * (x - x0) / dx);
                g.fill(x, y, x + 1, y + 1, color);
            }
        } else {
            // Step along Y — swap so we always walk top → bottom
            if (y0 > y1) { int t = x0; x0 = x1; x1 = t; t = y0; y0 = y1; y1 = t; }
            int dx = x1 - x0, dy = y1 - y0;
            for (int y = y0; y <= y1; y++) {
                int x = x0 + (int) Math.round((double) dx * (y - y0) / dy);
                g.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    // ── Data helpers ──────────────────────────────────────────────────────────

    private List<PricePoint> getViewData() {
        return switch (currentView) {
            // Day view: use 20-min recent snapshots (up to 72 = 24 h)
            case DAY   -> ClientPriceHistoryCache.getRecent(itemId);
            // Week view: last 168 hourly archive points = 7 days
            case WEEK  -> tail(ClientPriceHistoryCache.get(itemId), 168);
            // Month view: collapse hourly archive points into daily averages
            case MONTH -> aggregateToDaily(ClientPriceHistoryCache.get(itemId));
        };
    }

    private static List<PricePoint> tail(List<PricePoint> src, int n) {
        return src.size() <= n ? src : src.subList(src.size() - n, src.size());
    }

    /**
     * Collapses hourly points into one daily average per UTC calendar day.
     * Returns at most 30 entries (the most recent 30 days).
     */
    private static List<PricePoint> aggregateToDaily(List<PricePoint> hourly) {
        // LinkedHashMap preserves insertion (chronological) order
        LinkedHashMap<Long, List<Double>> byDay = new LinkedHashMap<>();
        for (PricePoint p : hourly) {
            long dayKey = p.timestamp() / 86_400_000L;
            byDay.computeIfAbsent(dayKey, k -> new ArrayList<>()).add(p.price());
        }
        List<PricePoint> result = new ArrayList<>(byDay.size());
        for (Map.Entry<Long, List<Double>> e : byDay.entrySet()) {
            long   midDayMs = e.getKey() * 86_400_000L + 43_200_000L; // noon UTC that day
            double avg      = e.getValue().stream().mapToDouble(v -> v).average().orElse(0);
            result.add(new PricePoint(midDayMs, avg));
        }
        // Keep last 30 days
        return result.size() > 30 ? result.subList(result.size() - 30, result.size()) : result;
    }

    /** Formats a timestamp appropriate for the current view's X-axis labels. */
    private String formatTime(long ms) {
        Instant instant = Instant.ofEpochMilli(ms);
        return switch (currentView) {
            case DAY   -> FMT_HOUR.format(instant);
            case WEEK  -> FMT_DAY.format(instant);
            case MONTH -> FMT_DATE.format(instant);
        };
    }

    /** Returns a short relative-time string for use in the hover tooltip. */
    private static String relativeTime(long timestampMs) {
        long agoMs   = System.currentTimeMillis() - timestampMs;
        long agoMin  = agoMs  / 60_000L;
        if (agoMin  <   1) return "Just now";
        if (agoMin  <  60) return agoMin + "m ago";
        long agoHrs  = agoMin / 60L;
        if (agoHrs  <  48) return agoHrs + "h ago";
        long agoDays = agoHrs / 24L;
        return agoDays + "d ago";
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Back button
        if (mouseX >= guiLeft + 4 && mouseX <= guiLeft + 64
                && mouseY >= guiTop + 4 && mouseY <= guiTop + 18) {
            minecraft.setScreen(parent);
            return true;
        }
        // Tab buttons
        int totalTabsW = TAB_W * 3 + TAB_GAP * 2;
        int tabsStartX = guiLeft + panelW / 2 - totalTabsW / 2;
        int tabY       = guiTop + 22;
        if (mouseY >= tabY && mouseY <= tabY + TAB_H) {
            View[] views = View.values();
            for (int i = 0; i < views.length; i++) {
                int tx = tabsStartX + i * (TAB_W + TAB_GAP);
                if (mouseX >= tx && mouseX <= tx + TAB_W) {
                    currentView = views[i];
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void drawFlatButton(GuiGraphics g, int x, int y, int w, int h,
                                String label, int mx, int my, int color, int hover) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hovered ? hover : color);
        g.fill(x,       y,       x + w, y + 1,   0xFFAAAAAA);
        g.fill(x,       y + h - 1, x + w, y + h, 0xFFAAAAAA);
        g.fill(x,       y,       x + 1, y + h,   0xFFAAAAAA);
        g.fill(x + w - 1, y, x + w, y + h,       0xFFAAAAAA);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}




