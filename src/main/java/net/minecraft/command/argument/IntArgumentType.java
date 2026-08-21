package net.minecraft.command.argument;

import com.mojang.brigadier.context.CommandContext;

/** Compatibility bridge to Brigadier's IntegerArgumentType for Minecraft 1.20.1. */
public final class IntArgumentType {
    private IntArgumentType() {}

    public static com.mojang.brigadier.arguments.IntegerArgumentType integer(int min, int max) {
        return com.mojang.brigadier.arguments.IntegerArgumentType.integer(min, max);
    }

    public static <S> int getInteger(CommandContext<S> context, String name) {
        return com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, name);
    }
}
