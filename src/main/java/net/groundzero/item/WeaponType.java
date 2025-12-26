package net.groundzero.item;

import net.groundzero.app.Core;

/**
 * Weapon types for reload/recoil services.
 * Provides weapon-specific configurations from GameConfig.
 */
public enum WeaponType {

    ASSAULT,
    AUTO,
    SNIPER,
    RPG;

    /* ==================== Magazine / Reload Config ==================== */

    public int getMagazineSize() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultMagazineSize;
            case AUTO -> Core.gameConfig.autoMagazineSize;
            case SNIPER -> Core.gameConfig.sniperMagazineSize;
            case RPG -> Core.gameConfig.rpgMagazineSize;
        };
    }

    public int getReloadTicks() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultReloadTicks;
            case AUTO -> Core.gameConfig.autoReloadTicks;
            case SNIPER -> Core.gameConfig.sniperReloadTicks;
            case RPG -> Core.gameConfig.rpgReloadTicks;
        };
    }

    /* ==================== Recoil Config ==================== */

    public double getRecoilPitch() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultRecoilPitch;
            case AUTO -> Core.gameConfig.autoRecoilPitch;
            case SNIPER -> Core.gameConfig.sniperRecoilPitch;
            case RPG -> Core.gameConfig.rpgRecoilPitch;
        };
    }

    public double getRecoilYaw() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultRecoilYaw;
            case AUTO -> Core.gameConfig.autoRecoilYaw;
            case SNIPER -> Core.gameConfig.sniperRecoilYaw;
            case RPG -> Core.gameConfig.rpgRecoilYaw;
        };
    }

    public int getRecoilRecoveryTicks() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultRecoilRecoveryTicks;
            case AUTO -> Core.gameConfig.autoRecoilRecoveryTicks;
            case SNIPER -> Core.gameConfig.sniperRecoilRecoveryTicks;
            case RPG -> Core.gameConfig.rpgRecoilRecoveryTicks;
        };
    }

    /* ==================== Projectile Config ==================== */

    public double getDamage() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultDamage;
            case AUTO -> Core.gameConfig.autoDamage;
            case SNIPER -> Core.gameConfig.sniperDamage;
            case RPG -> Core.gameConfig.rpgDamage;
        };
    }

    public double getProjectileSpeed() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultProjectileSpeed;
            case AUTO -> Core.gameConfig.autoProjectileSpeed;
            case SNIPER -> Core.gameConfig.sniperProjectileSpeed;
            case RPG -> Core.gameConfig.rpgProjectileSpeed;
        };
    }

    public double getSpread() {
        return switch (this) {
            case ASSAULT -> Core.gameConfig.assaultSpread;
            case AUTO -> Core.gameConfig.autoSpread;
            case SNIPER -> Core.gameConfig.sniperSpread;
            case RPG -> Core.gameConfig.rpgSpread;
        };
    }

    public String getWeaponId() {
        return switch (this) {
            case ASSAULT -> "gz_assault";
            case AUTO -> "gz_auto";
            case SNIPER -> "gz_sniper";
            case RPG -> "gz_rpg";
        };
    }
}