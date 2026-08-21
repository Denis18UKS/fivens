package net.minecraft.command.argument;

import com.mojang.brigadier.context.CommandContext;

/**
 * Compatibility bridge for older Fiven command registrations.
 * Minecraft's command argument helpers are provided by Brigadier on 1.20.1.
 */
public final class StringArgumentType {
    private StringArgumentType() {}

    public static com.mojang.brigadier.arguments.StringArgumentType word() {
        return com.mojang.brigadier.arguments.StringArgumentType.word();
    }

    public static com.mojang.brigadier.arguments.StringArgumentType greedyString() {
        return com.mojang.brigadier.arguments.StringArgumentType.greedyString();
    }

    public static <S> String getString(CommandContext<S> context, String name) {
        return com.mojang.brigadier.arguments.StringArgumentType.getString(context, name);
    }
}
