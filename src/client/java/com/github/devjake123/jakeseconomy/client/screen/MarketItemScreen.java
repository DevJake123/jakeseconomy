package com.github.devjake123.jakeseconomy.client.screen;

import com.github.devjake123.jakeseconomy.client.ClientAdvancementLockCache;
import com.github.devjake123.jakeseconomy.client.ClientMarketListingCache;
import com.github.devjake123.jakeseconomy.client.network.MarketPacketSender;
import com.github.devjake123.jakeseconomy.config.JakesEconomyConfigManager;
import com.github.devjake123.jakeseconomy.config.JakesEconomyPriceConfig;
import com.github.devjake123.jakeseconomy.economy.CurrencyFormatter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Per-item detail screen. All buttons are custom flat rects — no vanilla chrome.
 * Left-click BUY/SELL = 1, Shift+click = 64, Right-click = custom amount screen.
 */
public class MarketItemScreen extends Screen {

    // The item this screen is showing
    private final String itemId;

    // The parent screen to return to when Back is pressed
    private final Screen parent;

    private int guiLeft, guiTop, panelWidth, panelHeight;
    private boolean shiftHeld = false;

    public MarketItemScreen(String itemId, Screen parent) {
        super(Component.literal("Market"));
        this.itemId = itemId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth  = Math.min(280, width  - 20);
        panelHeight = Math.min(200, height - 40);
        guiLeft = (width  - panelWidth)  / 2;
        guiTop  = (height - panelHeight) / 2;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Panel
        graphics.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + panelHeight, 0xF0101010);
        graphics.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + 1, 0xFFAAAAAA);
        graphics.fill(guiLeft, guiTop + panelHeight - 1, guiLeft + panelWidth, guiTop + panelHeight, 0xFFAAAAAA);
        graphics.fill(guiLeft, guiTop, guiLeft + 1, guiTop + panelHeight, 0xFFAAAAAA);
        graphics.fill(guiLeft + panelWidth - 1, guiTop, guiLeft + panelWidth, guiTop + panelHeight, 0xFFAAAAAA);

        // Back button
        drawFlatButton(graphics, guiLeft + 4, guiTop + 4, 60, 14, "← Back", mouseX, mouseY, 0xFF222222, 0xFF333333);

        // Item icon (32×32, scaled 2×)
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rl));
            int iconX = guiLeft + panelWidth / 2 - 16;
            int iconY = guiTop + 36;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX, iconY, 0);
            graphics.pose().scale(2.0f, 2.0f, 1.0f);
            graphics.renderItem(stack, 0, 0);
            graphics.pose().popPose();
        }

        // Resolve lock state
        JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
        JakesEconomyPriceConfig.ItemPrice itemPrice = prices != null ? prices.allItems().get(itemId) : null;
        int lockId = itemPrice != null ? itemPrice.achievementLock : 0;
        boolean isLocked = ClientAdvancementLockCache.isLocked(lockId);
        String lockDisplayName = "";
        if (isLocked && prices != null) {
            JakesEconomyPriceConfig.AchievementLockDef lockDef = prices.achievementLocks.get(lockId);
            lockDisplayName = lockDef != null ? lockDef.displayName : "???";
        }

        // Display name
        String displayName = ItemDisplayHelper.getDisplayName(itemId);
        int nameColor = isLocked ? 0xFF666666 : 0xFFFFFFFF;
        graphics.drawString(font, displayName, guiLeft + panelWidth / 2 - font.width(displayName) / 2, guiTop + 78, nameColor);

        if (isLocked) {
            // Dark overlay over the icon area
            graphics.fill(guiLeft + panelWidth / 2 - 16, guiTop + 36,
                          guiLeft + panelWidth / 2 + 16, guiTop + 68, 0xAA000000);

            // Lock message lines
            String line1 = "Complete \"" + lockDisplayName + "\"";
            String line2 = "to start trading this item.";
            int cx = guiLeft + panelWidth / 2;
            graphics.drawString(font, line1, cx - font.width(line1) / 2, guiTop + 92,  0xFFAA4444);
            graphics.drawString(font, line2, cx - font.width(line2) / 2, guiTop + 104, 0xFF774444);

            // Greyed-out disabled button area so the layout is clear
            int sellX = guiLeft + panelWidth / 2 - 90;
            int buyX  = guiLeft + panelWidth / 2 + 10;
            int btnY  = guiTop + panelHeight - 52;
            graphics.fill(sellX, btnY, sellX + 80, btnY + 22, 0xFF1A1A1A);
            graphics.fill(buyX,  btnY, buyX  + 80, btnY + 22, 0xFF1A1A1A);
            graphics.drawString(font, "SELL", sellX + (80 - font.width("SELL")) / 2, btnY + 7, 0xFF333333);
            graphics.drawString(font, "BUY",  buyX  + (80 - font.width("BUY"))  / 2, btnY + 7, 0xFF333333);
        } else {
            // Price and trend — read from the server-synced cache so multiplayer shows live data
            double livePrice = ClientMarketListingCache.getPrice(itemId, -1);
            String priceText = livePrice >= 0 ? "Price: " + CurrencyFormatter.format(livePrice, true) : "Price: N/A";
            graphics.drawString(font, priceText, guiLeft + panelWidth / 2 - font.width(priceText) / 2, guiTop + 92, 0xFFFFDD55);

            // Trend
            String trend      = ClientMarketListingCache.getTrendLong(itemId);
            int    trendColor = ClientMarketListingCache.getTrendColor(itemId);
            graphics.drawString(font, trend, guiLeft + panelWidth / 2 - font.width(trend) / 2, guiTop + 106, trendColor);


            // SELL (dark red) and BUY (dark green) buttons
            int sellX = guiLeft + panelWidth / 2 - 90;
            int buyX  = guiLeft + panelWidth / 2 + 10;
            int btnY  = guiTop + panelHeight - 52;
            drawFlatButton(graphics, sellX, btnY, 80, 22, "SELL", mouseX, mouseY, 0xFF6B0000, 0xFF8B0000);
            drawFlatButton(graphics, buyX,  btnY, 80, 22, "BUY",  mouseX, mouseY, 0xFF004D00, 0xFF006400);

            // Hint below buttons
            String hint = shiftHeld ? "Release Shift for ×1" : "Shift = ×64   Right-click = custom";
            graphics.drawString(font, hint, guiLeft + panelWidth / 2 - font.width(hint) / 2, guiTop + panelHeight - 26, 0xFF666666);
        }

        super.render(graphics, mouseX, mouseY, delta);
    }

    /**
     * Right-click on BUY or SELL opens the custom amount screen.
     * We intercept raw mouse clicks before they reach the buttons.
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int sellX = guiLeft + panelWidth / 2 - 90;
        int buyX  = guiLeft + panelWidth / 2 + 10;
        int btnY  = guiTop + panelHeight - 52;

        // Back
        if (mouseX >= guiLeft + 4 && mouseX <= guiLeft + 64 && mouseY >= guiTop + 4 && mouseY <= guiTop + 18) {
            minecraft.setScreen(parent);
            return true;
        }

        // SELL / BUY — only if item is not locked
        if (mouseY >= btnY && mouseY <= btnY + 22) {
            JakesEconomyPriceConfig prices = JakesEconomyConfigManager.getPrices();
            JakesEconomyPriceConfig.ItemPrice itemPrice = prices != null ? prices.allItems().get(itemId) : null;
            boolean isLocked = ClientAdvancementLockCache.isLocked(itemPrice != null ? itemPrice.achievementLock : 0);
            if (!isLocked) {
                if (mouseX >= sellX && mouseX <= sellX + 80) {
                    if (button == 1) minecraft.setScreen(new MarketAmountScreen(itemId, false, this));
                    else MarketPacketSender.sendSell(itemId, shiftHeld ? 64 : 1);
                    return true;
                }
                if (mouseX >= buyX && mouseX <= buyX + 80) {
                    if (button == 1) minecraft.setScreen(new MarketAmountScreen(itemId, true, this));
                    else MarketPacketSender.sendBuy(itemId, shiftHeld ? 64 : 1);
                    return true;
                }
            } else if ((mouseX >= sellX && mouseX <= sellX + 80) || (mouseX >= buyX && mouseX <= buyX + 80)) {
                return true; // absorb clicks on disabled buttons
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) shiftHeld = true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) shiftHeld = false;
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // Draws a flat coloured button with a 1px grey border and centred label
    private void drawFlatButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, int color, int hover) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hovered ? hover : color);
        g.fill(x,y, x + w,y + 1,0xFFAAAAAA);
        g.fill(x,y + h - 1, x + w,y + h,0xFFAAAAAA);
        g.fill(x,y,x + 1,y + h,0xFFAAAAAA);
        g.fill(x + w - 1, y,x + w,y + h,0xFFAAAAAA);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
