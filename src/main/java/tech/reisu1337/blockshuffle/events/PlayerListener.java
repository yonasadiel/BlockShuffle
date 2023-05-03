package tech.reisu1337.blockshuffle.events;

import com.google.common.collect.Sets;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.*;
import tech.reisu1337.blockshuffle.BlockShuffle;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlayerListener implements Listener {
    private static final long ROUND_DURATION_SECONDS = 5*60;
    private static final String OBJECTIVE_NAME = "blockshuffle";

    private final BlockShuffle plugin;
    private final YamlConfiguration settings;
    private final Random random = new Random();
    private List<Material> easyMaterials, hardMaterials;

    private final Set<UUID> usersInGame = Sets.newConcurrentHashSet();
    private final Map<UUID, Date> userStartTime = new ConcurrentHashMap<>();
    private final Map<UUID, List<Material>> userMaterialMap = new ConcurrentHashMap<>();
    private Objective objective;
    private int updateTask;

    public PlayerListener(YamlConfiguration settings, BlockShuffle plugin) {
        this.settings = settings;
        this.plugin = plugin;
    }

    public void startGame() {
        Date startTime = new Date();
        this.easyMaterials = this.settings.getStringList("easy_materials").stream().map(Material::getMaterial).collect(Collectors.toList());
        this.hardMaterials = this.settings.getStringList("hard_materials").stream().map(Material::getMaterial).collect(Collectors.toList());
        Objective oldObjective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(OBJECTIVE_NAME);
        if (oldObjective != null) {
            oldObjective.unregister();
        }
        this.objective = Bukkit.getScoreboardManager().getMainScoreboard().registerNewObjective(OBJECTIVE_NAME, "dummy","Block Shuffle");
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.usersInGame.add(player.getUniqueId());
            this.userStartTime.put(player.getUniqueId(), startTime);
            this.nextRound(player, true, 0);
            this.objective.getScore(player.getName()).setScore(0);
        }
        this.updateTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(this.plugin, this::update, 0, 10);
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
        this.plugin.setInProgress(false);
    }

    public void update() {
        for (UUID playerUUID : this.usersInGame) {
            Player player = Bukkit.getPlayer(playerUUID);
            Date startTime = this.userStartTime.get(playerUUID);
            long remainngSeconds = ROUND_DURATION_SECONDS - (new Date().getTime() - startTime.getTime()) / 1000;

            if (remainngSeconds <= 0) {
                this.nextRound(player, false, 0);
                remainngSeconds = ROUND_DURATION_SECONDS;
            }
            player.setLevel((int) remainngSeconds);
            player.setExp((float) remainngSeconds / ROUND_DURATION_SECONDS);
        }
    }

    private void nextRound(Player player, boolean firstRound, int point) {
        this.userStartTime.put(player.getUniqueId(), new Date());
        String broadcastMessage = "&6<BlockShuffle> &2" + player.getName() + " &f";
        if (!firstRound) {
            if (point > 0) {
                broadcastMessage += "get " + point + "points! Next block: ";
                Score score = this.objective.getScore(player.getName());
                score.setScore(score.getScore() + point);
            } else {
                broadcastMessage += "failed! Next block: ";
            }
        } else {
            broadcastMessage += "get block: ";
        }
        List<Material> materialList = new ArrayList<>();
        materialList.add(this.getRandomEasyMaterial());
        materialList.add(this.getRandomHardMaterial());
        this.userMaterialMap.put(player.getUniqueId(), materialList);

        broadcastMessage += "&3" + this.getMaterialName(materialList.get(0)) + "&f (1 pt)";
        broadcastMessage += " or ";
        broadcastMessage += "&3" + this.getMaterialName(materialList.get(1)) + "&f (3 pts)!";

        this.plugin.getServer().broadcastMessage(ChatColor.translateAlternateColorCodes('&', broadcastMessage));
    }

    private Material getRandomEasyMaterial() {
        int randomIndex = this.random.nextInt(this.easyMaterials.size());
        return this.easyMaterials.get(randomIndex);
    }

    private Material getRandomHardMaterial() {
        int randomIndex = this.random.nextInt(this.hardMaterials.size());
        return this.hardMaterials.get(randomIndex);
    }

    private String getMaterialName(Material m) {
        String name = m.toString().replaceAll("_", " ");
        name = WordUtils.swapCase(name).toLowerCase(Locale.ROOT);
        name = WordUtils.capitalize(name);
        return name;
    }

    @EventHandler
    public void onPlayerMoveEvent(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!this.usersInGame.contains(player.getUniqueId())) {
            return;
        }
        List<Material> expected = this.userMaterialMap.get(player.getUniqueId());
        Material materialBelow = player.getLocation().getBlock().getRelative(BlockFace.DOWN).getBlockData().getMaterial();
        Material materialOn = player.getLocation().getBlock().getBlockData().getMaterial();
        if (expected.get(0) == materialBelow || expected.get(0) == materialOn) {
            this.nextRound(player, false, 1);
        } else if (expected.get(1) == materialBelow || expected.get(1) == materialOn) {
            this.nextRound(player, false, 3);
        }
    }
}