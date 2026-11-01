/*
 * Decompiled with CFR 0.152.
 */
package dev.Riyue.mod.commands.impl;

import dev.Riyue.RiyueClient;
import dev.Riyue.core.Manager;
import dev.Riyue.core.impl.ConfigManager;
import dev.Riyue.mod.commands.Command;
import dev.Riyue.mod.modules.impl.client.Fonts;
import java.io.File;
import java.util.List;

public class LoadCommand
extends Command {
    public LoadCommand() {
        super("load", "[config]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            this.sendUsage();
            return;
        }
        this.sendChatMessage("\u00a7fLoading..");
        ConfigManager.options = Manager.getFile("cfg" + File.separator + parameters[0] + ".cfg");
        RiyueClient.CONFIG = new ConfigManager();
        RiyueClient.CONFIG.load();
        ConfigManager.options = Manager.getFile("options.txt");
        RiyueClient.save();
        Fonts.INSTANCE.refresh();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}

