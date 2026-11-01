/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.item.Items
 */
package dev.luminous.mod.commands.impl;

import dev.luminous.RiyueClient;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Items;

public class CleanerCommand
extends Command {
    public CleanerCommand() {
        super("cleaner", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = new WindowsScreen(new ItemSelectWindow(RiyueClient.CLEANER));
            return;
        }
        switch (parameters[0]) {
            case "reset": {
                RiyueClient.CLEANER.clear();
                RiyueClient.CLEANER.add(Items.NETHERITE_SWORD.getTranslationKey());
                RiyueClient.CLEANER.add(Items.NETHERITE_PICKAXE.getTranslationKey());
                RiyueClient.CLEANER.add(Items.NETHERITE_HELMET.getTranslationKey());
                RiyueClient.CLEANER.add(Items.NETHERITE_CHESTPLATE.getTranslationKey());
                RiyueClient.CLEANER.add(Items.NETHERITE_LEGGINGS.getTranslationKey());
                RiyueClient.CLEANER.add(Items.NETHERITE_BOOTS.getTranslationKey());
                RiyueClient.CLEANER.add(Items.OBSIDIAN.getTranslationKey());
                RiyueClient.CLEANER.add(Items.ENDER_CHEST.getTranslationKey());
                RiyueClient.CLEANER.add(Items.ENDER_PEARL.getTranslationKey());
                RiyueClient.CLEANER.add(Items.ENCHANTED_GOLDEN_APPLE.getTranslationKey());
                RiyueClient.CLEANER.add(Items.EXPERIENCE_BOTTLE.getTranslationKey());
                RiyueClient.CLEANER.add(Items.COBWEB.getTranslationKey());
                RiyueClient.CLEANER.add(Items.POTION.getTranslationKey());
                RiyueClient.CLEANER.add(Items.SPLASH_POTION.getTranslationKey());
                RiyueClient.CLEANER.add(Items.TOTEM_OF_UNDYING.getTranslationKey());
                RiyueClient.CLEANER.add(Items.END_CRYSTAL.getTranslationKey());
                RiyueClient.CLEANER.add(Items.ELYTRA.getTranslationKey());
                RiyueClient.CLEANER.add(Items.FLINT_AND_STEEL.getTranslationKey());
                RiyueClient.CLEANER.add(Items.PISTON.getTranslationKey());
                RiyueClient.CLEANER.add(Items.STICKY_PISTON.getTranslationKey());
                RiyueClient.CLEANER.add(Items.REDSTONE_BLOCK.getTranslationKey());
                RiyueClient.CLEANER.add(Items.GLOWSTONE.getTranslationKey());
                RiyueClient.CLEANER.add(Items.RESPAWN_ANCHOR.getTranslationKey());
                RiyueClient.CLEANER.add(Items.ANVIL.getTranslationKey());
                this.sendChatMessage("\u00a7fItems list got reset");
                return;
            }
            case "clear": {
                RiyueClient.CLEANER.getList().clear();
                this.sendChatMessage("\u00a7fItems list got clear");
                return;
            }
            case "list": {
                if (RiyueClient.CLEANER.getList().isEmpty()) {
                    this.sendChatMessage("\u00a7fItems list is empty");
                    return;
                }
                for (String name : RiyueClient.CLEANER.getList()) {
                    this.sendChatMessage("\u00a7a" + name);
                }
                return;
            }
            case "add": {
                if (parameters.length == 2) {
                    RiyueClient.CLEANER.add(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (RiyueClient.CLEANER.inList(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
            case "remove": {
                if (parameters.length == 2) {
                    RiyueClient.CLEANER.remove(parameters[1]);
                    this.sendChatMessage("\u00a7f" + parameters[1] + (RiyueClient.CLEANER.inList(parameters[1]) ? " \u00a7ahas been added" : " \u00a7chas been removed"));
                    return;
                }
                this.sendUsage();
                return;
            }
        }
        if (parameters.length == 1) {
            this.sendChatMessage("\u00a7f" + parameters[0] + (RiyueClient.CLEANER.inList(parameters[0]) ? " \u00a7ais in whitelist" : " \u00a7cisn't in whitelist"));
            return;
        }
        this.sendUsage();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            ArrayList<String> correct = new ArrayList<String>();
            List<String> list = List.of("add", "remove", "list", "reset", "clear");
            for (String x : list) {
                if (!input.equalsIgnoreCase(RiyueClient.getPrefix() + "cleaner") && !x.toLowerCase().startsWith(input)) continue;
                correct.add(x);
            }
            int numCmds = correct.size();
            String[] commands = new String[numCmds];
            int i = 0;
            for (String x : correct) {
                commands[i++] = x;
            }
            return commands;
        }
        return null;
    }
}

