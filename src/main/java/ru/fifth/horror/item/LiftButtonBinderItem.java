package ru.fifth.horror.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import ru.fifth.horror.entity.LiftEntity;
import ru.fifth.horror.lift.LiftManager;

import java.util.UUID;

/** Uses the VANILLA stone button; this item only rebinds it to a lift/floor. */
public final class LiftButtonBinderItem extends Item {
    public LiftButtonBinderItem(Settings settings){super(settings);}

    @Override public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand){return ActionResult.PASS;}

    @Override public ActionResult useOnBlock(ItemUsageContext ctx){
        PlayerEntity p=ctx.getPlayer(); if(p==null)return ActionResult.PASS;
        if(!ctx.getWorld().getBlockState(ctx.getBlockPos()).isOf(Blocks.STONE_BUTTON))return ActionResult.PASS;
        if(!(ctx.getWorld() instanceof ServerWorld sw))return ActionResult.SUCCESS;
        var n=ctx.getStack().getOrCreateNbt();
        if(!n.contains("FivenLiftUuid")){p.sendMessage(Text.literal("§8[§cFiven§8] §7Сначала выбери лифт: ПКМ предметом по лифту."),true);return ActionResult.SUCCESS;}
        UUID liftUuid;
        try{liftUuid=UUID.fromString(n.getString("FivenLiftUuid"));}catch(Exception e){p.sendMessage(Text.literal("§cСохранённая привязка лифта повреждена. Выбери лифт заново."),true);return ActionResult.FAIL;}
        LiftEntity lift=LiftManager.findLift(sw.getServer(),liftUuid);
        if(lift==null){p.sendMessage(Text.literal("§cВыбранный лифт не найден."),true);return ActionResult.FAIL;}
        int floor=n.contains("FivenBindFloor")?n.getInt("FivenBindFloor"):1;
        if(!lift.canOpenOnFloor(floor)){
            p.sendMessage(Text.literal("§8[§cFiven§8] §7Этаж §c"+floor+" §7недоступен: кнопка сожжена."),true);
            return ActionResult.FAIL;
        }
        LiftManager.bindButton(sw.getServer(),sw,ctx.getBlockPos(),liftUuid,floor);
        p.sendMessage(Text.literal("§8[§cFiven§8] §7Каменная кнопка привязана к этажу §c"+floor+"§7."),true);
        return ActionResult.SUCCESS;
    }

    @Override public TypedActionResult<ItemStack> use(World world,PlayerEntity user,Hand hand){
        ItemStack stack=user.getStackInHand(hand);
        if(!world.isClient){
            var n=stack.getOrCreateNbt();
            int floor=n.getInt("FivenBindFloor");
            LiftEntity lift=null;
            if(n.contains("FivenLiftUuid")&&user.getServer()!=null){
                try{lift=LiftManager.findLift(user.getServer(),UUID.fromString(n.getString("FivenLiftUuid")));}catch(Exception ignored){}
            }
            floor=nextAllowedFloor(lift,floor);
            n.putInt("FivenBindFloor",floor);
            user.sendMessage(Text.literal("§8[§cFiven§8] §7Этаж для следующей привязки: §c"+floor),true);
        }
        return TypedActionResult.success(stack,world.isClient);
    }

    private static int nextAllowedFloor(LiftEntity lift,int current){
        int start=current<1||current>9?0:current;
        for(int i=1;i<=9;i++){
            int f=((start+i-1)%9)+1;
            if(lift==null||lift.canOpenOnFloor(f))return f;
        }
        return 1;
    }

    public static ActionResult selectLift(ItemStack stack, PlayerEntity player, LiftEntity lift){
        if(!player.getWorld().isClient){
            var n=stack.getOrCreateNbt();
            n.putString("FivenLiftUuid",lift.getUuidAsString());
            int current=n.getInt("FivenBindFloor");
            if(current<1||current>9||!lift.canOpenOnFloor(current))n.putInt("FivenBindFloor",firstAllowedFloor(lift));
            player.sendMessage(Text.literal("§8[§cFiven§8] §7Выбран лифт: §f"+lift.getLiftId()+"§7. ПКМ в воздухе меняет доступный этаж."),true);
        }
        return ActionResult.SUCCESS;
    }

    private static int firstAllowedFloor(LiftEntity lift){for(int f=1;f<=9;f++)if(lift.canOpenOnFloor(f))return f;return 1;}
}
