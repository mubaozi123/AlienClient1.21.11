package dev.Riyue;

import dev.Riyue.api.events.eventbus.EventBus;
import dev.Riyue.api.events.impl.InitEvent;
import dev.Riyue.core.impl.*;
import dev.Riyue.mod.modules.impl.client.ClientSetting;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;

public class RiyueClient implements ModInitializer {
    private static final int AES_KEY_SIZE = 256; // AES-256
    private static final int GCM_IV_LENGTH = 12; // GCM推荐IV长度
    private static final int GCM_TAG_LENGTH = 16; // GCM认证标签长度
    private static final int PBKDF2_ITERATIONS = 600000; // 迭代次数
    private static final int SALT_LENGTH = 16; // 盐值长度

    public static final String NAME = "RiyueClient";
    public static final String VERSION = "1.3.3";
    public static final EventBus EVENT_BUS = new EventBus();
    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static XrayManager XRAY;
    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;
    public static RotationManager ROTATION;
    public static BreakManager BREAK;
    public static PopManager POP;
    public static FriendManager FRIEND;
    public static TimerManager TIMER;
    public static ShaderManager SHADER;
    public static BlurManager BLUR;
    public static FPSManager FPS;
    public static ServerManager SERVER;
    public static ThreadManager THREAD;
    public static boolean loaded = false;
    public static long initTime;
    public static String userId;

    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }

    public static void save() {
        System.out.println("[" + RiyueClient.NAME + "] Saving");
        CONFIG.save();
        CLEANER.save();
        FRIEND.save();
        XRAY.save();
        TRADE.save();
        System.out.println("[" + RiyueClient.NAME + "] Saved");
    }

    public static final List<String> hwidCache = new ArrayList<>();

    private void register() {
        EVENT_BUS.registerLambdaFactory((lookupInMethod, klass) -> (MethodHandles.Lookup) lookupInMethod.invoke(null, klass, MethodHandles.lookup()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (loaded) {
                save();
            }
        }));
    }

    @Override
    public void onInitialize() {
        long start = System.nanoTime();
        register();
        // 轻量核心管理器同步初始化（配置/模块/命令），保证首帧前可用
        CONFIG = new ConfigManager();
        HOLE = new HoleManager();
        MODULE = new ModuleManager();
        COMMAND = new CommandManager();
        FRIEND = new FriendManager();
        XRAY = new XrayManager();
        CLEANER = new CleanerManager();
        CONFIG.load();

        // 重型/渲染相关管理器延迟到渲染窗口创建后再初始化，避免拖慢启动
        // （ShaderManager 静态块会注册并编译全部管线，BlurManager 断言渲染线程）
        deferredInitDone = false;

        initTime = System.currentTimeMillis();
        loaded = true;
        RiyueClient.EVENT_BUS.post(new InitEvent());

        File folder = new File(MinecraftClient.getInstance().runDirectory.getPath() + File.separator + RiyueClient.NAME.toLowerCase() + File.separator + "cfg");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        System.out.println("[" + NAME + "] Initialized in " + (System.nanoTime() - start) / 1_000_000 + " ms");
    }

    private static volatile boolean deferredInitDone = false;

    /**
     * 由 MixinMinecraftClient 在窗口初始化完成后（postWindowInit）调用一次。
     * 将着色器/模糊/线程等重量级初始化移出启动关键路径，显著缩短进入主菜单时间。
     */
    public static void deferredInit() {
        if (deferredInitDone) return;
        deferredInitDone = true;
        try {
            TRADE = new TradeManager();
            ROTATION = new RotationManager();
            BREAK = new BreakManager();
            PLAYER = new PlayerManager();
            POP = new PopManager();
            TIMER = new TimerManager();
            SHADER = new ShaderManager();
            BLUR = new BlurManager();
            FPS = new FPSManager();
            SERVER = new ServerManager();
            THREAD = new ThreadManager();
        } catch (Throwable t) {
            System.err.println("[" + NAME + "] Deferred init failed: " + t);
            t.printStackTrace();
        }
    }

    public static class Frame extends JFrame {
        public Frame(String hwid) {
            copyToClipboard(hwid);
            String message = "Failed verification.HWID(" + hwid + ")\n(Copied to clipboard)";
            JOptionPane.showMessageDialog(
                    this,
                    message,
                    "FAILED",
                    JOptionPane.WARNING_MESSAGE,
                    UIManager.getIcon("OptionPane.errorIcon")
            );
        }

        public static void copyToClipboard(String s) {
            StringSelection selection = new StringSelection(s);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, selection);
        }
    }
}