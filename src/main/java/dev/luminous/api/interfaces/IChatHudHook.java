/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.text.Text
 */
package dev.luminous.api.interfaces;

import net.minecraft.text.Text;

public interface IChatHudHook {
    public void riyueClient$addMessage(Text var1, int var2);

    public void riyueClient$addMessage(Text var1);

    public void riyueClient$addMessageOutSync(Text var1, int var2);
}

