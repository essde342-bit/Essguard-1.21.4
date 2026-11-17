package me.essde.essguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ViolationManager {
    private final EssGuard plugin;
    private final Map<UUID, Map<String, Double>> levels = new ConcurrentHashMap<>();
    public ViolationManager(EssGuard plugin){this.plugin=plugin;}
    public void flag(Player player,String check,double amount,String detail){
        double vl=levels.computeIfAbsent(player.getUniqueId(),k->new ConcurrentHashMap<>())
                .merge(check,amount,Double::sum);
        if(vl>=plugin.getConfig().getDouble("violations.alert-threshold",2.0)){
            Component msg=Component.text("[EssGuard] ",NamedTextColor.AQUA)
                    .append(Component.text(player.getName(),NamedTextColor.WHITE))
                    .append(Component.text(" failed "+check+" VL "+String.format("%.1f",vl)+" ("+detail+")",NamedTextColor.GRAY));
            for(Player staff:Bukkit.getOnlinePlayers()) if(staff.hasPermission("essguard.alerts")) staff.sendMessage(msg);
            if(plugin.getConfig().getBoolean("general.debug",false)) plugin.getLogger().info(player.getName()+" "+check+" "+detail);
        }
    }
    public void reward(Player p,String check,double amount){
        Map<String,Double> map=levels.get(p.getUniqueId()); if(map==null)return;
        map.computeIfPresent(check,(k,v)->Math.max(0,v-amount));
    }
    public double get(Player p,String check){return levels.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(check,0D);}
    public void clear(Player p){levels.remove(p.getUniqueId());}
    public void decay(){
        double d=plugin.getConfig().getDouble("violations.decay-per-second",0.10);
        levels.values().forEach(m->m.replaceAll((k,v)->Math.max(0,v-d)));
    }
}
