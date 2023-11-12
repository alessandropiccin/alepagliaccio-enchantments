package com.alessandrv.alessandrvenchantments.mixin;

import com.alessandrv.alessandrvenchantments.AlessandrvEnchantments;
import com.alessandrv.alessandrvenchantments.enchantments.ModEnchantments;
import com.alessandrv.alessandrvenchantments.enchantments.SpotterEnchantment;
import com.alessandrv.alessandrvenchantments.particles.ModParticles;
import com.alessandrv.alessandrvenchantments.statuseffects.ModStatusEffects;
import com.alessandrv.alessandrvenchantments.util.SoulboundItemsHolder;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.ArrayList;
import java.util.List;


@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements SoulboundItemsHolder {
    private static final List<ItemStack> soulboundItems = new ArrayList<>(); // Lista degli oggetti con l'incantesimo "Soulbound"
    private static final List<Integer> soulboundItemsSlot = new ArrayList<>(); // Lista degli oggetti con l'incantesimo "Soulbound"

    // Implementa il metodo dell'interfaccia
    @Override
    public List<ItemStack> getSoulboundItems() {
        return soulboundItems;
    }
    @Override
    public List<Integer> getSoulboundItemsSlot() {
        return soulboundItemsSlot;
    }


    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }
    @Inject(method = "tick", at = @At("HEAD"))
    private void tick(CallbackInfo ci) {
        ItemStack itemStackHead = this.getEquippedStack(EquipmentSlot.HEAD);

        int nightStalkerLevel = EnchantmentHelper.getLevel(ModEnchantments.NIGHT_STALKER, itemStackHead);
        if (nightStalkerLevel > 0) {
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 250, 0, false, false, false));
        }

        int glowingLevel = EnchantmentHelper.getLevel(ModEnchantments.GLOWING, itemStackHead);
        if (glowingLevel > 0) {
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 250, 0, false, false, false));
        }

        int spotterlevel = EnchantmentHelper.getLevel(ModEnchantments.SPOTTER, itemStackHead);
        if(spotterlevel>0){

            if(SpotterEnchantment.checkIfAttacked(this)){


                this.addStatusEffect(new StatusEffectInstance(ModStatusEffects.SPOTTER, 5, 0, false, false, false));

            }
        }
        if(ModEnchantments.hasFullArmorSet(ModEnchantments.VOIDLESS,  this)){
            this.addStatusEffect(new StatusEffectInstance(ModStatusEffects.VOIDLESS, 5, 0, false, false, false));

        }

    }


    @Shadow
    public abstract Iterable<ItemStack> getArmorItems();

    @Shadow public abstract PlayerInventory getInventory();

    @Shadow public abstract void setFireTicks(int fireTicks);


    @Inject(method = "dropInventory", at = @At("HEAD"))
    private void dropInventory(CallbackInfo ci) {


        soulboundItems.clear(); // Pulisci la lista soulboundItems
        soulboundItemsSlot.clear(); // Pulisci la lista soulboundItemsSlot
        for (int slot = 0; slot < getInventory().size(); slot++) {
            ItemStack stack = getInventory().getStack(slot);
            if (hasSoulboundEnchantment(stack)) {
                soulboundItems.add(stack.copy()); // Conserva l'oggetto nell'inventario
                stack.setCount(0); // Rimuovi l'oggetto dall'equipaggiamento
                soulboundItemsSlot.add(slot);
            }
        }


    }

    private boolean hasSoulboundEnchantment(ItemStack stack) {
        return EnchantmentHelper.getLevel(ModEnchantments.SOULBOUND, stack) > 0;
    }

    @Inject(method = "applyDamage", at = @At("HEAD"), cancellable = true)
    protected void applyDamage(DamageSource source, float amount, CallbackInfo ci) {
        if(source == DamageSource.OUT_OF_WORLD && this.hasStatusEffect(ModStatusEffects.VOIDLESS)){

            ServerWorld overworld = ((ServerWorld) this.getEntityWorld()).getServer().getWorld(World.OVERWORLD);
            assert overworld != null;

            MinecraftServer server = this.getServer();
            if (server != null) {
                ServerPlayerEntity serverPlayer = server.getPlayerManager().getPlayer(this.getUuid());
                if (serverPlayer != null) {
                    BlockPos spawnPos = serverPlayer.getSpawnPointPosition();
                    if(spawnPos == null) {
                        spawnPos = serverPlayer.getWorld().getSpawnPos();
                    }
                    this.teleportToSpawn(overworld, spawnPos);
                    this.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 200, 10, false, false));

                    this.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 10, 10, false, false));
                    SoundEvent soundEvent =  SoundEvents.ENTITY_ENDER_EYE_DEATH;

                    overworld.playSound(null, spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), soundEvent, SoundCategory.PLAYERS, 1.0F, 1.0F);
                    this.playSound(soundEvent, 1.0F, 1.0F);

                    overworld.spawnParticles(ModParticles.ENDERWAVE,
                            spawnPos.getX(), spawnPos.getY(), spawnPos.getZ(), 1, 0.0, 0, 0.0, 0.0);
                    ci.cancel();
                }
            }

        }
    }


    public void teleportToSpawn(ServerWorld overworld, BlockPos spawnPos) {

        this.teleport(overworld, spawnPos.getX(),spawnPos.getY(), spawnPos.getZ(), this.getYaw(), this.getPitch());
    }

    public void teleport(ServerWorld world, double destX, double destY, double destZ, float yaw, float pitch) {
        float f = MathHelper.clamp(pitch, -90.0f, 90.0f);
        if (world == this.getWorld()) {
            this.refreshPositionAndAngles(destX, destY, destZ, yaw, f);
            this.teleportPassengers();
            this.setHeadYaw(yaw);
        } else {
            this.detach();
            Object entity = this.getType().create(world);
            if (entity != null) {
                ((Entity) entity).copyFrom(this);
                ((Entity) entity).refreshPositionAndAngles(destX, destY, destZ, yaw, f);
                ((Entity) entity).setHeadYaw(yaw);
                this.setRemoved(RemovalReason.CHANGED_DIMENSION);
                AlessandrvEnchantments.LOGGER.info("CIAO");
                world.onDimensionChanged((Entity) entity);
            } else {
            }
        }
    }

    private void updatePassengerPosition(Entity passenger, PositionUpdater positionUpdater) {
        if (this.hasPassenger(passenger)) {
            double d = this.getY() + this.getMountedHeightOffset() + passenger.getHeightOffset();
            positionUpdater.accept(passenger, this.getX(), d, this.getZ());
        }
    }
    private void teleportPassengers() {
        this.streamSelfAndPassengers().forEach(entity -> {
            for (Entity entity2 : entity.getPassengerList()) {
                this.updatePassengerPosition(entity2, Entity::refreshPositionAfterTeleport);
            }
        });
    }

}