package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

/** VHS look editor for a selected television. */
public final class TvSettingsScreen extends HorrorScreen {
    private final Screen parent;private final BlockPos pos;private int quality;private float noise;private boolean mono;private String status="";
    public TvSettingsScreen(Screen parent,BlockPos pos,int q,float n,boolean mono){super(Text.literal("FIVEN / TV + VHS"));this.parent=parent;this.pos=pos;quality=q;noise=n;this.mono=mono;}
    @Override protected void init(){beginHorrorInit();int w=contentWidth(500),x=(width-w)/2,y=safeTop(),bh=20;
        addDrawableChild(HorrorButton.builder(Text.literal(qText()),b->{quality=(quality+1)%4;b.setMessage(Text.literal(qText()));}).dimensions(x,y,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(nText()),b->{noise+=.1f;if(noise>1.001f)noise=0;b.setMessage(Text.literal(nText()));}).dimensions(x,y+28,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(mText()),b->{mono=!mono;b.setMessage(Text.literal(mText()));}).dimensions(x,y+56,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить настройки изображения"),b->save()).dimensions(x,y+92,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,y+120,w,bh).build());}
    private String qText(){return "Качество VHS: "+switch(quality){case 0->"ОЧЕНЬ ПЛОХОЕ";case 1->"ПЛОХОЕ";case 2->"СРЕДНЕЕ";default->"ЧИЩЕ";};}private String nText(){return "Помехи: "+Math.round(noise*100)+"%";}private String mText(){return "Ч/Б: "+(mono?"ДА":"НЕТ");}
    private void save(){PacketByteBuf b=PacketByteBufs.create();b.writeBlockPos(pos);b.writeVarInt(quality);b.writeFloat(noise);b.writeBoolean(mono);ClientPlayNetworking.send(FifthNetworking.SAVE_TV_CONFIG,b);status="Сохранено";}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);c.drawCenteredTextWithShadow(textRenderer,"Настройки применяются к кассетам, проигрываемым на этом телевизоре",width/2,safeTop()-10,0xFFB69F97);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,safeTop()+150,0xFFD99090);super.render(c,mx,my,d);}
}
