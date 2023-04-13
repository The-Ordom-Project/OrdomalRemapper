package com.github.zly2006.fabricmodtest.client;

import net.fabricmc.api.ClientModInitializer;

public class FabricModTestClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("Hello Fabric world! (client)");
    }
}
