package com.github.devjake123.testmod.config;

public class JakesEconomyClientConfig {

    // The key used to open the Bazaar market GUI.
    // Uses GLFW key names as strings (e.g. "key.keyboard.b", "key.keyboard.m").
    // Can also be changed in Minecraft's Controls settings screen.
    public String openMarketKey = "key.keyboard.b";

    // Whether to display currency values in abbreviated form.
    // true  = "1.23M", "53.12M", "1.30B"  (easier to read at a glance)
    // false = "1,200,000", "53,144,100" (exact values always visible)
    public boolean useAbbreviatedCurrency = true;

    // Number of decimal places shown in abbreviated currency display.
    // e.g. 2 = "1.23M", 0 = "1M", 3 = "1.234M"
    public int abbreviationDecimalPlaces = 2;
}