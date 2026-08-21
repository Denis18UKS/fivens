package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

public class ChaptersScreen extends HorrorScreen {
    private final Screen parent;
    public ChaptersScreen(Screen parent){super(Text.literal("ПЯТЫЙ / ГЛАВЫ"));this.parent=parent;}
    @Override protected void init(){
        beginHorrorInit();
        int w=contentWidth(400),x=(width-w)/2,y=Math.max(safeTop()+40,height/2-30);
        addDrawableChild(HorrorButton.builder(Text.literal("Открыть карту / выбор сохранения"),b->client.setScreen(new SelectWorldScreen(this))).dimensions(x,y,w,25).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+34,w,23).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        if(height>210)c.drawCenteredTextWithShadow(textRenderer,"Сюжетные главы открываются прогрессом карты, а не гриндом.",width/2,safeTop()+8,0xFFB6A79F);
        super.render(c,mx,my,d);
    }
}
