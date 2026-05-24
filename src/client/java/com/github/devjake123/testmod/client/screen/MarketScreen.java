package com.github.devjake123.testmod.client.screen;

import com.github.devjake123.testmod.client.ClientAdvancementLockCache;
import com.github.devjake123.testmod.client.ClientBalanceCache;
import com.github.devjake123.testmod.client.ClientMarketListingCache;
import com.github.devjake123.testmod.client.ClientTransactionHistoryCache;
import com.github.devjake123.testmod.client.network.MarketPacketSender;
import com.github.devjake123.testmod.config.JakesEconomyConfigManager;
import com.github.devjake123.testmod.config.JakesEconomyPriceConfig;
import com.github.devjake123.testmod.economy.CurrencyFormatter;
import com.github.devjake123.testmod.economy.TransactionEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MarketScreen extends Screen {

    private static final int ROW_HEIGHT   = 18;
    private static final int TAB_HEIGHT   = 20;
    private static final int TAB_WIDTH    = 60;
    private static final int SIDEBAR_W    = 62;
    // Rows area starts at guiTop + 36 (header 22 + separator+rows header 14)
    private static final int ROWS_AREA_TOP_OFFSET = 36;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private enum NavMode { MARKET, WITHDRAW, HISTORY }
    private NavMode navMode = NavMode.MARKET;

    private int scrollOffset = 0;
    private int historyScrollOffset = 0;
    private String activeCategory = null;
    private final List<String> categories = new ArrayList<>();
    private List<Map.Entry<String, JakesEconomyPriceConfig.ItemPrice>> visibleItems = new ArrayList<>();
    /** Parallel to visibleItems — category key for each item (null = current-tab, set = cross-tab search result) */
    private List<String> visibleItemCategories = new ArrayList<>();
    private int guiLeft, guiTop, panelWidth, panelHeight;

    // Sort state — reset to ascending each time the screen is opened
    private boolean sortAscending = true;
    private int sortBtnX1, sortBtnY1, sortBtnX2, sortBtnY2;

    // Search box
    private boolean searchFocused = false;
    private final StringBuilder searchBuffer = new StringBuilder();
    /** Bounds set during render, used for hit-testing in mouseClicked */
    private int searchX1, searchX2, searchY1, searchY2;

    // Computed each frame — how many rows fit in the market list area
    private int marketMaxVisible = 10;

    // Market scrollbar drag state
    private boolean mScrollDragging = false;
    private int mDragStartY, mDragStartOffset, mScrollTrackTop, mScrollTrackH, mScrollThumbH;

    // History scrollbar drag state
    private boolean hScrollDragging = false;
    private int hDragStartY, hDragStartOffset, hScrollTrackTop, hScrollTrackH, hScrollThumbH;

    // Withdraw state
    private final long[] withdrawAmounts = new long[8];
    private int editingCoinIndex = -1;          // which row is being typed into (-1 = none)
    private final StringBuilder editBuffer = new StringBuilder();

    private static final String[] COIN_NAMES = {
            "Copper", "Copper Sack", "Silver", "Silver Sack",
            "Gold", "Gold Sack", "Platinum", "Platinum Sack"
    };
    // Must match JakesEconomyItems VALUE_* constants
    private static final long[] COIN_VALUES = {
            10L, 90L, 1_000L, 9_000L, 100_000L, 900_000L, 10_000_000L, 90_000_000L
    };

    public MarketScreen() {
        super(Component.literal("Market"));
    }

    @Override
    protected void init() {
        panelWidth  = Math.min(360, width  - 20);
        panelHeight = Math.min(240, height - 40);
        guiLeft = (width  - panelWidth)  / 2;
        guiTop  = (height - panelHeight) / 2;

        categories.clear();
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        if (prices != null && prices.categories != null) {
            categories.addAll(prices.categories.keySet());
        }
        if (activeCategory == null && !categories.isEmpty()) {
            activeCategory = categories.get(0);
        }
        sortAscending = true; // always start A→Z on each fresh screen open
        refreshItemList();
    }

    private void refreshItemList() {
        visibleItems.clear();
        visibleItemCategories.clear();
        scrollOffset = 0;
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        if (prices == null) return;

        String query = searchBuffer.toString().trim().toLowerCase();
        if (!query.isEmpty()) {
            // Cross-tab search — include every item whose display name or id contains the query
            for (Map.Entry<String, Map<String, JakesEconomyPriceConfig.ItemPrice>> catEntry : prices.categories.entrySet()) {
                for (Map.Entry<String, JakesEconomyPriceConfig.ItemPrice> itemEntry : catEntry.getValue().entrySet()) {
                    String name = ItemDisplayHelper.getDisplayName(itemEntry.getKey()).toLowerCase();
                    String id   = itemEntry.getKey().toLowerCase();
                    if (name.contains(query) || id.contains(query)) {
                        visibleItems.add(itemEntry);
                        visibleItemCategories.add(catEntry.getKey());
                    }
                }
            }
            sortAndPartition();
            return;
        }

        if (activeCategory == null) return;
        Map<String, JakesEconomyPriceConfig.ItemPrice> items = prices.categories.get(activeCategory);
        if (items != null) {
            for (Map.Entry<String, JakesEconomyPriceConfig.ItemPrice> entry : items.entrySet()) {
                visibleItems.add(entry);
                visibleItemCategories.add(null);
            }
        }
        sortAndPartition();
    }

    /**
     * Sorts visibleItems + visibleItemCategories in tandem by display name (respecting sortAscending),
     * then partitions so unlocked items come first and locked items are pushed to the bottom.
     * Both groups remain alphabetically ordered within themselves.
     */
    private void sortAndPartition() {
        if (visibleItems.size() <= 1) return;
        record SortPair(Map.Entry<String, JakesEconomyPriceConfig.ItemPrice> item, String cat) {}
        List<SortPair> pairs = new ArrayList<>();
        for (int i = 0; i < visibleItems.size(); i++) {
            pairs.add(new SortPair(visibleItems.get(i), visibleItemCategories.get(i)));
        }

        // Alphabetical sort
        Comparator<SortPair> cmp = Comparator.comparing(
                p -> ItemDisplayHelper.getDisplayName(p.item().getKey()),
                String.CASE_INSENSITIVE_ORDER);
        if (!sortAscending) cmp = cmp.reversed();
        pairs.sort(cmp);

        // Partition: unlocked first, locked last (both groups keep the alphabetical order from above)
        List<SortPair> unlocked = new ArrayList<>();
        List<SortPair> locked   = new ArrayList<>();
        for (SortPair p : pairs) {
            if (ClientAdvancementLockCache.isLocked(p.item().getValue().achievementLock)) {
                locked.add(p);
            } else {
                unlocked.add(p);
            }
        }

        visibleItems.clear();
        visibleItemCategories.clear();
        for (SortPair p : unlocked) { visibleItems.add(p.item()); visibleItemCategories.add(p.cat()); }
        for (SortPair p : locked)   { visibleItems.add(p.item()); visibleItemCategories.add(p.cat()); }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Panel background + border
        graphics.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + panelHeight, 0xF0101010);
        graphics.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + 1, 0xFFAAAAAA);
        graphics.fill(guiLeft, guiTop + panelHeight - 1, guiLeft + panelWidth, guiTop + panelHeight, 0xFFAAAAAA);
        graphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + panelHeight, 0xFFAAAAAA);
        graphics.fill(guiLeft + panelWidth - 1, guiTop, guiLeft + panelWidth,  guiTop + panelHeight, 0xFFAAAAAA);

        // Left sidebar
        int sideX = guiLeft + 1;
        int sideBottom = guiTop + panelHeight - 1;
        graphics.fill(sideX, guiTop + 1, sideX + SIDEBAR_W, sideBottom, 0xF0181818);
        graphics.fill(sideX + SIDEBAR_W, guiTop + 1, sideX + SIDEBAR_W + 1, sideBottom, 0xFF444444);

        // Nav buttons — Market, Withdraw, History
        String[] navLabels = { "Market", "Withdraw", "History" };
        NavMode[] navModes = { NavMode.MARKET, NavMode.WITHDRAW, NavMode.HISTORY };
        int navBtnH = 22;
        int navBtnY = guiTop + 8;
        for (int n = 0; n < navLabels.length; n++) {
            boolean active  = navMode == navModes[n];
            boolean hovered = mouseX >= sideX + 3 && mouseX <= sideX + SIDEBAR_W - 3
                    && mouseY >= navBtnY && mouseY < navBtnY + navBtnH;
            graphics.fill(sideX + 3, navBtnY, sideX + SIDEBAR_W - 3, navBtnY + navBtnH,
                    active ? 0xFF333333 : (hovered ? 0xFF252525 : 0xFF1A1A1A));
            if (active) graphics.fill(sideX + 3, navBtnY, sideX + 5, navBtnY + navBtnH, 0xFFFFAA00);
            int lx = sideX + 3 + (SIDEBAR_W - 6 - font.width(navLabels[n])) / 2;
            graphics.drawString(font, navLabels[n], lx, navBtnY + 7, active ? 0xFFFFFFFF : 0xFF999999);
            navBtnY += navBtnH + 3;
        }

        // Content area
        int contentX = guiLeft + SIDEBAR_W + 2;
        int contentW = panelWidth - SIDEBAR_W - 2;
        switch (navMode) {
            case MARKET   -> renderMarketContent(graphics, mouseX, mouseY, contentX, contentW);
            case WITHDRAW -> renderWithdrawContent(graphics, mouseX, mouseY, contentX, contentW);
            case HISTORY  -> renderHistoryContent(graphics, mouseX, mouseY, contentX, contentW);
        }

        // Close button
        boolean closeHover = mouseX >= guiLeft + panelWidth - 16 && mouseX <= guiLeft + panelWidth - 2
                && mouseY >= guiTop + 2 && mouseY <= guiTop + 14;
        graphics.fill(guiLeft + panelWidth - 16, guiTop + 2, guiLeft + panelWidth - 2, guiTop + 14,
                closeHover ? 0xFF8B0000 : 0xFF550000);
        graphics.drawString(font, "X", guiLeft + panelWidth - 12, guiTop + 4, 0xFFFFFFFF);

        super.render(graphics, mouseX, mouseY, delta);
    }

    // ─── Market content ───────────────────────────────────────────────────────

    private void renderMarketContent(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentW) {
        boolean isSearching = searchBuffer.length() > 0;

        // Tab bar
        int tabBarY = guiTop - TAB_HEIGHT;
        g.fill(contentX, tabBarY, contentX + contentW, guiTop, 0xF0181818);
        int tabW = Math.min(TAB_WIDTH, contentW / Math.max(1, categories.size()));
        int tabX = contentX;
        for (String cat : categories) {
            boolean active  = !isSearching && cat.equals(activeCategory);
            boolean hovered = mouseX >= tabX && mouseX < tabX + tabW - 2 && mouseY >= tabBarY && mouseY < guiTop;
            g.fill(tabX, tabBarY, tabX + tabW - 2, guiTop, active ? 0xFF333333 : (hovered ? 0xFF272727 : 0xFF1A1A1A));
            g.fill(tabX, tabBarY, tabX + tabW - 2, tabBarY + (active ? 2 : 1), active ? 0xFFFFAA00 : (isSearching ? 0xFF444444 : 0xFF666666));
            g.drawString(font, cat, tabX + (tabW - 2 - font.width(cat)) / 2, tabBarY + 6, active ? 0xFFFFFFFF : 0xFF777777);
            tabX += tabW;
        }

        // Title (left) + balance (right)
        g.drawString(font, "Jake's Economy", contentX + 4, guiTop + 5, 0xFFFFAA00);
        String balStr = CurrencyFormatter.format(ClientBalanceCache.get(), true);
        g.drawString(font, balStr, contentX + contentW - font.width(balStr) - 20, guiTop + 5, 0xFF88FF88);

        // Search box — in the header row between title and balance
        int titleEnd  = contentX + 4 + font.width("Jake's Economy") + 6;
        int balStart  = contentX + contentW - font.width(balStr) - 24;
        searchX1 = titleEnd;
        searchX2 = Math.min(balStart, titleEnd + 130); // cap width at 130px
        searchY1 = guiTop + 1;
        searchY2 = guiTop + 14;
        boolean searchHovered = mouseX >= searchX1 && mouseX <= searchX2 && mouseY >= searchY1 && mouseY <= searchY2;
        int searchBoxColor = searchFocused ? 0xFF1A1A3A : (searchHovered ? 0xFF252525 : 0xFF151515);
        g.fill(searchX1, searchY1, searchX2, searchY2, searchBoxColor);
        g.fill(searchX1, searchY1, searchX2, searchY1 + 1, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(searchX1, searchY2 - 1, searchX2, searchY2, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(searchX1, searchY1, searchX1 + 1, searchY2, searchFocused ? 0xFFFFAA00 : 0xFF444444);
        g.fill(searchX2 - 1, searchY1, searchX2, searchY2, searchFocused ? 0xFFFFAA00 : 0xFF444444);

        String searchText = searchBuffer.toString();
        if (searchText.isEmpty() && !searchFocused) {
            g.drawString(font, "Search items...", searchX1 + 3, searchY1 + 3, 0xFF555555);
        } else {
            String displaySearch = searchText + (searchFocused && System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "");
            // Clip display text to fit box
            int maxSearchW = searchX2 - searchX1 - 6;
            while (font.width(displaySearch) > maxSearchW && displaySearch.length() > 1) {
                displaySearch = displaySearch.substring(1); // scroll left
            }
            g.drawString(font, displaySearch, searchX1 + 3, searchY1 + 3, 0xFFFFFFFF);
        }

        // Column headers
        int listTop = guiTop + 22;
        int iconColW = 16;

        // Sort toggle button — sits above the icon column, left of "Item"
        sortBtnX1 = contentX + 2;
        sortBtnY1 = listTop - 1;
        sortBtnX2 = contentX + 14;
        sortBtnY2 = listTop + 9;
        boolean sortHov = mouseX >= sortBtnX1 && mouseX <= sortBtnX2
                && mouseY >= sortBtnY1 && mouseY <= sortBtnY2;
        g.fill(sortBtnX1, sortBtnY1, sortBtnX2, sortBtnY2, sortHov ? 0xFF383838 : 0xFF252525);
        g.fill(sortBtnX1, sortBtnY1, sortBtnX2, sortBtnY1 + 1, 0xFF555555);
        g.fill(sortBtnX1, sortBtnY2 - 1, sortBtnX2, sortBtnY2, 0xFF555555);
        String sortLabel = sortAscending ? "A" : "Z";
        g.drawString(font, sortLabel, sortBtnX1 + (12 - font.width(sortLabel)) / 2, listTop,
                sortAscending ? 0xFF88FF88 : 0xFFFF8888);

        // Column label "Item" shifted right 6 px to clear the sort button
        g.drawString(font, "Item",  contentX + 4 + iconColW + 6,  listTop, 0xFFCCCCCC);
        g.drawString(font, "Price", contentX + contentW - 110,     listTop, 0xFFCCCCCC);
        g.drawString(font, "Trend", contentX + contentW - 50,      listTop, 0xFFCCCCCC);
        // If searching, show a "Category" label cue on the far left
        if (isSearching) g.drawString(font, "Tab", contentX + contentW - 155, listTop, 0xFF777777);
        g.fill(contentX + 2, listTop + 10, contentX + contentW - 2, listTop + 11, 0xFF444444);

        // Compute how many rows fit before the panel bottom
        int rowsTop    = guiTop + ROWS_AREA_TOP_OFFSET;
        int rowsBottom = guiTop + panelHeight - 3;
        marketMaxVisible = Math.max(1, (rowsBottom - rowsTop) / ROW_HEIGHT);

        // Clamp scroll
        int maxScroll = Math.max(0, visibleItems.size() - marketMaxVisible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;

        // Scrollbar geometry (store for drag)
        int scrollBarX = contentX + contentW - 6;
        mScrollTrackTop = rowsTop;
        mScrollTrackH   = marketMaxVisible * ROW_HEIGHT;
        mScrollThumbH   = visibleItems.size() > marketMaxVisible
                ? Math.max(10, mScrollTrackH * marketMaxVisible / visibleItems.size()) : 0;

        // Item rows
        JakesEconomyPriceConfig pricesForLock = JakesEconomyConfigManager.getPrices();
        int rowY = rowsTop;
        int endIndex = Math.min(scrollOffset + marketMaxVisible, visibleItems.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            // Clip — don't render past panel bottom
            if (rowY + ROW_HEIGHT > rowsBottom) break;

            Map.Entry<String, JakesEconomyPriceConfig.ItemPrice> entry = visibleItems.get(i);
            String itemId  = entry.getKey();
            String itemCat = visibleItemCategories.size() > i ? visibleItemCategories.get(i) : null;

            // Achievement lock state for this row
            int lockId = entry.getValue().achievementLock;
            boolean isLocked = ClientAdvancementLockCache.isLocked(lockId);
            String lockDisplayName = "";
            if (isLocked && pricesForLock != null) {
                JakesEconomyPriceConfig.AchievementLockDef lockDef = pricesForLock.achievementLocks.get(lockId);
                lockDisplayName = lockDef != null ? lockDef.displayName : "???";
            }

            boolean hovered = !isLocked && mouseX >= contentX + 2 && mouseX <= contentX + contentW - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (hovered) g.fill(contentX + 2, rowY, contentX + contentW - 10, rowY + ROW_HEIGHT, 0x33FFFFFF);

            // Item icon badge — always rendered so players can recognise locked items
            g.fill(contentX + 2, rowY + 2, contentX + 2 + 14, rowY + ROW_HEIGHT - 2, 0xFF1A1A1A);
            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
                g.pose().pushPose();
                g.pose().translate(contentX + 3, rowY + 3, 0);
                g.pose().scale(0.75f, 0.75f, 1.0f);
                g.renderItem(stack, 0, 0);
                g.pose().popPose();
            }

            String displayName = ItemDisplayHelper.getDisplayName(itemId);

            if (isLocked) {
                // Dark overlay dims the entire row including the icon
                g.fill(contentX + 2, rowY, contentX + contentW - 10, rowY + ROW_HEIGHT, 0xAA000000);
                // Grey item name
                g.drawString(font, displayName, contentX + 4 + iconColW + 6, rowY + 4, 0xFF555555);
                // Lock message centred horizontally across the full row
                String lockMsg = "Complete " + lockDisplayName;
                int rowLeft  = contentX + 2;
                int rowRight = contentX + contentW - 10;
                int lockMsgX = rowLeft + (rowRight - rowLeft - font.width(lockMsg)) / 2;
                g.drawString(font, lockMsg, lockMsgX, rowY + 4, 0xFFAA4444);
            } else {
                double livePrice = ClientMarketListingCache.getPrice(itemId, entry.getValue().basePrice);
                String trend      = ClientMarketListingCache.getTrend(itemId);
                int    trendColor = ClientMarketListingCache.getTrendColor(itemId);

                g.drawString(font, displayName,                               contentX + 4 + iconColW + 6, rowY + 4, 0xFFFFFFFF);
                g.drawString(font, CurrencyFormatter.format(livePrice, true), contentX + contentW - 110, rowY + 4, 0xFFFFDD55);
                g.drawString(font, trend,                                     contentX + contentW - 50,  rowY + 4, trendColor);
            }

            // In search mode, show a small category badge on the right
            if (isSearching && itemCat != null) {
                String shortCat = itemCat.length() > 4 ? itemCat.substring(0, 4) : itemCat;
                g.drawString(font, shortCat, contentX + contentW - 155, rowY + 4, 0xFF888888);
            }

            rowY += ROW_HEIGHT;
        }

        if (visibleItems.isEmpty()) {
            String msg = isSearching ? "No results for \"" + searchBuffer + "\"."
                    : (activeCategory == null ? "No categories configured." : "No items in this category.");
            g.drawString(font, msg, contentX + (contentW - font.width(msg)) / 2, guiTop + 80, 0xFF666666);
        }

        // Scrollbar — always within track bounds
        if (visibleItems.size() > marketMaxVisible) {
            int thumbY = mScrollTrackTop + scrollOffset * (mScrollTrackH - mScrollThumbH) / Math.max(1, maxScroll);
            thumbY = Math.min(thumbY, mScrollTrackTop + mScrollTrackH - mScrollThumbH);
            g.fill(scrollBarX, mScrollTrackTop, scrollBarX + 4, mScrollTrackTop + mScrollTrackH, 0x33FFFFFF);
            g.fill(scrollBarX, thumbY,          scrollBarX + 4, thumbY + mScrollThumbH,          0xFFAAAAAA);
        }
    }

    // ─── Withdraw content ─────────────────────────────────────────────────────

    private static final int WD_ROW_H   = 18;
    private static final int WD_ROW_GAP = 1;
    private static final int WD_ARROW_W = 12;
    private static final int WD_AMT_W   = 28;

    /** Returns the Y start of withdraw row i, relative to screen. */
    private int wdRowY(int i) { return guiTop + 20 + i * (WD_ROW_H + WD_ROW_GAP); }

    /** Returns the X of the ▼ button for a withdraw row. */
    private int wdDownX(int contentX, int contentW) {
        return contentX + contentW - 4 - WD_ARROW_W * 2 - WD_AMT_W - 2;
    }

    private void renderWithdrawContent(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentW) {
        // Title + balance
        g.drawString(font, "Withdraw to Coins", contentX + 4, guiTop + 6, 0xFFFFAA00);
        String balStr = CurrencyFormatter.format(ClientBalanceCache.get(), true);
        g.drawString(font, balStr, contentX + contentW - font.width(balStr) - 20, guiTop + 6, 0xFF88FF88);

        int downX = wdDownX(contentX, contentW);
        int amtX  = downX + WD_ARROW_W + 2;
        int upX   = amtX  + WD_AMT_W   + 2;

        for (int i = 0; i < 8; i++) {
            int rowY = wdRowY(i);
            long amt = withdrawAmounts[i];

            // Row background
            g.fill(contentX + 2, rowY, contentX + contentW - 4, rowY + WD_ROW_H - 1, 0xFF1A1A1A);

            // Coin name
            g.drawString(font, COIN_NAMES[i], contentX + 5, rowY + 5, 0xFFDDDDDD);

            // Coin value label
            String valStr = "=" + CurrencyFormatter.format(COIN_VALUES[i], false);
            g.drawString(font, valStr, contentX + 68, rowY + 5, 0xFF666666);

            // ▼ button
            boolean downHov = mouseX >= downX && mouseX < downX + WD_ARROW_W && mouseY >= rowY + 2 && mouseY < rowY + WD_ROW_H - 2;
            g.fill(downX, rowY + 2, downX + WD_ARROW_W, rowY + WD_ROW_H - 2, downHov ? 0xFF555555 : 0xFF2A2A2A);
            g.drawString(font, "▼", downX + 2, rowY + 4, 0xFFCCCCCC);

            // Amount field — highlight when being edited
            boolean editing = editingCoinIndex == i;
            g.fill(amtX, rowY + 1, amtX + WD_AMT_W, rowY + WD_ROW_H - 1, editing ? 0xFF1A1A3A : 0xFF111111);
            String amtStr = editing
                    ? editBuffer.toString() + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : " ")
                    : String.valueOf(amt);
            g.drawString(font, amtStr, amtX + (WD_AMT_W - font.width(amtStr)) / 2, rowY + 5, 0xFFFFFFFF);

            // ▲ button
            boolean upHov = mouseX >= upX && mouseX < upX + WD_ARROW_W && mouseY >= rowY + 2 && mouseY < rowY + WD_ROW_H - 2;
            g.fill(upX, rowY + 2, upX + WD_ARROW_W, rowY + WD_ROW_H - 2, upHov ? 0xFF555555 : 0xFF2A2A2A);
            g.drawString(font, "▲", upX + 2, rowY + 4, 0xFFCCCCCC);
        }

        // Total + Confirm: placed just below the last row, guaranteed inside the panel
        int afterRows = wdRowY(8);  // y just after last row
        long total = computeWithdrawTotal();
        String totalStr = "Total: " + CurrencyFormatter.format(total, true);
        g.drawString(font, totalStr, contentX + 5, afterRows + 3, 0xFFFFDD55);

        int btnW = 70;
        int btnH = 14;
        int btnX = contentX + contentW - btnW - 6;
        int btnY = afterRows + 1;
        boolean btnHov = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH && total > 0;
        g.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHov ? 0xFF005500 : (total > 0 ? 0xFF003300 : 0xFF1A1A1A));
        g.drawString(font, "Confirm", btnX + (btnW - font.width("Confirm")) / 2, btnY + 3,
                total > 0 ? 0xFFFFFFFF : 0xFF555555);
    }

    private long computeWithdrawTotal() {
        long total = 0;
        for (int i = 0; i < 8; i++) total += withdrawAmounts[i] * COIN_VALUES[i];
        return total;
    }

    // ─── History content ──────────────────────────────────────────────────────

    private static final int HIST_ROW_H = 16;
    // scrollbar width reserved on right edge of content area
    private static final int HIST_SCROLL_W = 6;

    private void renderHistoryContent(GuiGraphics g, int mouseX, int mouseY, int contentX, int contentW) {
        // Title
        g.drawString(font, "Transaction History", contentX + 4, guiTop + 6, 0xFFFFAA00);

        List<TransactionEntry> history = ClientTransactionHistoryCache.get();

        // Usable width after reserving scrollbar space
        int usableW = contentW - HIST_SCROLL_W - 4;

        // Fixed column X positions (all relative to contentX, within usableW)
        // Badge: 0..14, Name: 18..nameEnd, Qty: nameEnd+2..qtyEnd, Amt: qtyEnd+2..amtEnd, Time: amtEnd+2..end
        int colBadgeX = contentX + 2;
        int colNameX  = contentX + 18;
        // Time col is right-aligned — "HH:mm" = 5 chars, font width ~25 px, give 28
        int colTimeX  = contentX + usableW - 28;
        // Amount right of qty, left of time — give 50 px for formatted amount
        int colAmtX   = colTimeX - 52;
        // Qty sign+number, give 26 px
        int colQtyX   = colAmtX - 28;
        // Name fills from colNameX to colQtyX-2
        int colNameMaxW = colQtyX - colNameX - 4;

        // Column headers — center "T" over the 14px badge same as B/S/W
        int listTop = guiTop + 20;
        g.drawString(font, "T", colBadgeX + (14 - font.width("T")) / 2, listTop, 0xFFAAAAAA);
        g.drawString(font, "Item",   colNameX,         listTop, 0xFFAAAAAA);
        g.drawString(font, "Qty",    colQtyX,          listTop, 0xFFAAAAAA);
        g.drawString(font, "Amt",    colAmtX,          listTop, 0xFFAAAAAA);
        g.drawString(font, "Time",   colTimeX,         listTop, 0xFFAAAAAA);
        g.fill(contentX + 2, listTop + 10, contentX + usableW, listTop + 11, 0xFF444444);

        if (history.isEmpty()) {
            String msg = "No transactions yet.";
            g.drawString(font, msg, contentX + (contentW - font.width(msg)) / 2, guiTop + 80, 0xFF666666);
            return;
        }

        // Calculate how many rows fit in the available height
        int listAreaTop    = listTop + 14;
        int listAreaBottom = guiTop + panelHeight - 4;
        int visibleRows    = Math.max(1, (listAreaBottom - listAreaTop) / HIST_ROW_H);

        // Clamp scroll offset
        int maxScroll = Math.max(0, history.size() - visibleRows);
        if (historyScrollOffset > maxScroll) historyScrollOffset = maxScroll;

        int rowY = listAreaTop;
        int end  = Math.min(historyScrollOffset + visibleRows, history.size());
        for (int i = historyScrollOffset; i < end; i++) {
            TransactionEntry tx = history.get(i);

            // Alternating row tint
            if (i % 2 == 0) g.fill(contentX + 2, rowY, contentX + usableW, rowY + HIST_ROW_H, 0x0AFFFFFF);

            // Type badge
            String typeLabel;
            int    typeColor;
            switch (tx.type()) {
                case "BUY"      -> { typeLabel = "B"; typeColor = 0xFF44FF88; }
                case "SELL"     -> { typeLabel = "S"; typeColor = 0xFFFF6644; }
                case "WITHDRAW" -> { typeLabel = "W"; typeColor = 0xFFFFDD55; }
                default         -> { typeLabel = "?"; typeColor = 0xFF888888; }
            }
            g.fill(colBadgeX, rowY + 2, colBadgeX + 14, rowY + HIST_ROW_H - 2, 0xFF1A1A1A);
            g.drawString(font, typeLabel, colBadgeX + (14 - font.width(typeLabel)) / 2, rowY + 4, typeColor);

            // Item name — truncate to fit
            String itemName = tx.type().equals("WITHDRAW") ? "Coins" : ItemDisplayHelper.getDisplayName(tx.itemId());
            while (font.width(itemName) > colNameMaxW && itemName.length() > 1) {
                itemName = itemName.substring(0, itemName.length() - 1);
            }
            g.drawString(font, itemName, colNameX, rowY + 4, 0xFFFFFFFF);

            // Quantity (signed)
            String qtyStr   = (tx.type().equals("BUY") || tx.type().equals("WITHDRAW") ? "-" : "+") + tx.quantity();
            int    qtyColor = tx.type().equals("SELL") ? 0xFF44FF88 : 0xFFFF6644;
            g.drawString(font, qtyStr, colQtyX, rowY + 4, qtyColor);

            // Amount — cast to double so it uses the 2-decimal overload
            String amtStr = CurrencyFormatter.format((double) tx.amount(), false);
            g.drawString(font, amtStr, colAmtX, rowY + 4, 0xFFFFDD55);

            // Time (HH:mm)
            String timeStr = TIME_FMT.format(Instant.ofEpochMilli(tx.timestamp()));
            g.drawString(font, timeStr, colTimeX, rowY + 4, 0xFF888888);

            rowY += HIST_ROW_H;
        }

        // Scrollbar — store geometry for drag, clamp thumb within track
        if (history.size() > visibleRows) {
            int scrollAreaH = visibleRows * HIST_ROW_H;
            int scrollBarX  = contentX + usableW + 2;
            hScrollTrackTop = listAreaTop;
            hScrollTrackH   = scrollAreaH;
            hScrollThumbH   = Math.max(10, scrollAreaH * visibleRows / history.size());
            int maxScroll2  = Math.max(1, history.size() - visibleRows);
            int thumbY = listAreaTop + historyScrollOffset * (scrollAreaH - hScrollThumbH) / maxScroll2;
            thumbY = Math.min(thumbY, listAreaTop + scrollAreaH - hScrollThumbH);
            g.fill(scrollBarX, listAreaTop, scrollBarX + HIST_SCROLL_W - 2, listAreaTop + scrollAreaH, 0x33FFFFFF);
            g.fill(scrollBarX, thumbY,      scrollBarX + HIST_SCROLL_W - 2, thumbY + hScrollThumbH,    0xFFAAAAAA);
        }
    }

    // ─── Mouse & keyboard ─────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Close button
        if (mouseX >= guiLeft + panelWidth - 16 && mouseX <= guiLeft + panelWidth - 2
                && mouseY >= guiTop + 2 && mouseY <= guiTop + 14) {
            onClose();
            return true;
        }

        // Sidebar nav
        int sideX = guiLeft + 1;
        NavMode[] navModes = { NavMode.MARKET, NavMode.WITHDRAW, NavMode.HISTORY };
        int navBtnH = 22;
        int navBtnY = guiTop + 8;
        for (int n = 0; n < 3; n++) {
            if (mouseX >= sideX + 3 && mouseX <= sideX + SIDEBAR_W - 3
                    && mouseY >= navBtnY && mouseY < navBtnY + navBtnH) {
                navMode = navModes[n];
                commitEdit();
                return true;
            }
            navBtnY += navBtnH + 3;
        }

        int contentX = guiLeft + SIDEBAR_W + 2;
        int contentW = panelWidth - SIDEBAR_W - 2;

        return switch (navMode) {
            case MARKET   -> handleMarketClick(mouseX, mouseY, button, contentX, contentW);
            case WITHDRAW -> handleWithdrawClick(mouseX, mouseY, button, contentX, contentW);
            case HISTORY  -> {
                int contentX2 = guiLeft + SIDEBAR_W + 2;
                int contentW2 = panelWidth - SIDEBAR_W - 2;
                int usableW2  = contentW2 - HIST_SCROLL_W - 4;
                int scrollBarX2 = contentX2 + usableW2 + 2;
                if (hScrollThumbH > 0 && mouseX >= scrollBarX2 && mouseX <= scrollBarX2 + HIST_SCROLL_W - 2
                        && mouseY >= hScrollTrackTop && mouseY <= hScrollTrackTop + hScrollTrackH) {
                    hScrollDragging  = true;
                    hDragStartY      = (int) mouseY;
                    hDragStartOffset = historyScrollOffset;
                }
                yield super.mouseClicked(mouseX, mouseY, button);
            }
        };
    }

    private boolean handleMarketClick(double mouseX, double mouseY, int button, int contentX, int contentW) {
        // Search box — click to focus/unfocus
        if (mouseX >= searchX1 && mouseX <= searchX2 && mouseY >= searchY1 && mouseY <= searchY2) {
            searchFocused = true;
            return true;
        }
        searchFocused = false;

        // Scrollbar drag start
        int scrollBarX = contentX + contentW - 6;
        if (visibleItems.size() > marketMaxVisible && mouseX >= scrollBarX && mouseX <= scrollBarX + 4
                && mouseY >= mScrollTrackTop && mouseY <= mScrollTrackTop + mScrollTrackH) {
            mScrollDragging  = true;
            mDragStartY      = (int) mouseY;
            mDragStartOffset = scrollOffset;
            return true;
        }

        // Sort button — toggle A→Z / Z→A
        if (mouseX >= sortBtnX1 && mouseX <= sortBtnX2 && mouseY >= sortBtnY1 && mouseY <= sortBtnY2) {
            sortAscending = !sortAscending;
            refreshItemList();
            return true;
        }

        // Tab clicks — only when not searching, or clicking a tab clears search
        int tabBarY = guiTop - TAB_HEIGHT;
        int tabW = Math.min(TAB_WIDTH, contentW / Math.max(1, categories.size()));
        int tabX = contentX;
        for (String cat : categories) {
            if (mouseX >= tabX && mouseX < tabX + tabW - 2 && mouseY >= tabBarY && mouseY < guiTop) {
                // Clear search and switch tab
                searchBuffer.setLength(0);
                searchFocused = false;
                activeCategory = cat;
                refreshItemList();
                return true;
            }
            tabX += tabW;
        }

        // Item row clicks — only within the visible track area
        int rowsBottom = guiTop + panelHeight - 3;
        int rowY = guiTop + ROWS_AREA_TOP_OFFSET;
        int endIndex = Math.min(scrollOffset + marketMaxVisible, visibleItems.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            if (rowY + ROW_HEIGHT > rowsBottom) break;
            if (mouseX >= contentX + 2 && mouseX <= contentX + contentW - 10
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                // Silently absorb clicks on locked items — nothing to open
                if (ClientAdvancementLockCache.isLocked(visibleItems.get(i).getValue().achievementLock)) {
                    return true;
                }
                // If this row came from a cross-tab search, swap to its tab first
                String itemCat = visibleItemCategories.size() > i ? visibleItemCategories.get(i) : null;
                if (itemCat != null) {
                    activeCategory = itemCat;
                }
                if (minecraft != null) minecraft.setScreen(new MarketItemScreen(visibleItems.get(i).getKey(), this));
                return true;
            }
            rowY += ROW_HEIGHT;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleWithdrawClick(double mouseX, double mouseY, int button, int contentX, int contentW) {
        int downX = wdDownX(contentX, contentW);
        int amtX  = downX + WD_ARROW_W + 2;
        int upX   = amtX  + WD_AMT_W   + 2;

        for (int i = 0; i < 8; i++) {
            int rowY = wdRowY(i);
            if (mouseY >= rowY + 2 && mouseY < rowY + WD_ROW_H - 2) {
                if (mouseX >= downX && mouseX < downX + WD_ARROW_W) {
                    commitEdit();
                    if (withdrawAmounts[i] > 0) withdrawAmounts[i]--;
                    return true;
                }
                if (mouseX >= upX && mouseX < upX + WD_ARROW_W) {
                    commitEdit();
                    withdrawAmounts[i]++;
                    return true;
                }
                // Amount field — click to type
                if (mouseX >= amtX && mouseX < amtX + WD_AMT_W) {
                    editingCoinIndex = i;
                    editBuffer.setLength(0);
                    editBuffer.append(withdrawAmounts[i]);
                    return true;
                }
            }
        }

        // Confirm button
        int afterRows = wdRowY(8);
        int btnW = 70; int btnH = 14;
        int btnX = contentX + contentW - btnW - 6;
        int btnY = afterRows + 1;
        if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH) {
            long total = computeWithdrawTotal();
            if (total > 0) {
                commitEdit();
                MarketPacketSender.sendWithdraw(
                        withdrawAmounts[0], withdrawAmounts[1],
                        withdrawAmounts[2], withdrawAmounts[3],
                        withdrawAmounts[4], withdrawAmounts[5],
                        withdrawAmounts[6], withdrawAmounts[7]);
                // Amounts not reseting is intentional
            }
            return true;
        }

        // Click elsewhere clears the active edit
        commitEdit();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Commits the current edit buffer into withdrawAmounts and clears the edit state. */
    private void commitEdit() {
        if (editingCoinIndex >= 0) {
            try { withdrawAmounts[editingCoinIndex] = Math.max(0, Long.parseLong(editBuffer.toString())); }
            catch (NumberFormatException ignored) {}
            editingCoinIndex = -1;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Withdraw edit field takes priority
        if (editingCoinIndex >= 0) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { commitEdit(); return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) { editingCoinIndex = -1; return true; }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && editBuffer.length() > 0) { editBuffer.deleteCharAt(editBuffer.length() - 1); return true; }
            return true;
        }
        // Search box input
        if (searchFocused) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                if (searchBuffer.length() > 0) { searchBuffer.setLength(0); refreshItemList(); }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                if (searchBuffer.length() > 0) { searchBuffer.deleteCharAt(searchBuffer.length() - 1); refreshItemList(); }
                return true;
            }
            return true; // consume all keys while search is focused
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (editingCoinIndex >= 0 && Character.isDigit(c) && editBuffer.length() < 10) {
            editBuffer.append(c);
            return true;
        }
        if (searchFocused && !Character.isISOControl(c) && searchBuffer.length() < 40) {
            searchBuffer.append(c);
            refreshItemList();
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mScrollDragging && mScrollTrackH > mScrollThumbH) {
            int travel    = mScrollTrackH - mScrollThumbH;
            int maxScroll = Math.max(0, visibleItems.size() - marketMaxVisible);
            int delta     = (int) ((mouseY - mDragStartY) * maxScroll / (double) travel);
            scrollOffset  = (int) Math.clamp(mDragStartOffset + delta, 0, maxScroll);
            return true;
        }
        if (hScrollDragging && hScrollTrackH > hScrollThumbH) {
            List<TransactionEntry> history = ClientTransactionHistoryCache.get();
            int listAreaTop    = guiTop + 34;
            int listAreaBottom = guiTop + panelHeight - 4;
            int visibleRows    = Math.max(1, (listAreaBottom - listAreaTop) / HIST_ROW_H);
            int maxScroll      = Math.max(0, history.size() - visibleRows);
            int travel         = hScrollTrackH - hScrollThumbH;
            int delta          = (int) ((mouseY - hDragStartY) * maxScroll / (double) travel);
            historyScrollOffset = (int) Math.clamp(hDragStartOffset + delta, 0, maxScroll);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mScrollDragging = false;
        hScrollDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (navMode == NavMode.MARKET) {
            int maxScroll = Math.max(0, visibleItems.size() - marketMaxVisible);
            scrollOffset  = (int) Math.clamp(scrollOffset - vertical, 0, maxScroll);
        } else if (navMode == NavMode.HISTORY) {
            List<TransactionEntry> history = ClientTransactionHistoryCache.get();
            int listAreaTop    = guiTop + 34;
            int listAreaBottom = guiTop + panelHeight - 4;
            int visibleRows    = Math.max(1, (listAreaBottom - listAreaTop) / HIST_ROW_H);
            int maxScroll      = Math.max(0, history.size() - visibleRows);
            historyScrollOffset = (int) Math.clamp(historyScrollOffset - vertical, 0, maxScroll);
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}

