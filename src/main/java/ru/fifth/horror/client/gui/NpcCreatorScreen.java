package ru.fifth.horror.client.gui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.client.NpcTemplateStore;
import ru.fifth.horror.network.FifthNetworking;

public class NpcCreatorScreen extends HorrorScreen {
    private static final Gson GSON=new Gson();
    private final Screen parent;private final int entityId;private final String initialJson;
    private TextFieldWidget id,name,model,texture,animFile,scale,script;
    private boolean showName;private String skinBase64="";private String status="";
    public NpcCreatorScreen(Screen parent,int entityId,String initialJson){super(Text.literal(entityId<0?"ПЯТЫЙ / СОЗДАНИЕ NPC":"ПЯТЫЙ / РЕДАКТОР NPC"));this.parent=parent;this.entityId=entityId;this.initialJson=initialJson;}

    @Override protected void init(){
        beginHorrorInit();
        int w=contentWidth(560),x=(width-w)/2,top=safeTop(),gap=8,col=(w-gap)/2,fh=19,row=27;
        id=horrorField(x,top,col,fh,"npc_01",64);
        name=horrorField(x+col+gap,top,w-col-gap,fh,"Безымянный",96);
        model=horrorField(x,top+row,col,fh,"fiven:geo/npc_default.geo.json",256);
        texture=horrorField(x+col+gap,top+row,w-col-gap,fh,"fiven:textures/entity/npc_default.png",256);
        animFile=horrorField(x,top+row*2,col,fh,"fiven:animations/npc_default.animation.json",256);
        script=horrorField(x+col+gap,top+row*2,w-col-gap,fh,"",128);
        scale=horrorField(x,top+row*3,Math.max(72,col/2),fh,"1.0",8);
        if(initialJson!=null&&!initialJson.isBlank())loadInitial();

        int rightX=x+col+gap;
        addDrawableChild(HorrorButton.builder(Text.literal(showName?"Имя над NPC: ДА":"Имя над NPC: НЕТ"),b->{showName=!showName;b.setMessage(Text.literal(showName?"Имя над NPC: ДА":"Имя над NPC: НЕТ"));}).dimensions(rightX,top+row*3,w-col-gap,fh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Скин 64×64 + второй слой"),b->client.setScreen(new SkinEditorScreen(this,skinBase64))).dimensions(x,top+row*4,col,21).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Анимации / автообнаружение"),b->client.setScreen(new AnimationListScreen(this,entityId,e->animFile.setText(e.file().toString())))).dimensions(rightX,top+row*4,w-col-gap,21).build());
        boolean connected=client!=null&&client.getNetworkHandler()!=null;
        String saveLabel=entityId<0?(connected?"Сохранить + получить яйцо":"Сохранить шаблон"):"Сохранить NPC";
        addDrawableChild(HorrorButton.builder(Text.literal(saveLabel),b->save()).dimensions(x,top+row*5,w,22).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"),b->client.setScreen(parent)).dimensions(x,top+row*6,w,22).build());
    }

    private void loadInitial(){try{JsonObject o=GSON.fromJson(initialJson,JsonObject.class);if(o.has("id"))id.setText(o.get("id").getAsString());if(o.has("name"))name.setText(o.get("name").getAsString());if(o.has("model"))model.setText(o.get("model").getAsString());if(o.has("texture"))texture.setText(o.get("texture").getAsString());if(o.has("animationFile"))animFile.setText(o.get("animationFile").getAsString());if(o.has("scale"))scale.setText(o.get("scale").getAsString());if(o.has("aiScript"))script.setText(o.get("aiScript").getAsString());if(o.has("showName"))showName=o.get("showName").getAsBoolean();if(o.has("skinBase64"))skinBase64=o.get("skinBase64").getAsString();}catch(Exception ignored){}}
    public void setSkinBase64(String value){skinBase64=value==null?"":value;}
    private void save(){
        JsonObject o=new JsonObject();o.addProperty("id",id.getText());o.addProperty("name",name.getText());o.addProperty("showName",showName);o.addProperty("model",model.getText());o.addProperty("texture",texture.getText());o.addProperty("animationFile",animFile.getText());o.addProperty("skinBase64",skinBase64);try{o.addProperty("scale",Float.parseFloat(scale.getText()));}catch(Exception e){o.addProperty("scale",1.0f);}o.addProperty("aiScript",script.getText());o.addProperty("aiEnabled",false);String json=GSON.toJson(o);
        if(entityId<0){String saved=NpcTemplateStore.save(json);if(client==null||client.getNetworkHandler()==null){status=saved==null?"Не удалось сохранить шаблон":"Шаблон сохранён: "+saved;return;}PacketByteBuf buf=PacketByteBufs.create();buf.writeString(json,32767);ClientPlayNetworking.send(FifthNetworking.CREATE_NPC_EGG,buf);client.setScreen(parent);}
        else{if(client==null||client.getNetworkHandler()==null){status="NPC можно сохранить только внутри мира.";return;}PacketByteBuf buf=PacketByteBufs.create();buf.writeVarInt(entityId);buf.writeString(json,32767);ClientPlayNetworking.send(FifthNetworking.SAVE_NPC,buf);client.setScreen(parent);}
    }

    @Override public void render(DrawContext c,int mx,int my,float d){
        horrorBackground(c);
        int w=contentWidth(560),x=(width-w)/2,top=safeTop(),gap=8,col=(w-gap)/2,row=27,right=x+col+gap;
        c.drawTextWithShadow(textRenderer,"ID NPC",x,top-10,0xFFAE9E96);c.drawTextWithShadow(textRenderer,"Имя",right,top-10,0xFFAE9E96);
        c.drawTextWithShadow(textRenderer,"Geo-модель",x,top+row-10,0xFF988C86);c.drawTextWithShadow(textRenderer,"Текстура",right,top+row-10,0xFF988C86);
        c.drawTextWithShadow(textRenderer,"Animation JSON",x,top+row*2-10,0xFF988C86);c.drawTextWithShadow(textRenderer,"AI-скрипт",right,top+row*2-10,0xFF988C86);
        c.drawTextWithShadow(textRenderer,"Масштаб",x,top+row*3-10,0xFF988C86);
        if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,Math.min(height-16,top+row*7),0xFFD9B2A8);
        super.render(c,mx,my,d);
    }
}
