package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.network.FifthNetworking;

/** 2D 9-floor panel. In edit mode also provides precise nudge/rotation controls. */
public final class LiftPanelScreen extends HorrorScreen {
    private final Screen parent; private final int id; private int mask; private final boolean edit;
    private double px, py, pz; private float yaw; private String status="";
    public LiftPanelScreen(Screen p,int entityId,int mask,boolean edit,double x,double y,double z,float yaw){
        super(Text.literal(edit?"FIVEN / РЕДАКТОР ПАНЕЛИ":"ЛИФТ")); parent=p; id=entityId; this.mask=mask; this.edit=edit; px=x;py=y;pz=z;this.yaw=yaw;
    }
    @Override protected void init(){
        beginHorrorInit();
        int size=Math.max(30,Math.min(50,(Math.min(width,height)-190)/3)),gap=7,total=size*3+gap*2,x=(width-total)/2,y=Math.max(safeTop()+14,(height-total)/2-(edit?55:10));
        for(int f=1;f<=9;f++){
            final int floor=f;int col=(f-1)%3,row=(f-1)/3;boolean on=(mask&(1<<(f-1)))!=0;
            String label=(on?"":"§8")+f+(f==2||f==5||f==8?" §4✕":"");
            addDrawableChild(HorrorButton.builder(Text.literal(label),b->{
                if(edit){boolean now=(mask&(1<<(floor-1)))==0;if(now)mask|=1<<(floor-1);else mask&=~(1<<(floor-1));send("enable",floor,now?1:0);clearAndInit();}
                else if(on)send("press",floor,0);
            }).dimensions(x+col*(size+gap),y+row*(size+gap),size,size).build());
        }
        int yy=y+total+8;
        if(edit){
            int bw=(total-gap*3)/4;
            addDrawableChild(HorrorButton.builder(Text.literal("X−"),b->nudge(-.0625,0,0,0)).dimensions(x,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("X+"),b->nudge(.0625,0,0,0)).dimensions(x+bw+gap,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("Y−"),b->nudge(0,-.0625,0,0)).dimensions(x+(bw+gap)*2,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("Y+"),b->nudge(0,.0625,0,0)).dimensions(x+(bw+gap)*3,yy,bw,18).build());
            yy+=22;
            addDrawableChild(HorrorButton.builder(Text.literal("Z−"),b->nudge(0,0,-.0625,0)).dimensions(x,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("Z+"),b->nudge(0,0,.0625,0)).dimensions(x+bw+gap,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("↺"),b->nudge(0,0,0,-5)).dimensions(x+(bw+gap)*2,yy,bw,18).build());
            addDrawableChild(HorrorButton.builder(Text.literal("↻"),b->nudge(0,0,0,5)).dimensions(x+(bw+gap)*3,yy,bw,18).build());
            yy+=24;
        }
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,yy,total,20).build());
    }
    private void send(String a,int floor,int value){PacketByteBuf b=PacketByteBufs.create();b.writeVarInt(id);b.writeString(a,32);b.writeVarInt(floor);b.writeVarInt(value);ClientPlayNetworking.send(FifthNetworking.LIFT_PANEL_CONTROL,b);status=a+" "+floor;}
    private void nudge(double dx,double dy,double dz,float dyaw){px+=dx;py+=dy;pz+=dz;yaw+=dyaw;PacketByteBuf b=PacketByteBufs.create();b.writeVarInt(id);b.writeDouble(px);b.writeDouble(py);b.writeDouble(pz);b.writeFloat(yaw);ClientPlayNetworking.send(FifthNetworking.LIFT_PANEL_TRANSFORM,b);status=String.format("%.3f %.3f %.3f | %.0f°",px,py,pz,yaw);}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);c.drawCenteredTextWithShadow(textRenderer,edit?"Кнопки 2 / 5 / 8 по умолчанию сожжены. Ниже — точная позиция панели.":"Выбери этаж",width/2,safeTop(),0xFFD0B9AE);if(edit&&!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-safeBottom()-12,0xFFD99090);super.render(c,mx,my,d);}
}
