package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import ru.fifth.horror.network.FifthNetworking;

/** Explicit floor chooser for the vanilla Stone Button binder. */
public final class LiftButtonBinderScreen extends HorrorScreen {
    private final Screen parent;
    private final Hand hand;
    private final String liftName;
    private int selected;

    public LiftButtonBinderScreen(Screen parent, Hand hand, int currentFloor, String liftName) {
        super(Text.literal("FIVEN / ПРИВЯЗКА КНОПКИ ЛИФТА"));
        this.parent=parent;this.hand=hand;this.selected=Math.max(1,Math.min(9,currentFloor));this.liftName=liftName==null||liftName.isBlank()?"не выбран":liftName;
    }

    @Override protected void init(){
        beginHorrorInit();int w=contentWidth(420),x=(width-w)/2,y=safeTop(),gap=7,bh=30,cell=(w-gap*2)/3;
        for(int floor=1;floor<=9;floor++){
            final int f=floor;int col=(floor-1)%3,row=(floor-1)/3;
            addDrawableChild(HorrorButton.builder(Text.literal(label(f)),b->{selected=f;save();clearAndInit();})
                    .dimensions(x+col*(cell+gap),y+34+row*(bh+gap),cell,bh).build());
        }
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+34+3*(bh+gap)+8,w,22).build());
    }

    private String label(int f){return (f==selected?"§c▶ §r":"")+f+" этаж";}
    private void save(){PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(hand==Hand.OFF_HAND?1:0);out.writeVarInt(selected);ClientPlayNetworking.send(FifthNetworking.LIFT_BINDER_FLOOR,out);}

    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int y=safeTop();c.drawCenteredTextWithShadow(textRenderer,"Выбранный лифт: "+liftName,width/2,y+4,0xFFD0B9AE);c.drawCenteredTextWithShadow(textRenderer,"Выбери этаж, затем ПКМ этим инструментом по обычной Stone Button",width/2,y+17,0xFFB69F97);super.render(c,mx,my,d);}
}
