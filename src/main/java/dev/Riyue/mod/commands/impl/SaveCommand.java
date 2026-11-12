/*
 * Decompiled with CFR 0.152.
 */
package dev.Riyue.mod.commands.impl;

import dev.Riyue.RiyueClient;
import dev.Riyue.core.Manager;
import dev.Riyue.core.impl.ConfigManager;
import dev.Riyue.mod.commands.Command;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.util.List;

public class SaveCommand
extends Command {
    public SaveCommand() {
        super("save", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 1) {
            this.sendChatMessage("\u00a7fSaving config named " + parameters[0]);
            // 与 Manager.getFolder() / RiyueClient.onInitialize() 保持一致：用 FabricLoader 的游戏目录，
            // 不依赖 static final MinecraftClient mc（它在窗口创建前可能为 null，且 runDirectory 在
            // 不同启动器/版本下语义不稳定）。
            File folder = new File(FabricLoader.getInstance().getGameDir().toFile(),
                    dev.Riyue.RiyueClient.NAME.toLowerCase() + File.separator + "cfg");
            if (!folder.exists()) {
                folder.mkdirs();
            }
            ConfigManager.options = Manager.getFile("cfg" + File.separator + parameters[0] + ".cfg");
            RiyueClient.save();
            ConfigManager.options = Manager.getFile("options.txt");
        } else {
            this.sendChatMessage("\u00a7fSaving..");
        }
        RiyueClient.save();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}

