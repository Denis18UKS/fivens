package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.entity.LiftButtonEntity;
import software.bernie.geckolib.model.GeoModel;

public final class LiftButtonModel extends GeoModel<LiftButtonEntity> {
    @Override public Identifier getModelResource(LiftButtonEntity animatable) { return FifthMod.id("geo/lift_button.geo.json"); }
    @Override public Identifier getTextureResource(LiftButtonEntity animatable) { return FifthMod.id("textures/entity/lift_button.png"); }
    @Override public Identifier getAnimationResource(LiftButtonEntity animatable) { return FifthMod.id("animations/lift_button.animation.json"); }
}
