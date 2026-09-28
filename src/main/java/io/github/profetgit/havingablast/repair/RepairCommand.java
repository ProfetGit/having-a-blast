package io.github.profetgit.havingablast.repair;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.profetgit.havingablast.Config;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * /havingablast repair creeper|tnt|bed|anchor|crystal|fireball|wither [true|false], delay [seconds], status, now. Operators only. There is no real game
 * rule: 26.x syncs game rule values to clients, and an unknown rule could break vanilla clients on a modded server.
 */
public final class RepairCommand {
    private RepairCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        if (io.github.profetgit.havingablast.dev.RepairTest.ACTIVE) {
            d.register(Commands.literal("habtest").then(Commands.literal("run").then(Commands.argument("what", com.mojang.brigadier.arguments.StringArgumentType.greedyString()).executes(c -> {
                io.github.profetgit.havingablast.dev.RepairTest.start(c.getSource().getServer(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "what"));
                return 1;
            }))));
        }
        var repair = Commands.literal("repair");
        for (Kind k : Kind.values()) repair.then(toggle(k));
        d.register(Commands.literal("havingablast").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(repair
                .then(Commands.literal("delay")
                    .executes(c -> say(c.getSource(), "Repair starts " + Config.get().repairDelaySeconds + " s after a blast"))
                    .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 86400)).executes(c -> {
                        Config.get().repairDelaySeconds = IntegerArgumentType.getInteger(c, "seconds");
                        Config.save();
                        return say(c.getSource(), "Repair now starts " + Config.get().repairDelaySeconds + " s after a blast");
                    })))
                .then(Commands.literal("status").executes(c -> {
                    int n = 0, g = 0;
                    for (ServerLevel level : c.getSource().getServer().getAllLevels()) {
                        Ledger l = Ledger.of(level);
                        n += l.pending();
                        g += l.groups.size();
                    }
                    StringBuilder on = new StringBuilder();
                    for (Kind k : Kind.values()) on.append(on.isEmpty() ? "" : ", ").append(k.label).append(' ').append(onOff(k.enabled()));
                    return say(c.getSource(), "Repair: " + on + "; delay " + Config.get().repairDelaySeconds + " s; " + n + " blocks in " + g + " blasts waiting");
                }))
                .then(Commands.literal("now").executes(c -> {
                    int n = 0;
                    for (ServerLevel level : c.getSource().getServer().getAllLevels()) n += Repair.repairNow(level);
                    return say(c.getSource(), "Repairing " + n + " blocks now");
                }))));
    }

    static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> toggle(Kind k) {
        return Commands.literal(k.id)
            .executes(c -> say(c.getSource(), k.label + " repair is " + onOff(k.enabled())))
            .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                boolean on = BoolArgumentType.getBool(c, "on");
                k.set(on);
                Config.save();
                return say(c.getSource(), k.label + " repair " + onOff(on));
            }));
    }

    static String onOff(boolean on) {
        return on ? "on" : "off";
    }

    static int say(CommandSourceStack s, String msg) {
        s.sendSuccess(() -> Component.literal(msg), true);
        return 1;
    }
}
