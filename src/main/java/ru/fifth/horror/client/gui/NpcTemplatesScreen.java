package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.NpcTemplateStore;
import ru.fifth.horror.network.FifthNetworking;

import java.util.List;

public class NpcTemplatesScreen extends HorrorScreen {
    private final Screen parent;
    private int page;
    private String status="";
    public NpcTemplatesScreen(Screen parent){super(Text.literal("ПЯТЫЙ / ШАБЛОНЫ NPC"));this.parent=parent;}
    @Override protected void init(){beginHorrorInit();rebuild();}

    private void rebuild(){
        clearChildren();beginHorrorInit();
        List<NpcTemplateStore.Template> all=NpcTemplateStore.list();
        int w=contentWidth(500),x=(width-w)/2,top=safeTop()+8,bh=23,gap=5;
        int navH=24, available=Math.max(50,height-top-safeBottom()-navH-34);
        int per=Math.max(2,Math.min(10,available/(bh+gap)));
        int maxPage=Math.max(0,(all.size()-1)/per);page=Math.min(page,maxPage);
        int start=page*per;
        for(int i=start;i<Math.min(start+per,all.size());i++){
            var t=all.get(i);int yy=top+(i-start)*(bh+gap);
            addDrawableChild(HorrorButton.builder(Text.literal(t.name()+"   ["+t.id()+"]"),b->{
                if(client!=null&&client.getNetworkHandler()!=null){PacketByteBuf buf=PacketByteBufs.create();buf.writeString(t.json(),32767);ClientPlayNetworking.send(FifthNetworking.CREATE_NPC_EGG,buf);status="Яйцо NPC запрошено: "+t.name();}
                else status="Войди в мир, чтобы получить яйцо. Шаблон сохранён локально.";
            }).dimensions(x,yy,w,bh).compact().build());
        }
        int ny=height-safeBottom()-22;
        int side=Math.min(48,Math.max(34,w/8));
        addDrawableChild(HorrorButton.builder(Text.literal("‹"),b->{if(page>0){page--;rebuild();}}).dimensions(x,ny,side,22).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x+side+6,ny,w-side*2-12,22).build());
        addDrawableChild(HorrorButton.builder(Text.literal("›"),b->{if((page+1)*per<all.size()){page++;rebuild();}}).dimensions(x+w-side,ny,side,22).build());
    }

    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,32,0xFFD9B2A8);
        if(height>250)c.drawCenteredTextWithShadow(textRenderer,"Шаблоны можно создавать в меню; яйцо выдаётся только внутри мира.",width/2,height-safeBottom()-38,0xFF978B85);
        super.render(c,mx,my,d);
    }
}
