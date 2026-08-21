package ru.fifth.horror;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import ru.fifth.horror.block.ScriptComputerBlock;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import ru.fifth.horror.block.TelevisionBlock;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.block.CassetteDriveBlock;
import ru.fifth.horror.block.CassetteDriveBlockEntity;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.entity.LiftButtonEntity;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.entity.LiftPanelEntity;
import ru.fifth.horror.item.BuildLayerToolItem;
import ru.fifth.horror.item.NpcPathToolItem;
import ru.fifth.horror.item.NpcSpawnEggItem;
import ru.fifth.horror.item.NpcStateToolItem;
import ru.fifth.horror.item.NpcComputerLinkToolItem;
import ru.fifth.horror.item.EntityPlacerItem;
import ru.fifth.horror.item.LiftButtonBinderItem;
import ru.fifth.horror.item.NpcEditorToolItem;
import ru.fifth.horror.item.MflEditorToolItem;
import ru.fifth.horror.item.MflPathToolItem;
import ru.fifth.horror.item.VhsCassetteItem;
import ru.fifth.horror.item.TvLinkToolItem;
import ru.fifth.horror.item.AnimationConditionToolItem;
import ru.fifth.horror.item.LiftPanelItem;
import ru.fifth.horror.item.LiftPanelToolItem;
import ru.fifth.horror.item.LiftEditorToolItem;
import ru.fifth.horror.item.EntityShaderToolItem;
import ru.fifth.horror.animation.AnimationConditionManager;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.network.FifthNetworking;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;
import software.bernie.geckolib.GeckoLib;

