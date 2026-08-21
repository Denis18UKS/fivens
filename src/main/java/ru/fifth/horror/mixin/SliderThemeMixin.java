package ru.fifth.horror.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.fifth.horror.client.gui.HorrorTheme;

@Mixin(SliderWidget.class)
public abstract class SliderThemeMixin {
    @Shadow protected double value;

    @Inject(method = "renderButton", at = @At("TAIL"))
    private void fifth$slider(DrawContext c, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!HorrorTheme.shouldThemeVanillaWidgets()) return;
        SliderWidget self = (SliderWidget)(Object)this;
        int x=self.getX(), y=self.getY(), w=self.getWidth(), h=self.getHeight();
        boolean hot=self.isHovered() && self.active;
        c.fill(x,y,x+w,y+h,hot?0xFF29171B:0xFF0A0C0F);
        c.fill(x,y,x+w-5,y+1,hot?0xFFE09A9F:0xFF745056);
        c.fill(x,y,x+2,y+h,hot?0xFFD15B66:0xFF60363C);
        int trackY=y+h-5;
        c.fill(x+7,trackY,x+w-7,trackY+2,0xFF34272A);
        int knob=x+7+(int)Math.round(Math.max(0,Math.min(1,value))*(Math.max(1,w-16)));
        c.fill(knob-2,y+3,knob+3,y+h-3,hot?0xFFD26B74:0xFF9A555C);
        var tr=MinecraftClient.getInstance().textRenderer;
        Text msg=self.getMessage();
        String raw=msg.getString();
        if(tr.getWidth(raw)>w-18) msg=Text.literal(tr.trimToWidth(raw,Math.max(10,w-28))+"…");
        c.drawTextWithShadow(tr,msg,x+Math.max(7,(w-tr.getWidth(msg))/2),y+Math.max(1,(h-8)/2),self.active?0xFFE6D8D0:0xFF746A66);
    }
}
