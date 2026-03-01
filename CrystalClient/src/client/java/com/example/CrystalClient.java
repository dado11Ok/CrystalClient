package com.example.crystalclient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.*;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import java.util.Map;

public class CrystalClient implements ClientModInitializer {

    // --- Toggles ---
    public static boolean speedEnabled    = false;
    public static boolean jumpEnabled     = false;
    public static boolean flyEnabled      = false;
    public static boolean playerEsp       = false;
    public static boolean mobEsp          = false;
    public static boolean chestEsp        = false;
    public static boolean itemEsp         = false;

    // --- Keybinds ---
    private static KeyBinding speedKey;
    private static KeyBinding jumpKey;
    private static KeyBinding flyKey;
    private static KeyBinding playerEspKey;
    private static KeyBinding mobEspKey;
    private static KeyBinding chestEspKey;
    private static KeyBinding itemEspKey;

    @Override
    public void onInitializeClient() {

        // Register keybinds (you can change these in Options > Controls > CrystalClient)
        speedKey     = reg("speed",     GLFW.GLFW_KEY_R);
        jumpKey      = reg("jump",      GLFW.GLFW_KEY_J);
        flyKey       = reg("fly",       GLFW.GLFW_KEY_F);
        playerEspKey = reg("playeresp", GLFW.GLFW_KEY_Z);
        mobEspKey    = reg("mobesp",    GLFW.GLFW_KEY_X);
        chestEspKey  = reg("chestesp",  GLFW.GLFW_KEY_G);
        itemEspKey   = reg("itemesp",   GLFW.GLFW_KEY_H);

        // ---- Tick: toggle keys + apply effects ----
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (speedKey.wasPressed())     speedEnabled = !speedEnabled;
            if (jumpKey.wasPressed())      jumpEnabled  = !jumpEnabled;
            if (flyKey.wasPressed()) {
                flyEnabled = !flyEnabled;
                if (client.player != null) {
                    client.player.getAbilities().allowFlying = flyEnabled;
                    if (!flyEnabled) client.player.getAbilities().flying = false;
                    client.player.sendAbilitiesUpdate();
                }
            }
            if (playerEspKey.wasPressed()) playerEsp = !playerEsp;
            if (mobEspKey.wasPressed())    mobEsp    = !mobEsp;
            if (chestEspKey.wasPressed())  chestEsp  = !chestEsp;
            if (itemEspKey.wasPressed())   itemEsp   = !itemEsp;

            if (client.player != null) {
                if (speedEnabled)
                    client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED,      20, 4, false, false));
                if (jumpEnabled)
                    client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 20, 4, false, false));
            }
        });

        // ---- ESP Rendering ----
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (context.consumers() == null) return;

            MatrixStack matrices = context.matrixStack();
            if (matrices == null) return;

            Vec3d cam = context.camera().getPos();
            VertexConsumer lines = context.consumers().getBuffer(RenderLayer.LINES);

            // Player ESP - RED
            if (playerEsp) {
                for (PlayerEntity player : client.world.getPlayers()) {
                    if (player == client.player) continue;
                    drawEntityBox(matrices, lines, player, cam, 1f, 0f, 0f);
                }
            }

            // Mob ESP - YELLOW
            if (mobEsp) {
                for (Entity entity : client.world.getEntities()) {
                    if (entity instanceof MobEntity mob)
                        drawEntityBox(matrices, lines, mob, cam, 1f, 1f, 0f);
                }
            }

            // Item ESP - CYAN
            if (itemEsp) {
                for (Entity entity : client.world.getEntities()) {
                    if (entity instanceof ItemEntity item)
                        drawEntityBox(matrices, lines, item, cam, 0f, 1f, 1f);
                }
            }

            // Chest/Shulker/Barrel ESP - GREEN
            if (chestEsp) {
                int px = (int) client.player.getX() >> 4;
                int pz = (int) client.player.getZ() >> 4;
                for (int cx = px - 8; cx <= px + 8; cx++) {
                    for (int cz = pz - 8; cz <= pz + 8; cz++) {
                        WorldChunk chunk = client.world.getChunkManager().getWorldChunk(cx, cz);
                        if (chunk == null) continue;
                        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                            BlockEntity be = entry.getValue();
                            if (be instanceof ChestBlockEntity ||
                                be instanceof ShulkerBoxBlockEntity ||
                                be instanceof BarrelBlockEntity ||
                                be instanceof EnderChestBlockEntity) {
                                drawBlockBox(matrices, lines, entry.getKey(), cam, 0f, 1f, 0f);
                            }
                        }
                    }
                }
            }
        });

        // ---- HUD Overlay ----
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int x = 5, y = 5;
            drawContext.drawText(client.textRenderer, "§b§l[CrystalClient]",              x, y,      0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fSpeed     [R]: " + onOff(speedEnabled),  x, y+14, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fJump      [J]: " + onOff(jumpEnabled),   x, y+24, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fFly       [F]: " + onOff(flyEnabled),    x, y+34, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fPlayerESP [Z]: " + onOff(playerEsp),     x, y+44, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fMobESP    [X]: " + onOff(mobEsp),        x, y+54, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fChestESP  [G]: " + onOff(chestEsp),      x, y+64, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer, "§fItemESP   [H]: " + onOff(itemEsp),       x, y+74, 0xFFFFFF, true);
        });
    }

    // ---- Helper: register keybind ----
    private KeyBinding reg(String name, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.crystalclient." + name, InputUtil.Type.KEYSYM, key, "CrystalClient"
        ));
    }

    // ---- Helper: ON/OFF text ----
    private String onOff(boolean val) {
        return val ? "§aON" : "§cOFF";
    }

    // ---- Draw box around entity ----
    private void drawEntityBox(MatrixStack matrices, VertexConsumer lines, Entity entity, Vec3d cam, float r, float g, float b) {
        Box box = entity.getBoundingBox();
        drawBox(matrices, lines,
            box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z,
            box.maxX - cam.x, box.maxY - cam.y, box.maxZ - cam.z,
            r, g, b);
    }

    // ---- Draw box around block ----
    private void drawBlockBox(MatrixStack matrices, VertexConsumer lines, BlockPos pos, Vec3d cam, float r, float g, float b) {
        drawBox(matrices, lines,
            pos.getX()     - cam.x, pos.getY()     - cam.y, pos.getZ()     - cam.z,
            pos.getX() + 1 - cam.x, pos.getY() + 1 - cam.y, pos.getZ() + 1 - cam.z,
            r, g, b);
    }

    // ---- Draw wireframe box ----
    private void drawBox(MatrixStack matrices, VertexConsumer lines,
                         double x1, double y1, double z1,
                         double x2, double y2, double z2,
                         float r, float g, float b) {
        Matrix4f m = matrices.peek().getPositionMatrix();
        float ax = (float)x1, ay = (float)y1, az = (float)z1;
        float bx = (float)x2, by = (float)y2, bz = (float)z2;
        // Bottom
        ln(lines,m, ax,ay,az, bx,ay,az, r,g,b);
        ln(lines,m, bx,ay,az, bx,ay,bz, r,g,b);
        ln(lines,m, bx,ay,bz, ax,ay,bz, r,g,b);
        ln(lines,m, ax,ay,bz, ax,ay,az, r,g,b);
        // Top
        ln(lines,m, ax,by,az, bx,by,az, r,g,b);
        ln(lines,m, bx,by,az, bx,by,bz, r,g,b);
        ln(lines,m, bx,by,bz, ax,by,bz, r,g,b);
        ln(lines,m, ax,by,bz, ax,by,az, r,g,b);
        // Sides
        ln(lines,m, ax,ay,az, ax,by,az, r,g,b);
        ln(lines,m, bx,ay,az, bx,by,az, r,g,b);
        ln(lines,m, bx,ay,bz, bx,by,bz, r,g,b);
        ln(lines,m, ax,ay,bz, ax,by,bz, r,g,b);
    }

    // ---- Draw single line ----
    private void ln(VertexConsumer v, Matrix4f m,
                    float x1, float y1, float z1,
                    float x2, float y2, float z2,
                    float r, float g, float b) {
        float dx = x2-x1, dy = y2-y1, dz = z2-z1;
        float len = (float)Math.sqrt(dx*dx+dy*dy+dz*dz);
        if (len == 0) return;
        v.vertex(m, x1, y1, z1).color(r, g, b, 1f).normal(dx/len, dy/len, dz/len);
        v.vertex(m, x2, y2, z2).color(r, g, b, 1f).normal(dx/len, dy/len, dz/len);
    }
}