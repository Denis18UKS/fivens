package ru.fifth.horror.client;

import net.minecraft.util.Identifier;
import ru.fifth.horror.FifthMod;
import ru.fifth.horror.entity.MonsterForLiftEntity;
import software.bernie.geckolib.model.GeoModel;

public final class MonsterForLiftModel extends GeoModel<MonsterForLiftEntity> {
    @Override public Identifier getModelResource(MonsterForLiftEntity animatable) { return FifthMod.id("geo/monster_for_lift.geo.json"); }
    @Override public Identifier getTextureResource(MonsterForLiftEntity animatable) { return FifthMod.id("textures/entity/monster_for_lift.png"); }
    @Override public Identifier getAnimationResource(MonsterForLiftEntity animatable) { return FifthMod.id("animations/monster_for_lift.animation.json"); }
}
