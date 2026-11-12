/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.block.Block
 *  net.minecraft.util.math.BlockPos
 */
package dev.Riyue.api.utils.path;

import dev.Riyue.api.utils.Wrapper;
import dev.Riyue.mod.modules.impl.client.BaritoneModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class BaritoneUtil
implements Wrapper {
    public static boolean loaded;

    public static void gotoPos(BlockPos pos) {
        if (loaded) {
            BaritoneModule.gotoPos(pos);
        }
    }

    public static void forward() {
        if (loaded) {
            BaritoneModule.forward();
        }
    }

    public static void mine(Block block) {
        if (loaded) {
            BaritoneModule.mine(block);
        }
    }

    public static boolean isPathing() {
        if (loaded) {
            return BaritoneModule.isPathing();
        }
        return false;
    }

    public static void cancelEverything() {
        if (loaded) {
            BaritoneModule.cancelEverything();
        }
    }

    public static boolean isActive() {
        if (loaded) {
            return BaritoneModule.isActive();
        }
        return false;
    }

    static {
        // 旧实现用 Package.getPackages() 扫描 "baritone.api" 包来判断 Baritone 是否存在。
        // 在 Fabric 下这不可靠：Package.getPackages() 只返回当前类加载器已定义过的包，
        // Baritone 装了但入口点尚未初始化时会误判 false（相关模块永远不注册）；反过来，
        // 若别的 mod 提前加载了该包又会误判 true，调用其方法时抛 NoClassDefFoundError。
        // 直接问 FabricLoader 才是权威判定，与 Sodium 的条件化 plugin 保持一致。
        try {
            loaded = FabricLoader.getInstance().isModLoaded("baritone");
        } catch (Throwable ignored) {
            loaded = false;
        }
    }
}

