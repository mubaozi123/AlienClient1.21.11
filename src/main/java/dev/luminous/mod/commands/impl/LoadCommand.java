/*
 * Decompiled with CFR 0.152.
 */
package dev.luminous.mod.commands.impl;

import dev.luminous.RiyueClient;
import dev.luminous.core.Manager;
import dev.luminous.core.impl.ConfigManager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.modules.impl.client.Fonts;
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

