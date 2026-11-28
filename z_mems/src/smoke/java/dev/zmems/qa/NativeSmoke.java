package dev.zmems.qa;

import com.google.gson.Gson;
import dev.zmems.Catalog;
import java.nio.file.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-server verification plugin; never included in Z_Mems. */
public final class NativeSmoke extends JavaPlugin {
 Player player; World world; int stage=0,tick=0,since=0,index=0;boolean second,finished;final Map<String,String> checks=new LinkedHashMap<>();NamespacedKey item=new NamespacedKey("z_mems","item"),kind=new NamespacedKey("z_mems","kind");Block floor;List<String> scenes;
 @Override public void onEnable(){second=Files.exists(Path.of("zmems-first.ok"));Bukkit.getScheduler().runTaskTimer(this,(Runnable)this::step,1,1);}
 void check(String n,boolean ok){if(!ok)throw new IllegalStateException(n);checks.put(n,"PASS");}
 void next(){stage++;since=tick;}int age(){return tick-since;}
 void report(String status,String error){try{Files.writeString(Path.of("zmems-report.json"),new Gson().toJson(Map.of("status",status,"phase",second?2:1,"checks",checks,"failure",error)));}catch(Exception ignored){}finished=true;}
 long temporary(){return world.getEntities().stream().filter(e->{String v=e.getPersistentDataContainer().get(kind,PersistentDataType.STRING);return v!=null&&v.startsWith("scene_");}).count();}
 List<Interaction> orbs(){return world.getEntitiesByClass(Interaction.class).stream().filter(e->"orb".equals(e.getPersistentDataContainer().get(kind,PersistentDataType.STRING))).toList();}
 ItemStack findItem(String id){return Arrays.stream(player.getInventory().getContents()).filter(Objects::nonNull).filter(s->s.hasItemMeta()&&id.equals(s.getItemMeta().getPersistentDataContainer().get(item,PersistentDataType.STRING))).findFirst().orElse(null);}
 void step(){if(finished)return;tick++;try{
  if(tick>5000)throw new IllegalStateException("timeout stage="+stage+" index="+index);
  if(stage==0){
    player=Bukkit.getPlayerExact("MemsTester");if(player==null)return;player.setOp(true);world=Bukkit.getWorlds().get(0);int y=world.getHighestBlockYAt(0,0);floor=world.getBlockAt(0,y,0);
    player.teleport(new Location(world,.5,y+1,.5,0,0));player.setGameMode(GameMode.SURVIVAL);player.setInvulnerable(true);
    check("zmems_enabled",Bukkit.getPluginManager().isPluginEnabled("Z_Mems"));check("ztest_enabled",Bukkit.getPluginManager().isPluginEnabled("Z_Test"));check("zlor_enabled",Bukkit.getPluginManager().isPluginEnabled("Z_Lor"));check("zverity_enabled",Bukkit.getPluginManager().isPluginEnabled("Z_Verity"));
    if(second){check("orb_saved_after_restart",orbs().size()==1);next();return;}
    check("fresh_world",orbs().isEmpty());Material before=floor.getType();
    player.performCommand("zmems give @s orb_yellow 1");ItemStack stack=findItem("orb_yellow");check("orb_command",stack!=null);player.getInventory().setItemInMainHand(stack);
    var ev=new PlayerInteractEvent(player,Action.RIGHT_CLICK_BLOCK,stack,floor,BlockFace.UP,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(ev);
    check("orb_placed",ev.isCancelled()&&orbs().size()==1);check("existing_block_not_replaced",floor.getType()==before);
    var orb=orbs().get(0);check("orb_persistent",orb.isPersistent()&&world.getEntitiesByClass(ItemDisplay.class).stream().anyMatch(d->"orb_display".equals(d.getPersistentDataContainer().get(kind,PersistentDataType.STRING))&&d.isPersistent()));
    player.getInventory().clear();player.performCommand("zmems menu");check("menu_opens",player.getOpenInventory().getTopInventory().getSize()==27);player.closeInventory();
    player.setOp(false);player.performCommand("zmems give @s orb_red 1");check("give_permission_enforced",findItem("orb_red")==null);player.setOp(true);
    scenes=Catalog.SCENES.stream().map(Catalog.Entry::id).toList();next();return;
  }
  if(second){
    if(stage==1&&age()>10){Interaction orb=orbs().get(0);player.getInventory().setItemInMainHand(null);player.setSneaking(true);var e=new PlayerInteractEntityEvent(player,orb,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(e);check("orb_retrieve",e.isCancelled()&&orbs().isEmpty());check("orb_returned",findItem("orb_yellow")!=null);next();return;}
    if(stage==2&&age()>5){check("orb_model_removed",world.getEntitiesByClass(ItemDisplay.class).stream().noneMatch(d->"orb_display".equals(d.getPersistentDataContainer().get(kind,PersistentDataType.STRING))));report("PASS","");return;}
  } else {
    if(stage==1){
      if(index>=scenes.size()){stage=4;since=tick;return;}
      String id=scenes.get(index);player.performCommand("zmems run "+id);if(id.equals("meow_jump")){Location from=player.getLocation();Bukkit.getPluginManager().callEvent(new com.destroystokyo.paper.event.player.PlayerJumpEvent(player,from,from.clone().add(0,.42,0)));check("scene_"+id,true);player.performCommand("zmems stop");index++;return;}next();return;
    }
    if(stage==2&&age()>24){String id=scenes.get(index);check("scene_"+id,temporary()>0);check("world_preserved_"+id,floor.getType()!=Material.AIR);player.performCommand("zmems stop");next();return;}
    if(stage==3&&age()>3){check("cleanup_"+scenes.get(index),temporary()==0);index++;stage=1;return;}
    if(stage==4){check("all_13_scenes",index==13);player.performCommand("zmems give @s orbital 1");ItemStack token=findItem("orbital");check("orbital_item_given",token!=null);player.getInventory().setItemInMainHand(token);
       var use=new PlayerInteractEvent(player,Action.RIGHT_CLICK_AIR,token,null,BlockFace.SELF,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(use);check("scene_token_used",use.isCancelled()&&temporary()>0);next();return;}
    if(stage==5&&age()>220){check("orbital_countdown_and_impact",temporary()>0&&floor.getType()!=Material.AIR);player.performCommand("zmems stop");Files.writeString(Path.of("zmems-first.ok"),"ok");report("PASS","");return;}
  }
 }catch(Throwable e){getLogger().severe("Z_Mems QA: "+e);report("FAIL",e.toString());}}
}
