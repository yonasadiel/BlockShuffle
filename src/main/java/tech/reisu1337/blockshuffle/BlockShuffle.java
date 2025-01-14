package tech.reisu1337.blockshuffle;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import tech.reisu1337.blockshuffle.commands.BlockShuffleCommand;
import tech.reisu1337.blockshuffle.events.PlayerListener;
import tech.reisu1337.blockshuffle.menus.BlockShuffleMenu;

import java.io.File;
import java.util.logging.Logger;

public final class BlockShuffle extends JavaPlugin {
    private File settingsFile;
    private boolean inProgress;

    public static Logger LOGGER;

    @Override
    public void onEnable() {
        LOGGER = this.getLogger();
        this.settingsFile = this.getDataFolder().toPath().resolve("settings.yml").toFile();
        this.createSettingsFile();

        YamlConfiguration settings = YamlConfiguration.loadConfiguration(this.settingsFile);

        PlayerListener playerListener = new PlayerListener(settings, this);
        BlockShuffleMenu blockShuffleMenu = new BlockShuffleMenu(playerListener, settings, this);
        this.getServer().getPluginManager().registerEvents(playerListener, this);
        this.getServer().getPluginManager().registerEvents(blockShuffleMenu, this);

        this.getCommand("blockshuffle").setExecutor(new BlockShuffleCommand(playerListener, blockShuffleMenu, this, settings));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private void createSettingsFile() {
        if (!this.settingsFile.exists()) {
            this.saveResource("settings.yml", false);
        }
    }

    public boolean isInProgress() {
        return this.inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
}

/*
 __ __ __
|YY|OO|BB|
|RR|WW|RR|
|WW|BB|GG|
 ‾‾ ‾‾ ‾‾
*/