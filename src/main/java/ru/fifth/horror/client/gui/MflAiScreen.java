package ru.fifth.horror.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.network.FifthNetworking;

/** Logical/scripted AI settings for one MFL. */
public final class MflAiScreen extends HorrorScreen {
    private final Screen parent;
    private final int entityId;
    private MonsterForLiftEntity.AiMode mode;
    private boolean hunt;
    private boolean patrol;
    private TextFieldWidget range, angle, walk, run, search;
    private String status = "";

    public MflAiScreen(Screen parent, MonsterForLiftEntity mfl) {
        super(Text.literal("FIVEN / MFL AI"));
        this.parent = parent;
        this.entityId = mfl.getId();
        this.mode = mfl.getAiMode();
        this.hunt = mfl.isHuntEnabled();
        this.patrol = mfl.isPatrolEnabled();
    }

    @Override
    protected void init() {
        beginHorrorInit();
        int w = contentWidth(520), x = (width - w) / 2, y = safeTop(), bh = 20, gap = 6;
        addDrawableChild(HorrorButton.builder(Text.literal(modeText()), b -> {
            mode = switch (mode) { case OFF -> MonsterForLiftEntity.AiMode.LOGICAL; case LOGICAL -> MonsterForLiftEntity.AiMode.SCRIPTED; case SCRIPTED -> MonsterForLiftEntity.AiMode.OFF; };
            b.setMessage(Text.literal(modeText()));
        }).dimensions(x, y, w, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(huntText()), b -> { hunt = !hunt; b.setMessage(Text.literal(huntText())); }).dimensions(x, y + 28, (w-gap)/2, bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal(patrolText()), b -> { patrol = !patrol; b.setMessage(Text.literal(patrolText())); }).dimensions(x+(w-gap)/2+gap, y+28, (w-gap)/2, bh).build());

        int col=(w-gap)/2;
        range=horrorField(x,y+70,col,bh,Double.toString(24),12);
        angle=horrorField(x+col+gap,y+70,col,bh,Double.toString(105),12);
        walk=horrorField(x,y+112,col,bh,Double.toString(.72),12);
        run=horrorField(x+col+gap,y+112,col,bh,Double.toString(1.18),12);
        search=horrorField(x,y+154,w,bh,Integer.toString(120),12);

        addDrawableChild(HorrorButton.builder(Text.literal("Сохранить AI"), b -> save()).dimensions(x,y+188,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Тест скримера"), b -> sendControl("screamer", "")).dimensions(x,y+216,w,bh).build());
        addDrawableChild(HorrorButton.builder(Text.literal("Назад"), b -> client.setScreen(parent)).dimensions(x,y+244,w,bh).build());
    }

    private String modeText(){return "Режим ИИ: "+switch(mode){case OFF->"ВЫКЛ";case LOGICAL->"ЛОГИЧЕСКИЙ";case SCRIPTED->"СЦЕНАРНЫЙ";};}
    private String huntText(){return "Охота на игрока: "+(hunt?"ДА":"НЕТ");}
    private String patrolText(){return "Патруль: "+(patrol?"ДА":"НЕТ");}

    private void save(){
        try{
            double r=Double.parseDouble(range.getText()), a=Double.parseDouble(angle.getText()), w=Double.parseDouble(walk.getText()), rs=Double.parseDouble(run.getText());
            int st=Integer.parseInt(search.getText());
            PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(entityId);out.writeVarInt(mode.ordinal());out.writeBoolean(hunt);out.writeBoolean(patrol);out.writeDouble(r);out.writeDouble(a);out.writeDouble(w);out.writeDouble(rs);out.writeVarInt(st);
            ClientPlayNetworking.send(FifthNetworking.MFL_CONFIG,out);status="Настройки AI отправлены.";
        }catch(Exception e){status="Проверь числовые поля.";}
    }

    private void sendControl(String action,String arg){PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(entityId);out.writeString(action,64);if("animation".equals(action))out.writeString(arg,128);ClientPlayNetworking.send(FifthNetworking.MFL_CONTROL,out);status="Команда отправлена: "+action;}

    @Override public void render(DrawContext c,int mx,int my,float d){horrorBackground(c);int w=contentWidth(520),x=(width-w)/2,y=safeTop();c.drawTextWithShadow(textRenderer,"Дальность зрения",x,y+58,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Угол зрения",x+(w+6)/2,y+58,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Скорость walking",x,y+100,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Скорость run",x+(w+6)/2,y+100,0xFFB69F97);c.drawTextWithShadow(textRenderer,"Поиск после потери цели, тики",x,y+142,0xFFB69F97);if(!status.isBlank())c.drawCenteredTextWithShadow(textRenderer,status,width/2,Math.min(height-safeBottom()-12,y+274),0xFFD99090);super.render(c,mx,my,d);}
}
