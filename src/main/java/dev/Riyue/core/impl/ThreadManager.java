package dev.Riyue.core.impl;

import com.google.common.collect.Lists;
import dev.Riyue.RiyueClient;
import dev.Riyue.api.events.eventbus.EventListener;
import dev.Riyue.api.events.eventbus.EventPriority;
import dev.Riyue.api.events.impl.ClientTickEvent;
import dev.Riyue.api.events.impl.UpdateEvent;
import dev.Riyue.api.utils.Wrapper;
import dev.Riyue.api.utils.render.JelloUtil;
import dev.Riyue.api.utils.world.BlockUtil;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.client.ClientSetting;
import dev.Riyue.mod.modules.impl.combat.AutoAnchor;
import dev.Riyue.mod.modules.impl.combat.AutoCrystal;
import dev.Riyue.mod.modules.impl.render.HoleESP;
import dev.Riyue.mod.modules.impl.render.PlaceRender;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import net.minecraft.util.math.BlockPos;

public class ThreadManager implements Wrapper {
    /**
     * 兼容旧代码的执行入口。内部已改为固定大小、按 CPU 核心数伸缩的线程池，
     * 不再使用 newCachedThreadPool（任务风暴时会创建大量线程，拖垮多核性能）。
     */
    public static final ExecutorService EXECUTOR = WorkPool.compute();
    public static ClientService clientService;
    public volatile Iterable<Entity> threadSafeEntityList = Collections.emptyList();
    public volatile List<AbstractClientPlayerEntity> threadSafePlayersList = Collections.emptyList();
    public volatile boolean tickRunning = false;
    /** 后台服务线程心跳，用于检测卡死并重建线程。 */
    private volatile long lastServiceBeat = System.currentTimeMillis();

    public ThreadManager() {
        init();
    }

