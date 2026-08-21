package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import ru.fifth.horror.network.FifthNetworking;

import java.util.*;

/** Searchable entity list + live shader binding settings. */
public final class EntityShaderScreen extends HorrorScreen {
    private final Screen parent; private TextFieldWidget search,hex,ox,oy,oz,intensity;
    private final List<Entity> entities=new ArrayList<>(); private UUID selected; private String type="dark",status="";private int page;
    public EntityShaderScreen(Screen parent){super(Text.literal("FIVEN / ШЕЙДЕРЫ СУЩНОСТЕЙ"));this.parent=parent;}
    @Override protected void init(){
        beginHorrorInit();int w=contentWidth(600),x=(width-w)/2,y=safeTop(),g=6,bh=20;
        search=horrorField(x,y,w,bh,"Поиск сущности...",128);search.setChangedListener(s->{page=0;reload();refresh();});
        addDrawableChild(HorrorButton.builder(Text.literal(typeText()),b->{type=switch(type){case "dark"->"eyes";case "eyes"->"off";default->"dark";};b.setMessage(Text.literal(typeText()));}).dimensions(x,y+28,w,bh).build());
        for(int i=0;i<6;i++){final int row=i;addDrawableChild(HorrorButton.builder(Text.literal("-"),b->choose(row)).dimensions(x,y+56+i*24,w,bh).build());}
        int col=(w-g*4)/5;
        hex=horrorField(x,y+206,col,bh,"#FF2020",10);ox=horrorField(x+col+g,y+206,col,bh,"0",12);oy=horrorField(x+(col+g)*2,y+206,col,bh,"0",12);oz=horrorField(x+(col+g)*3,y+206,col,bh,"0",12);intensity=horrorField(x+(col+g)*4,y+206,col,bh,"1.0",8);
        addDrawableChild(HorrorButton.builder(Text.literal("<"),b->{page=Math.max(0,page-1);refresh();}).dimensions(x,y+234,48,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Привязать / обновить эффект"),b->save()).dimensions(x+54,y+234,w-108,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(">"),b->{page++;refresh();}).dimensions(x+w-48,y+234,48,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+262,w,bh).build());reload();refresh();
    }
    private String typeText(){return switch(type){case "dark"->"Эффект: ТЁМНЫЙ СИЛУЭТ (shader)";case "eyes"->"Эффект: СВЕТЯЩИЕСЯ ГЛАЗА (shader)";default->"Эффект: ВЫКЛЮЧИТЬ";};}
    private void reload(){entities.clear();MinecraftClient c=MinecraftClient.getInstance();if(c.world==null||c.player==null)return;Box box=c.player.getBoundingBox().expand(128);entities.add(c.player);entities.addAll(c.world.getOtherEntities(c.player,box,e->true));String q=search==null?"":search.getText().toLowerCase(Locale.ROOT);entities.removeIf(e->!q.isBlank()&&!(e.getName().getString()+" "+e.getType().toString()+" "+e.getUuidAsString()).toLowerCase(Locale.ROOT).contains(q));entities.sort(Comparator.comparing(e->e.getName().getString(),String.CASE_INSENSITIVE_ORDER));}
    private void refresh(){int max=Math.max(0,(entities.size()-1)/6);page=Math.min(page,max);int start=page*6,idx=0;int y=safeTop()+56;for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget b&&b.getY()>=y&&b.getY()<y+144){int p=start+idx++;if(p<entities.size()){Entity e=entities.get(p);boolean sel=e.getUuid().equals(selected);b.setMessage(Text.literal((sel?"§c▶ ":"")+e.getName().getString()+" §8["+e.getType().toString()+"]"));b.active=true;}else{b.setMessage(Text.literal("-"));b.active=false;}}}
    private void choose(int row){int p=page*6+row;if(p<entities.size()){selected=entities.get(p).getUuid();status="Выбрано: "+entities.get(p).getName().getString();refresh();}}
    private void save(){if(selected==null){status="Сначала выбери сущность.";return;}try{String h=hex.getText().trim().replace("#","");int rgb=Integer.parseInt(h,16)&0xFFFFFF;int argb=0xFF000000|rgb;double x=Double.parseDouble(ox.getText()),y=Double.parseDouble(oy.getText()),z=Double.parseDouble(oz.getText());float in=Math.max(.05f,Math.min(4f,Float.parseFloat(intensity.getText())));PacketByteBuf b=PacketByteBufs.create();b.writeString(selected.toString(),64);b.writeString(type,16);b.writeInt(argb);b.writeDouble(x);b.writeDouble(y);b.writeDouble(z);b.writeFloat(in);ClientPlayNetworking.send(FifthNetworking.ENTITY_EFFECT_SAVE,b);status="Эффект сохранён.";}catch(Exception e){status="Проверь HEX, смещение и интенсивность.";}}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int w=contentWidth(600),x=(width-w)/2,y=safeTop();c.drawTextWithShadow(textRenderer,"Цвет",x,y+195,0xFFB69F97);c.drawTextWithShadow(textRenderer,"X",x+(w+6)/5,y+195,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Y",x+((w+6)/5)*2,y+195,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Z",x+((w+6)/5)*3,y+195,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Сила",x+((w+6)/5)*4,y+195,0xFFB69F97);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,Math.min(height-safeBottom()-12,y+288),0xFFD99090);super.render(c,mx,my,d);}
}
