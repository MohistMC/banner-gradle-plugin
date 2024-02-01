package com.mohistmc.banner.gradle

import org.gradle.api.Project

class BannerExtension {

    private final Project project
    private String mcVersion
    private String bukkitVersion
    private boolean sharedSpigot = true
    private String packageName = "official"

    BannerExtension(Project project) {
        this.project = project
        this.mcVersion = "1.20.4"
        this.bukkitVersion = "v1_20_R3"
    }

    String getMcVersion() {
        return mcVersion
    }

    void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion
    }

    String getBukkitVersion() {
        return bukkitVersion
    }

    void setBukkitVersion(String bukkitVersion) {
        this.bukkitVersion = bukkitVersion
    }

    boolean getSharedSpigot() {
        return sharedSpigot
    }

    void setSharedSpigot(boolean sharedSpigot) {
        this.sharedSpigot = sharedSpigot
    }

    String getPackageName() {
        return packageName
    }

    void setPackageName(String packageName) {
        this.packageName = packageName
    }
}
