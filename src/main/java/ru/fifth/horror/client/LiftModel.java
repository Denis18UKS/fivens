package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.block.LiftBlockEntity;
import software.bernie.geckolib.model.GeoModel;

public final class LiftModel extends GeoModel<LiftBlockEntity> {
    @Override public Identifier getModelResource(LiftBlockEntity animatable) { return FifthMod.id("geo/lift.geo.json"); }
    @Override public Identifier getTextureResource(LiftBlockEntity animatable) { return FifthMod.id("textures/entity/lift.png"); }
    @Override public Identifier getAnimationResource(LiftBlockEntity animatable) { return FifthMod.id("animations/lift.animation.json"); }
}
