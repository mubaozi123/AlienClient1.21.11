package dev.Riyue.core.impl;

import dev.Riyue.core.Manager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Hashtable;
import java.util.List;

/**
 * 套装(kit)配置管理（移植自 Loppu 的 dev.luminous.core.impl.KitConfigManager）。
 * 改用标准 JDK IO，避免引入 commons-io / guava 额外依赖。
 */
public class KitConfigManager extends Manager {
    public static final KitConfigManager INSTANCE = new KitConfigManager();
    public static File kitsFile = getFile("kits.txt");
    private final Hashtable<String, String> kitSettings = new Hashtable<>();
    private String currentKit = "";

    public KitConfigManager() {
        this.load();
    }

    public void load() {
        kitSettings.clear();

        try {
            if (kitsFile.exists()) {
                List<String> list = Files.readAllLines(kitsFile.toPath(), StandardCharsets.UTF_8);
                for (String s : list) {
                    try {
                        int idx = s.indexOf(':');
                        if (idx > 0) {
                            kitSettings.put(s.substring(0, idx), s.substring(idx + 1));
                        }
                    } catch (Exception e) {
                        System.out.println("Skipping bad kit option: " + s);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("[RiyueClient] Failed to load kit settings from file");
        }

        // Load current kit
        currentKit = getString("current_kit", "");
    }

    public void save() {
        try (PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(kitsFile), StandardCharsets.UTF_8))) {
            printwriter.println("current_kit:" + currentKit);
            for (String key : kitSettings.keySet()) {
                if (!key.equals("current_kit")) {
                    printwriter.println(key + ":" + kitSettings.get(key));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("[RiyueClient] Failed to save kit settings");
        }
    }

    public void setCurrentKit(String kitName) {
        this.currentKit = kitName;
        kitSettings.put("current_kit", kitName);
        save();
    }

    public String getCurrentKit() {
        return currentKit;
    }

    public int getInt(String setting, int defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null && isInteger(s)) ? Integer.parseInt(s) : defaultValue;
    }

    public float getFloat(String setting, float defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null && isFloat(s)) ? Float.parseFloat(s) : defaultValue;
    }

    public boolean getBoolean(String setting) {
        String s = kitSettings.get(setting);
        return Boolean.parseBoolean(s);
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null) ? Boolean.parseBoolean(s) : defaultValue;
    }

    public String getString(String setting) {
        return kitSettings.get(setting);
    }

    public String getString(String setting, String defaultValue) {
        String s = kitSettings.get(setting);
        return (s != null) ? s : defaultValue;
    }


    public boolean isInteger(String str) {
        return str.matches("^[-+]?[\\d]*$");
    }

    public boolean isFloat(String str) {
        return str.matches("^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$");
    }
}