public final class FifthMod implements ModInitializer {
    public static final String MOD_ID = "fiven";
    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static final EntityType<DirectorNpcEntity> DIRECTOR_NPC = Registry.register(
            Registries.ENTITY_TYPE, id("director_npc"),
            FabricEntityTypeBuilder.create(SpawnGroup.CREATURE, DirectorNpcEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f)).trackRangeBlocks(96).build());

    public static final EntityType<LiftEntity> LIFT = Registry.register(
            Registries.ENTITY_TYPE, id("lift"),
            FabricEntityTypeBuilder.<LiftEntity>create(SpawnGroup.MISC, LiftEntity::new)
                    .dimensions(EntityDimensions.fixed(3.0f, 7.4f)).trackRangeBlocks(128).build());

    public static final EntityType<LiftButtonEntity> LIFT_BUTTON = Registry.register(
            Registries.ENTITY_TYPE, id("lift_button"),
            FabricEntityTypeBuilder.<LiftButtonEntity>create(SpawnGroup.MISC, LiftButtonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 0.45f)).trackRangeBlocks(96).build());


    public static final EntityType<LiftPanelEntity> LIFT_PANEL = Registry.register(Registries.ENTITY_TYPE,id("lift_panel"),FabricEntityTypeBuilder.<LiftPanelEntity>create(SpawnGroup.MISC,LiftPanelEntity::new).dimensions(EntityDimensions.fixed(1.0f,1.0f)).trackRangeBlocks(96).build());

    public static final EntityType<MonsterForLiftEntity> MONSTER_FOR_LIFT = Registry.register(
            Registries.ENTITY_TYPE, id("monster_for_lift"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, MonsterForLiftEntity::new)
                    .dimensions(EntityDimensions.fixed(0.85f, 2.55f)).trackRangeBlocks(96).build());

    public static final Item NPC_CREATOR = Registry.register(Registries.ITEM, id("npc_creator"), new Item(new Item.Settings().maxCount(1)));
    public static final Item NPC_SPAWN_EGG = Registry.register(Registries.ITEM, id("npc_spawn_egg"), new NpcSpawnEggItem(new Item.Settings().maxCount(16)));
    public static final Item NPC_PATH_TOOL = Registry.register(Registries.ITEM, id("npc_path_tool"), new NpcPathToolItem(new Item.Settings().maxCount(1)));
    public static final Item CAMERA_TOOL = Registry.register(Registries.ITEM, id("camera_tool"), new Item(new Item.Settings().maxCount(1)));
    public static final Item BUILD_LAYER_TOOL = Registry.register(Registries.ITEM, id("build_layer_tool"), new BuildLayerToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_EDITOR_TOOL = Registry.register(Registries.ITEM, id("npc_editor_tool"), new NpcEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_STATE_TOOL = Registry.register(Registries.ITEM, id("npc_state_tool"), new NpcStateToolItem(new Item.Settings().maxCount(1)));
    public static final Item NPC_LINK_TOOL = Registry.register(Registries.ITEM, id("npc_link_tool"), new NpcComputerLinkToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_ITEM = Registry.register(Registries.ITEM, id("lift"), new EntityPlacerItem(new Item.Settings().maxCount(8), () -> LIFT));
    public static final Item LIFT_BUTTON_BINDER = Registry.register(Registries.ITEM, id("lift_button_binder"), new LiftButtonBinderItem(new Item.Settings().maxCount(1)));
    public static final Item MONSTER_FOR_LIFT_ITEM = Registry.register(Registries.ITEM, id("monster_for_lift"), new EntityPlacerItem(new Item.Settings().maxCount(16), () -> MONSTER_FOR_LIFT));
    public static final Item MFL_SPAWN_EGG = Registry.register(Registries.ITEM,id("mfl_spawn_egg"),new SpawnEggItem(MONSTER_FOR_LIFT,0x121014,0x9B1022,new Item.Settings()));
    public static final Item MFL_EDITOR_TOOL = Registry.register(Registries.ITEM,id("mfl_editor_tool"),new MflEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item MFL_PATH_TOOL = Registry.register(Registries.ITEM,id("mfl_path_tool"),new MflPathToolItem(new Item.Settings().maxCount(1)));
    public static final Item VHS_CASSETTE = Registry.register(Registries.ITEM,id("vhs_cassette"),new VhsCassetteItem(new Item.Settings()));


    public static final Block TELEVISION = Registry.register(Registries.BLOCK,id("television"),new TelevisionBlock(AbstractBlock.Settings.create().strength(2.5f)));
    public static final Item TELEVISION_ITEM = Registry.register(Registries.ITEM,id("television"),new BlockItem(TELEVISION,new Item.Settings()));
    public static final Block CASSETTE_DRIVE = Registry.register(Registries.BLOCK,id("cassette_drive"),new CassetteDriveBlock(AbstractBlock.Settings.create().strength(2.5f)));
    public static final Item CASSETTE_DRIVE_ITEM = Registry.register(Registries.ITEM,id("cassette_drive"),new BlockItem(CASSETTE_DRIVE,new Item.Settings()));
    public static final BlockEntityType<TelevisionBlockEntity> TELEVISION_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("television"),FabricBlockEntityTypeBuilder.create(TelevisionBlockEntity::new,TELEVISION).build());
    public static final BlockEntityType<CassetteDriveBlockEntity> CASSETTE_DRIVE_BE = Registry.register(Registries.BLOCK_ENTITY_TYPE,id("cassette_drive"),FabricBlockEntityTypeBuilder.create(CassetteDriveBlockEntity::new,CASSETTE_DRIVE).build());
    public static final Item TV_LINK_TOOL = Registry.register(Registries.ITEM,id("tv_link_tool"),new TvLinkToolItem(new Item.Settings().maxCount(1)));
    public static final Item ANIMATION_CONDITION_TOOL = Registry.register(Registries.ITEM,id("animation_condition_tool"),new AnimationConditionToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_PANEL_ITEM = Registry.register(Registries.ITEM,id("lift_panel"),new LiftPanelItem(new Item.Settings().maxCount(16)));
    public static final Item LIFT_PANEL_TOOL = Registry.register(Registries.ITEM,id("lift_panel_tool"),new LiftPanelToolItem(new Item.Settings().maxCount(1)));
    public static final Item LIFT_EDITOR_TOOL = Registry.register(Registries.ITEM,id("lift_editor_tool"),new LiftEditorToolItem(new Item.Settings().maxCount(1)));
    public static final Item ENTITY_SHADER_TOOL = Registry.register(Registries.ITEM,id("entity_shader_tool"),new EntityShaderToolItem(new Item.Settings().maxCount(1)));
    public static final Item CUTSCENE_LIBRARY_TOOL = Registry.register(Registries.ITEM,id("cutscene_library_tool"),new Item(new Item.Settings().maxCount(1)));

    public static final Block SCRIPT_COMPUTER = Registry.register(Registries.BLOCK, id("script_computer"),
            new ScriptComputerBlock(AbstractBlock.Settings.create().strength(4.0f).requiresTool()));
    public static final Item SCRIPT_COMPUTER_ITEM = Registry.register(Registries.ITEM, id("script_computer"),
            new BlockItem(SCRIPT_COMPUTER, new Item.Settings()));
    public static final BlockEntityType<ScriptComputerBlockEntity> SCRIPT_COMPUTER_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE, id("script_computer"),
            FabricBlockEntityTypeBuilder.create(ScriptComputerBlockEntity::new, SCRIPT_COMPUTER).build());

    public static final RegistryKey<ItemGroup> FIFTH_ITEM_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("director_tools"));

    @Override
    public void onInitialize() {
        GeckoLib.initialize();
        Registry.register(Registries.ITEM_GROUP, FIFTH_ITEM_GROUP_KEY, FabricItemGroup.builder()
                .displayName(Text.translatable("itemGroup.fiven.director_tools"))
                .icon(() -> new ItemStack(NPC_CREATOR))
                .entries((context, entries) -> {
                    entries.add(NPC_CREATOR);
                    entries.add(NPC_SPAWN_EGG);
                    entries.add(NPC_PATH_TOOL);
                    entries.add(NPC_EDITOR_TOOL);
                    entries.add(CAMERA_TOOL);
                    entries.add(BUILD_LAYER_TOOL);
                    entries.add(NPC_STATE_TOOL);
                    entries.add(NPC_LINK_TOOL);
                    entries.add(SCRIPT_COMPUTER_ITEM);
                    entries.add(LIFT_BUTTON_BINDER);
                    entries.add(LIFT_ITEM);
                    entries.add(MFL_SPAWN_EGG);
                    entries.add(MFL_EDITOR_TOOL);
                    entries.add(MFL_PATH_TOOL);
                    entries.add(VHS_CASSETTE);
                    entries.add(TELEVISION_ITEM);
                    entries.add(CASSETTE_DRIVE_ITEM);
                    entries.add(TV_LINK_TOOL);
                    entries.add(ANIMATION_CONDITION_TOOL);
                    entries.add(LIFT_PANEL_ITEM);
                    entries.add(LIFT_PANEL_TOOL);
                    entries.add(LIFT_EDITOR_TOOL);
                    entries.add(ENTITY_SHADER_TOOL);
                    entries.add(CUTSCENE_LIBRARY_TOOL);
                }).build());
        DefaultAttributeContainer.Builder attrs = DirectorNpcEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
        FabricDefaultAttributeRegistry.register(DIRECTOR_NPC, attrs);
        FabricDefaultAttributeRegistry.register(MONSTER_FOR_LIFT, MonsterForLiftEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 35.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0));
        FifthNetworking.registerServer();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { FifthScriptEngine.reload(server); StructureLayerManager.restoreDefaults(server); LiftManager.load(server); AnimationConditionManager.load(server); EntityEffectManager.load(server); });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> EntityEffectManager.syncAll(handler.player)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            FifthScriptEngine.tick(server);
            CutsceneManager.tick(server);
            LiftManager.tick(server);
            AnimationConditionManager.tick(server);
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player.getStackInHand(hand).isOf(LIFT_BUTTON_BINDER) && entity instanceof LiftEntity lift)
                return LiftButtonBinderItem.selectLift(player.getStackInHand(hand), player, lift);
            if (player.getStackInHand(hand).isOf(LIFT_EDITOR_TOOL) && entity instanceof LiftEntity liftEdit) {
                if (!world.isClient) FifthNetworking.openLiftEditor((ServerPlayerEntity) player, liftEdit);
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            if (player.getStackInHand(hand).isOf(MFL_PATH_TOOL) && entity instanceof MonsterForLiftEntity mfl)
                return MflPathToolItem.select(player.getStackInHand(hand), player, mfl);
            if(entity instanceof LiftPanelEntity panel){if(player.getStackInHand(hand).isOf(LIFT_PANEL_TOOL)){if(!world.isClient){var n=player.getStackInHand(hand).getOrCreateNbt();if(n.containsUuid("FivenLiftUuid"))panel.setLiftUuid(n.getUuid("FivenLiftUuid"));FifthNetworking.openLiftPanel((ServerPlayerEntity)player,panel,true);}return net.minecraft.util.ActionResult.SUCCESS;}if(!world.isClient)FifthNetworking.openLiftPanel((ServerPlayerEntity)player,panel,false);return net.minecraft.util.ActionResult.SUCCESS;}
            if (player.getStackInHand(hand).isOf(LIFT_PANEL_TOOL) && entity instanceof LiftEntity lift2){if(!world.isClient){player.getStackInHand(hand).getOrCreateNbt().putUuid("FivenLiftUuid",lift2.getUuid());player.sendMessage(Text.literal("§7Лифт выбран для панели: §f"+lift2.getLiftId()),true);}return net.minecraft.util.ActionResult.SUCCESS;}
            return net.minecraft.util.ActionResult.PASS;
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient && world instanceof net.minecraft.server.world.ServerWorld sw && world.getBlockState(hit.getBlockPos()).isOf(net.minecraft.block.Blocks.STONE_BUTTON)) {
                if (LiftManager.callBoundButton((ServerPlayerEntity)player, hit.getBlockPos())) return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fiven")
                        .then(CommandManager.literal("tools").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            p.giveItemStack(NPC_CREATOR.getDefaultStack());
                            p.giveItemStack(NPC_PATH_TOOL.getDefaultStack());
                            p.giveItemStack(NPC_EDITOR_TOOL.getDefaultStack());
                            p.giveItemStack(CAMERA_TOOL.getDefaultStack());
                            p.giveItemStack(BUILD_LAYER_TOOL.getDefaultStack());
                            p.giveItemStack(NPC_STATE_TOOL.getDefaultStack());
                            p.giveItemStack(NPC_LINK_TOOL.getDefaultStack());
                            p.giveItemStack(SCRIPT_COMPUTER_ITEM.getDefaultStack());
                            p.giveItemStack(LIFT_BUTTON_BINDER.getDefaultStack());
                            p.giveItemStack(LIFT_ITEM.getDefaultStack());
                            p.giveItemStack(MFL_SPAWN_EGG.getDefaultStack());
                            p.giveItemStack(MFL_EDITOR_TOOL.getDefaultStack());
                            p.giveItemStack(MFL_PATH_TOOL.getDefaultStack());
                            p.giveItemStack(VHS_CASSETTE.getDefaultStack());
                            p.giveItemStack(TELEVISION_ITEM.getDefaultStack());
                            p.giveItemStack(CASSETTE_DRIVE_ITEM.getDefaultStack());
                            p.giveItemStack(TV_LINK_TOOL.getDefaultStack());
                            p.giveItemStack(ANIMATION_CONDITION_TOOL.getDefaultStack());
                            p.giveItemStack(LIFT_PANEL_ITEM.getDefaultStack());
                            p.giveItemStack(LIFT_PANEL_TOOL.getDefaultStack());
                            p.giveItemStack(LIFT_EDITOR_TOOL.getDefaultStack());
                            p.giveItemStack(ENTITY_SHADER_TOOL.getDefaultStack());
                            p.giveItemStack(CUTSCENE_LIBRARY_TOOL.getDefaultStack());
                            p.sendMessage(Text.literal("§8[§cПятый§8] §7Режиссёрские инструменты выданы."), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("restore-defaults").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                            StructureLayerManager.restoreDefaults(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("Слои по умолчанию восстановлены."), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("reload-scripts").requires(s -> s.hasPermissionLevel(2)).executes(ctx -> {
                            FifthScriptEngine.reload(ctx.getSource().getServer());
                            ctx.getSource().sendFeedback(() -> Text.literal("FifthScript перезагружен."), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("animation")
                                .then(CommandManager.literal("play").then(CommandManager.argument("target", net.minecraft.command.argument.StringArgumentType.word()).then(CommandManager.argument("animation", net.minecraft.command.argument.StringArgumentType.greedyString()).executes(ctx->{String target=net.minecraft.command.argument.StringArgumentType.getString(ctx,"target");String anim=net.minecraft.command.argument.StringArgumentType.getString(ctx,"animation");var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),target);if(e==null){ctx.getSource().sendError(Text.literal("Сущность не найдена."));return 0;}AnimationConditionManager.play(e,anim);return 1;}))))
                                .then(CommandManager.literal("stop").then(CommandManager.argument("target", net.minecraft.command.argument.StringArgumentType.word()).executes(ctx->{var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),net.minecraft.command.argument.StringArgumentType.getString(ctx,"target"));if(e==null)return 0;AnimationConditionManager.stop(e);return 1;})))
                                .then(CommandManager.literal("condition").then(CommandManager.argument("id", net.minecraft.command.argument.StringArgumentType.word()).executes(ctx->AnimationConditionManager.trigger(ctx.getSource().getServer(),net.minecraft.command.argument.StringArgumentType.getString(ctx,"id"))?1:0))))
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("fivenanim")
                        .then(CommandManager.literal("play").then(CommandManager.argument("target", net.minecraft.command.argument.StringArgumentType.word()).then(CommandManager.argument("animation", net.minecraft.command.argument.StringArgumentType.greedyString()).executes(ctx->{String target=net.minecraft.command.argument.StringArgumentType.getString(ctx,"target");String anim=net.minecraft.command.argument.StringArgumentType.getString(ctx,"animation");var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),target);if(e==null){ctx.getSource().sendError(Text.literal("Сущность не найдена."));return 0;}AnimationConditionManager.play(e,anim);return 1;}))))
                        .then(CommandManager.literal("stop").then(CommandManager.argument("target", net.minecraft.command.argument.StringArgumentType.word()).executes(ctx->{var e=AnimationConditionManager.findNamed(ctx.getSource().getServer(),net.minecraft.command.argument.StringArgumentType.getString(ctx,"target"));if(e==null)return 0;AnimationConditionManager.stop(e);return 1;})))
                        .then(CommandManager.literal("condition").then(CommandManager.argument("id", net.minecraft.command.argument.StringArgumentType.word()).executes(ctx->AnimationConditionManager.trigger(ctx.getSource().getServer(),net.minecraft.command.argument.StringArgumentType.getString(ctx,"id"))?1:0)))
        ));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("lift")
                        .then(CommandManager.argument("floor", net.minecraft.command.argument.IntArgumentType.integer(1,9)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            LiftEntity lift = LiftManager.nearestLift(p, 48);
                            if (lift == null) { ctx.getSource().sendError(Text.literal("Рядом не найден лифт Fiven.")); return 0; }
                            int floor = net.minecraft.command.argument.IntArgumentType.getInteger(ctx, "floor");
                            return LiftManager.travel(p, lift, floor) ? 1 : 0;
                        }))
        ));
    }
}
