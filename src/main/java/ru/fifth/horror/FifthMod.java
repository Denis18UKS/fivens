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
import ru.fifth.horror.block.CassetteDriveBlock;
import ru.fifth.horror.block.CassetteDriveBlockEntity;
import ru.fifth.horror.block.ScriptComputerBlock;
import ru.fifth.horror.block.ScriptComputerBlockEntity;
import ru.fifth.horror.block.TelevisionBlock;
import ru.fifth.horror.block.TelevisionBlockEntity;
import ru.fifth.horror.cutscene.CutsceneManager;
import ru.fifth.horror.effect.EntityEffectManager;
import ru.fifth.horror.entity.DirectorNpcEntity;
import ru.fifth.horror.entity.LiftButtonEntity;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.entity.LiftPanelEntity;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import ru.fifth.horror.item.AnimationConditionToolItem;
import ru.fifth.horror.item.BuildLayerToolItem;
import ru.fifth.horror.item.EntityPlacerItem;
import ru.fifth.horror.item.EntityShaderToolItem;
import ru.fifth.horror.item.LiftButtonBinderItem;
import ru.fifth.horror.item.LiftEditorToolItem;
import ru.fifth.horror.item.LiftPanelItem;
import ru.fifth.horror.item.LiftPanelToolItem;
import ru.fifth.horror.item.MflEditorToolItem;
import ru.fifth.horror.item.MflPathToolItem;
import ru.fifth.horror.item.NpcComputerLinkToolItem;
import ru.fifth.horror.item.NpcEditorToolItem;
import ru.fifth.horror.item.NpcPathToolItem;
import ru.fifth.horror.item.NpcSpawnEggItem;
import ru.fifth.horror.item.NpcStateToolItem;
import ru.fifth.horror.item.TvLinkToolItem;
import ru.fifth.horror.item.VhsCassetteItem;
import ru.fifth.horror.lift.LiftManager;
import ru.fifth.horror.network.FifthNetworking;
import ru.fifth.horror.script.FifthScriptEngine;
import ru.fifth.horror.structure.StructureLayerManager;
import software.bernie.geckolib.GeckoLib;

public final class FifthMod implements ModInitializer {

    public static final String MOD_ID = "fiven";

    public static Identifier id(String path) {
        return new Identifier(MOD_ID, path);
    }

    // ============================================================
    // ENTITIES
    // ============================================================

    public static final EntityType<DirectorNpcEntity> DIRECTOR_NPC = Registry.register(
            Registries.ENTITY_TYPE,
            id("director_npc"),
            FabricEntityTypeBuilder.create(
                    SpawnGroup.CREATURE,
                    DirectorNpcEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(96)
                    .build());

    public static final EntityType<LiftEntity> LIFT = Registry.register(
            Registries.ENTITY_TYPE,
            id("lift"),
            FabricEntityTypeBuilder.<LiftEntity>create(
                    SpawnGroup.MISC,
                    LiftEntity::new)
                    .dimensions(EntityDimensions.fixed(3.0f, 7.4f))
                    .trackRangeBlocks(128)
                    .build());

    public static final EntityType<LiftButtonEntity> LIFT_BUTTON = Registry.register(
            Registries.ENTITY_TYPE,
            id("lift_button"),
            FabricEntityTypeBuilder.<LiftButtonEntity>create(
                    SpawnGroup.MISC,
                    LiftButtonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.7f, 0.45f))
                    .trackRangeBlocks(96)
                    .build());

