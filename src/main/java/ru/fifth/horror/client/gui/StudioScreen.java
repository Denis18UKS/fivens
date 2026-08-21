package ru.fifth.horror.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import ru.fifth.horror.client.AnimationCatalog;

public class StudioScreen extends HorrorScreen {
    private final Screen parent;
    public StudioScreen(Screen p){super(Text.literal("ПЯТЫЙ / РЕЖИССЁРСКАЯ СТУДИЯ"));parent=p;}

    @Override protected void init(){
        beginHorrorInit();
        int w=contentWidth(430), x=(width-w)/2, top=safeTop()+10;
        int bh=Math.max(19, Math.min(24, (height-top-safeBottom()-70)/6));
        int gap=5;
        addDrawableChild(HorrorButton.builder(Text.literal("Создать шаблон NPC"),b->client.setScreen(new NpcCreatorScreen(this,-1,null))).dimensions(x,top,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Шаблоны NPC / получить яйцо"),b->client.setScreen(new NpcTemplatesScreen(this))).dimensions(x,top+(bh+gap),w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Каталог GeckoLib-анимаций"),b->client.setScreen(new AnimationListScreen(this,null))).dimensions(x,top+2*(bh+gap),w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Редактор катсцены / камеры"),b->client.setScreen(new CameraEditorScreen(this))).dimensions(x,top+3*(bh+gap),w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Библиотека катсцен"),b->client.setScreen(new CutsceneLibraryScreen(this))).dimensions(x,top+4*(bh+gap),w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,top+5*(bh+gap)+4,w,bh).build());
    }

    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        int w=contentWidth(460), x=(width-w)/2;
        int infoH=Math.min(64, Math.max(38, height/5));
        int y=height-safeBottom()-infoH;
        panel(c,x,y,w,infoH);
        if(height>=250){
            c.drawCenteredTextWithShadow(textRenderer,"Автообнаружено анимаций: "+AnimationCatalog.INSTANCE.entries().size(),width/2,y+8,0xFFC8B5AB);
            c.drawCenteredTextWithShadow(textRenderer,"NPC без startAi() остаётся статуей. Маршруты и слои запускаются сценариями.",width/2,y+24,0xFF988D87);
            c.drawCenteredTextWithShadow(textRenderer,"Доступ к студии не показывается игроку в обычном меню.",width/2,y+40,0xFF7E7470);
        }
        super.render(c,mx,my,d);
    }
}
