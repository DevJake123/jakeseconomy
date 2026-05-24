package com.github.devjake123.testmod.client.screen;

import com.github.devjake123.testmod.client.network.MarketPacketSender;
import com.github.devjake123.testmod.economy.CurrencyFormatter;
import com.github.devjake123.testmod.economy.MarketManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom amount input — opened by right-clicking BUY or SELL in MarketItemScreen.
 * Back and Confirm are custom flat buttons. EditBox is kept as a widget.
 */
public class MarketAmountScreen extends Screen {

    private final String  itemId;
    private final boolean isBuying; // true = buy, false = sell
    private final Screen  parent;

    private int guiLeft, guiTop, panelWidth, panelHeight;
    private EditBox amountField;

    public MarketAmountScreen(String itemId, boolean isBuying, Screen parent) {
        super(Component.literal("Market"));
        this.itemId   = itemId;
        this.isBuying = isBuying;
        this.parent   = parent;
    }

    @Override
    protected void init() {
        panelWidth  = Math.min(240, width  - 20);
        panelHeight = Math.min(160, height - 40);
        guiLeft = (width  - panelWidth)  / 2;
        guiTop  = (height - panelHeight) / 2;

        // EditBox is a widget — kept so keyboard input works normally
        amountField = new EditBox(font,
                guiLeft + panelWidth / 2 - 50, guiTop + 76, 100, 16,
                Component.literal("Amount"));
        amountField.setMaxLength(10);
        amountField.setValue("1");
        amountField.setFilter(s -> s.isEmpty() || s.matches("\\d+"));
        amountField.setBordered(false); // we draw our own border
        addRenderableWidget(amountField);
        setFocused(amountField);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        // Panel
        graphics.fill(guiLeft, guiTop, guiLeft + panelWidth, guiTop + panelHeight, 0xF0101010);
        graphics.fill(guiLeft,guiTop,guiLeft + panelWidth, guiTop + 1, 0xFFAAAAAA);
        graphics.fill(guiLeft,guiTop + panelHeight - 1, guiLeft + panelWidth, guiTop + panelHeight,   0xFFAAAAAA);
        graphics.fill(guiLeft,guiTop,guiLeft + 1,guiTop + panelHeight,0xFFAAAAAA);
        graphics.fill(guiLeft + panelWidth - 1, guiTop,guiLeft + panelWidth, guiTop + panelHeight,      0xFFAAAAAA);

        // Back button
        drawFlatButton(graphics, guiLeft + 4, guiTop + 4, 60, 14, "← Back", mouseX, mouseY, 0xFF222222, 0xFF333333);

        // Title
        String action = isBuying ? "Buy" : "Sell";
        String displayName = itemId.contains(":") ? itemId.split(":")[1] : itemId;
        displayName = displayName.replace("_", " ");
        if (!displayName.isEmpty()) displayName = Character.toUpperCase(displayName.charAt(0)) + displayName.substring(1);
        String title = action + " — " + displayName;
        graphics.drawString(font, title, guiLeft + panelWidth / 2 - font.width(title) / 2, guiTop + 26, 0xFFFFFFFF);

        // "Amount:" label
        graphics.drawString(font, "Amount:", guiLeft + panelWidth / 2 - font.width("Amount:") / 2, guiTop + 62, 0xFFCCCCCC);

        // EditBox border (since we turned off the built-in border)
        int fieldX = guiLeft + panelWidth / 2 - 50;
        int fieldY = guiTop + 74;
        graphics.fill(fieldX - 1,       fieldY - 1,      fieldX + 101, fieldY + 17, 0xFFAAAAAA); // border
        graphics.fill(fieldX,           fieldY,          fieldX + 100, fieldY + 16, 0xFF1A1A1A); // background

        // Live total
        long qty = 0;
        try { qty = Long.parseLong(amountField.getValue()); } catch (NumberFormatException ignored) {}
        if (qty > 0) {
            double price = -1;
            try { price = MarketManager.get().getCurrentPrice(itemId); } catch (IllegalStateException ignored) {}
            if (price >= 0) {
                long total = isBuying ? (long) Math.ceil(price * qty) : (long) Math.floor(price * qty);
                String costLabel = (isBuying ? "Total cost: " : "You receive: ") + CurrencyFormatter.format(total, true);
                graphics.drawString(font, costLabel, guiLeft + panelWidth / 2 - font.width(costLabel) / 2, guiTop + 100, 0xFFFFDD55);
            }
        }

        // Confirm button
        int confirmColor = isBuying ? 0xFF004D00 : 0xFF6B0000;
        int confirmHover = isBuying ? 0xFF006400 : 0xFF8B0000;
        drawFlatButton(graphics, guiLeft + panelWidth / 2 - 40, guiTop + panelHeight - 34, 80, 20,
                "Confirm", mouseX, mouseY, confirmColor, confirmHover);

        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Back
        if (mouseX >= guiLeft + 4 && mouseX <= guiLeft + 64 && mouseY >= guiTop + 4 && mouseY <= guiTop + 18) {
            minecraft.setScreen(parent);
            return true;
        }
        // Confirm
        int confirmX = guiLeft + panelWidth / 2 - 40;
        int confirmY = guiTop + panelHeight - 34;
        if (mouseX >= confirmX && mouseX <= confirmX + 80 && mouseY >= confirmY && mouseY <= confirmY + 20) {
            confirm();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void confirm() {
        long quantity;
        try { quantity = Long.parseLong(amountField.getValue()); } catch (NumberFormatException e) { return; }
        if (quantity <= 0) return;
        if (isBuying) MarketPacketSender.sendBuy(itemId, quantity);
        else          MarketPacketSender.sendSell(itemId, quantity);
        minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawFlatButton(GuiGraphics g, int x, int y, int w, int h, String label, int mx, int my, int color, int hover) {
        boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
        g.fill(x, y, x + w, y + h, hovered ? hover : color);
        g.fill(x,         y,         x + w,     y + 1,     0xFFAAAAAA);
        g.fill(x,         y + h - 1, x + w,     y + h,     0xFFAAAAAA);
        g.fill(x,         y,         x + 1,     y + h,     0xFFAAAAAA);
        g.fill(x + w - 1, y,         x + w,     y + h,     0xFFAAAAAA);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}