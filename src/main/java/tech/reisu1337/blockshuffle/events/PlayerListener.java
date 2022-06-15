package tech.reisu1337.blockshuffle.events;

import com.google.common.collect.Sets;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;
import tech.reisu1337.blockshuffle.BlockShuffle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {
    private static final long ROUND_DURATION_SECONDS = 60;

    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private String materialPath;
    private final Random random = new Random();
    private List<Material> materials;

    private final Set<UUID> usersInGame = Sets.newConcurrentHashSet();
    private final Map<UUID, Date> userStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, Material> userMaterialMap = new ConcurrentHashMap<>();
    private Objective objective;
    private int updateTask;

    // FOR DEBUG
//    private int inc;

    public PlayerListener(YamlConfiguration settings, BlockShuffle plugin) {
        this.settings = settings;
        this.plugin = plugin;
    }

    public void startGame() {
        Date startTime = new Date();
        this.materials = this.settings.getStringList(this.materialPath).stream().map(Material::getMaterial).collect(Collectors.toList());
        this.objective = Bukkit.getScoreboardManager().getMainScoreboard().registerNewObjective("blockshuffle", "dummy","Block Shuffle");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.usersInGame.add(player.getUniqueId());
            this.userStartTime.put(player.getUniqueId(), startTime);
            this.nextRound(player, true, false);
            this.objective.getScore(player.getName()).setScore(0);
        }
        this.updateTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::update, 0, 10);
//        this.inc = 0;
    }

    public void finishGame() {
        Player winner = null;
        int winnerScore = 0;
        // TODO: store player's current level and exp?
        for (UUID playerUUID : this.usersInGame) {
            Player player = Bukkit.getPlayer(playerUUID);
            player.setLevel(0);
            player.setExp(0);
            int score = this.objective.getScore(player.getName()).getScore();
            if (winner == null || score > winnerScore) {
                winnerScore = score;
                winner = player;
            }
        }
        this.plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', "&6<BlockShuffle> &2" + winner.getName() + " &f is the winner!"));

        this.usersInGame.clear();
        this.userStartTime.clear();
        this.userMaterialMap.clear();
        Bukkit.getScheduler().cancelTask(this.updateTask);
        this.objective.unregister();
    }

    public void update() {
        for (UUID playerUUID : this.usersInGame) {
            Player player = Bukkit.getPlayer(playerUUID);
            Date startTime = this.userStartTime.get(playerUUID);
            long remainngSeconds = ROUND_DURATION_SECONDS - (new Date().getTime() - startTime.getTime()) / 1000;

            if (remainngSeconds <= 0) {
                this.nextRound(player, false, false);
                remainngSeconds = ROUND_DURATION_SECONDS;
            }
            player.setLevel((int) remainngSeconds);
            player.setExp((float) remainngSeconds / ROUND_DURATION_SECONDS);
        }
    }

    private void nextRound(Player player, boolean firstRound, boolean success) {
        this.userStartTime.put(player.getUniqueId(), new Date());
        String broadcastMessage = "&6<BlockShuffle> &2" + player.getName() + " &f";
        if (!firstRound) {
            if (success) {
                broadcastMessage += "success! Next block: ";
                Score score = this.objective.getScore(player.getName());
                score.setScore(score.getScore() + 1);
            } else {
                broadcastMessage += "failed! Next block: ";
            }
        } else {
            broadcastMessage += "get block: ";
        }
        Material randomBlock = this.getRandomMaterial();
        this.userMaterialMap.put(player.getUniqueId(), randomBlock);
        // FOR DEBUG
        player.getInventory().addItem(new ItemStack(randomBlock));

        String randomBlockName = randomBlock.toString().replaceAll("_", " ");
        randomBlockName = WordUtils.swapCase(randomBlockName).toLowerCase(Locale.ROOT);
        randomBlockName = WordUtils.capitalize(randomBlockName);
        broadcastMessage += "&3" + randomBlockName + "&f!";

        this.plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMessage));
    }

    private Material getRandomMaterial() {
        int randomIndex = this.random.nextInt(this.materials.size());
//        Material material = this.materials.get(this.inc);
//        this.inc += 1;
//        return material;
        return this.materials.get(randomIndex);
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Material materialBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData().getMaterial();
        if (this.userMaterialMap.get(player.getUniqueId()) == materialBelow) {
            this.nextRound(player, false, true);
        }
    }

    public void setMaterialPath(String materialPath) {
        this.materialPath = materialPath;
    }
}