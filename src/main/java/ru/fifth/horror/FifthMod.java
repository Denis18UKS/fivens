package ru.fifth.horror;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import ru.fifth.horror.animation.AnimationConditionManager;
import ru.fifth.horror.block.*;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.entity.*;
import ru.fifth.horror.item.*;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.network.FifthNetworking;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;
import software.bernie.geckolib.GeckoLib;

/** Main registry and server bootstrap for Fiven. */
public final class FifthMod implements ModInitializer {
    public static final String MOD_ID = "fiven";
    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    // Living/gameplay entities. Legacy lift/panel entity ids stay registered only so old worlds can load.
    public static final EntityType<DirectorNpcEntity> DIRECTOR_NPC = Registry.register(Registries.ENTITY_TYPE, id("director_npc"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, DirectorNpcEntity::new).dimensions(EntityDimensions.fixed(.6f,1.8f)).trackRangeBlocks(96).build());
    public static final EntityType<LiftEntity> LIFT = Registry.register(Registries.ENTITY_TYPE, id("lift"),
            FabricEntityTypeBuilder.<LiftEntity>create(SpawnGroup.MISC, LiftEntity::new).dimensions(EntityDimensions.fixed(3f,7.4f)).trackRangeBlocks(128).build());
    public static final EntityType<LiftButtonEntity> LIFT_BUTTON = Registry.register(Registries.ENTITY_TYPE, id("lift_button"),
            FabricEntityTypeBuilder.<LiftButtonEntity>create(SpawnGroup.MISC, LiftButtonEntity::new).dimensions(EntityDimensions.fixed(.7f,.45f)).trackRangeBlocks(96).build());
    public static final EntityType<LiftPanelEntity> LIFT_PANEL = Registry.register(Registries.ENTITY_TYPE, id("lift_panel"),
            FabricEntityTypeBuilder.<LiftPanelEntity>create(SpawnGroup.MISC, LiftPanelEntity::new).dimensions(EntityDimensions.fixed(1f,1f)).trackRangeBlocks(96).build());
    public static final EntityType<MonsterForLiftEntity> MONSTER_FOR_LIFT = Registry.register(Registries.ENTITY_TYPE, id("monster_for_lift"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MonsterForLiftEntity::new).dimensions(EntityDimensions.fixed(.85f,2.55f)).trackRangeBlocks(96).build());

    // Physical blocks.
    public static final Block LIFT_BLOCK = Registry.register(Registries.BLOCK, id("lift"), new LiftBlock(AbstractBlock.Settings.create().strength(5f).requiresTool()));
    public static final Item LIFT_ITEM = Registry.register(Registries.ITEM, id("lift"), new BlockItem(LIFT_BLOCK, new Item.Settings().maxCount(8)));
    public static final Block LIFT_PANEL_BLOCK = Registry.register(Registries.BLOCK, id("lift_panel"), new LiftPanelBlock(AbstractBlock.Settings.create().strength(2f)));
    public static final Item LIFT_PANEL_ITEM = Registry.register(Registries.ITEM, id("lift_panel"), new BlockItem(LIFT_PANEL_BLOCK, new Item.Settings().maxCount(16)));

    public static final Block TELEVISION = Registry.register(Registries.BLOCK,id("television"),new TelevisionBlock(AbstractBlock.Settings.create().strength(2.5f)));
    public static final Item TELEVISION_ITEM = Registry.register(Registries.ITEM,id("television"),new BlockItem(TELEVISION,new Item.Settings()));
    public static final Block CASSETTE_DRIVE = Registry.register(Registries.BLOCK,id("cassette_drive"),new CassetteDriveBlock(AbstractBlock.Settings.create().strength(2.5f)));
    public static final Item CASSETTE_DRIVE_ITEM = Registry.register(Registries.ITEM,id("cassette_drive"),new BlockItem(CASSETTE_DRIVE,new Item.Settings()));
    public static final Block SCRIPT_COMPUTER = Registry.register(Registries.BLOCK,id("script_computer"),new ScriptComputerBlock(AbstractBlock.Settings.create().strength(4f).requiresTool()));
    public static final Item SCRIPT_COMPUTER_ITEM = Registry.register(Registries.ITEM,id("script_computer"),new BlockItem(SCRIPT_COMPUTER,new Item.Settings()));

