package com.github.zly2006.fabricmodtest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MainScreenMixin extends Screen {
    protected MainScreenMixin(Component component) {
        super(component);
    }

    @Inject(at = @At("HEAD"), method = "init")
    private void init(CallbackInfo info) {
        System.out.println("Hello Fabric, Hello Mixin!");
        System.out.println("Powered by Ordom.");
    }

    @Inject(at = @At("RETURN"), method = "render")
    private void render(PoseStack poseStack, int mouseX, int mouseY, float p_96742_, CallbackInfo ci) {
        Minecraft.getInstance().font.draw(poseStack, "Mixin works!!!", 10, 10, 0xffffff);
        System.out.println("Mixin works!!!");
    }
}
