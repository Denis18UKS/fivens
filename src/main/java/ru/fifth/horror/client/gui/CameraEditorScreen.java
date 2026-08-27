package ru.fifth.horror.client.gui;

import com.google.gson.Gson;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.VhsRecorderClient;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

import java.util.HashMap;
import java.util.Map;

/** Camera/cutscene editor with per-world draft persistence and real VHS recording. */
public class CameraEditorScreen extends HorrorScreen {
    private static final Gson GSON=new Gson();
    private static final Map<String,CutsceneDefinition> DRAFTS=new HashMap<>();
    private final Screen parent;private final CutsceneDefinition scene;private TextFieldWidget id,duration,fov,subtitle;private boolean hideHud=true,lock=true,teleportAtEnd;private String status="";private boolean initializedFields;

    public CameraEditorScreen(Screen parent){super(Text.literal("ПЯТЫЙ / КАТСЦЕНЫ"));this.parent=parent;CutsceneDefinition draft=DRAFTS.get(draftKey());this.scene=draft==null?new CutsceneDefinition():copy(draft);}
    public CameraEditorScreen(Screen parent,CutsceneDefinition existing){super(Text.literal("ПЯТЫЙ / КАТСЦЕНЫ"));this.parent=parent;this.scene=existing==null?new CutsceneDefinition():copy(existing);}
    private static CutsceneDefinition copy(CutsceneDefinition d){return GSON.fromJson(GSON.toJson(d),CutsceneDefinition.class);}
    private static String draftKey(){MinecraftClient c=MinecraftClient.getInstance();String player=c.player==null?"menu":c.player.getUuidAsString();String world=c.world==null?"none":c.world.getRegistryKey().getValue().toString();return player+"|"+world;}

    @Override protected void init(){
        beginHorrorInit();hideHud=scene.hideHud;lock=scene.lockInput;teleportAtEnd=scene.teleportPlayerAtEnd;int w=contentWidth(520),x=(width-w)/2,top=safeTop(),gap=6,bh=20;boolean compact=height<300;
        int idW=Math.max(100,(int)(w*.52)),small=(w-idW-gap*2)/2;id=horrorField(x,top,idW,bh,scene.id,128);duration=horrorField(x+idW+gap,top,small,bh,"40",10);fov=horrorField(x+idW+gap+small+gap,top,w-idW-small-gap*2,bh,"70",10);subtitle=horrorField(x,top+27,w,bh,"",256);initializedFields=true;
        int by=top+54,half=(w-gap)/2,step=compact?24:27;
        addDrawableChild(HorrorButton.builder(Text.literal("+ Ключевой кадр из позиции игрока"),b->addFrame()).dimensions(x,by,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("HUD: "+(hideHud?"СКРЫТ":"ВИДЕН")),b->{hideHud=!hideHud;b.setMessage(Text.literal("HUD: "+(hideHud?"СКРЫТ":"ВИДЕН")));persistDraft();}).dimensions(x,by+step,half,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Управление: "+(lock?"БЛОК":"СВОБОДНО")),b->{lock=!lock;b.setMessage(Text.literal("Управление: "+(lock?"БЛОК":"СВОБОДНО")));persistDraft();}).dimensions(x+half+gap,by+step,w-half-gap,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Финальная точка игрока: "+(teleportAtEnd?"ДА":"НЕТ")),b->{teleportAtEnd=!teleportAtEnd;b.setMessage(Text.literal("Финальная точка игрока: "+(teleportAtEnd?"ДА":"НЕТ")));persistDraft();}).dimensions(x,by+step*2,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить"),b->save()).dimensions(x,by+step*3,half,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("▶ Проиграть"),b->play()).dimensions(x+half+gap,by+step*3,w-half-gap,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("📼 Записать VHS"),b->cassette()).dimensions(x,by+step*4,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Очистить кадры"),b->{scene.keyframes.clear();status="Кадры очищены";persistDraft();}).dimensions(x,by+step*5,half,bh).build());addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x+half+gap,by+step*5,w-half-gap,bh).build());
    }
    private boolean inWorld(){return client!=null&&client.player!=null&&client.world!=null&&client.getNetworkHandler()!=null;}
    private void syncFields(){if(!initializedFields)return;scene.id=id.getText().isBlank()?"scene":id.getText().trim();scene.hideHud=hideHud;scene.lockInput=lock;scene.teleportPlayerAtEnd=teleportAtEnd;}
    private void persistDraft(){syncFields();DRAFTS.put(draftKey(),copy(scene));}
    private void addFrame(){if(!inWorld()){status="Кадр снимается только внутри мира.";return;}int dur=40;double view=70;try{dur=Integer.parseInt(duration.getText());}catch(Exception ignored){}try{view=Double.parseDouble(fov.getText());}catch(Exception ignored){}dur=Math.max(1,Math.min(12000,dur));view=Math.max(1,Math.min(179,view));var p=client.player;var k=new CutsceneDefinition.Keyframe(p.getX(),p.getEyeY(),p.getZ(),p.getYaw(),p.getPitch(),view,dur);k.subtitle=subtitle.getText();scene.keyframes.add(k);status="Кадр добавлен: "+scene.keyframes.size();persistDraft();}
    private boolean save(){syncFields();if(!inWorld()){status="Сохранение доступно после входа в мир.";return false;}if(scene.keyframes.isEmpty()){status="Добавь хотя бы один ключевой кадр.";return false;}PacketByteBuf out=PacketByteBufs.create();out.writeString(GSON.toJson(scene),1_000_000);ClientPlayNetworking.send(FifthNetworking.SAVE_CUTSCENE,out);persistDraft();status="Сцена сохранена: "+scene.id;return true;}
    private void play(){if(!save())return;PacketByteBuf out=PacketByteBufs.create();out.writeString(scene.id,128);ClientPlayNetworking.send(FifthNetworking.PLAY_CUTSCENE,out);client.setScreen(null);}
    private void cassette(){if(!save())return;status="Подготовка записи VHS...";VhsRecorderClient.start(copy(scene));}
    @Override public void removed(){persistDraft();super.removed();}
    @Override public void render(DrawContext c,int mx,int my,float delta){horrorBackground(c);int w=contentWidth(520),x=(width-w)/2,top=safeTop(),gap=6;int idW=Math.max(100,(int)(w*.52)),small=(w-idW-gap*2)/2;c.drawTextWithShadow(textRenderer,"ID сцены",x,top-10,0xFFAE9E96);c.drawTextWithShadow(textRenderer,"Ticks",x+idW+gap,top-10,0xFFAE9E96);c.drawTextWithShadow(textRenderer,"FOV",x+idW+gap+small+gap,top-10,0xFFAE9E96);if(height>230)c.drawCenteredTextWithShadow(textRenderer,"Кадров: "+scene.keyframes.size(),width/2,height-safeBottom()-26,0xFFB4A49C);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,height-safeBottom()-12,0xFFD5A9A2);super.render(c,mx,my,delta);}
}