    public static final EntityType<LiftPanelEntity> LIFT_PANEL = Registry.register(
            Registries.ENTITY_TYPE,
            id("lift_panel"),
            FabricEntityTypeBuilder.<LiftPanelEntity>create(
                    SpawnGroup.MISC,
                    LiftPanelEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0f, 1.0f))
                    .trackRangeBlocks(96)
                    .build());

    public static final EntityType<MonsterForLiftEntity> MONSTER_FOR_LIFT = Registry.register(
            Registries.ENTITY_TYPE,
            id("monster_for_lift"),
            FabricEntityTypeBuilder.create(
                    SpawnGroup.MONSTER,
                    MonsterForLiftEntity::new)
                    .dimensions(EntityDimensions.fixed(0.85f, 2.55f))
                    .trackRangeBlocks(96)
                    .build());

    // ============================================================
    // ITEMS
    // ============================================================

    public static final Item NPC_CREATOR = Registry.register(
            Registries.ITEM,
            id("npc_creator"),
            new Item(new Item.Settings().maxCount(1)));

    public static final Item NPC_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            id("npc_spawn_egg"),
            new NpcSpawnEggItem(
                    new Item.Settings().maxCount(16)));

    public static final Item NPC_PATH_TOOL = Registry.register(
            Registries.ITEM,
            id("npc_path_tool"),
            new NpcPathToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item CAMERA_TOOL = Registry.register(
            Registries.ITEM,
            id("camera_tool"),
            new Item(
                    new Item.Settings().maxCount(1)));

    public static final Item BUILD_LAYER_TOOL = Registry.register(
            Registries.ITEM,
            id("build_layer_tool"),
            new BuildLayerToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item NPC_EDITOR_TOOL = Registry.register(
            Registries.ITEM,
            id("npc_editor_tool"),
            new NpcEditorToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item NPC_STATE_TOOL = Registry.register(
            Registries.ITEM,
            id("npc_state_tool"),
            new NpcStateToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item NPC_LINK_TOOL = Registry.register(
            Registries.ITEM,
            id("npc_link_tool"),
            new NpcComputerLinkToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item LIFT_ITEM = Registry.register(
            Registries.ITEM,
            id("lift"),
            new EntityPlacerItem(
                    new Item.Settings().maxCount(8),
                    () -> LIFT));

    public static final Item LIFT_BUTTON_BINDER = Registry.register(
            Registries.ITEM,
            id("lift_button_binder"),
            new LiftButtonBinderItem(
                    new Item.Settings().maxCount(1)));

    public static final Item MONSTER_FOR_LIFT_ITEM = Registry.register(
            Registries.ITEM,
            id("monster_for_lift"),
            new EntityPlacerItem(
                    new Item.Settings().maxCount(16),
                    () -> MONSTER_FOR_LIFT));

    public static final Item MFL_SPAWN_EGG = Registry.register(
            Registries.ITEM,
            id("mfl_spawn_egg"),
            new SpawnEggItem(
                    MONSTER_FOR_LIFT,
                    0x121014,
                    0x9B1022,
                    new Item.Settings()));

    public static final Item MFL_EDITOR_TOOL = Registry.register(
            Registries.ITEM,
            id("mfl_editor_tool"),
            new MflEditorToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item MFL_PATH_TOOL = Registry.register(
            Registries.ITEM,
            id("mfl_path_tool"),
            new MflPathToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item VHS_CASSETTE = Registry.register(
            Registries.ITEM,
            id("vhs_cassette"),
            new VhsCassetteItem(
                    new Item.Settings()));

    // ============================================================
    // TV / VHS
    // ============================================================

    public static final Block TELEVISION = Registry.register(
            Registries.BLOCK,
            id("television"),
            new TelevisionBlock(
                    AbstractBlock.Settings.create()
                            .strength(2.5f)));

    public static final Item TELEVISION_ITEM = Registry.register(
            Registries.ITEM,
            id("television"),
            new BlockItem(
                    TELEVISION,
                    new Item.Settings()));

    public static final Block CASSETTE_DRIVE = Registry.register(
            Registries.BLOCK,
            id("cassette_drive"),
            new CassetteDriveBlock(
                    AbstractBlock.Settings.create()
                            .strength(2.5f)));

    public static final Item CASSETTE_DRIVE_ITEM = Registry.register(
            Registries.ITEM,
            id("cassette_drive"),
            new BlockItem(
                    CASSETTE_DRIVE,
                    new Item.Settings()));

    public static final BlockEntityType<TelevisionBlockEntity> TELEVISION_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id("television"),
            FabricBlockEntityTypeBuilder
                    .create(
                            TelevisionBlockEntity::new,
                            TELEVISION)
                    .build());

    public static final BlockEntityType<CassetteDriveBlockEntity> CASSETTE_DRIVE_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id("cassette_drive"),
            FabricBlockEntityTypeBuilder
                    .create(
                            CassetteDriveBlockEntity::new,
                            CASSETTE_DRIVE)
                    .build());

    public static final Item TV_LINK_TOOL = Registry.register(
            Registries.ITEM,
            id("tv_link_tool"),
            new TvLinkToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item ANIMATION_CONDITION_TOOL = Registry.register(
            Registries.ITEM,
            id("animation_condition_tool"),
            new AnimationConditionToolItem(
                    new Item.Settings().maxCount(1)));

    // ============================================================
    // LIFT TOOLS
    // ============================================================

    public static final Item LIFT_PANEL_ITEM = Registry.register(
            Registries.ITEM,
            id("lift_panel"),
            new LiftPanelItem(
                    new Item.Settings().maxCount(16)));

    public static final Item LIFT_PANEL_TOOL = Registry.register(
            Registries.ITEM,
            id("lift_panel_tool"),
            new LiftPanelToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item LIFT_EDITOR_TOOL = Registry.register(
            Registries.ITEM,
            id("lift_editor_tool"),
            new LiftEditorToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item ENTITY_SHADER_TOOL = Registry.register(
            Registries.ITEM,
            id("entity_shader_tool"),
            new EntityShaderToolItem(
                    new Item.Settings().maxCount(1)));

    public static final Item CUTSCENE_LIBRARY_TOOL = Registry.register(
            Registries.ITEM,
            id("cutscene_library_tool"),
            new Item(
                    new Item.Settings().maxCount(1)));

    // ============================================================
    // SCRIPT COMPUTER
    // ============================================================

    public static final Block SCRIPT_COMPUTER = Registry.register(
            Registries.BLOCK,
            id("script_computer"),
            new ScriptComputerBlock(
                    AbstractBlock.Settings.create()
                            .strength(4.0f)
                            .requiresTool()));

    public static final Item SCRIPT_COMPUTER_ITEM = Registry.register(
            Registries.ITEM,
            id("script_computer"),
            new BlockItem(
                    SCRIPT_COMPUTER,
                    new Item.Settings()));

    public static final BlockEntityType<ScriptComputerBlockEntity> SCRIPT_COMPUTER_BE = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            id("script_computer"),
            FabricBlockEntityTypeBuilder
                    .create(
                            ScriptComputerBlockEntity::new,
                            SCRIPT_COMPUTER)
                    .build());

    // ============================================================
    // ITEM GROUP
    // ============================================================

    public static final RegistryKey<ItemGroup> FIFTH_ITEM_GROUP_KEY = RegistryKey.of(
            RegistryKeys.ITEM_GROUP,
            id("director_tools"));

    // ============================================================
    // INITIALIZATION
    // ============================================================

    @Override
    public void onInitialize() {

        GeckoLib.initialize();

        registerItemGroup();
        registerAttributes();
        registerRuntimeSystems();
        registerInteractions();
        registerCommands();
    }

    // ============================================================
    // ITEM GROUP
    // ============================================================

    private static void registerItemGroup() {

        Registry.register(
                Registries.ITEM_GROUP,
                FIFTH_ITEM_GROUP_KEY,

                FabricItemGroup.builder()
                        .displayName(
                                Text.translatable(
                                        "itemGroup.fiven.director_tools"))
                        .icon(
                                () -> new ItemStack(NPC_CREATOR))
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
                        })
                        .build());
    }

    // ============================================================
    // ATTRIBUTES
    // ============================================================

    private static void registerAttributes() {

        DefaultAttributeContainer.Builder npcAttributes = DirectorNpcEntity.createMobAttributes()
                .add(
                        EntityAttributes.GENERIC_MAX_HEALTH,
                        20.0)
                .add(
                        EntityAttributes.GENERIC_MOVEMENT_SPEED,
                        0.25)
                .add(
                        EntityAttributes.GENERIC_FOLLOW_RANGE,
                        48.0);

        FabricDefaultAttributeRegistry.register(
                DIRECTOR_NPC,
                npcAttributes);

        FabricDefaultAttributeRegistry.register(
                MONSTER_FOR_LIFT,

                MonsterForLiftEntity.createMobAttributes()
                        .add(
                                EntityAttributes.GENERIC_MAX_HEALTH,
                                35.0)
                        .add(
                                EntityAttributes.GENERIC_MOVEMENT_SPEED,
                                0.28)
                        .add(
                                EntityAttributes.GENERIC_FOLLOW_RANGE,
                                48.0));
    }

    // ============================================================
    // SERVER SYSTEMS
    // ============================================================

    private static void registerRuntimeSystems() {

        FifthNetworking.registerServer();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {

            FifthScriptEngine.reload(server);

            StructureLayerManager.restoreDefaults(server);

            LiftManager.load(server);

            AnimationConditionManager.load(server);

            EntityEffectManager.load(server);
        });

        ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> server.execute(
                        () -> EntityEffectManager.syncAll(
                                handler.player)));

        ServerTickEvents.END_SERVER_TICK.register(server -> {

            FifthScriptEngine.tick(server);

            CutsceneManager.tick(server);

            LiftManager.tick(server);

            AnimationConditionManager.tick(server);
        });
    }

    // ============================================================
    // ENTITY / BLOCK INTERACTIONS
    // ============================================================

    private static void registerInteractions() {

        UseEntityCallback.EVENT.register(
                (player, world, hand, entity, hit) -> {

                    ItemStack heldStack = player.getStackInHand(hand);

                    // ------------------------------------------------
                    // LIFT BUTTON BINDER -> SELECT LIFT
                    // ------------------------------------------------

                    if (heldStack.isOf(LIFT_BUTTON_BINDER)
                            && entity instanceof LiftEntity lift) {

                        return LiftButtonBinderItem.selectLift(
                                heldStack,
                                player,
                                lift);
                    }

                    // ------------------------------------------------
                    // LIFT EDITOR
                    // ------------------------------------------------

                    if (heldStack.isOf(LIFT_EDITOR_TOOL)
                            && entity instanceof LiftEntity liftEdit) {

                        if (!world.isClient
                                && player instanceof ServerPlayerEntity serverPlayer) {

                            FifthNetworking.openLiftEditor(
                                    serverPlayer,
                                    liftEdit);
                        }

                        return ActionResult.SUCCESS;
                    }

                    // ------------------------------------------------
                    // MFL PATH
                    // ------------------------------------------------

                    if (heldStack.isOf(MFL_PATH_TOOL)
                            && entity instanceof MonsterForLiftEntity monster) {

                        return MflPathToolItem.select(
                                heldStack,
                                player,
                                monster);
                    }

                    // ------------------------------------------------
                    // LIFT PANEL
                    // ------------------------------------------------

                    if (entity instanceof LiftPanelEntity panel) {

                        if (heldStack.isOf(LIFT_PANEL_TOOL)) {

                            if (!world.isClient
                                    && player instanceof ServerPlayerEntity serverPlayer) {

                                var nbt = heldStack.getOrCreateNbt();

                                if (nbt.containsUuid("FivenLiftUuid")) {

                                    panel.setLiftUuid(
                                            nbt.getUuid(
                                                    "FivenLiftUuid"));
                                }

                                FifthNetworking.openLiftPanel(
                                        serverPlayer,
                                        panel,
                                        true);
                            }

                            return ActionResult.SUCCESS;
                        }

                        if (!world.isClient
                                && player instanceof ServerPlayerEntity serverPlayer) {

                            FifthNetworking.openLiftPanel(
                                    serverPlayer,
                                    panel,
                                    false);
                        }

                        return ActionResult.SUCCESS;
                    }

                    // ------------------------------------------------
                    // SELECT LIFT FOR PANEL
                    // ------------------------------------------------

                    if (heldStack.isOf(LIFT_PANEL_TOOL)
                            && entity instanceof LiftEntity lift) {

                        if (!world.isClient) {

                            heldStack.getOrCreateNbt()
                                    .putUuid(
                                            "FivenLiftUuid",
                                            lift.getUuid());

                            player.sendMessage(
                                    Text.literal(
                                            "§7Лифт выбран для панели: §f"
                                                    + lift.getLiftId()),
                                    true);
                        }

                        return ActionResult.SUCCESS;
                    }

                    return ActionResult.PASS;
                });

        UseBlockCallback.EVENT.register(
                (player, world, hand, hit) -> {

                    if (!world.isClient
                            && player instanceof ServerPlayerEntity serverPlayer
                            && world.getBlockState(
                                    hit.getBlockPos()).isOf(
                                            net.minecraft.block.Blocks.STONE_BUTTON)) {

                        /*
                         * Вызываем лифт,
                         * но обязательно возвращаем PASS ниже,
                         * чтобы Minecraft сам продолжил обработку
                         * каменной кнопки:
                         *
                         * - проиграл звук;
                         * - включил pressed=true;
                         * - запустил обычную анимацию.
                         */

                        LiftManager.callBoundButton(
                                serverPlayer,
                                hit.getBlockPos());
                    }

                    return ActionResult.PASS;
                });
    }

    // ============================================================
    // COMMANDS
    // ============================================================

    private static void registerCommands() {

        registerMainCommand();

        registerAnimationAliasCommand();

        registerLiftCommand();
    }

    // ============================================================
    // /fiven
    // ============================================================

    private static void registerMainCommand() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(

                            CommandManager.literal("fiven")

                                    // ------------------------------------------------
                                    // /fiven tools
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal("tools")
                                                    .requires(
                                                            source -> source.hasPermissionLevel(2))
                                                    .executes(ctx -> {

                                                        ServerPlayerEntity player = ctx.getSource()
                                                                .getPlayerOrThrow();

                                                        giveDirectorTools(player);

                                                        player.sendMessage(
                                                                Text.literal(
                                                                        "§8[§cПятый§8] "
                                                                                + "§7Режиссёрские инструменты выданы."),
                                                                false);

                                                        return 1;
                                                    }))

                                    // ------------------------------------------------
                                    // /fiven restore-defaults
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "restore-defaults")
                                                    .requires(
                                                            source -> source.hasPermissionLevel(2))
                                                    .executes(ctx -> {

                                                        StructureLayerManager
                                                                .restoreDefaults(
                                                                        ctx.getSource()
                                                                                .getServer());

                                                        ctx.getSource()
                                                                .sendFeedback(
                                                                        () -> Text.literal(
                                                                                "Слои по умолчанию восстановлены."),
                                                                        false);

                                                        return 1;
                                                    }))

                                    // ------------------------------------------------
                                    // /fiven reload-scripts
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "reload-scripts")
                                                    .requires(
                                                            source -> source.hasPermissionLevel(2))
                                                    .executes(ctx -> {

                                                        FifthScriptEngine.reload(
                                                                ctx.getSource()
                                                                        .getServer());

                                                        ctx.getSource()
                                                                .sendFeedback(
                                                                        () -> Text.literal(
                                                                                "FifthScript перезагружен."),
                                                                        false);

                                                        return 1;
                                                    }))

                                    // ------------------------------------------------
                                    // /fiven animation
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "animation")

                                                    // ----------------------------
                                                    // PLAY
                                                    // ----------------------------

                                                    .then(
                                                            CommandManager.literal(
                                                                    "play")
                                                                    .then(
                                                                            CommandManager.argument(
                                                                                    "target",
                                                                                    StringArgumentType.word())
                                                                                    .then(
                                                                                            CommandManager.argument(
                                                                                                    "animation",
                                                                                                    StringArgumentType
                                                                                                            .greedyString())
                                                                                                    .executes(ctx -> {

                                                                                                        String target = StringArgumentType
                                                                                                                .getString(
                                                                                                                        ctx,
                                                                                                                        "target");

                                                                                                        String animation = StringArgumentType
                                                                                                                .getString(
                                                                                                                        ctx,
                                                                                                                        "animation");

                                                                                                        var entity = AnimationConditionManager
                                                                                                                .findNamed(
                                                                                                                        ctx.getSource()
                                                                                                                                .getServer(),
                                                                                                                        target);

                                                                                                        if (entity == null) {

                                                                                                            ctx.getSource()
                                                                                                                    .sendError(
                                                                                                                            Text.literal(
                                                                                                                                    "Сущность не найдена."));

                                                                                                            return 0;
                                                                                                        }

                                                                                                        AnimationConditionManager
                                                                                                                .play(
                                                                                                                        entity,
                                                                                                                        animation);

                                                                                                        return 1;
                                                                                                    }))))

                                                    // ----------------------------
                                                    // STOP
                                                    // ----------------------------

                                                    .then(
                                                            CommandManager.literal(
                                                                    "stop")
                                                                    .then(
                                                                            CommandManager.argument(
                                                                                    "target",
                                                                                    StringArgumentType.word())
                                                                                    .executes(ctx -> {

                                                                                        String target = StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "target");

                                                                                        var entity = AnimationConditionManager
                                                                                                .findNamed(
                                                                                                        ctx.getSource()
                                                                                                                .getServer(),
                                                                                                        target);

                                                                                        if (entity == null) {
                                                                                            return 0;
                                                                                        }

                                                                                        AnimationConditionManager
                                                                                                .stop(entity);

                                                                                        return 1;
                                                                                    })))

                                                    // ----------------------------
                                                    // CONDITION
                                                    // ----------------------------

                                                    .then(
                                                            CommandManager.literal(
                                                                    "condition")
                                                                    .then(
                                                                            CommandManager.argument(
                                                                                    "id",
                                                                                    StringArgumentType.word())
                                                                                    .executes(ctx -> {

                                                                                        String id = StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "id");

                                                                                        boolean result = AnimationConditionManager
                                                                                                .trigger(
                                                                                                        ctx.getSource()
                                                                                                                .getServer(),
                                                                                                        id);

                                                                                        return result ? 1 : 0;
                                                                                    })))));
                });
    }

    // ============================================================
    // /fivenanim
    // ============================================================

    private static void registerAnimationAliasCommand() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(

                            CommandManager.literal(
                                    "fivenanim")

                                    // ------------------------------------------------
                                    // /fivenanim play <target> <animation>
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "play")
                                                    .then(
                                                            CommandManager.argument(
                                                                    "target",
                                                                    StringArgumentType.word())
                                                                    .then(
                                                                            CommandManager.argument(
                                                                                    "animation",
                                                                                    StringArgumentType.greedyString())
                                                                                    .executes(ctx -> {

                                                                                        String target = StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "target");

                                                                                        String animation = StringArgumentType
                                                                                                .getString(
                                                                                                        ctx,
                                                                                                        "animation");

                                                                                        var entity = AnimationConditionManager
                                                                                                .findNamed(
                                                                                                        ctx.getSource()
                                                                                                                .getServer(),
                                                                                                        target);

                                                                                        if (entity == null) {

                                                                                            ctx.getSource()
                                                                                                    .sendError(
                                                                                                            Text.literal(
                                                                                                                    "Сущность не найдена."));

                                                                                            return 0;
                                                                                        }

                                                                                        AnimationConditionManager
                                                                                                .play(
                                                                                                        entity,
                                                                                                        animation);

                                                                                        return 1;
                                                                                    }))))

                                    // ------------------------------------------------
                                    // /fivenanim stop <target>
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "stop")
                                                    .then(
                                                            CommandManager.argument(
                                                                    "target",
                                                                    StringArgumentType.word())
                                                                    .executes(ctx -> {

                                                                        String target = StringArgumentType.getString(
                                                                                ctx,
                                                                                "target");

                                                                        var entity = AnimationConditionManager
                                                                                .findNamed(
                                                                                        ctx.getSource()
                                                                                                .getServer(),
                                                                                        target);

                                                                        if (entity == null) {
                                                                            return 0;
                                                                        }

                                                                        AnimationConditionManager
                                                                                .stop(entity);

                                                                        return 1;
                                                                    })))

                                    // ------------------------------------------------
                                    // /fivenanim condition <id>
                                    // ------------------------------------------------

                                    .then(
                                            CommandManager.literal(
                                                    "condition")
                                                    .then(
                                                            CommandManager.argument(
                                                                    "id",
                                                                    StringArgumentType.word())
                                                                    .executes(ctx -> {

                                                                        String id = StringArgumentType.getString(
                                                                                ctx,
                                                                                "id");

                                                                        boolean result = AnimationConditionManager
                                                                                .trigger(
                                                                                        ctx.getSource()
                                                                                                .getServer(),
                                                                                        id);

                                                                        return result ? 1 : 0;
                                                                    }))));
                });
    }

    // ============================================================
    // /lift <floor>
    // ============================================================

    private static void registerLiftCommand() {

        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess, environment) -> {

                    dispatcher.register(

                            CommandManager.literal(
                                    "lift")
                                    .then(
                                            CommandManager.argument(
                                                    "floor",
                                                    IntegerArgumentType.integer(
                                                            1,
                                                            9))
                                                    .executes(ctx -> {

                                                        ServerPlayerEntity player = ctx.getSource()
                                                                .getPlayerOrThrow();

                                                        LiftEntity lift = LiftManager.nearestLift(
                                                                player,
                                                                48);

                                                        if (lift == null) {

                                                            ctx.getSource()
                                                                    .sendError(
                                                                            Text.literal(
                                                                                    "Рядом не найден лифт Fiven."));

                                                            return 0;
                                                        }

                                                        int floor = IntegerArgumentType
                                                                .getInteger(
                                                                        ctx,
                                                                        "floor");

                                                        return LiftManager.travel(
                                                                player,
                                                                lift,
                                                                floor) ? 1 : 0;
                                                    })));
                });
    }

    // ============================================================
    // GIVE TOOLS
    // ============================================================

    private static void giveDirectorTools(
            ServerPlayerEntity player) {

        player.giveItemStack(
                NPC_CREATOR.getDefaultStack());

        player.giveItemStack(
                NPC_PATH_TOOL.getDefaultStack());

        player.giveItemStack(
                NPC_EDITOR_TOOL.getDefaultStack());

        player.giveItemStack(
                CAMERA_TOOL.getDefaultStack());

        player.giveItemStack(
                BUILD_LAYER_TOOL.getDefaultStack());

        player.giveItemStack(
                NPC_STATE_TOOL.getDefaultStack());

        player.giveItemStack(
                NPC_LINK_TOOL.getDefaultStack());

        player.giveItemStack(
                SCRIPT_COMPUTER_ITEM.getDefaultStack());

        player.giveItemStack(
                LIFT_BUTTON_BINDER.getDefaultStack());

        player.giveItemStack(
                LIFT_ITEM.getDefaultStack());

        player.giveItemStack(
                MFL_SPAWN_EGG.getDefaultStack());

        player.giveItemStack(
                MFL_EDITOR_TOOL.getDefaultStack());

        player.giveItemStack(
                MFL_PATH_TOOL.getDefaultStack());

        player.giveItemStack(
                VHS_CASSETTE.getDefaultStack());

        player.giveItemStack(
                TELEVISION_ITEM.getDefaultStack());

        player.giveItemStack(
                CASSETTE_DRIVE_ITEM.getDefaultStack());

        player.giveItemStack(
                TV_LINK_TOOL.getDefaultStack());

        player.giveItemStack(
                ANIMATION_CONDITION_TOOL.getDefaultStack());

        player.giveItemStack(
                LIFT_PANEL_ITEM.getDefaultStack());

        player.giveItemStack(
                LIFT_PANEL_TOOL.getDefaultStack());

        player.giveItemStack(
                LIFT_EDITOR_TOOL.getDefaultStack());

        player.giveItemStack(
                ENTITY_SHADER_TOOL.getDefaultStack());

        player.giveItemStack(
                CUTSCENE_LIBRARY_TOOL.getDefaultStack());
    }
}