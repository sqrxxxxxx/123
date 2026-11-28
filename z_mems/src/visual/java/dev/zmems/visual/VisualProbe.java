package dev.zmems.visual;
import java.nio.file.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
public final class VisualProbe extends JavaPlugin{
 int age=0;Player viewer;
 @Override public void onEnable(){Bukkit.getScheduler().runTaskTimer(this,this::tick,20,1);}
 void tick(){try{
  if(viewer==null){viewer=Bukkit.getPlayerExact("LorObserver");if(viewer==null)return;viewer.setOp(true);viewer.setGameMode(GameMode.CREATIVE);viewer.setInvulnerable(true);World w=Bukkit.getWorlds().get(0);int y=w.getHighestBlockYAt(0,0);viewer.teleport(new Location(w,.5,y+1,.5,0,0));}
  age++;
  if(age==100)viewer.performCommand("zmems run smiley_show");
  if(age==300)Files.writeString(Path.of("capture-smile.txt"),"ready");
  if(age==420){viewer.performCommand("zmems stop");viewer.performCommand("zmems run pig_dance");}
  if(age==610)Files.writeString(Path.of("capture-pig.txt"),"ready");
  if(age==730){viewer.performCommand("zmems stop");viewer.performCommand("zmems run cow_rift");}
  if(age==920)Files.writeString(Path.of("capture-cow.txt"),"ready");
 }catch(Exception e){try{Files.writeString(Path.of("visual-error.txt"),e.toString());}catch(Exception ignored){}}}
}
