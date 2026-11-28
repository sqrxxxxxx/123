package dev.zmems;

import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** Persistent item displays and interaction entities; no vanilla blocks are replaced. */
final class OrbManager implements Listener {
    private final MemsPlugin p;private final Map<UUID,Long> used=new HashMap<>();
    OrbManager(MemsPlugin p){this.p=p;}
    private boolean allowed(Player player){if(player.hasPermission("zmems.use"))return true;player.sendMessage(MemsPlugin.msg("§cНет права zmems.use."));return false;}
    private boolean debounce(Player player){long now=System.currentTimeMillis(),interval=Math.max(50,Math.min(1000,p.getConfig().getLong("orbs.placement-cooldown-ticks",4)*50));Long last=used.get(player.getUniqueId());if(last!=null&&now-last<interval)return false;used.put(player.getUniqueId(),now);return true;}
    private int count(World w){return (int)w.getEntitiesByClass(Interaction.class).stream().filter(e->"orb".equals(e.getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING))).count();}
    boolean place(Player player,String id,Location center){
        if(!allowed(player)||!debounce(player))return false;int cap=Math.max(1,Math.min(2048,p.getConfig().getInt("orbs.max-loaded-per-world",256)));
        if(count(center.getWorld())>=cap){player.sendMessage(MemsPlugin.msg("§cДостигнут лимит смайлов в загруженных чанках."));return false;}
        if(center.getWorld().getNearbyEntitiesByType(Interaction.class,center,.42,e->"orb".equals(e.getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING))).size()>0){player.sendMessage(MemsPlugin.msg("§eЗдесь уже есть смайл."));return false;}
        if(!center.getBlock().isPassable()){player.sendMessage(MemsPlugin.msg("§eДля смайла нужно свободное место."));return false;}
        ItemDisplay display=null;
        try{
            var item=p.make(id);display=center.getWorld().spawn(center,ItemDisplay.class,d->{
                d.setItemStack(item);d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);d.setPersistent(true);
                d.getPersistentDataContainer().set(p.kindKey,PersistentDataType.STRING,"orb_display");d.getPersistentDataContainer().set(p.itemKey,PersistentDataType.STRING,id);
                var t=d.getTransformation();t.getScale().set(1.1f,1.1f,1.1f);d.setTransformation(t);d.setDisplayWidth(2);d.setDisplayHeight(2);
            });
            var did=display.getUniqueId();Interaction click=center.getWorld().spawn(center.clone().subtract(0,.48,0),Interaction.class,i->{
                i.setInteractionWidth(1.0f);i.setInteractionHeight(.96f);i.setResponsive(true);i.setPersistent(true);
                i.getPersistentDataContainer().set(p.kindKey,PersistentDataType.STRING,"orb");i.getPersistentDataContainer().set(p.itemKey,PersistentDataType.STRING,id);i.getPersistentDataContainer().set(p.linkKey,PersistentDataType.STRING,did.toString());i.getPersistentDataContainer().set(p.ownerKey,PersistentDataType.STRING,player.getUniqueId().toString());
            });
            if(!display.isValid()||!click.isValid()){display.remove();click.remove();return false;}
            if(player.getGameMode()!=GameMode.CREATIVE){ItemStack hand=player.getInventory().getItemInMainHand();hand.setAmount(hand.getAmount()-1);player.getInventory().setItemInMainHand(hand);}
            center.getWorld().playSound(center,"z_mems:chime",.45f,1f);return true;
        }catch(RuntimeException ex){if(display!=null)display.remove();p.getLogger().warning("Не удалось разместить смайл: "+ex.getMessage());return false;}
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void block(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null)return;String id=p.id(e.getItem());if(id==null||!Catalog.ORBS.containsKey(id))return;
        e.setCancelled(true);Block b=e.getClickedBlock().getRelative(e.getBlockFace());Location center=b.getLocation().add(.5,.5,.5);place(e.getPlayer(),id,center);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void orb(PlayerInteractEntityEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!(e.getRightClicked() instanceof Interaction box)||!"orb".equals(box.getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING)))return;
        e.setCancelled(true);Player player=e.getPlayer();String held=p.id(player.getInventory().getItemInMainHand());
        if(held!=null&&Catalog.ORBS.containsKey(held)){place(player,held,box.getLocation().add(0,1.44,0));return;}
        if(player.isSneaking()&&player.getInventory().getItemInMainHand().getType().isAir()){
            String owner=box.getPersistentDataContainer().get(p.ownerKey,PersistentDataType.STRING);if(!player.hasPermission("zmems.admin")&&!player.getUniqueId().toString().equals(owner)){player.sendMessage(MemsPlugin.msg("§cЭтот смайл поставил другой игрок."));return;}
            if(!debounce(player))return;String id=box.getPersistentDataContainer().get(p.itemKey,PersistentDataType.STRING),link=box.getPersistentDataContainer().get(p.linkKey,PersistentDataType.STRING);Entity display=null;try{display=Bukkit.getEntity(UUID.fromString(link));}catch(Exception ignored){}
            if(display!=null&&"orb_display".equals(display.getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING)))display.remove();box.remove();if(id!=null&&Catalog.ORBS.containsKey(id)&&player.getGameMode()!=GameMode.CREATIVE)p.give(player,player,id,1);return;
        }
        if(debounce(player))box.getWorld().playSound(box.getLocation(),"z_mems:chime",.6f,1.1f);
    }
    @EventHandler public void quit(PlayerQuitEvent e){used.remove(e.getPlayer().getUniqueId());}
}
