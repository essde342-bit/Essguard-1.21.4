package me.essde.essguard;

import me.essde.essguard.checks.CombatCheck;
import me.essde.essguard.checks.MovementCheck;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EssGuard extends JavaPlugin implements Listener {
    private final Map<UUID, PlayerData> data = new ConcurrentHashMap<>();
    private ViolationManager violations;
    private MovementCheck movement;
    private int tick;
    private long previousTickNs;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        violations = new ViolationManager(this);
        movement = new MovementCheck(this);

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new CombatCheck(this), this);

        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tick++;
            long now = System.nanoTime();
            boolean serverLag = previousTickNs != 0L && now - previousTickNs > 75_000_000L;
            previousTickNs = now;
            for (Player player : Bukkit.getOnlinePlayers()) {
                movement.sample(player, tick, now, serverLag);
            }
        }, 1L, 1L);

        Bukkit.getScheduler().runTaskTimer(this, () -> violations.decay(), 20L, 20L);

        if (getCommand("essguard") != null) {
            getCommand("essguard").setExecutor((sender, command, label, args) -> {
                if (args.length == 0) {
                    sender.sendMessage("EssGuard 1.0.0 | /essguard reload | /essguard info <player>");
                    return true;
                }
                if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("essguard.admin")) {
                    reloadConfig();
                    sender.sendMessage("EssGuard config reloaded.");
                    return true;
                }
                if (args[0].equalsIgnoreCase("info") && args.length > 1
                        && sender.hasPermission("essguard.admin")) {
                    Player target = Bukkit.getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage("Player not found.");
                        return true;
                    }
                    sender.sendMessage("Speed " + violations.get(target, "Speed")
                            + " | Fly " + violations.get(target, "Fly")
                            + " | Jesus " + violations.get(target, "Jesus")
                            + " | Reach " + violations.get(target, "Reach"));
                    return true;
                }
                sender.sendMessage("No permission or unknown command.");
                return true;
            });
        }

        getLogger().info("EssGuard enabled for Paper 1.21.4 (alert-only).");
    }

    @Override
    public void onDisable() {
        data.clear();
        previousTickNs = 0L;
    }

    public PlayerData getData(Player player) {
        return data.computeIfAbsent(player.getUniqueId(), key -> new PlayerData());
    }

    public ViolationManager getViolations() {
        return violations;
    }

    public boolean isInGrace(Player player) {
        PlayerData playerData = getData(player);
        long now = System.currentTimeMillis();
        long velocityGrace = getConfig().getLong("safety.velocity-grace-ms", 950L);
        long damageGrace = getConfig().getLong("safety.damage-grace-ms", 600L);
        return now - playerData.getLastVelocityMs() < velocityGrace
                || now - playerData.getLastDamageMs() < damageGrace;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        getData(event.getPlayer()).initialize(event.getPlayer().getLocation(), System.nanoTime(), tick);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        data.remove(event.getPlayer().getUniqueId());
        violations.clear(event.getPlayer());
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled() || event.getTo() == null) return;
        getData(event.getPlayer()).initialize(event.getTo(), System.nanoTime(), tick);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        getData(event.getPlayer()).initialize(event.getRespawnLocation(), System.nanoTime(), tick);
    }

    @EventHandler
    public void onVelocity(PlayerVelocityEvent event) {
        if (!event.isCancelled()) {
            getData(event.getPlayer()).setLastVelocityMs(System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player player) {
            getData(player).setLastDamageMs(System.currentTimeMillis());
        }
    }
}