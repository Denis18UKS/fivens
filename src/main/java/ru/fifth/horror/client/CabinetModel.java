package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.CabinetBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public final class CabinetModel extends GeoModel<CabinetBlockEntity> {
    @Override public Identifier getModelResource(CabinetBlockEntity animatable) { return FifthMod.id("geo/player_case.geo.json"); }
    @Override public Identifier getTextureResource(CabinetBlockEntity animatable) { return FifthMod.id("textures/block/player_case_texture.png"); }
    @Override public Identifier getAnimationResource(CabinetBlockEntity animatable) { return FifthMod.id("animations/door_pcase.animation.json"); }
}
