package me.essde.essguard;

import me.essde.essguard.checks.MovementCheck;
import me.essde.essguard.checks.CombatCheck;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EssGuard extends JavaPlugin implements Listener {
    private final Map<UUID,PlayerData> data=new ConcurrentHashMap<>();
    private ViolationManager violations;
    private MovementCheck movement;
    private int tick;
    @Override public void onEnable(){
        saveDefaultConfig(); violations=new ViolationManager(this); movement=new MovementCheck(this);
        Bukkit.getPluginManager().registerEvents(this,this);
        Bukkit.getPluginManager().registerEvents(new CombatCheck(this),this);
        Bukkit.getScheduler().runTaskTimer(this,()->{
            tick++; long now=System.nanoTime();
            for(Player p:Bukkit.getOnlinePlayers()) movement.sample(p,tick,now,false);
        },1L,1L);
        Bukkit.getScheduler().runTaskTimer(this,()->violations.decay(),20L,20L);
        if(getCommand("essguard")!=null)getCommand("essguard").setExecutor((sender,cmd,label,args)->{
            if(args.length==0){sender.sendMessage("EssGuard 1.0.0 | /essguard reload");return true;}
            if(args[0].equalsIgnoreCase("reload")&&sender.hasPermission("essguard.admin")){
                reloadConfig();sender.sendMessage("EssGuard config reloaded.");return true;
            }
            if(args[0].equalsIgnoreCase("info")&&args.length>1&&sender.hasPermission("essguard.admin")){
                Player target=Bukkit.getPlayerExact(args[1]); if(target==null){sender.sendMessage("Player not found.");return true;}
                sender.sendMessage("Speed "+violations.get(target,"Speed")+" | Fly "+violations.get(target,"Fly")+" | Jesus "+violations.get(target,"Jesus"));return true;
            }
            sender.sendMessage("No permission or unknown command.");return true;
        });
        getLogger().info("EssGuard enabled for Paper 1.21.4 (alert-only).");
    }
    @Override public void onDisable(){data.clear();}
    public PlayerData getData(Player p){return data.computeIfAbsent(p.getUniqueId(),k->new PlayerData());}
    public ViolationManager getViolations(){return violations;}
    public boolean isInGrace(Player p){PlayerData d=getData(p);long now=System.currentTimeMillis();return now-d.getLastVelocityMs()<950||now-d.getLastDamageMs()<600;}
    @EventHandler public void onJoin(PlayerJoinEvent e){getData(e.getPlayer()).initialize(e.getPlayer().getLocation(),System.nanoTime(),tick);}
    @EventHandler public void onQuit(PlayerQuitEvent e){data.remove(e.getPlayer().getUniqueId());violations.clear(e.getPlayer());}
    @EventHandler public void onTeleport(PlayerTeleportEvent e){if(e.isCancelled())return;PlayerData d=getData(e.getPlayer());d.initialize(e.getTo(),System.nanoTime(),tick);}
    @EventHandler public void onVelocity(PlayerVelocityEvent e){getData(e.getPlayer()).setLastVelocityMs(System.currentTimeMillis());}
    @EventHandler public void onDamage(EntityDamageEvent e){if(e.getEntity() instanceof Player p)getData(p).setLastDamageMs(System.currentTimeMillis());}
}
