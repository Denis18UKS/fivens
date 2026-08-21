package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;

public class PlayMenuScreen extends HorrorScreen {
    private final Screen parent;
    public PlayMenuScreen(Screen parent){super(Text.literal("ПЯТЫЙ / ИГРАТЬ"));this.parent=parent;}
    @Override protected void init(){
        beginHorrorInit();
        int w=contentWidth(390),x=(width-w)/2,bh=24,gap=8;
        int total=bh*3+gap*2, y=Math.max(safeTop()+12,(height-total)/2);
        addDrawableChild(HorrorButton.builder(Text.literal("Одиночная игра / тест карты"),b->client.setScreen(new SelectWorldScreen(this))).dimensions(x,y,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сетевая игра"),b->client.setScreen(new MultiplayerScreen(this))).dimensions(x,y+bh+gap,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+2*(bh+gap),w,bh).build());
    }
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);super.render(c,mx,my,d);}
}
