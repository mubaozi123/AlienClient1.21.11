package dev.Riyue.core;

import dev.Riyue.RiyueClient;
import net.minecraft.client.MinecraftClient;

import java.io.File;

/**
 * 配置/数据文件夹辅助（移植自 Loppu 的 dev.luminous.core.Manager）。
 * 提供客户端运行目录下的子文件夹与文件访问，用于套装(kit)等持久化数据。
 */
public class Manager {
    public static final MinecraftClient mc = MinecraftClient.getInstance();

    public static File getFile(String s) {
        File folder = Manager.getFolder();
        return new File(folder, s);
    }

    public static File getFolder() {
        File folder = new File(Manager.mc.runDirectory.getPath() + File.separator + RiyueClient.NAME.toLowerCase());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }
}
