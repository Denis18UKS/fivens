package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.AnimationCatalog;
import ru.fifth.horror.network.FifthNetworking;

import java.util.List;
import java.util.Locale;

/** Responsive condition editor. Placeholder text is never used as a real search query. */
public final class AnimationConditionScreen extends HorrorScreen {
    private final Screen parent; private final String uuid;
    private TextFieldWidget idField,valueField,search,conditionAnimField;
    private String type="player_near",selected="",status="";private boolean screamer;private int page,rows=4;
    public AnimationConditionScreen(Screen parent,Entity entity){super(Text.literal("FIVEN / УСЛОВИЯ И СКРИМЕРЫ"));this.parent=parent;this.uuid=entity.getUuidAsString();}

    @Override protected void init(){
        beginHorrorInit();int w=contentWidth(540),x=(width-w)/2,y=safeTop(),g=6,bh=20;rows=Math.max(2,Math.min(5,(height-y-safeBottom()-174)/24));
        idField=horrorField(x,y,(w-g)/2,bh,"rule_1",64);valueField=horrorField(x+(w+g)/2,y,(w-g)/2,bh,"6",16);
        search=horrorField(x,y+27,w,bh,"",96);search.setPlaceholder(Text.literal("Поиск анимации для действия..."));search.setChangedListener(v->{page=0;refresh();});
        conditionAnimField=horrorField(x,y+54,w,bh,"",128);conditionAnimField.setPlaceholder(Text.literal("Для 'текущая анимация' введи имя: running / idle / ..."));
        addDrawableChild(HorrorButton.builder(typeText(),b->{type=switch(type){case "player_near"->"timer";case "timer"->"animation_is";default->"player_near";};b.setMessage(typeText());}).dimensions(x,y+81,(w-g)/2,bh).build());
        addDrawableChild(HorrorButton.builder(actionText(),b->{screamer=!screamer;b.setMessage(actionText());}).dimensions(x+(w+g)/2,y+81,(w-g)/2,bh).build());
        int listY=y+108;for(int i=0;i<rows;i++){final int r=i;addDrawableChild(HorrorButton.builder(Text.literal("-"),b->choose(r)).dimensions(x,listY+i*24,w,bh).build());}
        int navY=listY+rows*24+2;addDrawableChild(HorrorButton.builder(Text.literal("‹"),b->{page=Math.max(0,page-1);refresh();}).dimensions(x,navY,46,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("Сохранить"),b->save()).dimensions(x+52,navY,w-104,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("›"),b->{page++;refresh();}).dimensions(x+w-46,navY,46,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,navY+27,w,bh).build());refresh();
    }
    private Text typeText(){return Text.literal(switch(type){case "timer"->"Условие: таймер (тики)";case "animation_is"->"Условие: текущая анимация";default->"Условие: игрок рядом";});}
    private Text actionText(){return Text.literal(screamer?"Действие: СКРИМЕР":"Действие: анимация");}
    private List<AnimationCatalog.Entry> list(){String q=search==null?"":search.getText().trim().toLowerCase(Locale.ROOT);return AnimationCatalog.INSTANCE.entries().stream().filter(e->q.isBlank()||(e.name()+" "+e.file()+" "+e.description()).toLowerCase(Locale.ROOT).contains(q)).toList();}
    private void refresh(){if(search==null)return;var list=list();int max=Math.max(0,(list.size()-1)/Math.max(1,rows));page=Math.min(page,max);int start=page*rows,idx=0;int listY=safeTop()+108;for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget b&&b.getY()>=listY&&b.getY()<listY+rows*24){int p=start+idx++;if(p<list.size()){var e=list.get(p);b.setMessage(Text.literal(e.name()+" §8— §7"+e.description()));b.active=true;}else{b.setMessage(Text.literal("-"));b.active=false;}}}
    private void choose(int row){var list=list();int p=page*rows+row;if(p<list.size()){selected=list.get(p).name();status="Действие: "+selected;}}
    private void save(){
        if(!screamer&&selected.isBlank()){status="Выбери анимацию действия или включи Скример.";return;}if("animation_is".equals(type)&&conditionAnimField.getText().trim().isBlank()){status="Укажи анимацию, которую нужно отслеживать.";return;}
        double value=6;try{value=Double.parseDouble(valueField.getText());}catch(Exception ignored){}
        PacketByteBuf b=PacketByteBufs.create();b.writeString(idField.getText().isBlank()?"rule":idField.getText().trim(),64);b.writeString(uuid,64);b.writeString(selected,256);b.writeString(type,32);b.writeDouble(value);b.writeString(conditionAnimField.getText().trim(),256);b.writeBoolean(screamer);ClientPlayNetworking.send(FifthNetworking.SAVE_ANIMATION_CONDITION,b);status="Условие сохранено.";
    }
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int w=contentWidth(540),x=(width-w)/2;c.drawTextWithShadow(textRenderer,"ID правила",x,safeTop()-10,0xFFB69F97);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-safeBottom()-11,0xFFD99090);super.render(c,mx,my,d);}
}
