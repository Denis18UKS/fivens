package ru.fifth.horror.client.gui;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

public class CameraEditorScreen extends HorrorScreen {
    private static final Gson GSON=new Gson();
    private static CutsceneDefinition scene=new CutsceneDefinition();
    private final Screen parent;
    private TextFieldWidget id,duration,fov,subtitle;
    private boolean hideHud=true,lock=true,teleportAtEnd=false;
    private String status="";

    public CameraEditorScreen(Screen parent){super(Text.literal("ПЯТЫЙ / КАТСЦЕНЫ"));this.parent=parent;}
    public CameraEditorScreen(Screen parent, CutsceneDefinition existing){super(Text.literal("ПЯТЫЙ / КАТСЦЕНЫ"));this.parent=parent;if(existing!=null)scene=existing;}
    public static void setScene(CutsceneDefinition existing){if(existing!=null)scene=existing;}

    @Override protected void init(){
        beginHorrorInit();
        teleportAtEnd=scene.teleportPlayerAtEnd;
        hideHud=scene.hideHud; lock=scene.lockInput;
        int w=contentWidth(500),x=(width-w)/2,top=safeTop();
        int gap=6;
        int idW=Math.max(100,(int)(w*0.52));
        int small=(w-idW-gap*2)/2;
        id=horrorField(x,top,idW,20,scene.id,128);
        duration=horrorField(x+idW+gap,top,small,20,"40",10);
        fov=horrorField(x+idW+gap+small+gap,top,w-idW-small-gap*2,20,"70",10);
        subtitle=horrorField(x,top+28,w,20,"",256);

        int by=top+57,bh=21;
        addDrawableChild(HorrorButton.builder(Text.literal("+ Ключевой кадр из позиции игрока"),b->addFrame()).dimensions(x,by,w,bh).build());
        int half=(w-gap)/2;
        addDrawableChild(HorrorButton.builder(Text.literal("HUD: "+(hideHud?"СКРЫТ":"ВИДЕН")),b->{hideHud=!hideHud;b.setMessage(Text.literal("HUD: "+(hideHud?"СКРЫТ":"ВИДЕН")));}).dimensions(x,by+27,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Управление: "+(lock?"БЛОК":"СВОБОДНО")),b->{lock=!lock;b.setMessage(Text.literal("Управление: "+(lock?"БЛОК":"СВОБОДНО")));}).dimensions(x+half+gap,by+27,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Спавнить игрока в конечной точке: "+(teleportAtEnd?"ДА":"НЕТ")),b->{teleportAtEnd=!teleportAtEnd;b.setMessage(Text.literal("Спавнить игрока в конечной точке: "+(teleportAtEnd?"ДА":"НЕТ")));}).dimensions(x,by+54,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить сцену"),b->save()).dimensions(x,by+81,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Проиграть"),b->play()).dimensions(x+half+gap,by+81,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Очистить кадры"),b->{scene.keyframes.clear();status="Ключевые кадры очищены.";}).dimensions(x,by+108,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x+half+gap,by+108,half,bh).build());
    }

    private boolean inWorld(){return client!=null&&client.player!=null&&client.world!=null&&client.getNetworkHandler()!=null;}
    private void addFrame(){
        if(!inWorld()){status="Кадр снимается только внутри мира: нужна позиция камеры.";return;}
        int dur=40;double fv=70;try{dur=Integer.parseInt(duration.getText());}catch(Exception ignored){}try{fv=Double.parseDouble(fov.getText());}catch(Exception ignored){}
        var p=client.player;var k=new CutsceneDefinition.Keyframe(p.getX(),p.getEyeY(),p.getZ(),p.getYaw(),p.getPitch(),fv,Math.max(1,dur));k.subtitle=subtitle.getText();scene.keyframes.add(k);status="Кадр добавлен: "+scene.keyframes.size();
    }
    private boolean save(){
        scene.id=id.getText().isBlank()?"scene":id.getText();scene.hideHud=hideHud;scene.lockInput=lock;scene.teleportPlayerAtEnd=teleportAtEnd;
        if(!inWorld()){status="Сохранение в мир доступно после входа на карту.";return false;}
        PacketByteBuf out=PacketByteBufs.create();out.writeString(GSON.toJson(scene),1_000_000);ClientPlayNetworking.send(FifthNetworking.SAVE_CUTSCENE,out);status="Сцена сохранена: "+scene.id;return true;
    }
    private void play(){if(!save())return;PacketByteBuf out=PacketByteBufs.create();out.writeString(scene.id,128);ClientPlayNetworking.send(FifthNetworking.PLAY_CUTSCENE,out);client.setScreen(null);}

    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        int w=contentWidth(500),x=(width-w)/2,top=safeTop();int gap=6,idW=Math.max(100,(int)(w*0.52));int small=(w-idW-gap*2)/2;
        c.drawTextWithShadow(textRenderer,"ID сцены",x,top-10,0xFFAE9E96);
        c.drawTextWithShadow(textRenderer,"Ticks",x+idW+gap,top-10,0xFFAE9E96);
        c.drawTextWithShadow(textRenderer,"FOV",x+idW+gap+small+gap,top-10,0xFFAE9E96);
        c.drawTextWithShadow(textRenderer,"Субтитр кадра",x,top+20,0xFF9D918B);
        int infoY=Math.min(height-safeBottom()-36,top+198);
        if(height>245)c.drawCenteredTextWithShadow(textRenderer,"Кадров: "+scene.keyframes.size()+". Камера плавно интерполируется между точками.",width/2,infoY,0xFFB4A49C);
        if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,Math.min(height-18,infoY+15),0xFFD5A9A2);
        super.render(c,mx,my,d);
    }
}