    public void init() {
        RiyueClient.EVENT_BUS.subscribe(this);
        clientService = new ClientService();
        clientService.setName("RiyueClientService");
        clientService.setDaemon(true);
        clientService.start();

       /* try {
            String hwidTemp = System.getenv("PROCESSOR_IDENTIFIER")
                    + System.getenv("PROCESSOR_LEVEL")
                    + System.getenv("PROCESSOR_ARCHITEW6432")
                    + System.getenv("NUMBER_OF_PROCESSORS")
                    + System.getenv("COMPUTERNAME")
                    + System.getenv("PROCESSOR_REVISION")
                    + System.getProperty("user.name")
                    + System.getenv("PROCESSOR_ARCHITECTURE");

            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(hwidTemp.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte aByteData : md.digest()) {
                String hex = Integer.toHexString(0xff & aByteData);
                hexString.append(hex);
            }
            String hwid = hexString.toString();

            for (String inputLine : RiyueClient.hwidCache) {
                try {
                    String temp;
                    {
                        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        SecretKeySpec secretKey;
                        {
                            byte[] key = "FUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGA".getBytes(StandardCharsets.UTF_8);
                            MessageDigest sha = MessageDigest.getInstance("SHA-256");
                            key = sha.digest(key);
                            key = Arrays.copyOf(key, 16);
                            secretKey = new SecretKeySpec(key, "AES");
                        }
                        byte[] iv = new byte[16];
                        IvParameterSpec ivParams = new IvParameterSpec(iv);
                        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
                        temp = Base64.getEncoder().encodeToString(cipher.doFinal(hwid.getBytes(StandardCharsets.UTF_8)));
                    }
                    String temp2;
                    {
                        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                        SecretKeySpec secretKey;
                        {
                            byte[] key = "Ttwu0jVhihBQhobbLIkAeaAWKpgydqpkhMnyiDrorXDoe2N1Mt".getBytes(StandardCharsets.UTF_8);
                            MessageDigest sha = MessageDigest.getInstance("SHA-256");
                            key = sha.digest(key);
                            key = Arrays.copyOf(key, 16);
                            secretKey = new SecretKeySpec(key, "AES");
                        }
                        byte[] iv = new byte[16];
                        IvParameterSpec ivParams = new IvParameterSpec(iv);
                        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
                        byte[] original = cipher.doFinal(Base64.getDecoder().decode(inputLine));
                        temp2 = new String(original, StandardCharsets.UTF_8);
                    }
                    if (temp.equals(temp2)) {
                        try {
                            {
                                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                SecretKeySpec secretKey;
                                {
                                    byte[] key = "FUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGAFUCKYOUNIGGA".getBytes(StandardCharsets.UTF_8);
                                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                                    key = sha.digest(key);
                                    key = Arrays.copyOf(key, 16);
                                    secretKey = new SecretKeySpec(key, "AES");
                                }
                                byte[] iv = new byte[16];
                                IvParameterSpec ivParams = new IvParameterSpec(iv);
                                cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivParams);
                                temp = Base64.getEncoder().encodeToString(cipher.doFinal(hwid.getBytes(StandardCharsets.UTF_8)));
                            }
                            {
                                Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
                                SecretKeySpec secretKey;
                                {
                                    byte[] key = "Ttwu0jVhihBQhobbLIkAeaAWKpgydqpkhMnyiDrorXDoe2N1Mt".getBytes(StandardCharsets.UTF_8);
                                    MessageDigest sha = MessageDigest.getInstance("SHA-256");
                                    key = sha.digest(key);
                                    key = Arrays.copyOf(key, 16);
                                    secretKey = new SecretKeySpec(key, "AES");
                                }
                                byte[] iv = new byte[16];
                                IvParameterSpec ivParams = new IvParameterSpec(iv);
                                cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParams);
                                byte[] original = cipher.doFinal(Base64.getDecoder().decode(inputLine));
                                temp2 = new String(original, StandardCharsets.UTF_8);
                            }
                            if (!temp.equals(temp2)) {
                                try {
                                    MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                    "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                            .invoke(0, 0, 0, 0);
                                } catch (Throwable ignored) {
                                }
                                MinecraftClient.getInstance().close();
                                System.gc();
                                System.exit(-1);
                            }
                            {
                                String hwidTemp2 = System.getenv("PROCESSOR_IDENTIFIER")
                                        + System.getenv("PROCESSOR_LEVEL")
                                        + System.getenv("PROCESSOR_ARCHITEW6432")
                                        + System.getenv("NUMBER_OF_PROCESSORS")
                                        + System.getenv("COMPUTERNAME")
                                        + System.getenv("PROCESSOR_REVISION")
                                        + System.getProperty("user.name")
                                        + System.getenv("PROCESSOR_ARCHITECTURE");
                                MessageDigest md2 = MessageDigest.getInstance("MD5");
                                md2.update(hwidTemp2.getBytes());
                                StringBuilder hexString2 = new StringBuilder();
                                for (byte aByteData : md2.digest()) {
                                    String hex = Integer.toHexString(0xff & aByteData);
                                    hexString2.append(hex);
                                }
                                String hwid2 = hexString2.toString();
                                if (!hwid2.equals(hwid)) {
                                    try {

                                        MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                        "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                                .invoke(0, 0, 0, 0);
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                            RiyueClient.EVENT_BUS.subscribe(this);
                            clientService = new ClientService();
                            clientService.setName("RiyueClientService");
                            clientService.setDaemon(true);
                            clientService.start();
                            if (hwid.isEmpty()) {
                                try {
                                    MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                                                    "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                                            .invoke(0, 0, 0, 0);
                                } catch (Throwable ignored) {
                                }
                                MinecraftClient.getInstance().close();
                                System.gc();
                                System.exit(-1);
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
        try {
            MethodHandles.lookup().findStatic(Class.forName("com.sun.jna.Native"),
                            "ffi_call", MethodType.methodType(void.class, long.class, long.class, long.class, long.class))
                    .invoke(0, 0, 0, 0);
        } catch (Throwable ignored) {
        }
        MinecraftClient.getInstance().close();
        System.gc();
        System.exit(-1);*/
    }

