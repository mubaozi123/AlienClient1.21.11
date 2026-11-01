/*
 * Decompiled with CFR 0.152.
 */
package dev.luminous.api.interfaces;

import dev.luminous.api.utils.math.FadeUtils;

public interface IChatHudLineHook {
    public int riyueClient$getMessageId();

    public void riyueClient$setMessageId(int var1);

    public boolean riyueClient$getSync();

    public void riyueClient$setSync(boolean var1);

    public FadeUtils riyueClient$getFade();

    public void riyueClient$setFade(FadeUtils var1);
}

