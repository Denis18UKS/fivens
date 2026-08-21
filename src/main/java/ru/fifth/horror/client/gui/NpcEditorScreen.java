package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.AnimationCatalog;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.network.FifthNetworking;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Visual NPC control: no script computer required. */
public final class NpcEditorScreen extends HorrorScreen {
    private final Screen parent; private final int entityId; private DirectorNpcEntity npc;
    private TextFieldWidget search; private boolean personal=true; private int page; private String selectedAnimation=""; private String selectedFile=""; private String status="";
    public NpcEditorScreen(Screen parent, DirectorNpcEntity npc){super(Text.literal("FIVEN / NPC EDITOR"));this.parent=parent;this.npc=npc;this.entityId=npc.getId();}
    @Override protected void init(){beginHorrorInit(); npc=client!=null&&client.world!=null&&client.world.getEntityById(entityId) instanceof DirectorNpcEntity n?n:npc;
        int w=contentWidth(540),x=(width-w)/2,top=safeTop(),gap=6,bh=20;search=horrorField(x,top,w,bh,"Поиск анимации...",96);
        int half=(w-gap)/2;
        addDrawableChild(HorrorButton.builder(Text.literal("Состояние: "+(npc!=null&&npc.isAiEnabled()?"АКТИВЕН":"СТАТУЯ")),b->{control("toggle_ai","");if(npc!=null)status=npc.isAiEnabled()?"Запрошена статуизация":"Запрошен запуск NPC";}).dimensions(x,top+27,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Маршрут: ▶ / ■"),b->control("toggle_path","loop")).dimensions(x+half+gap,top+27,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Анимации: ЛИЧНЫЕ"),b->{personal=!personal;b.setMessage(Text.literal("Анимации: "+(personal?"ЛИЧНЫЕ":"ВСЕ")));page=0;}).dimensions(x,top+54,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("■ Стоп анимации"),b->preview("","")).dimensions(x+half+gap,top+54,half,bh).build());
        int y=top+82; for(int i=0;i<6;i++){final int row=i;addDrawableChild(HorrorButton.builder(Text.literal("-"),b->choose(row,b)).dimensions(x,y+i*25,w,bh).build());}
        addDrawableChild(HorrorButton.builder(Text.literal("<"),b->{page=Math.max(0,page-1);refresh();}).dimensions(x,y+153,50,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть выбранную"),b->{if(!selectedAnimation.isBlank())preview(selectedFile,selectedAnimation);}).dimensions(x+56,y+153,w-112,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(">"),b->{page++;refresh();}).dimensions(x+w-50,y+153,50,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+180,w,bh).build());
        search.setChangedListener(v->{page=0;refresh();}); refresh();
    }
    private List<AnimationCatalog.Entry> filtered(){List<AnimationCatalog.Entry> out=new ArrayList<>();String q=search==null?"":search.getText().toLowerCase(Locale.ROOT);String own=npc==null?"":npc.getAnimationResource().toString();
        for(var e:AnimationCatalog.INSTANCE.entries()){if(personal&&!e.file().toString().equals(own))continue;String hay=(e.name()+" "+e.description()+" "+e.file()).toLowerCase(Locale.ROOT);if(q.isBlank()||hay.contains(q))out.add(e);}return out;}
    private void refresh(){List<AnimationCatalog.Entry> list=filtered();int max=Math.max(0,(list.size()-1)/6);if(page>max)page=max;int start=page*6;int idx=0;for(var child:children())if(child instanceof net.minecraft.client.gui.widget.ClickableWidget b&&b.getY()>=safeTop()+82&&b.getY()<safeTop()+82+150){int p=start+idx++;if(p<list.size()){var e=list.get(p);b.setMessage(Text.literal(e.name()+"  §8— §7"+e.description()));b.active=true;}else{b.setMessage(Text.literal("-"));b.active=false;}}}
    private void choose(int row, net.minecraft.client.gui.widget.ClickableWidget button){List<AnimationCatalog.Entry> list=filtered();int p=page*6+row;if(p>=list.size())return;var e=list.get(p);selectedAnimation=e.name();selectedFile=e.file().toString();status="Выбрано: "+selectedAnimation;}
    private void preview(String file,String anim){if(client==null||client.getNetworkHandler()==null)return;PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(entityId);out.writeString(file,512);out.writeString(anim,512);ClientPlayNetworking.send(FifthNetworking.PREVIEW_NPC_ANIMATION,out);status=anim.isBlank()?"Анимация остановлена.":"Играет: "+anim;}
    private void control(String action,String arg){if(client==null||client.getNetworkHandler()==null)return;PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(entityId);out.writeString(action,64);out.writeString(arg,256);ClientPlayNetworking.send(FifthNetworking.NPC_CONTROL,out);}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int w=contentWidth(540),x=(width-w)/2;String title=npc==null?"NPC":npc.getNpcId()+" | точек пути: "+npc.getPathPoints().size();c.drawCenteredTextWithShadow(textRenderer,title,width/2,Math.max(4,safeTop()-12),0xFFD0B9AE);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-safeBottom()-12,0xFFD89090);super.render(c,mx,my,d);}
}
