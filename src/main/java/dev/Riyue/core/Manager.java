/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 */
package dev.Riyue.core;

import java.io.File;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

public class Manager {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static File getFile(String s) {
        File folder = Manager.getFolder();
        return new File(folder, s);
    }

    public static File getFolder() {
        // 使用 Fabric Loader 的游戏目录，而不是 mc.runDirectory。
        // mc 是 static final，若类在窗口创建前被初始化就会永久为 null，
        // 导致所有配置文件读写在启动阶段直接 NPE。
        File folder = new File(FabricLoader.getInstance().getGameDir().toFile(),
                dev.Riyue.RiyueClient.NAME.toLowerCase());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }
}

