package com.github.zly2006.fabricmodtest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.server.level.ServerPlayer;

public class FabricModTest implements ModInitializer {
    public void onInitialize() {
        System.out.println("Hello Fabric world!");
    }

    /**
     * Test method, the class name should be translated.
     * @param player The player to get the display name.
     * @return The display name of the player.
     */
    String playerDisplayName(ServerPlayer player) {
        return player.getDisplayName().getString();
    }
}

