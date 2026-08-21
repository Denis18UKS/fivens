package ru.fifth.horror.client;

import com.google.gson.Gson;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.TypedActionResult;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.client.gui.*;
import ru.fifth.horror.cutscene.CutsceneDefinition;
import ru.fifth.horror.network.FifthNetworking;

public final class FifthClient implements ClientModInitializer {
    private static final Gson GSON=new Gson();
    @Override public void onInitializeClient(){
        EntityRendererRegistry.register(FifthMod.DIRECTOR_NPC, DirectorNpcRenderer::new);
        EntityRendererRegistry.register(FifthMod.LIFT, LiftRenderer::new);
        EntityRendererRegistry.register(FifthMod.LIFT_BUTTON, LiftButtonRenderer::new);
        EntityRendererRegistry.register(FifthMod.MONSTER_FOR_LIFT, MonsterForLiftRenderer::new);
        EntityRendererRegistry.register(FifthMod.LIFT_PANEL,LiftPanelRenderer::new);
        BlockEntityRendererRegistry.register(FifthMod.CASSETTE_DRIVE_BE,CassetteDriveRenderer::new);
        BlockEntityRendererRegistry.register(FifthMod.TELEVISION_BE,TelevisionRenderer::new);
        EntityEffectRenderer.init();
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(AnimationCatalog.INSTANCE);
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_COMPUTER,(client,handler,buf,responseSender)->{var pos=buf.readBlockPos();String name=buf.readString(128),script=buf.readString(1_000_000);client.execute(()->client.setScreen(new ScriptComputerScreen(pos,name,script)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_PAYLOAD,(client,handler,buf,responseSender)->{String json=buf.readString(1_000_000);client.execute(()->{try{CutscenePlayback.start(GSON.fromJson(json,CutsceneDefinition.class));client.setScreen(null);}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_LIFT_PANEL,(client,handler,buf,responseSender)->{int id=buf.readVarInt(),mask=buf.readVarInt();boolean edit=buf.readBoolean();double x=buf.readDouble(),y=buf.readDouble(),z=buf.readDouble();float yaw=buf.readFloat();client.execute(()->client.setScreen(new LiftPanelScreen(client.currentScreen,id,mask,edit,x,y,z,yaw)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.VHS_PLAYBACK,(client,handler,buf,responseSender)->{String json=buf.readString(1_000_000);int mode=buf.readVarInt();var pos=buf.readBlockPos();client.execute(()->{try{VhsPlayback.start(GSON.fromJson(json,CutsceneDefinition.class),mode,pos);}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_LIBRARY_PAYLOAD,(client,handler,buf,responseSender)->{int n=buf.readVarInt();java.util.List<CutsceneLibraryScreen.Info> rows=new java.util.ArrayList<>();for(int i=0;i<n;i++)rows.add(new CutsceneLibraryScreen.Info(buf.readString(128),buf.readVarInt(),buf.readVarInt(),buf.readBoolean()));client.execute(()->{if(client.currentScreen instanceof CutsceneLibraryScreen lib)lib.update(rows);});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.CUTSCENE_EDIT_PAYLOAD,(client,handler,buf,responseSender)->{String json=buf.readString(1_000_000);client.execute(()->{try{var scene=GSON.fromJson(json,CutsceneDefinition.class);client.setScreen(new CameraEditorScreen(client.currentScreen,scene));}catch(Exception ignored){}});});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.LIFT_TRAVEL_START,(client,handler,buf,responseSender)->{int from=buf.readVarInt(),to=buf.readVarInt(),ticks=buf.readVarInt();client.execute(()->LiftTravelOverlay.start(from,to,ticks));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.LIFT_TRAVEL_END,(client,handler,buf,responseSender)->{int floor=buf.readVarInt();client.execute(()->LiftTravelOverlay.finish(floor));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.OPEN_LIFT_EDITOR,(client,handler,buf,responseSender)->{int id=buf.readVarInt(),floor=buf.readVarInt(),mask=buf.readVarInt();var stage=buf.readBlockPos();client.execute(()->client.setScreen(new LiftEditorScreen(client.currentScreen,id,floor,mask,stage)));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.ENTITY_EFFECT_SYNC,(client,handler,buf,responseSender)->{var cfg=ru.fifth.horror.effect.EntityEffectManager.read(buf);client.execute(()->EntityEffectRenderer.update(cfg));});
        ClientPlayNetworking.registerGlobalReceiver(FifthNetworking.NPC_LIBRARY_PAYLOAD,(client,handler,buf,responseSender)->{
            int count=Math.min(2048,buf.readVarInt());
            java.util.List<ScriptComputerScreen.NpcInfo> rows=new java.util.ArrayList<>(count);
            for(int i=0;i<count;i++) rows.add(new ScriptComputerScreen.NpcInfo(buf.readUuid(),buf.readString(128),buf.readString(256),buf.readBoolean(),buf.readString(512),buf.readVarInt(),buf.readString(256)));
            client.execute(()->{if(client.currentScreen instanceof ScriptComputerScreen screen)screen.updateNpcLibrary(rows);});
        });

        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.NPC_EDITOR_TOOL)&&entity instanceof ru.fifth.horror.entity.DirectorNpcEntity npc){MinecraftClient.getInstance().setScreen(new NpcEditorScreen(MinecraftClient.getInstance().currentScreen,npc));return net.minecraft.util.ActionResult.SUCCESS;}
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.MFL_EDITOR_TOOL)&&entity instanceof ru.fifth.horror.entity.MonsterForLiftEntity mfl){MinecraftClient.getInstance().setScreen(new MflEditorScreen(MinecraftClient.getInstance().currentScreen,mfl));return net.minecraft.util.ActionResult.SUCCESS;}
            if(world.isClient&&player.getStackInHand(hand).isOf(FifthMod.ANIMATION_CONDITION_TOOL)){MinecraftClient.getInstance().setScreen(new AnimationConditionScreen(MinecraftClient.getInstance().currentScreen,entity));return net.minecraft.util.ActionResult.SUCCESS;}
            return net.minecraft.util.ActionResult.PASS;
        });

        UseItemCallback.EVENT.register((player,world,hand)->{
            if(!world.isClient)return TypedActionResult.pass(player.getStackInHand(hand));
            var stack=player.getStackInHand(hand);MinecraftClient c=MinecraftClient.getInstance();
            if(stack.isOf(FifthMod.NPC_CREATOR)){c.setScreen(new NpcCreatorScreen(c.currentScreen,-1,null));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.CAMERA_TOOL)){c.setScreen(new CameraEditorScreen(c.currentScreen));return TypedActionResult.success(stack);}
            // Air-click always opens the layer editor. Coordinates may be selected before OR after opening it.
            if(stack.isOf(FifthMod.BUILD_LAYER_TOOL)){c.setScreen(new BuildLayerScreen(c.currentScreen,stack.copy()));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.ENTITY_SHADER_TOOL)){c.setScreen(new EntityShaderScreen(c.currentScreen));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.CUTSCENE_LIBRARY_TOOL)){c.setScreen(new CutsceneLibraryScreen(c.currentScreen));return TypedActionResult.success(stack);}
            if(stack.isOf(FifthMod.TV_LINK_TOOL)&&stack.getNbt()!=null&&stack.getNbt().contains("FivenTvPos")){var pos=net.minecraft.util.math.BlockPos.fromLong(stack.getNbt().getLong("FivenTvPos"));if(c.world!=null&&c.world.getBlockEntity(pos) instanceof ru.fifth.horror.block.TelevisionBlockEntity tv){c.setScreen(new TvSettingsScreen(c.currentScreen,pos,tv.getQuality(),tv.getNoise(),tv.isMonochrome()));return TypedActionResult.success(stack);}}
            return TypedActionResult.pass(stack);
        });

        // No visible developer key binding: the studio is intentionally reachable only from the secret elevator hotspot.
        HudRenderCallback.EVENT.register((drawContext,tickDelta)->{LiftTravelOverlay.render(drawContext);VhsPlayback.render(drawContext);});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            MenuMusicController.tick(client);
            LiftTravelOverlay.tick();
            VhsPlayback.tick();
            CutscenePlayback.tick();
            if(CutscenePlayback.lockInput()&&client.player!=null&&client.player.input!=null){client.player.input.movementForward=0;client.player.input.movementSideways=0;client.player.input.jumping=false;client.player.input.sneaking=false;}
        });
    }
}
