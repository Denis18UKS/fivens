package ru.fifth.horror.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.LiftBlockEntity;
import ru.fifth.horror.block.LiftPanelBlockEntity;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class FifthNetworking {
    private static final Gson GSON = new Gson();

    public static final Identifier OPEN_COMPUTER=id("open_computer"),SAVE_COMPUTER=id("save_computer"),RUN_COMPUTER=id("run_computer"),CREATE_NPC_EGG=id("create_npc_egg"),SAVE_NPC=id("save_npc"),STRUCTURE_CAPTURE=id("structure_capture"),STRUCTURE_ACTIVATE=id("structure_activate"),SAVE_CUTSCENE=id("save_cutscene"),PLAY_CUTSCENE=id("play_cutscene"),CUTSCENE_PAYLOAD=id("cutscene_payload"),CUTSCENE_END_TELEPORT=id("cutscene_end_teleport"),REQUEST_NPC_LIBRARY=id("request_npc_library"),NPC_LIBRARY_PAYLOAD=id("npc_library_payload"),PREVIEW_NPC_ANIMATION=id("preview_npc_animation"),NPC_CONTROL=id("npc_control"),MFL_CONTROL=id("mfl_control"),MFL_CONFIG=id("mfl_config"),REQUEST_CUTSCENE_LIBRARY=id("request_cutscene_library"),CUTSCENE_LIBRARY_PAYLOAD=id("cutscene_library_payload"),REQUEST_CUTSCENE_EDIT=id("request_cutscene_edit"),CUTSCENE_EDIT_PAYLOAD=id("cutscene_edit_payload"),CREATE_CASSETTE=id("create_cassette"),VHS_PLAYBACK=id("vhs_playback"),SAVE_ANIMATION_CONDITION=id("save_animation_condition"),OPEN_LIFT_PANEL=id("open_lift_panel"),LIFT_PANEL_CONTROL=id("lift_panel_control"),LIFT_TRAVEL_START=id("lift_travel_start"),LIFT_TRAVEL_END=id("lift_travel_end"),OPEN_LIFT_EDITOR=id("open_lift_editor"),SAVE_LIFT_CONFIG=id("save_lift_config"),LIFT_PANEL_TRANSFORM=id("lift_panel_transform"),LIFT_BINDER_FLOOR=id("lift_binder_floor"),LIFT_CURSE_CONFIG=id("lift_curse_config"),ENTITY_EFFECT_SAVE=id("entity_effect_save"),ENTITY_EFFECT_SYNC=id("entity_effect_sync"),SAVE_TV_CONFIG=id("save_tv_config"),SCREAMER=id("screamer");
    private static Identifier id(String path){return FifthMod.id(path);} private FifthNetworking(){}

    public static void registerServer(){
        ServerPlayNetworking.registerGlobalReceiver(SAVE_LIFT_CONFIG,(server,player,handler,buf,responseSender)->{
            BlockPos pos=buf.readBlockPos();String liftId=buf.readString(64);int floor=buf.readVarInt();int mask=buf.readVarInt();BlockPos stage=buf.readBlockPos();
            server.execute(()->{if(!player.hasPermissionLevel(2))return;if(player.getWorld().getBlockEntity(pos) instanceof LiftBlockEntity lift){lift.setLiftId(liftId);lift.setCurrentFloor(floor);lift.setTargetFloor(floor);lift.setOpenFloorMask(mask);lift.setStageOrigin(stage);LiftManager.register(lift);player.sendMessage(Text.literal("§8[§cFiven§8] §7Лифт §f"+lift.getLiftId()+" §7сохранён."),true);}});
        });

        ServerPlayNetworking.registerGlobalReceiver(LIFT_CURSE_CONFIG,(server,player,handler,buf,responseSender)->{
            BlockPos pos=buf.readBlockPos();boolean cursed=buf.readBoolean();
            server.execute(()->{if(!player.hasPermissionLevel(2))return;if(player.getWorld().getBlockEntity(pos) instanceof LiftBlockEntity lift){lift.setCursed(cursed);player.sendMessage(Text.literal("§8[§cFiven§8] §7Тип лифта: "+(cursed?"§cПРОКЛЯТЫЙ":"§aОБЫЧНЫЙ")),true);}});
        });

        ServerPlayNetworking.registerGlobalReceiver(LIFT_BINDER_FLOOR,(server,player,handler,buf,responseSender)->{
            int handIndex=buf.readVarInt();int floor=Math.max(1,Math.min(9,buf.readVarInt()));
            server.execute(()->{Hand hand=handIndex==1?Hand.OFF_HAND:Hand.MAIN_HAND;ItemStack stack=player.getStackInHand(hand);if(stack.isOf(FifthMod.LIFT_BUTTON_BINDER)){stack.getOrCreateNbt().putInt("FivenBindFloor",floor);player.sendMessage(Text.literal("§8[§cFiven§8] §7Этаж для привязки: §c"+floor),true);}});
        });

        ServerPlayNetworking.registerGlobalReceiver(LIFT_PANEL_CONTROL,(server,player,handler,buf,responseSender)->{
            BlockPos pos=buf.readBlockPos();String action=buf.readString(32);int floor=Math.max(1,Math.min(9,buf.readVarInt()));int value=buf.readVarInt();
            server.execute(()->{if(!(player.getWorld().getBlockEntity(pos) instanceof LiftPanelBlockEntity panel))return;LiftBlockEntity lift=panel.resolveLift(server);
                if("door".equals(action)||"enable".equals(action)){if(!player.hasPermissionLevel(2))return;panel.setEnabled(floor,value!=0);if(lift!=null)lift.setOpenOnFloor(floor,value!=0);return;}
                if("press".equals(action)){if(lift==null){player.sendMessage(Text.literal("§8[§cFiven§8] §7Панель не привязана к блоку лифта."),true);return;}LiftManager.travel(player,lift,floor);}
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(ENTITY_EFFECT_SAVE,(server,player,handler,buf,responseSender)->{EntityEffectManager.Config cfg=EntityEffectManager.read(buf);server.execute(()->{if(player.hasPermissionLevel(2))EntityEffectManager.save(server,cfg);});});
        ServerPlayNetworking.registerGlobalReceiver(SAVE_TV_CONFIG,(server,player,handler,buf,responseSender)->{BlockPos pos=buf.readBlockPos();int quality=buf.readVarInt();float noise=buf.readFloat();boolean mono=buf.readBoolean();server.execute(()->{if(player.hasPermissionLevel(2)&&player.getWorld().getBlockEntity(pos) instanceof ru.fifth.horror.block.TelevisionBlockEntity tv)tv.configure(quality,noise,mono);});});
        ServerPlayNetworking.registerGlobalReceiver(CREATE_NPC_EGG,(server,player,handler,buf,responseSender)->{String json=buf.readString(32767);server.execute(()->giveNpcEgg(player,json));});
        ServerPlayNetworking.registerGlobalReceiver(SAVE_NPC,(server,player,handler,buf,responseSender)->{int entityId=buf.readVarInt();String json=buf.readString(32767);server.execute(()->{if(player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc&&player.hasPermissionLevel(2)){npc.applyTemplateJson(json);FifthScriptEngine.indexNpc(npc);}});});
        ServerPlayNetworking.registerGlobalReceiver(SAVE_COMPUTER,(server,player,handler,buf,responseSender)->{BlockPos pos=buf.readBlockPos();String name=buf.readString(128),script=buf.readString(1_000_000);server.execute(()->{if(!player.hasPermissionLevel(2))return;if(player.getWorld().getBlockEntity(pos) instanceof ScriptComputerBlockEntity be){be.setScriptName(name);be.setScript(script);be.markDirty();FifthScriptEngine.saveScript(server,name,script);player.sendMessage(Text.literal("§7Сценарий сохранён: §f"+name),false);}});});
        ServerPlayNetworking.registerGlobalReceiver(RUN_COMPUTER,(server,player,handler,buf,responseSender)->{BlockPos pos=buf.readBlockPos();String name=buf.readString(128),script=buf.readString(1_000_000);boolean hasNpc=buf.readBoolean();java.util.UUID uuid=hasNpc?buf.readUuid():null;String npcId=hasNpc?buf.readString(128):"";server.execute(()->{if(!player.hasPermissionLevel(2))return;FifthScriptEngine.saveScript(server,name,script);if(player.getWorld().getBlockEntity(pos) instanceof ScriptComputerBlockEntity be){be.setScriptName(name);be.setScript(script);be.markDirty();}if(uuid!=null){DirectorNpcEntity npc=FifthScriptEngine.findNpc(server,uuid,npcId);if(npc==null){player.sendMessage(Text.literal("§cВыбранный NPC больше не найден: §f"+npcId),false);return;}FifthScriptEngine.runNamedForNpc(server,name,player,npc);}else FifthScriptEngine.runNamed(server,name,player);});});

        ServerPlayNetworking.registerGlobalReceiver(STRUCTURE_CAPTURE,(server,player,handler,buf,responseSender)->{String build=buf.readString(128),variant=buf.readString(128),group=buf.readString(128);boolean def=buf.readBoolean(),restore=buf.readBoolean();String floorSet=buf.readString(64);int floor=buf.readVarInt();BlockPos a=buf.readBlockPos(),b=buf.readBlockPos();server.execute(()->{if(!player.hasPermissionLevel(2))return;StructureLayerManager.capture(server,player.getServerWorld(),build,variant,group,def,restore,floorSet,floor,a,b);player.sendMessage(Text.literal("§7Слой сохранён: §f"+build+"/"+variant+(floor>0?" §8[§f"+floorSet+":"+floor+"§8]":"")),false);});});
        ServerPlayNetworking.registerGlobalReceiver(STRUCTURE_ACTIVATE,(server,player,handler,buf,responseSender)->{String build=buf.readString(128),variant=buf.readString(128);server.execute(()->{if(player.hasPermissionLevel(2))StructureLayerManager.activate(server,player.getServerWorld(),build,variant);});});

        ServerPlayNetworking.registerGlobalReceiver(SAVE_ANIMATION_CONDITION,(server,player,handler,buf,responseSender)->{String id=buf.readString(64),uuid=buf.readString(64),anim=buf.readString(256),type=buf.readString(32);double value=buf.readDouble();String conditionAnim=buf.readableBytes()>0?buf.readString(256):"";boolean screamer=buf.readableBytes()>0&&buf.readBoolean();server.execute(()->{if(player.hasPermissionLevel(2))ru.fifth.horror.animation.AnimationConditionManager.saveRule(server,new ru.fifth.horror.animation.AnimationConditionManager.Rule(id,uuid,anim,type,value,conditionAnim,screamer));});});

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_CUTSCENE_LIBRARY,(server,player,handler,buf,responseSender)->server.execute(()->{if(!player.hasPermissionLevel(2))return;var list=CutsceneManager.list(server);PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(list.size());for(var info:list){out.writeString(info.id(),128);out.writeVarInt(info.frames());out.writeVarInt(info.ticks());out.writeBoolean(info.teleportAtEnd());}ServerPlayNetworking.send(player,CUTSCENE_LIBRARY_PAYLOAD,out);}));
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_CUTSCENE_EDIT,(server,player,handler,buf,responseSender)->{String id=buf.readString(128);server.execute(()->{if(!player.hasPermissionLevel(2))return;String json=CutsceneManager.json(server,id);if(json.isBlank())return;PacketByteBuf out=PacketByteBufs.create();out.writeString(json,1_000_000);ServerPlayNetworking.send(player,CUTSCENE_EDIT_PAYLOAD,out);});});
        ServerPlayNetworking.registerGlobalReceiver(CREATE_CASSETTE,(server,player,handler,buf,responseSender)->{String id=buf.readString(128);server.execute(()->{if(!player.hasPermissionLevel(2)||CutsceneManager.load(server,id)==null)return;ItemStack stack=new ItemStack(FifthMod.VHS_CASSETTE);ru.fifth.horror.item.VhsCassetteItem.setRecording(stack,id);player.giveItemStack(stack);});});
        ServerPlayNetworking.registerGlobalReceiver(SAVE_CUTSCENE,(server,player,handler,buf,responseSender)->{String json=buf.readString(1_000_000);server.execute(()->{if(player.hasPermissionLevel(2))CutsceneManager.save(server,GSON.fromJson(json,CutsceneDefinition.class));});});
        ServerPlayNetworking.registerGlobalReceiver(PLAY_CUTSCENE,(server,player,handler,buf,responseSender)->{String id=buf.readString(128);server.execute(()->{if(player.hasPermissionLevel(2))CutsceneManager.play(server,id);});});
        ServerPlayNetworking.registerGlobalReceiver(CUTSCENE_END_TELEPORT,(server,player,handler,buf,responseSender)->{String id=buf.readString(128);server.execute(()->{CutsceneDefinition scene=CutsceneManager.load(server,id);if(scene==null||!scene.teleportPlayerAtEnd||scene.keyframes.isEmpty())return;var end=scene.keyframes.get(scene.keyframes.size()-1);double eye=player.getEyeY()-player.getY();player.teleport(player.getServerWorld(),end.x,end.y-eye,end.z,end.yaw,end.pitch);});});

        ServerPlayNetworking.registerGlobalReceiver(REQUEST_NPC_LIBRARY,(server,player,handler,buf,responseSender)->server.execute(()->sendNpcLibrary(player)));
        ServerPlayNetworking.registerGlobalReceiver(MFL_CONFIG,(server,player,handler,buf,responseSender)->{
            int entityId=buf.readVarInt();int mode=buf.readVarInt();boolean hunt=buf.readBoolean(),patrol=buf.readBoolean();double range=buf.readDouble(),angle=buf.readDouble(),walk=buf.readDouble(),run=buf.readDouble();int search=buf.readVarInt();
            server.execute(()->{if(!player.hasPermissionLevel(2)||!(player.getWorld().getEntityById(entityId) instanceof MonsterForLiftEntity m))return;m.setAiMode(MonsterForLiftEntity.AiMode.values()[Math.max(0,Math.min(MonsterForLiftEntity.AiMode.values().length-1,mode))]);m.setHuntEnabled(hunt);m.setPatrolEnabled(patrol);m.setVisionRange(range);m.setVisionAngle(angle);m.setWalkSpeed(walk);m.setRunSpeed(run);m.setSearchDurationTicks(search);});
        });
        ServerPlayNetworking.registerGlobalReceiver(MFL_CONTROL,(server,player,handler,buf,responseSender)->{int entityId=buf.readVarInt();String action=buf.readString(64);String arg="animation".equals(action)?buf.readString(128):"";server.execute(()->{if(!player.hasPermissionLevel(2)||!(player.getWorld().getEntityById(entityId) instanceof MonsterForLiftEntity m))return;switch(action){case "toggle_route"->{if(m.isRouteRunning())m.stopRoute();else m.startRoute(true,.72);}case "clear_route"->m.clearRoute();case "animation"->m.preview(arg);case "screamer"->m.triggerScreamer(player);}});});
        ServerPlayNetworking.registerGlobalReceiver(NPC_CONTROL,(server,player,handler,buf,responseSender)->{int entityId=buf.readVarInt();String action=buf.readString(64);buf.readString(256);server.execute(()->{if(!player.hasPermissionLevel(2)||!(player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc))return;FifthScriptEngine.indexNpc(npc);switch(action){case "toggle_ai"->npc.setAiEnabled(!npc.isAiEnabled());case "toggle_path"->{if(npc.isRouteRunning())npc.stopPath();else{npc.setAiEnabled(true);npc.followPath(true,.25);}}case "start_path"->{npc.setAiEnabled(true);npc.followPath(true,.25);}case "stop_path"->npc.stopPath();case "statue"->npc.setAiEnabled(false);case "start"->npc.setAiEnabled(true);}});});
        ServerPlayNetworking.registerGlobalReceiver(PREVIEW_NPC_ANIMATION,(server,player,handler,buf,responseSender)->{int entityId=buf.readVarInt();String file=buf.readString(512),animation=buf.readString(512);server.execute(()->{if(!player.hasPermissionLevel(2))return;if(player.getWorld().getEntityById(entityId) instanceof DirectorNpcEntity npc){FifthScriptEngine.indexNpc(npc);if(!file.isBlank())npc.setAnimationResource(file);npc.setCurrentAnimation(animation);}});});
    }

    private static void sendNpcLibrary(ServerPlayerEntity player){if(!player.hasPermissionLevel(2))return;record Row(DirectorNpcEntity npc,String world){}List<Row> rows=new ArrayList<>();Box all=new Box(-30_000_000,-2048,-30_000_000,30_000_000,4096,30_000_000);var server=player.getServerWorld().getServer();for(var world:server.getWorlds()){String worldId=world.getRegistryKey().getValue().toString();for(DirectorNpcEntity npc:world.getEntitiesByClass(DirectorNpcEntity.class,all,n->!n.isRemoved())){FifthScriptEngine.indexNpc(npc);rows.add(new Row(npc,worldId));}}rows.sort(Comparator.comparing((Row r)->r.npc().getNpcId(),String.CASE_INSENSITIVE_ORDER));PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(Math.min(rows.size(),2048));for(int i=0;i<rows.size()&&i<2048;i++){DirectorNpcEntity npc=rows.get(i).npc();out.writeUuid(npc.getUuid());out.writeString(npc.getNpcId(),128);out.writeString(npc.getCustomName()==null?npc.getNpcId():npc.getCustomName().getString(),256);out.writeBoolean(npc.isAiEnabled());out.writeString(npc.getAiScript(),512);out.writeVarInt(npc.getPathPoints().size());out.writeString(rows.get(i).world(),256);}ServerPlayNetworking.send(player,NPC_LIBRARY_PAYLOAD,out);}
    private static void giveNpcEgg(ServerPlayerEntity player,String json){if(!player.hasPermissionLevel(2))return;JsonObject object=GSON.fromJson(json,JsonObject.class);ItemStack stack=new ItemStack(FifthMod.NPC_SPAWN_EGG);stack.getOrCreateNbt().putString("FifthNpcTemplate",json);if(object!=null&&object.has("name"))stack.setCustomName(Text.literal("NPC: "+object.get("name").getAsString()));player.giveItemStack(stack);}

    public static void openLiftPanel(ServerPlayerEntity player,LiftPanelBlockEntity panel,boolean edit){int mask=panel.getEnabledMask();LiftBlockEntity lift=panel.resolveLift(player.getServer());if(lift!=null)mask=lift.getOpenFloorMask();PacketByteBuf out=PacketByteBufs.create();out.writeBlockPos(panel.getPos());out.writeVarInt(mask);out.writeBoolean(edit);ServerPlayNetworking.send(player,OPEN_LIFT_PANEL,out);}
    public static void openLiftEditor(ServerPlayerEntity player,LiftBlockEntity lift){PacketByteBuf out=PacketByteBufs.create();out.writeBlockPos(lift.getPos());out.writeString(lift.getLiftId(),64);out.writeVarInt(lift.getCurrentFloor());out.writeVarInt(lift.getOpenFloorMask());out.writeBlockPos(lift.getStageOrigin());ServerPlayNetworking.send(player,OPEN_LIFT_EDITOR,out);}
    public static void openComputer(ServerPlayerEntity player,ScriptComputerBlockEntity be){PacketByteBuf out=PacketByteBufs.create();out.writeBlockPos(be.getPos());out.writeString(be.getScriptName());out.writeString(be.getScript(),1_000_000);ServerPlayNetworking.send(player,OPEN_COMPUTER,out);}
    public static void sendScreamer(ServerPlayerEntity player,int ticks,float intensity){PacketByteBuf out=PacketByteBufs.create();out.writeVarInt(Math.max(1,Math.min(200,ticks)));out.writeFloat(Math.max(.1f,Math.min(3f,intensity)));ServerPlayNetworking.send(player,SCREAMER,out);}
}
