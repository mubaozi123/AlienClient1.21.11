/*
 * Decompiled with CFR 0.152.
 */
package dev.Riyue.mod.commands.impl;

import dev.Riyue.RiyueClient;
import dev.Riyue.core.impl.ConfigManager;
import dev.Riyue.mod.commands.Command;
import java.util.List;

public class ReloadCommand
extends Command {
    public ReloadCommand() {
        super("reload", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        this.sendChatMessage("\u00a7fReloading..");
        RiyueClient.CONFIG = new ConfigManager();
        RiyueClient.CONFIG.load();
        RiyueClient.CLEANER.read();
        RiyueClient.XRAY.read();
        RiyueClient.TRADE.read();
        RiyueClient.FRIEND.read();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}

