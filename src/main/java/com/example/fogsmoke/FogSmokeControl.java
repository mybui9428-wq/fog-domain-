package com.example.fogsmoke;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Iterator;

/** Khả năng điều khiển khói và sương mù dành riêng cho tubi2531. */
@EventBusSubscriber(modid = ExampleMod.MODID)
public final class FogSmokeControl {
    private static final String CONTROLLER = "tubi2531";
    private static final int DEFAULT_DURATION = 20 * 30;
    private static final Map<UUID, SmokeCloud> CLOUDS = new HashMap<>();
    
    private static final class SmokeCloud {
        ServerLevel level;
        Vec3 center;
        int radius;
        int ticks;

        SmokeCloud(ServerLevel level, Vec3 center, int radius, int ticks) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.ticks = ticks;
        }
    }

    private FogSmokeControl() {}

    private static boolean isController(ServerPlayer player) {
        return player.getName().getString().equalsIgnoreCase(CONTROLLER);
    }

    private static boolean allowed(ServerPlayer player) {
        if (isController(player)) return true;
        player.sendSystemMessage(Component.literal("Năng lực Khói Sương chỉ thuộc về tubi2531."));
        return false;
    }

    private static Vec3 aimPoint(ServerPlayer player, double distance) {
        return player.getEyePosition().add(player.getLookAngle().normalize().scale(distance));
    }

    private static void startCloud(ServerPlayer player, int radius) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 center = aimPoint(player, 7.0D);
        CLOUDS.put(player.getUUID(), new SmokeCloud(level, center, radius, DEFAULT_DURATION));
        player.sendSystemMessage(Component.literal("Đã tạo màn sương mù bán kính " + radius + " block trong 30 giây. Dùng /fogsmoke move để điều khiển."));
    }

    private static void pullEnemies(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        AABB area = player.getBoundingBox().inflate(12.0D);
        int affected = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && entity.isAlive() && !(entity instanceof ServerPlayer p && isController(p)))) {
            Vec3 toward = player.position().subtract(target.position()).normalize();
            target.setDeltaMovement(toward.scale(1.15D).add(0.0D, 0.18D, 0.0D));
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                    18, 0.45D, 0.55D, 0.45D, 0.025D);
            affected++;
        }
        player.sendSystemMessage(Component.literal("Khói đã kéo " + affected + " sinh vật về phía bạn."));
    }

    private static void bindEnemies(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        LivingEntity target = null;
        double nearest = 18.0D;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(18.0D),
                entity -> entity != player && entity.isAlive() && !(entity instanceof ServerPlayer p && isController(p)))) {
            Vec3 delta = candidate.getBoundingBox().getCenter().subtract(eye);
            double projection = delta.dot(look);
            if (projection < 0.0D || projection > 18.0D) continue;
            Vec3 closest = eye.add(look.scale(projection));
            if (candidate.getBoundingBox().getCenter().distanceTo(closest) <= 1.5D && projection < nearest) {
                target = candidate;
                nearest = projection;
            }
        }
        if (target == null) {
            player.sendSystemMessage(Component.literal("Không tìm thấy mục tiêu trong tầm ngắm 18 block."));
            return;
        }
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 10, false, true, true));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true, true));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                50, 0.8D, 0.8D, 0.8D, 0.04D);
        player.sendSystemMessage(Component.literal("Đã dùng Khói Trói Buộc lên mục tiêu trong 5 giây."));
    }

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("fogsmoke")
                .then(Commands.literal("help").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    p.sendSystemMessage(Component.literal("Khói Sương: /fogsmoke cloud [bán kính 4-20], /fogsmoke move, /fogsmoke expand, /fogsmoke shrink, /fogsmoke pull, /fogsmoke bind, /fogsmoke dash, /fogsmoke hide, /fogsmoke clear, /fogsmoke status"));
                    return 1;
                }))
                .then(Commands.literal("cloud")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            if (!allowed(p)) return 0;
                            startCloud(p, 9);
                            return 1;
                        })
                        .then(Commands.argument("radius", IntegerArgumentType.integer(4, 20))
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    if (!allowed(p)) return 0;
                                    startCloud(p, IntegerArgumentType.getInteger(ctx, "radius"));
                                    return 1;
                                })))
                .then(Commands.literal("move").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    SmokeCloud cloud = CLOUDS.get(p.getUUID());
                    if (cloud == null) {
                        p.sendSystemMessage(Component.literal("Bạn chưa tạo màn sương. Dùng /fogsmoke cloud."));
                        return 0;
                    }
                    cloud.level = (ServerLevel) p.level();
                    cloud.center = aimPoint(p, 7.0D);
                    p.sendSystemMessage(Component.literal("Đã di chuyển màn sương đến vị trí bạn đang nhìn."));
                    return 1;
                }))
                .then(Commands.literal("expand").executes(ctx -> changeRadius(ctx.getSource().getPlayerOrException(), 2)))
                .then(Commands.literal("shrink").executes(ctx -> changeRadius(ctx.getSource().getPlayerOrException(), -2)))
                .then(Commands.literal("pull").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    pullEnemies(p);
                    return 1;
                }))
                .then(Commands.literal("bind").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    bindEnemies(p);
                    return 1;
                }))
                .then(Commands.literal("dash").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    Vec3 motion = p.getLookAngle().normalize().scale(1.55D);
                    p.setDeltaMovement(motion.x, Math.max(0.12D, motion.y), motion.z);
                    p.hurtMarked = true;
                    ServerLevel level = (ServerLevel) p.level();
                    level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8D, p.getZ(), 35, 0.6D, 0.7D, 0.6D, 0.08D);
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 0.8D, p.getZ(), 25, 0.5D, 0.6D, 0.5D, 0.04D);
                    p.sendSystemMessage(Component.literal("Hóa khói: lướt nhanh theo hướng nhìn."));
                    return 1;
                }))
                .then(Commands.literal("hide").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 100, 0, false, false, true));
                    ServerLevel level = (ServerLevel) p.level();
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX(), p.getY() + 1.0D, p.getZ(), 70, 0.7D, 1.0D, 0.7D, 0.05D);
                    p.sendSystemMessage(Component.literal("Bạn đã ẩn thân trong khói trong 5 giây."));
                    return 1;
                }))
                .then(Commands.literal("clear").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    CLOUDS.remove(p.getUUID());
                    p.sendSystemMessage(Component.literal("Đã giải tán màn sương do bạn tạo."));
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    if (!allowed(p)) return 0;
                    SmokeCloud cloud = CLOUDS.get(p.getUUID());
                    p.sendSystemMessage(Component.literal(cloud == null ? "Không có màn sương đang hoạt động." :
                            "Màn sương đang hoạt động | Bán kính: " + cloud.radius + " | Còn " + (cloud.ticks / 20) + " giây."));
                    return 1;
                }))
        );
    }

    private static int changeRadius(ServerPlayer player, int amount) {
        if (!allowed(player)) return 0;
        SmokeCloud cloud = CLOUDS.get(player.getUUID());
        if (cloud == null) {
            player.sendSystemMessage(Component.literal("Bạn chưa tạo màn sương. Dùng /fogsmoke cloud."));
            return 0;
        }
        cloud.radius = Math.max(4, Math.min(20, cloud.radius + amount));
        player.sendSystemMessage(Component.literal("Bán kính sương mù: " + cloud.radius + " block."));
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID, SmokeCloud>> iterator = CLOUDS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, SmokeCloud> entry = iterator.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            SmokeCloud cloud = entry.getValue();
            if (cloud.ticks <= 0) {
                iterator.remove();
                continue;
            }
            if (player == null || !player.isAlive() || player.level() != cloud.level) continue;
            cloud.ticks--;
            if (cloud.ticks % 4 == 0) {
                double r = cloud.radius;
                cloud.level.sendParticles(ParticleTypes.CLOUD,
                        cloud.center.x, cloud.center.y, cloud.center.z,
                        cloud.radius * 3, r * 0.42D, 1.8D, r * 0.42D, 0.015D);
                cloud.level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        cloud.center.x, cloud.center.y, cloud.center.z,
                        cloud.radius * 2, r * 0.35D, 1.2D, r * 0.35D, 0.025D);
                cloud.level.sendParticles(ParticleTypes.CLOUD,
                        cloud.center.x, cloud.center.y, cloud.center.z,
                        cloud.radius, r * 0.30D, 0.5D, r * 0.30D, 0.01D);
            }
            if (cloud.ticks % 20 == 0) {
                AABB area = new AABB(cloud.center, cloud.center).inflate(cloud.radius, 3.0D, cloud.radius);
                for (LivingEntity target : cloud.level.getEntitiesOfClass(LivingEntity.class, area,
                        entity -> entity != player && entity.isAlive() && !(entity instanceof ServerPlayer p && isController(p)))) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0, true, false, true));
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 45, 1, true, false, true));
                }
            }
        }
    }
}
