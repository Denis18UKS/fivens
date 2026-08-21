package ru.fifth.horror.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;

/** MFL = monster_for_lift. Default behavior stays intact; optional editor route can override it. */
public final class MonsterForLiftEntity extends PathAwareEntity implements GeoEntity {
    private static final RawAnimation IDLE=RawAnimation.begin().thenLoop("idle"),WALKING=RawAnimation.begin().thenLoop("walking"),LOOK_LEFT=RawAnimation.begin().thenPlay("looking_left"),LOOK_RIGHT=RawAnimation.begin().thenPlay("looking_right"),LOOK_BACK=RawAnimation.begin().thenPlay("looking_backward");
    private final AnimatableInstanceCache cache=GeckoLibUtil.createInstanceCache(this); private int lookVariant;
    private final List<Vec3d> route=new ArrayList<>(); private boolean routeRunning,routeLoop=true; private double routeSpeed=.72; private int routeIndex;
    public MonsterForLiftEntity(EntityType<? extends PathAwareEntity> type,World world){super(type,world);}
    @Override protected void initGoals(){goalSelector.add(2,new WanderAroundFarGoal(this,.72,18));goalSelector.add(3,new LookAtEntityGoal(this,PlayerEntity.class,14f));goalSelector.add(4,new LookAroundGoal(this));}
    @Override public void tick(){super.tick();if(!getWorld().isClient&&routeRunning&&!route.isEmpty())tickRoute();}
    private void tickRoute(){routeIndex=Math.max(0,Math.min(routeIndex,route.size()-1));Vec3d p=route.get(routeIndex);if(squaredDistanceTo(p)<.8){routeIndex++;if(routeIndex>=route.size()){if(routeLoop)routeIndex=0;else{routeRunning=false;return;}}p=route.get(routeIndex);}getNavigation().startMovingTo(p.x,p.y,p.z,Math.max(.15,Math.min(2.5,routeSpeed)));}
    public void addRoutePoint(Vec3d p){route.add(p);} public void clearRoute(){route.clear();routeIndex=0;routeRunning=false;getNavigation().stop();}
    public List<Vec3d> getRoute(){return List.copyOf(route);} public boolean isRouteRunning(){return routeRunning;}
    public void startRoute(boolean loop,double speed){routeLoop=loop;routeSpeed=speed;routeIndex=0;routeRunning=!route.isEmpty();}
    public void stopRoute(){routeRunning=false;getNavigation().stop();}
    @Override public ActionResult interactMob(PlayerEntity player,Hand hand){if(player.isSneaking()){if(!getWorld().isClient){String trigger=switch(lookVariant++%3){case 0->"look_left";case 1->"look_right";default->"look_back";};triggerAnim("main",trigger);}return ActionResult.success(getWorld().isClient);}return super.interactMob(player,hand);}
    public void preview(String name){if(getWorld().isClient)return;switch(name){case "looking_left"->triggerAnim("main","look_left");case "looking_right"->triggerAnim("main","look_right");case "looking_backward"->triggerAnim("main","look_back");}}
    @Override public void registerControllers(AnimatableManager.ControllerRegistrar c){c.add(new AnimationController<>(this,"main",3,state->{if(state.isMoving())return state.setAndContinue(WALKING);return state.setAndContinue(IDLE);}).triggerableAnim("look_left",LOOK_LEFT).triggerableAnim("look_right",LOOK_RIGHT).triggerableAnim("look_back",LOOK_BACK));}
    @Override public AnimatableInstanceCache getAnimatableInstanceCache(){return cache;}
    @Override public void writeCustomDataToNbt(NbtCompound n){super.writeCustomDataToNbt(n);n.putInt("FivenMflLook",lookVariant);n.putBoolean("FivenMflRouteRunning",routeRunning);n.putBoolean("FivenMflRouteLoop",routeLoop);n.putDouble("FivenMflRouteSpeed",routeSpeed);NbtList l=new NbtList();for(Vec3d p:route){NbtCompound c=new NbtCompound();c.putDouble("x",p.x);c.putDouble("y",p.y);c.putDouble("z",p.z);l.add(c);}n.put("FivenMflRoute",l);}
    @Override public void readCustomDataFromNbt(NbtCompound n){super.readCustomDataFromNbt(n);lookVariant=n.getInt("FivenMflLook");routeRunning=n.getBoolean("FivenMflRouteRunning");routeLoop=!n.contains("FivenMflRouteLoop")||n.getBoolean("FivenMflRouteLoop");routeSpeed=n.contains("FivenMflRouteSpeed")?n.getDouble("FivenMflRouteSpeed"):.72;route.clear();NbtList l=n.getList("FivenMflRoute", NbtElement.COMPOUND_TYPE);for(int i=0;i<l.size();i++){NbtCompound c=l.getCompound(i);route.add(new Vec3d(c.getDouble("x"),c.getDouble("y"),c.getDouble("z")));}}
}
