package dev.Riyue.api.events.eventbus;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Default implementation of {@link IEventBus}.
 */
public class EventBus implements IEventBus {
    public final Map<Class<?>, List<IListener>> listenerMap = new ConcurrentHashMap<>();
    private final Map<Object, List<IListener>> listenerCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<IListener>> staticListenerCache = new ConcurrentHashMap<>();
    private final List<LambdaFactoryInfo> lambdaFactoryInfos = new ArrayList<>();

    /** 订阅者抛错要就地隔离，但不能让一个坏模块每 tick 刷一屏日志。 */
    private static final AtomicInteger FAILURE_LOG_COUNT = new AtomicInteger();
    private static final int MAX_FAILURE_LOGS = 20;

    @Override
    public void registerLambdaFactory(LambdaListener.Factory factory) {
        synchronized (lambdaFactoryInfos) {
            lambdaFactoryInfos.add(new LambdaFactoryInfo(factory));
        }
    }

    @Override
    public <T> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            for (IListener listener : listeners) {
                try {
                    listener.call(event);
                } catch (Throwable t) {
                    handleListenerFailure(event, listener, t);
                }
            }
        }

        return event;
    }

    @Override
    public <T extends ICancellable> T post(T event) {
        List<IListener> listeners = listenerMap.get(event.getClass());

        if (listeners != null) {
            event.setCancelled(false);

            for (IListener listener : listeners) {
                try {
                    listener.call(event);
                } catch (Throwable t) {
                    handleListenerFailure(event, listener, t);
                }
                if (event.isCancelled()) break;
            }
        }

        return event;
    }

    /**
     * 事件总线通常从 Minecraft 主循环（tick / 渲染 / 网络包处理）的 mixin 注入点调用，
     * 任何一个订阅者抛出 AbstractMethodError、NoClassDefFoundError 等 Error 都会穿透调用方的
     * catch(Exception) 直接崩掉主循环（历史崩溃：不死图腾粒子 AbstractMethodError）。
     * 这里只隔离非致命 Throwable；VirtualMachineError / ThreadDeath 属于 JVM 级致命错误，继续抛出。
     * 隔离后其余订阅者仍照常执行，避免一个坏模块瘫痪整个事件。
     */
    @SuppressWarnings("removal") // ThreadDeath 在 JDK 21 起标记 for removal，但仍是必须放行的致命 Error
    private static void handleListenerFailure(Object event, IListener listener, Throwable t) {
        if (t instanceof VirtualMachineError) throw (VirtualMachineError) t;
        if (t instanceof ThreadDeath) throw (ThreadDeath) t;

        if (FAILURE_LOG_COUNT.getAndIncrement() < MAX_FAILURE_LOGS) {
            System.err.println("[RiyueClient] Event listener isolated: event="
                    + event.getClass().getName() + " listener=" + listener.getClass().getName()
                    + " throwable=" + t);
            t.printStackTrace();
        }
    }

    @Override
    public void subscribe(Object object) {
        subscribe(getListeners(object.getClass(), object), false);
    }

    @Override
    public void subscribe(Class<?> klass) {
        subscribe(getListeners(klass, null), true);
    }

    @Override
    public void subscribe(IListener listener) {
        subscribe(listener, false);
    }

    private void subscribe(List<IListener> listeners, boolean onlyStatic) {
        for (IListener listener : listeners) subscribe(listener, onlyStatic);
    }

    private void subscribe(IListener listener, boolean onlyStatic) {
        if (onlyStatic) {
            if (listener.isStatic())
                insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
        } else {
            insert(listenerMap.computeIfAbsent(listener.getTarget(), aClass -> new CopyOnWriteArrayList<>()), listener);
        }
    }

    private void insert(List<IListener> listeners, IListener listener) {
        int i = 0;
        for (; i < listeners.size(); i++) {
            if (listener.getPriority() > listeners.get(i).getPriority()) break;
        }

        listeners.add(i, listener);
    }

    @Override
    public void unsubscribe(Object object) {
        unsubscribe(getListeners(object.getClass(), object), false);
    }

    @Override
    public void unsubscribe(Class<?> klass) {
        unsubscribe(getListeners(klass, null), true);
    }

    @Override
    public void unsubscribe(IListener listener) {
        unsubscribe(listener, false);
    }

    private void unsubscribe(List<IListener> listeners, boolean staticOnly) {
        for (IListener listener : listeners) unsubscribe(listener, staticOnly);
    }

    private void unsubscribe(IListener listener, boolean staticOnly) {
        List<IListener> l = listenerMap.get(listener.getTarget());

        if (l != null) {
            if (staticOnly) {
                if (listener.isStatic()) l.remove(listener);
            } else l.remove(listener);
        }
    }

    private List<IListener> getListeners(Class<?> klass, Object object) {
        Function<Object, List<IListener>> func = o -> {
            List<IListener> listeners = new CopyOnWriteArrayList<>();

            getListeners(listeners, klass, object);

            return listeners;
        };

        if (object == null) return staticListenerCache.computeIfAbsent(klass, func);

        // We need to check if the instances are the same and avoid using .equals() and .hashCode()
        for (Object key : listenerCache.keySet()) {
            if (key == object) return listenerCache.get(object);
        }

        List<IListener> listeners = func.apply(object);
        listenerCache.put(object, listeners);
        return listeners;
    }

    private void getListeners(List<IListener> listeners, Class<?> klass, Object object) {
        for (Method method : klass.getDeclaredMethods()) {
            if (isValid(method)) {
                listeners.add(new LambdaListener(getLambdaFactory(klass), klass, object, method));
            }
        }

        if (klass.getSuperclass() != null) getListeners(listeners, klass.getSuperclass(), object);
    }

    private boolean isValid(Method method) {
        if (!method.isAnnotationPresent(EventListener.class)) return false;
        if (method.getReturnType() != void.class) return false;
        if (method.getParameterCount() != 1) return false;

        return !method.getParameters()[0].getType().isPrimitive();
    }

    private LambdaListener.Factory getLambdaFactory(Class<?> klass) {
        synchronized (lambdaFactoryInfos) {
            for (LambdaFactoryInfo info : lambdaFactoryInfos) {
                return info.factory;
            }
        }

        throw new NoLambdaFactoryException(klass);
    }

    private record LambdaFactoryInfo(LambdaListener.Factory factory) {
    }
}
