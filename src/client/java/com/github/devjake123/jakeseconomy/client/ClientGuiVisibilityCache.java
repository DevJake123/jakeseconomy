package com.github.devjake123.jakeseconomy.client;

/**
 * Client-side cache of GUI tab visibility settings synced from the server config.
 * Used by GUI screens to determine which tabs should be shown or hidden.
 */
public class ClientGuiVisibilityCache {
    private static boolean showMarketTab = true;
    private static boolean showWithdrawTab = true;
    private static boolean showHistoryTab = true;
    private static boolean showAuctionTab = true;
    private static boolean allowHotkeyOpen = true;

    public static void set(boolean market, boolean withdraw, boolean history, boolean auction, boolean hotkeyOpen) {
        showMarketTab = market;
        showWithdrawTab = withdraw;
        showHistoryTab = history;
        showAuctionTab = auction;
        allowHotkeyOpen = hotkeyOpen;
    }

    public static boolean showMarket()      { return showMarketTab; }
    public static boolean showWithdraw()    { return showWithdrawTab; }
    public static boolean showHistory()     { return showHistoryTab; }
    public static boolean showAuction()     { return showAuctionTab; }
    public static boolean allowHotkeyOpen() { return allowHotkeyOpen; }
}
