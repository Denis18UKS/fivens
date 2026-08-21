package ru.fifth.horror.client;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceType;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.TypedActionResult;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.client.gui.*;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

public final class FifthClient implements ClientModInitializer {
    private static final Gson GSON=new Gson();
    @Override public void onInitializeClient(){
        EntityRendererRegistry.register(FifthMod.DIRECTOR_NPC,DirectorNpcRenderer::new);
        EntityRendererRegistry.register(FifthMod.LIFT_BUTTON,LiftButtonRenderer::new); // legacy only
        EntityRendererRegistry.register(FifthMod.MONSTER_FOR_LIFT,MonsterForLiftRenderer::new);
        EntityRendererRegistry.register(FifthMod.LIFT_PANEL,LiftPanelRenderer::new); // legacy only
        BlockEntityRendererRegistry.register(FifthMod.LIFT_BE,LiftRenderer::new);
        BlockEntityRendererRegistry.register(FifthMod.CASSETTE_DRIVE_BE,CassetteDriveRenderer::new);
        BlockEntityRendererRegistry.register(FifthMod.TELEVISION_BE,TelevisionRenderer::new);
        EntityEffectRenderer.init();ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(AnimationCatalog.INSTANCE);

        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_COMPUTER,(client,handler,buf,sender)->{var pos=buf.readBlockPos();String name=buf.readString(128),script=buf.readString(1_000_000);client.execute(()->client.setScreen(new ScriptComputerScreen(pos,name,script)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_PAYLOAD,(client,handler,buf,sender)->{String json=buf.readString(1_000_000);client.execute(()->{try{CutscenePlayback.start(GSON.fromJson(json,CutsceneDefinition.class));client.setScreen(null);}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_LIFT_PANEL,(client,handler,buf,sender)->{var pos=buf.readBlockPos();int mask=buf.readVarInt();boolean edit=buf.readBoolean();client.execute(()->client.setScreen(new LiftPanelScreen(client.currentScreen,pos,mask,edit)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.VHS_PLAYBACK,(client,handler,buf,sender)->{String json=buf.readString(1_000_000);int mode=buf.readVarInt();var pos=buf.readBlockPos();client.execute(()->{try{VhsPlayback.start(GSON.fromJson(json,CutsceneDefinition.class),mode,pos);}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_LIBRARY_PAYLOAD,(client,handler,buf,sender)->{int n=buf.readVarInt();java.util.List<CutsceneLibraryScreen.Info> rows=new java.util.ArrayList<>();for(int i=0;i<n;i++)rows.add(new CutsceneLibraryScreen.Info(buf.readString(128),buf.readVarInt(),buf.readVarInt(),buf.readBoolean()));client.execute(()->{if(client.currentScreen instanceof CutsceneLibraryScreen lib)lib.update(rows);});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_EDIT_PAYLOAD,(client,handler,buf,sender)->{String json=buf.readString(1_000_000);client.execute(()->{try{client.setScreen(new CameraEditorScreen(client.currentScreen,GSON.fromJson(json,CutsceneDefinition.class)));}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.LIFT_TRAVEL_START,(client,handler,buf,sender)->{int from=buf.readVarInt(),to=buf.readVarInt(),ticks=buf.readVarInt();client.execute(()->LiftTravelOverlay.start(from,to,ticks));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.LIFT_TRAVEL_END,(client,handler,buf,sender)->{int floor=buf.readVarInt();client.execute(()->LiftTravelOverlay.finish(floor));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_LIFT_EDITOR,(client,handler,buf,sender)->{var pos=buf.readBlockPos();String liftId=buf.readString(64);int floor=buf.readVarInt(),mask=buf.readVarInt();var stage=buf.readBlockPos();client.execute(()->client.setScreen(new LiftEditorScreen(client.currentScreen,pos,liftId,floor,mask,stage)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.ENTITY_EFFECT_SYNC,(client,handler,buf,sender)->{var cfg=ru.fifth.horror.effect.EntityEffectManager.read(buf);client.execute(()->EntityEffectRenderer.update(cfg));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.NPC_LIBRARY_PAYLOAD,(client,handler,buf,sender)->{int count=Math.min(2048,buf.readVarInt());java.util.List<ScriptComputerScreen.NpcInfo> rows=new java.util.ArrayList<>(count);for(int i=0;i<count;i++)rows.add(new ScriptComputerScreen.NpcInfo(buf.readUuid(),buf.readString(128),buf.readString(256),buf.readBoolean(),buf.readString(512),buf.readVarInt(),buf.readString(256)));client.execute(()->{if(client.currentScreen instanceof ScriptComputerScreen screen)screen.updateNpcLibrary(rows);});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.SCREAMER,(client,handler,buf,sender)->{int ticks=buf.readVarInt();float intensity=buf.readFloat();client.execute(()->{ScreamerOverlay.start(ticks,intensity);if(client.player!=null)client.player.playSound(SoundEvents.ENTITY_ENDERMAN_SCREAM,1.0f,.55f);});});

        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient&&CutscenePlayback.lockInput())return net.minecraft.util.ActionResult.PASS;
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.NPC_EDITOR_TOOL)&&entity instanceof ru.fifth.horror.entity.DirectorNpcEntity npc){MinecraftClient.getInstance().setScreen(new NpcEditorScreen(MinecraftClient.getInstance().currentScreen,npc));return net.minecraft.util.ActionResult.SUCCESS;}
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.MFL_EDITOR_TOOL)&&entity instanceof ru.fifth.horror.entity.MonsterForLiftEntity mfl){MinecraftClient.getInstance().setScreen(new MflEditorScreen(MinecraftClient.getInstance().currentScreen,mfl));return net.minecraft.util.ActionResult.SUCCESS;}
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.ANIMATION_CONDITION_TOOL)){MinecraftClient.getInstance().setScreen(new AnimationConditionScreen(MinecraftClient.getInstance().currentScreen,entity));return net.minecraft.util.ActionResult.SUCCESS;}
            return net.minecraft.util.ActionResult.PASS;
        });
        UseItemCallback.EVENT.register((player,world,hand)->{
            if(!world.isClient)return TypedActionResult.pass(player.getStackInHand(hand));var stack=player.getStackInHand(hand);if(CutscenePlayback.lockInput())return TypedActionResult.pass(stack);MinecraftClient client=MinecraftClient.getInstance();
            if(stack.isOf(FifthMod.NPC_CREATOR)){client.setScreen(new NpcCreatorScreen(client.currentScreen,-1,null));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.CAMERA_TOOL)){client.setScreen(new CameraEditorScreen(client.currentScreen));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.BUILD_LAYER_TOOL)){client.setScreen(new BuildLayerScreen(client.currentScreen,stack.copy()));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.ENTITY_SHADER_TOOL)){client.setScreen(new EntityShaderScreen(client.currentScreen));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.CUTSCENE_LIBRARY_TOOL)){client.setScreen(new CutsceneLibraryScreen(client.currentScreen));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.TV_LINK_TOOL)&&stack.getNbt()!=null&&stack.getNbt().contains("FivenTvPos")){var pos=net.minecraft.util.math.BlockPos.fromLong(stack.getNbt().getLong("FivenTvPos"));if(client.world!=null&&client.world.getBlockEntity(pos) instanceof ru.fifth.horror.block.TelevisionBlockEntity tv){client.setScreen(new TvSettingsScreen(client.currentScreen,pos,tv.getQuality(),tv.getNoise(),tv.isMonochrome()));return TypedActionResult.success(stack);}}
            return TypedActionResult.pass(stack);
        });

        ItemTooltipCallback.EVENT.register((stack,context,lines)->{var id=Registries.ITEM.getId(stack.getItem());if(!FifthMod.MOD_ID.equals(id.getNamespace()))return;if(!Screen.hasShiftDown()){lines.add(Text.literal("§8Зажмите Shift для описания"));return;}for(String line:description(id.getPath()).split("\\n"))lines.add(Text.literal("§7"+line));});
        HudRenderCallback.EVENT.register((draw,tickDelta)->{LiftTravelOverlay.render(draw);VhsPlayback.render(draw);ScreamerOverlay.render(draw);});
        ClientTickEvents.START_CLIENT_TICK.register(CutsceneInputLock::apply);
        ClientTickEvents.END_CLIENT_TICK.register(client->{MenuMusicController.tick(client);LiftTravelOverlay.tick();VhsPlayback.tick();ScreamerOverlay.tick();CutscenePlayback.tick();CutsceneInputLock.apply(client);});
    }

    private static String description(String id){return switch(id){
        case "lift"->"Физический блок кабины лифта.\nShift+ПКМ: сведения; Lift Editor: этажи и область слоёв.";
        case "lift_panel"->"Настенная панель этажей 1–9.\nСвяжи её с лифтом через Lift Panel Tool.";
        case "lift_button_binder"->"Привязка обычной каменной кнопки к лифту.\nПКМ по лифту → выбери этаж → ПКМ по Stone Button.";
        case "lift_panel_tool"->"Связывает панель этажей с выбранным блоком лифта.";
        case "lift_editor_tool"->"Открывает настройки ID, этажей, дверей и области слоёв лифта.";
        case "vhs_cassette"->"VHS-кассета. Может хранить ID записи катсцены.";
        case "television"->"Телевизор Fiven для воспроизведения VHS-записей.";
        case "cassette_drive"->"Кассетовод. ПКМ кассетой — вставить; Shift+ПКМ — извлечь.";
        case "tv_link_tool"->"Связывает телевизор и кассетовод и открывает настройки VHS.";
        case "camera_tool"->"Режиссёрская камера: ключевые кадры, запись и получение кассеты.";
        case "cutscene_library_tool"->"Библиотека сохранённых катсцен, предпросмотр и VHS.";
        case "animation_condition_tool"->"Условия анимаций и скримеров, включая текущую анимацию сущности.";
        case "entity_shader_tool"->"Привязывает world-space хоррор-эффекты к выбранным сущностям.";
        case "mfl_spawn_egg"->"Яйцо monster_for_lift (MFL).";
        case "mfl_editor_tool"->"Настройки MFL, маршрута и его личных анимаций.";
        case "mfl_path_tool"->"Прокладывает маршрут выбранного MFL по точкам.";
        case "npc_creator"->"Создаёт шаблон нового режиссёрского NPC.";
        case "npc_spawn_egg"->"Спавнит NPC из сохранённого шаблона.";
        case "npc_editor_tool"->"Основной визуальный редактор NPC без обязательного скриптинга.";
        case "npc_path_tool"->"Создаёт и редактирует маршрут выбранного NPC.";
        case "npc_state_tool"->"Переключает состояние/активность NPC.";
        case "npc_link_tool"->"Связывает NPC со сценарным компьютером.";
        case "build_layer_tool"->"Сохраняет слои постройки и помечает их этажами лифта.";
        case "script_computer"->"Сценарный компьютер для расширенной логики карты.";
        default->"Инструмент/блок внутреннего движка Fiven.\nИспользуется при создании карты «Пятый».";};}
}
