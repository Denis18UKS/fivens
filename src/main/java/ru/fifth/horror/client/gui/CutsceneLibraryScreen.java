package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.network.FifthNetworking;

import java.util.*;

/** Responsive cutscene library; placeholder is visual only and never becomes a search query. */
public final class CutsceneLibraryScreen extends HorrorScreen {
    public record Info(String id,int frames,int ticks,boolean teleport){}
    private final Screen parent;private TextFieldWidget search;private List<Info> all=new ArrayList<>();private int page,rows=5;private String selected="",status="";
    public CutsceneLibraryScreen(Screen parent){super(Text.literal("FIVEN / БИБЛИОТЕКА КАТСЦЕН"));this.parent=parent;}
    @Override protected void init(){
        beginHorrorInit();int w=contentWidth(560),x=(width-w)/2,y=safeTop(),bh=20,g=6;rows=Math.max(2,Math.min(7,(height-y-safeBottom()-130)/24));
        search=horrorField(x,y,w,bh,"",128);search.setPlaceholder(Text.literal("Поиск по названию / ID..."));search.setChangedListener(v->{page=0;refresh();});
        for(int i=0;i<rows;i++){final int row=i;addDrawableChild(HorrorButton.builder(Text.literal("-"),b->choose(row)).dimensions(x,y+28+i*24,w,bh).build());}
        int yy=y+28+rows*24+4;int third=Math.max(70,(w-2*g)/3);addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть"),b->play()).dimensions(x,yy,third,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("✎ Редактировать"),b->edit()).dimensions(x+third+g,yy,third,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("📼 Кассета"),b->cassette()).dimensions(x+2*(third+g),yy,w-2*(third+g),bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("‹"),b->{page=Math.max(0,page-1);refresh();}).dimensions(x,yy+27,46,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("Обновить"),b->request()).dimensions(x+52,yy+27,w-104,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("›"),b->{page++;refresh();}).dimensions(x+w-46,yy+27,46,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,yy+54,w,bh).build());request();
    }
    private List<Info> filtered(){String q=search==null?"":search.getText().trim().toLowerCase(Locale.ROOT);return all.stream().filter(i->q.isBlank()||i.id().toLowerCase(Locale.ROOT).contains(q)).toList();}
    private void refresh(){if(search==null)return;var list=filtered();int max=Math.max(0,(list.size()-1)/Math.max(1,rows));page=Math.min(page,max);int start=page*rows,idx=0;int top=safeTop()+28;for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget b&&b.getY()>=top&&b.getY()<top+rows*24){int p=start+idx++;if(p<list.size()){var i=list.get(p);b.setMessage(Text.literal(i.id()+"  §8| §7"+i.frames()+" кадров §8| §7"+String.format(Locale.ROOT,"%.1fs",i.ticks()/20.0)));b.active=true;}else{b.setMessage(Text.literal("-"));b.active=false;}}}
    private void choose(int row){var list=filtered();int p=page*rows+row;if(p<list.size()){selected=list.get(p).id();status="Выбрано: "+selected;}}
    private void request(){if(client==null||client.getNetworkHandler()==null){status="Библиотека доступна после входа в мир.";return;}ClientPlayNetworking.send(FifthNetworking.REQUEST_CUTSCENE_LIBRARY,PacketByteBufs.empty());status="Обновляю библиотеку...";}
    public void update(List<Info> rows){all=new ArrayList<>(rows);refresh();status="Катсцен: "+all.size();}
    private void play(){if(selected.isBlank())return;PacketByteBuf b=PacketByteBufs.create();b.writeString(selected,128);ClientPlayNetworking.send(FifthNetworking.PLAY_CUTSCENE,b);client.setScreen(null);}
    private void edit(){if(selected.isBlank())return;PacketByteBuf b=PacketByteBufs.create();b.writeString(selected,128);ClientPlayNetworking.send(FifthNetworking.REQUEST_CUTSCENE_EDIT,b);status="Загружаю сцену...";}
    private void cassette(){if(selected.isBlank())return;PacketByteBuf b=PacketByteBufs.create();b.writeString(selected,128);ClientPlayNetworking.send(FifthNetworking.CREATE_CASSETTE,b);status="Кассета с записью выдана: "+selected;}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-safeBottom()-11,0xFFD79A9A);super.render(c,mx,my,d);}
}
