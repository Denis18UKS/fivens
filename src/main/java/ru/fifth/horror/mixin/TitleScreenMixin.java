package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.FifthTitleScreen;

@Mixin(TitleScreen.class)
public class TitleScreenMixin {
    @Inject(method="init",at=@At("TAIL"))
    private void fifth$replace(CallbackInfo ci){MinecraftClient c=MinecraftClient.getInstance();if(!(c.currentScreen instanceof FifthTitleScreen))c.setScreen(new FifthTitleScreen());}
}
