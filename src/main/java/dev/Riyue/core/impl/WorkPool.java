package dev.Riyue.core.impl;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多核并行计算池：用于把重量级的模块后台计算（伤害扫描、坑位扫描等）
 * 分摊到多个工作线程上并行执行，充分利用多核 CPU。
 * <p>
 * 与旧的 {@code newCachedThreadPool()} 不同，这里使用固定大小的线程池，
 * 避免任务风暴时创建大量线程导致上下文切换开销和 GC 压力。
 */
public final class WorkPool {
    /** 可用核心数（至少 2，避免单核机器上过度拆分）。 */
    public static final int CORES = Math.max(2, Runtime.getRuntime().availableProcessors());

    /** 通用后台计算池（模块 onThread 任务等）。 */
    private static final ThreadPoolExecutor COMPUTE = create("RiyueCompute-", CORES);

    /** 专用世界扫描池（HoleESP / AutoCrystal 位置扫描），与通用任务隔离，防止互相饿死。 */
    private static final ThreadPoolExecutor WORLD_SCAN = create("RiyueWorldScan-", Math.max(2, CORES / 2));

    private WorkPool() {
    }

    private static ThreadPoolExecutor create(String namePrefix, int size) {
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, namePrefix + counter.getAndIncrement());
            t.setDaemon(true);
            // 后台计算线程优先级调低，避免抢占客户端主线程/渲染线程时间片
            t.setPriority(Thread.MIN_PRIORITY + 1);
            return t;
        };
        ThreadPoolExecutor pool = new ThreadPoolExecutor(size, size,
                30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(256), factory,
                new ThreadPoolExecutor.DiscardOldestPolicy());
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    private static final AtomicInteger counter = new AtomicInteger();

    public static ThreadPoolExecutor compute() {
        return COMPUTE;
    }

    public static ThreadPoolExecutor worldScan() {
        return WORLD_SCAN;
    }

    /** 提交一个后台任务（通用池）。 */
    public static void submit(Runnable task) {
        COMPUTE.execute(task);
    }
}
