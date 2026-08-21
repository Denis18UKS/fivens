package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.entity.LiftEntity;
import software.bernie.geckolib.model.GeoModel;

public final class LiftModel extends GeoModel<LiftEntity> {
    @Override public Identifier getModelResource(LiftEntity animatable) { return FifthMod.id("geo/lift.geo.json"); }
    @Override public Identifier getTextureResource(LiftEntity animatable) { return FifthMod.id("textures/entity/lift.png"); }
    @Override public Identifier getAnimationResource(LiftEntity animatable) { return FifthMod.id("animations/lift.animation.json"); }
}