    // Block entities.
    public static final BlockEntityType<LiftBlockEntity> LIFT_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("lift"),
            FabricBlockEntityTypeBuilder.create(LiftBlockEntity::new,LIFT_BLOCK).build());
    public static final BlockEntityType<LiftPanelBlockEntity> LIFT_PANEL_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("lift_panel"),
            FabricBlockEntityTypeBuilder.create(LiftPanelBlockEntity::new,LIFT_PANEL_BLOCK).build());
    public static final BlockEntityType<TelevisionBlockEntity> TELEVISION_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("television"),
            FabricBlockEntityTypeBuilder.create(TelevisionBlockEntity::new,TELEVISION).build());
    public static final BlockEntityType<CassetteDriveBlockEntity> CASSETTE_DRIVE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("cassette_drive"),
            FabricBlockEntityTypeBuilder.create(CassetteDriveBlockEntity::new,CASSETTE_DRIVE).build());
    public static final BlockEntityType<ScriptComputerBlockEntity> SCRIPT_COMPUTER_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("script_computer"),
            FabricBlockEntityTypeBuilder.create(ScriptComputerBlockEntity::new,SCRIPT_COMPUTER).build());

    // Director tools/items.
    public static final Item NPC_CREATOR=reg("npc_creator",new Item(new Item.Settings().maxCount(1)));
    public static final Item NPC_SPAWN_EGG=reg("npc_spawn_egg",new NpcSpawnEggItem(new Item.Settings().maxCount(16)));
    public static final Item NPC_PATH_TOOL=reg("npc_path_tool",new NpcPathToolItem(new Item.Settings().maxCount(1)));
    public static final Item CAMERA_TOOL=reg("camera_tool",new Item(new Item.Settings().maxCount(1)));
    public static final Item BUILD_LAYER_TOOL=reg("build_layer_tool",new BuildLayerToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_EDITOR_TOOL=reg("npc_editor_tool",new NpcEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_STATE_TOOL=reg("npc_state_tool",new NpcStateToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_LINK_TOOL=reg("npc_link_tool",new NpcComputerLinkToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_BUTTON_BINDER=reg("lift_button_binder",new LiftButtonBinderItem(new Item.Settings().maxCount(1)));
    public static final Item MONSTER_FOR_LIFT_ITEM=reg("monster_for_lift",new EntityPlacerItem(new Item.Settings().maxCount(16),()->MONSTER_FOR_LIFT));
    public static final Item MFL_SPAWN_EGG=reg("mfl_spawn_egg",new SpawnEggItem(MONSTER_FOR_LIFT,0x121014,0x9B1022,new Item.Settings()));
    public static final Item MFL_EDITOR_TOOL=reg("mfl_editor_tool",new MflEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item MFL_PATH_TOOL=reg("mfl_path_tool",new MflPathToolItem(new Item.Settings().maxCount(1)));
    public static final Item VHS_CASSETTE=reg("vhs_cassette",new VhsCassetteItem(new Item.Settings()));
    public static final Item TV_LINK_TOOL=reg("tv_link_tool",new TvLinkToolItem(new Item.Settings().maxCount(1)));
    public static final Item ANIMATION_CONDITION_TOOL=reg("animation_condition_tool",new AnimationConditionToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_PANEL_TOOL=reg("lift_panel_tool",new LiftPanelToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_EDITOR_TOOL=reg("lift_editor_tool",new LiftEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item ENTITY_SHADER_TOOL=reg("entity_shader_tool",new EntityShaderToolItem(new Item.Settings().maxCount(1)));
    public static final Item CUTSCENE_LIBRARY_TOOL=reg("cutscene_library_tool",new Item(new Item.Settings().maxCount(1)));

    private static Item reg(String id,Item item){return Registry.register(Registries.ITEM,FifthMod.id(id),item);}
    public static final RegistryKey<ItemGroup> FIFTH_ITEM_GROUP_KEY=RegistryKey.of(RegistryKeys.ITEM_GROUP,id("director_tools"));

    @Override public void onInitialize(){
        GeckoLib.initialize();
        registerItemGroup(); registerAttributes(); FifthNetworking.registerServer();
        ServerLifecycleEvents.SERVER_STARTED.register(server->{FifthScriptEngine.reload(server);StructureLayerManager.restoreDefaults(server);LiftManager.load(server);AnimationConditionManager.load(server);EntityEffectManager.load(server);});
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->server.execute(()->EntityEffectManager.syncAll(handler.player)));
        ServerTickEvents.END_SERVER_TICK.register(server->{FifthScriptEngine.tick(server);CutsceneManager.tick(server);LiftManager.tick(server);AnimationConditionManager.tick(server);});
        registerInteractions(); registerCommands();
    }

    private static void registerAttributes(){
        DefaultAttributeContainer.Builder attrs=DirectorNpcEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,20).add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.25).add(EntityAttributes.GENERIC_FOLLOW_RANGE,48);
        FabricDefaultAttributeRegistry.register(DIRECTOR_NPC,attrs);
        FabricDefaultAttributeRegistry.register(MONSTER_FOR_LIFT,MonsterForLiftEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH,35).add(EntityAttributes.GENERIC_MOVEMENT_SPEED,.28).add(EntityAttributes.GENERIC_FOLLOW_RANGE,48));
    }

    private static void registerItemGroup(){
        Registry.register(Registries.ITEM_GROUP,FIFTH_ITEM_GROUP_KEY,FabricItemGroup.builder().displayName(Text.translatable("itemGroup.fiven.director_tools")).icon(()->new ItemStack(NPC_CREATOR)).entries((c,e)->{
            for(Item item:new Item[]{NPC_CREATOR,NPC_SPAWN_EGG,NPC_PATH_TOOL,NPC_EDITOR_TOOL,CAMERA_TOOL,BUILD_LAYER_TOOL,TriggerZoneFeature.TRIGGER_ZONE_TOOL,NPC_STATE_TOOL,NPC_LINK_TOOL,SCRIPT_COMPUTER_ITEM,LIFT_BUTTON_BINDER,LIFT_ITEM,LIFT_PANEL_ITEM,LIFT_PANEL_TOOL,LIFT_EDITOR_TOOL,MFL_SPAWN_EGG,MFL_EDITOR_TOOL,MFL_PATH_TOOL,VHS_CASSETTE,TELEVISION_ITEM,CASSETTE_DRIVE_ITEM,TV_LINK_TOOL,ANIMATION_CONDITION_TOOL,ENTITY_SHADER_TOOL,CUTSCENE_LIBRARY_TOOL})e.add(item);
        }).build());
    }

    private static void registerInteractions(){
        // Legacy entity lift only: lets old worlds tell the player to migrate; new lift selection happens on the block.
        UseEntityCallback.EVENT.register((player,world,hand,entity,hit)->{
            if(player.getStackInHand(hand).isOf(LIFT_BUTTON_BINDER)&&entity instanceof LiftEntity legacy)return LiftButtonBinderItem.selectLift(player.getStackInHand(hand),player,legacy);
            if(player.getStackInHand(hand).isOf(MFL_PATH_TOOL)&&entity instanceof MonsterForLiftEntity mfl)return MflPathToolItem.select(player.getStackInHand(hand),player,mfl);
            return ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player,world,hand,hit)->{
            if(!world.isClient&&player instanceof ServerPlayerEntity sp&&world.getBlockState(hit.getBlockPos()).isOf(Blocks.STONE_BUTTON)){
                if(LiftManager.callBoundButton(sp,hit.getBlockPos()))return ActionResult.SUCCESS; // Fiven-owned button: no vanilla redstone use.
            }
            return ActionResult.PASS;
        });
    }

    private static void registerCommands(){
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(CommandManager.literal("fiven")
                .then(CommandManager.literal("tools").requires(s->s.hasPermissionLevel(2)).executes(ctx->{giveDirectorTools(ctx.getSource().getPlayerOrThrow());return 1;}))
                .then(CommandManager.literal("restore-defaults").requires(s->s.hasPermissionLevel(2)).executes(ctx->{StructureLayerManager.restoreDefaults(ctx.getSource().getServer());ctx.getSource().sendFeedback(()->Text.literal("Слои по умолчанию восстановлены."),false);return 1;}))
                .then(CommandManager.literal("reload-scripts").requires(s->s.hasPermissionLevel(2)).executes(ctx->{FifthScriptEngine.reload(ctx.getSource().getServer());ctx.getSource().sendFeedback(()->Text.literal("FifthScript перезагружен."),false);return 1;}))
                .then(animationTree("animation"))));
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(animationTree("fivenanim")));
        CommandRegistrationCallback.EVENT.register((dispatcher,registryAccess,environment)->dispatcher.register(CommandManager.literal("lift").then(CommandManager.argument("floor",IntegerArgumentType.integer(1,9)).executes(ctx->{ServerPlayerEntity p=ctx.getSource().getPlayerOrThrow();LiftBlockEntity lift=LiftManager.nearestLift(p,48);if(lift==null){ctx.getSource().sendError(Text.literal("Рядом не найден блок лифта Fiven."));return 0;}return LiftManager.travel(p,lift,IntegerArgumentType.getInteger(ctx,"floor"))?1:0;}))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.minecraft.server.command.ServerCommandSource> animationTree(String literal){
        var root=CommandManager.literal(literal);
        if("animation".equals(literal)){} // used as /fiven animation
        return root
                .then(CommandManager.literal("play").then(CommandManager.argument("target",StringArgumentType.word()).then(CommandManager.argument("animation",StringArgumentType.greedyString()).executes(ctx->{var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"target"));if(e==null){ctx.getSource().sendError(Text.literal("Сущность не найдена."));return 0;}AnimationConditionManager.play(e,StringArgumentType.getString(ctx,"animation"));return 1;}))))
                .then(CommandManager.literal("stop").then(CommandManager.argument("target",StringArgumentType.word()).executes(ctx->{var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"target"));if(e==null)return 0;AnimationConditionManager.stop(e);return 1;})))
                .then(CommandManager.literal("condition").then(CommandManager.argument("id",StringArgumentType.word()).executes(ctx->AnimationConditionManager.trigger(ctx.getSource().getServer(),StringArgumentType.getString(ctx,"id"))?1:0)));
    }

    private static void giveDirectorTools(ServerPlayerEntity p){
        for(Item item:new Item[]{NPC_CREATOR,NPC_PATH_TOOL,NPC_EDITOR_TOOL,CAMERA_TOOL,BUILD_LAYER_TOOL,TriggerZoneFeature.TRIGGER_ZONE_TOOL,NPC_STATE_TOOL,NPC_LINK_TOOL,SCRIPT_COMPUTER_ITEM,LIFT_BUTTON_BINDER,LIFT_ITEM,LIFT_PANEL_ITEM,LIFT_PANEL_TOOL,LIFT_EDITOR_TOOL,MFL_SPAWN_EGG,MFL_EDITOR_TOOL,MFL_PATH_TOOL,VHS_CASSETTE,TELEVISION_ITEM,CASSETTE_DRIVE_ITEM,TV_LINK_TOOL,ANIMATION_CONDITION_TOOL,ENTITY_SHADER_TOOL,CUTSCENE_LIBRARY_TOOL,FivenExtraContent.MFL_HIDING_TOOL,FivenExtraContent.CLOCK_ARMS_ITEM})p.giveItemStack(item.getDefaultStack());
        p.sendMessage(Text.literal("§8[§cПятый§8] §7Полный набор режиссёрских инструментов выдан."),false);
    }
}
