package dev.Riyue.mod.modules.impl.render;

import dev.Riyue.RiyueClient;
import dev.Riyue.api.utils.render.Render2DUtil;
import dev.Riyue.api.utils.render.TextUtil;
import dev.Riyue.core.impl.FontManager;
import dev.Riyue.mod.modules.Module;
import dev.Riyue.mod.modules.impl.client.TextRadar;
import dev.Riyue.mod.modules.impl.player.Freecam;
import dev.Riyue.mod.modules.settings.impl.BooleanSetting;
import dev.Riyue.mod.modules.settings.impl.ColorSetting;
import dev.Riyue.mod.modules.settings.impl.EnumSetting;
import dev.Riyue.mod.modules.settings.impl.SliderSetting;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector4d;

public class NameTags2D
        extends Module {
    public static NameTags2D INSTANCE;
    private final SliderSetting scale = add(new SliderSetting("Scale", 0.68f, 0.1f, 2f, 0.01));
    private final SliderSetting minScale = add(new SliderSetting("MinScale", 0.2f, 0.1f, 1f, 0.01));
    private final SliderSetting scaled = add(new SliderSetting("Scaled", 1, 0, 2, 0.01));
    private final SliderSetting offset = add(new SliderSetting("Offset", 0.315f, 0.001f, 1f, 0.001));
    private final SliderSetting height = add(new SliderSetting("Height", 0, -3, 3, 0.01));
    private final BooleanSetting god = add(new BooleanSetting("God", true));
    private final BooleanSetting gamemode = add(new BooleanSetting("Gamemode", false));
    private final BooleanSetting ping = add(new BooleanSetting("Ping", false));
    private final BooleanSetting health = add(new BooleanSetting("Health", true));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", true));
    private final BooleanSetting pops = add(new BooleanSetting("TotemPops", true));
    private final BooleanSetting enchants = add(new BooleanSetting("Enchants", true));
    private final ColorSetting outline = add(new ColorSetting("Outline", new Color(0x99FFFFFF, true)).injectBoolean(true));
    private final ColorSetting rect = add(new ColorSetting("Rect", new Color(0x99000001, true)).injectBoolean(true));
    private final ColorSetting friendColor = add(new ColorSetting("FriendColor", new Color(0xFF60D8FF, true)));
    private final ColorSetting color = add(new ColorSetting("Color", new Color(0xFFFFFFFF, true)));
    private final ColorSetting turtleColor = add(new ColorSetting("TurtleColor", new Color(85, 85, 255)));
    public final EnumSetting<Font> font = add(new EnumSetting<>("FontMode", Font.Fast));
    private final SliderSetting armorHeight = add(new SliderSetting("ArmorHeight", 0.3f, -10, 10f));
    private final SliderSetting armorScale = add(new SliderSetting("ArmorScale", 0.9f, 0.1f, 2f, 0.01f));
    private final EnumSetting<Armor> armorMode = add(new EnumSetting<>("ArmorMode", Armor.Full));

    public NameTags2D() {
        super("NameTags2D", Module.Category.Render);
        setChinese("2D名字标签");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        if (nullCheck()) return;
        for (PlayerEntity ent : mc.world.getPlayers()) {
            if (ent == mc.player && mc.options.getPerspective().isFirstPerson() && (Freecam.INSTANCE == null || Freecam.INSTANCE.isOff())) continue;
            double x = ent.lastRenderX + (ent.getX() - ent.lastRenderX) * tickDelta;
            double y = ent.lastRenderY + (ent.getY() - ent.lastRenderY) * tickDelta;
            double z = ent.lastRenderZ + (ent.getZ() - ent.lastRenderZ) * tickDelta;
            Vec3d preVec = new Vec3d(x, y + height.getValue() + ent.getBoundingBox().getLengthY() + 0.3, z);
            Vec3d vector = TextUtil.worldSpaceToScreenSpace(new Vec3d(preVec.x, preVec.y, preVec.z));
            if (!(vector.z > 0.0) || !(vector.z < 1.0)) continue;
            Vector4d position = new Vector4d(vector.x, vector.y, vector.z, 0.0);
            position.x = Math.min(vector.x, position.x);
            position.y = Math.min(vector.y, position.y);
            position.z = Math.max(vector.x, position.z);

            boolean hasTurtle = TextRadar.hasTurtleEffect(ent);
            int turtleSeconds = 0;
            String turtleText = "";
            float turtleTextWidth = 0;

            if (hasTurtle) {
                turtleSeconds = TextRadar.getTurtleSeconds(ent);
                turtleText = turtleSeconds + "s";
                turtleTextWidth = this.font.getValue() == Font.Fancy
                        ? FontManager.ui.getWidth(turtleText)
                        : mc.textRenderer.getWidth(turtleText);
            }

            String final_string = "";
            if (this.god.getValue() && ent.hasStatusEffect(StatusEffects.SLOWNESS)) {
                final_string = final_string + "§4GOD ";
            }
            if (this.ping.getValue()) {
                final_string = final_string + getEntityPing(ent) + "ms ";
            }
            if (this.gamemode.getValue()) {
                final_string = final_string + this.translateGamemode(getEntityGamemode(ent)) + " ";
            }
            final_string = final_string + String.valueOf(Formatting.RESET) + ent.getName().getString();
            if (this.health.getValue()) {
                final_string = final_string + " " + String.valueOf(this.getHealthColor(ent)) + round2(ent.getAbsorptionAmount() + ent.getHealth());
            }
            if (this.distance.getValue()) {
                final_string = final_string + " " + String.valueOf(Formatting.RESET) + String.format("%.1f", Float.valueOf(mc.player.distanceTo((Entity) ent))) + "m";
            }
            if (this.pops.getValue() && RiyueClient.POP.getPop(ent.getName().getString()) != 0) {
                final_string = final_string + " §bPop " + String.valueOf(Formatting.LIGHT_PURPLE) + RiyueClient.POP.getPop(ent.getName().getString());
            }

            double posX = position.x;
            double posY = position.y;
            double endPosX = position.z;
            float diff = (float) (endPosX - posX) / 2.0f;

            float baseTextWidth = this.font.getValue() == Font.Fancy
                    ? FontManager.ui.getWidth(final_string) * 1.0f
                    : (float) mc.textRenderer.getWidth(final_string);

            float totalWidth = baseTextWidth;
            if (hasTurtle) {
                totalWidth += turtleTextWidth + 2;
            }

            float tagX = (float) ((posX + (double) diff - (double) (totalWidth / 2.0f)) * 1.0);
            ArrayList<ItemStack> stacks = new ArrayList<>();
            stacks.add(ent.getMainHandStack());
            stacks.add(ent.getEquippedStack(EquipmentSlot.HEAD));
            stacks.add(ent.getEquippedStack(EquipmentSlot.CHEST));
            stacks.add(ent.getEquippedStack(EquipmentSlot.LEGS));
            stacks.add(ent.getEquippedStack(EquipmentSlot.FEET));
            stacks.add(ent.getOffHandStack());
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f, (float) (posY - 13.0f) + 6.5f);

            // 修复距离计算：1.21.11 使用 getEyePos()
            double distanceToEntity = mc.player.getEyePos().distanceTo(preVec);
            float clampedDistance = MathHelper.clamp((float) distanceToEntity, 0.0f, 100.0f);
            float size = (float) Math.max(1.0 - (double) (clampedDistance * 0.01f) * this.scaled.getValueFloat(), 0.0);

            context.getMatrices().scale(Math.max(this.scale.getValueFloat() * size, this.minScale.getValueFloat()), Math.max(this.scale.getValueFloat() * size, this.minScale.getValueFloat()));
            context.getMatrices().translate(0.0f, (float) (this.offset.getValueFloat() * clampedDistance));
            context.getMatrices().translate(-(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f), -((float) (posY - 13.0f + 6.5f)));
            float item_offset = 0.0f;
            if (this.armorMode.getValue() != Armor.None) {
                int count = 0;
                for (ItemStack armorComponent : stacks) {
                    ++count;
                    if (!armorComponent.isEmpty()) {
                        context.getMatrices().pushMatrix();
                        context.getMatrices().translate(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f, (float) (posY - 13.0f) + 6.5f);
                        context.getMatrices().scale(this.armorScale.getValueFloat(), this.armorScale.getValueFloat());
                        context.getMatrices().translate(-(tagX - 2.0f + (totalWidth + 4.0f) / 2.0f), -((float) (posY - 13.0f + 6.5f)));
                        context.getMatrices().translate((float) (posX - 52.5 + item_offset), (float) (posY - 29.0f) + this.armorHeight.getValueFloat());
                        float durability = armorComponent.getMaxDamage() - armorComponent.getDamage();
                        int percent = (int) (durability / (float) armorComponent.getMaxDamage() * 100.0f);
                        Color armorColor = percent <= 33 ? Color.RED : (percent <= 66 ? Color.ORANGE : Color.GREEN);
                        switch (this.armorMode.getValue()) {
                            case OnlyArmor -> {
                                if (count > 1 && count < 6) {
                                    context.drawItem(armorComponent, 0, 0);
                                    context.drawStackOverlay(mc.textRenderer, armorComponent, 0, 0);
                                }
                            }
                            case Item -> {
                                context.drawItem(armorComponent, 0, 0);
                                context.drawItem(armorComponent, 0, 0);
                            }
                            case Full -> {
                                context.drawItem(armorComponent, 0, 0);
                                context.drawItem(armorComponent, 0, 0);
                                if (armorComponent.getMaxDamage() > 0) {
                                    if (this.font.getValue() == Font.Fancy) {
                                        FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), 9 - FontManager.ui.getWidth(String.valueOf(percent)) / 2, -FontManager.ui.getFontHeight() + 3, armorColor.getRGB());
                                    } else {
                                        context.drawText(mc.textRenderer, String.valueOf(percent), 9 - mc.textRenderer.getWidth(String.valueOf(percent)) / 2, -mc.textRenderer.fontHeight + 1, armorColor.getRGB(), true);
                                    }
                                }
                            }
                            case Durability -> {
                                context.drawItem(armorComponent, 0, 0);
                                if (armorComponent.getMaxDamage() > 0) {
                                    if (!armorComponent.isItemBarVisible()) {
                                        int i = armorComponent.getItemBarStep();
                                        int j = armorComponent.getItemBarColor();
                                        int k = 2;
                                        int l = 13;
                                        context.fill(k, l, k + 13, l + 2, -16777216);
                                        context.fill(k, l, k + i, l + 1, j | 0xFF000000);
                                    }
                                    if (this.font.getValue() == Font.Fancy) {
                                        FontManager.ui.drawString(context.getMatrices(), String.valueOf(percent), 9 - FontManager.ui.getWidth(String.valueOf(percent)) / 2, 7, armorColor.getRGB());
                                    } else {
                                        context.drawText(mc.textRenderer, String.valueOf(percent), 9 - mc.textRenderer.getWidth(String.valueOf(percent)) / 2, 5, armorColor.getRGB(), true);
                                    }
                                }
                            }
                            default -> {
                            }
                        }
                        context.getMatrices().popMatrix();
                        if (this.enchants.getValue()) {
                            float enchantmentY = 0.0f;
                            try {
                                Registry<Enchantment> enchantmentRegistry = mc.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                                for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : armorComponent.getEnchantments().getEnchantmentEntries()) {
                                    Identifier id = enchantmentRegistry.getId(entry.getKey().value());
                                    if (id == null) continue;
                                    int levelValue = entry.getIntValue();
                                    String encName;
                                    switch (id.toString()) {
                                        case "minecraft:blast_protection" -> encName = "B" + levelValue;
                                        case "minecraft:protection" -> encName = "P" + levelValue;
                                        case "minecraft:thorns" -> encName = "T" + levelValue;
                                        case "minecraft:sharpness" -> encName = "S" + levelValue;
                                        case "minecraft:efficiency" -> encName = "E" + levelValue;
                                        case "minecraft:unbreaking" -> encName = "U" + levelValue;
                                        case "minecraft:power" -> encName = "PO" + levelValue;
                                        default -> {
                                            continue;
                                        }
                                    }
                                    if (this.font.getValue() == Font.Fancy) {
                                        FontManager.ui.drawString(context.getMatrices(), encName, (float) (posX - 50 + item_offset), (float) posY - 45 + enchantmentY, -1);
                                    } else {
                                        context.getMatrices().pushMatrix();
                                        context.getMatrices().translate((float) (posX - 50f + item_offset), (float) (posY - 45f + enchantmentY));
                                        context.drawText(mc.textRenderer, encName, 0, 0, -1, true);
                                        context.getMatrices().popMatrix();
                                    }
                                    enchantmentY -= 8;
                                }
                            } catch (Exception ignored) {
                                // ignore
                            }
                        }
                    }
                    item_offset += 18.0f;
                }
            }
            if (this.rect.booleanValue) {
                Render2DUtil.drawRect(context.getMatrices(), tagX - 2.0f, (float) (posY - 14.0f), totalWidth + 4.0f, 13.0f, this.rect.getValue());
            }
            if (this.outline.booleanValue) {
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float) (posY - 14.0f), totalWidth + 6.0f, 1.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float) (posY - 2.0f), totalWidth + 6.0f, 1.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX - 3.0f, (float) (posY - 14.0f), 1.0f, 12.0f, this.outline.getValue());
                Render2DUtil.drawRect(context.getMatrices(), tagX + totalWidth + 2.0f, (float) (posY - 14.0f), 1.0f, 12.0f, this.outline.getValue());
            }

            boolean isFriend = RiyueClient.FRIEND.isFriend(ent);
            int textColor;

            if (isFriend) {
                textColor = this.friendColor.getValue().getRGB();
            } else if (hasTurtle) {
                textColor = this.turtleColor.getValue().getRGB();
            } else {
                textColor = this.color.getValue().getRGB();
            }

            float currentX = tagX;

            if (hasTurtle) {
                if (this.font.getValue() == Font.Fancy) {
                    FontManager.ui.drawString(context.getMatrices(), turtleText, currentX, (float) posY - 10.0f, this.turtleColor.getValue().getRGB());
                } else {
                    context.getMatrices().pushMatrix();
                    context.getMatrices().translate(currentX, (float) posY - 11.0f);
                    context.drawText(mc.textRenderer, turtleText, 0, 0, this.turtleColor.getValue().getRGB(), true);
                    context.getMatrices().popMatrix();
                }
                currentX += turtleTextWidth + 2;
            }

            if (this.font.getValue() == Font.Fancy) {
                FontManager.ui.drawString(context.getMatrices(), final_string, currentX, (float) posY - 10.0f, textColor);
            } else {
                context.getMatrices().pushMatrix();
                context.getMatrices().translate(currentX, (float) posY - 11.0f);
                context.drawText(mc.textRenderer, final_string, 0, 0, textColor, true);
                context.getMatrices().popMatrix();
            }

            context.getMatrices().popMatrix();
        }
    }

    public static String getEntityPing(PlayerEntity entity) {
        if (mc.getNetworkHandler() == null) {
            return "-1";
        }
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        if (playerListEntry == null) {
            return "-1";
        }
        int ping = playerListEntry.getLatency();
        Formatting color = Formatting.GREEN;
        if (ping >= 100) {
            color = Formatting.YELLOW;
        }
        if (ping >= 250) {
            color = Formatting.RED;
        }
        return color.toString() + ping;
    }

    public static GameMode getEntityGamemode(PlayerEntity entity) {
        if (entity == null || mc.getNetworkHandler() == null) {
            return null;
        }
        PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(entity.getUuid());
        return playerListEntry == null ? null : playerListEntry.getGameMode();
    }

    private String translateGamemode(GameMode gamemode) {
        if (gamemode == null) {
            return "§7[BOT]";
        }
        return switch (gamemode) {
            case SURVIVAL -> "§b[S]";
            case CREATIVE -> "§c[C]";
            case SPECTATOR -> "§7[SP]";
            case ADVENTURE -> "§e[A]";
            default -> throw new IllegalStateException("Unexpected gamemode: " + gamemode);
        };
    }

    private Formatting getHealthColor(@NotNull PlayerEntity entity) {
        int health = (int) ((int) entity.getHealth() + entity.getAbsorptionAmount());
        if (health >= 18) {
            return Formatting.GREEN;
        }
        if (health >= 12) {
            return Formatting.YELLOW;
        }
        if (health >= 6) {
            return Formatting.RED;
        }
        return Formatting.DARK_RED;
    }

    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(1, RoundingMode.HALF_UP);
        return bd.floatValue();
    }

    public enum Font {
        Fancy,
        Fast
    }

    public enum Armor {
        None,
        Full,
        Durability,
        Item,
        OnlyArmor
    }
}
