package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class CreditsScreen extends HorrorScreen {
    private final Screen parent;
    public CreditsScreen(Screen p){super(Text.literal("ПЯТЫЙ / АВТОРЫ"));parent=p;}
    @Override protected void init(){
        beginHorrorInit();
        int w=contentWidth(300),x=(width-w)/2;
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,height-safeBottom()-24,w,22).build());
    }
    @Override public void render(DrawContext c,int x,int y,float d){
        horrorBackground(c);
        int w=contentWidth(430),px=(width-w)/2,py=Math.max(safeTop()+18,height/2-52);
        panel(c,px,py,w,92);
        c.drawCenteredTextWithShadow(textRenderer,"Проект: «Пятый»",width/2,py+16,0xFFF0E3DA);
        c.drawCenteredTextWithShadow(textRenderer,"Сюжетная хоррор-карта / Fabric 1.20.1",width/2,py+39,0xFFB5A7A0);
        c.drawCenteredTextWithShadow(textRenderer,"Кинематографичные NPC, катсцены и сценарные слои мира",width/2,py+60,0xFF8F8580);
        super.render(c,x,y,d);
    }
}
