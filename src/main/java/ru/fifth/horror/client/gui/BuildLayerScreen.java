package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import ru.fifth.horror.network.FifthNetworking;

public class BuildLayerScreen extends HorrorScreen {
    private final Screen parent; private final BlockPos a,b;
    private TextFieldWidget build,variant,group,floor;
    private boolean defaultActive=true,restore=true; private String status="";
    public BuildLayerScreen(Screen parent,ItemStack stack){super(Text.literal("FIVEN / СЛОИ И ЭТАЖИ"));this.parent=parent;
        this.a=stack.hasNbt()&&stack.getNbt().contains("FifthPosA")?BlockPos.fromLong(stack.getNbt().getLong("FifthPosA")):null;
        this.b=stack.hasNbt()&&stack.getNbt().contains("FifthPosB")?BlockPos.fromLong(stack.getNbt().getLong("FifthPosB")):null;}
    @Override protected void init(){
        beginHorrorInit();int w=contentWidth(480),x=(width-w)/2,top=safeTop();int h=20,gap=6;
        build=horrorField(x,top,w,h,"lift",128);variant=horrorField(x,top+29,w,h,"floor_1",128);group=horrorField(x,top+58,(int)(w*.70),h,"lift_floors",128);
        floor=horrorField(x+(int)(w*.70)+gap,top+58,w-(int)(w*.70)-gap,h,"1",2);
        int half=(w-gap)/2,bh=21;
        addDrawableChild(HorrorButton.builder(Text.literal("По умолчанию: ДА"),q->{defaultActive=!defaultActive;q.setMessage(Text.literal("По умолчанию: "+(defaultActive?"ДА":"НЕТ")));}).dimensions(x,top+88,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Автовосстановление: ДА"),q->{restore=!restore;q.setMessage(Text.literal("Автовосстановление: "+(restore?"ДА":"НЕТ")));}).dimensions(x+half+gap,top+88,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить слой / этаж"),q->capture()).dimensions(x,top+116,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Активировать / заменить"),q->activate()).dimensions(x,top+144,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),q->client.setScreen(parent)).dimensions(x,top+174,w,bh).build());
    }
    private int floorValue(){try{return Math.max(0,Math.min(9,Integer.parseInt(floor.getText().trim())));}catch(Exception e){return 0;}}
    private void capture(){
        if(a==null||b==null){status="Сначала выбери A и B: клик по блоку = A, Shift+клик = B.";return;}if(client==null||client.getNetworkHandler()==null){status="Слои сохраняются только внутри мира.";return;}
        PacketByteBuf out=PacketByteBufs.create();out.writeString(build.getText(),128);out.writeString(variant.getText(),128);out.writeString(group.getText(),128);out.writeBoolean(defaultActive);out.writeBoolean(restore);out.writeVarInt(floorValue());out.writeBlockPos(a);out.writeBlockPos(b);ClientPlayNetworking.send(FifthNetworking.STRUCTURE_CAPTURE,out);status=floorValue()>0?"Этаж "+floorValue()+" для лифта/набора «"+build.getText()+"» отправлен на сохранение.":"Обычный слой отправлен на сохранение.";
    }
    private void activate(){if(client==null||client.getNetworkHandler()==null){status="Активация доступна только внутри мира.";return;}PacketByteBuf out=PacketByteBufs.create();out.writeString(build.getText(),128);out.writeString(variant.getText(),128);ClientPlayNetworking.send(FifthNetworking.STRUCTURE_ACTIVATE,out);status="Запрошена замена слоя.";}
    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int w=contentWidth(480),x=(width-w)/2,top=safeTop();
        c.drawTextWithShadow(textRenderer,"ID лифта / набора этажей",x,top-10,0xFFAD9D95);c.drawTextWithShadow(textRenderer,"Вариант / слой",x,top+19,0xFF9F918B);c.drawTextWithShadow(textRenderer,"Группа замены",x,top+48,0xFF9F918B);c.drawTextWithShadow(textRenderer,"Этаж 0=не этаж",x+(int)(w*.70)+6,top+48,0xFFB74848);
        String apos=a==null?"не выбрана":a.toShortString(),bpos=b==null?"не выбрана":b.toShortString();int infoY=Math.min(height-33,top+207);c.drawCenteredTextWithShadow(textRenderer,"A: "+apos+"    B: "+bpos,width/2,infoY,0xFFB7A49B);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,Math.min(height-18,infoY+14),0xFFD5A9A2);super.render(c,mx,my,d);}
}