    public Iterable<Entity> getEntities() {
        return threadSafeEntityList;
    }

    public List<AbstractClientPlayerEntity> getPlayers() {
        return threadSafePlayersList;
    }

    public void execute(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    @EventListener(priority = EventPriority.HIGHEST)
    public void onEvent(ClientTickEvent event) {
        RiyueClient.POP.onUpdate();
        RiyueClient.SERVER.onUpdate();
        if (event.isPre()) {
            JelloUtil.updateJello();
            tickRunning = true;
            // 修复：placedPos 是普通 ArrayList，主线程写入、后台线程读取存在竞态。
            // 这里先原子交换出快照再清空，避免与后台线程并发修改。
            List<BlockPos> placed = BlockUtil.takePlacedPositionsSnapshot();
            if (!placed.isEmpty()) {
                for (BlockPos pos : placed) PlaceRender.INSTANCE.create(pos);
            }
            RiyueClient.PLAYER.onUpdate();
            if (!Module.nullCheck())
                RiyueClient.EVENT_BUS.post(UpdateEvent.INSTANCE);
        } else {
            if (mc.world == null || mc.player == null) {
                tickRunning = false;
                return;
            }
            // 先在主线程完成拷贝，最后一步才释放 tickRunning 标志，
            // 修复原先"先置 false 后拷贝"导致后台线程读到半更新世界数据的竞态。
            threadSafeEntityList = Lists.newArrayList(mc.world.getEntities());
            threadSafePlayersList = Lists.newArrayList(mc.world.getPlayers());
            tickRunning = false;
        }

        // 卡死看门狗：服务线程若超过 5 秒没有心跳（例如被异常打断或自旋卡住），重建之
        if (!clientService.isAlive() || clientService.isInterrupted()
                || System.currentTimeMillis() - lastServiceBeat > 5000L) {
            ClientService old = clientService;
            clientService = new ClientService();
            clientService.setName("RiyueService");
            clientService.setDaemon(true);
            clientService.start();
            try {
                old.interrupt();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 多核后台服务：不再用单线程串行跑所有模块的 onThread()，
     * 而是把每个模块的后台计算并行提交到 WorkPool，由多个 CPU 核心同时执行；
     * 本线程只负责调度与节流。
     */
    public class ClientService extends Thread {
        @Override
        public void run() {
            while (!isInterrupted()) {
                try {
                    if (tickRunning) {
                        // 修复：原先此处是无界忙等自旋（烧满一个核心）。
                        // 改为带超时等待，tick 期间几乎不占 CPU。
                        synchronized (ThreadManager.this) {
                            if (tickRunning) {
                                ThreadManager.this.wait(8L);
                            }
                        }
                        continue;
                    }
                    lastServiceBeat = System.currentTimeMillis();
                    // 并行派发各模块的后台任务，充分利用多核
                    java.util.concurrent.CompletableFuture<?> ac =
                            java.util.concurrent.CompletableFuture.runAsync(AutoCrystal.INSTANCE::onThread, WorkPool.compute());
                    java.util.concurrent.CompletableFuture<?> he =
                            java.util.concurrent.CompletableFuture.runAsync(HoleESP.INSTANCE::onThread, WorkPool.worldScan());
                    java.util.concurrent.CompletableFuture<?> aa =
                            java.util.concurrent.CompletableFuture.runAsync(AutoAnchor.INSTANCE::onThread, WorkPool.compute());
                    java.util.concurrent.CompletableFuture.allOf(ac, he, aa).join();
                } catch (InterruptedException e) {
                    interrupt();
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue())
                        CommandManager.sendMessage("§4An error has occurred [Thread] Message: [" + e.getMessage() + "]");
                }
            }
        }
    }
}