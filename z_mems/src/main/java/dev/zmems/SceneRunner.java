package dev.zmems;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/** Small, bounded, temporary displays; all block-like scenery is a display entity. */
final class SceneRunner implements Listener {
    private final MemsPlugin p;private final List<Scene> active=new ArrayList<>();private final Map<UUID,Long> meowing=new HashMap<>();
    SceneRunner(MemsPlugin p){this.p=p;}
    void start(){Bukkit.getScheduler().runTaskTimer(p,this::tick,2,2);}
    void toggleMeow(Player player){if(meowing.remove(player.getUniqueId())!=null){player.sendMessage(MemsPlugin.msg("§7Мяуканье при прыжках выключено."));return;}meowing.put(player.getUniqueId(),System.currentTimeMillis()+120_000);player.sendMessage(MemsPlugin.msg("§dКаждый твой прыжок будет сопровождаться мяуканьем в течение двух минут."));}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void jump(PlayerJumpEvent e){Long until=meowing.get(e.getPlayer().getUniqueId());if(until==null)return;if(until<System.currentTimeMillis()){meowing.remove(e.getPlayer().getUniqueId());return;}e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(),Sound.ENTITY_CAT_AMBIENT,1f,1.15f);}
    void playerQuit(Player player){meowing.remove(player.getUniqueId());}
    boolean launch(Player player,String id){
        if(Catalog.scene(id)==null||id.equals("meow_jump"))return false;int limit=Math.max(1,Math.min(12,p.getConfig().getInt("scenes.max-active",3)));
        if(active.size()>=limit){player.sendMessage(MemsPlugin.msg("§eУже запущено максимальное число сцен. Подожди или используй /zmems stop."));return false;}
        Vector dir=player.getLocation().getDirection().setY(0);if(dir.lengthSquared()<.001)dir=new Vector(0,0,1);dir.normalize();
        double distance=Set.of("cow_rift","pig_dance","fruit_festival").contains(id)?15:11;
        Location center=player.getLocation().clone().add(dir.multiply(distance));center.setY(player.getLocation().getY()+1.5);center.setYaw(0);center.setPitch(0);
        if(!center.getWorld().isChunkLoaded(center.getBlockX()>>4,center.getBlockZ()>>4)){player.sendMessage(MemsPlugin.msg("§eМесто для сцены не загружено; подойди ближе."));return false;}
        Scene s=new Scene(player,id,center);try{s.setup();active.add(s);player.sendMessage(MemsPlugin.msg("§aСцена: §f"+Catalog.label(id)));return true;}catch(RuntimeException e){s.remove();p.getLogger().warning("Сцена не запущена: "+e.getMessage());player.sendMessage(MemsPlugin.msg("§cНе удалось запустить сцену; проверь журнал сервера."));return false;}
    }
    void tick(){for(var it=active.iterator();it.hasNext();){Scene s=it.next();try{if(!s.step()){s.remove();it.remove();}}catch(Exception ex){p.getLogger().warning("Ошибка сцены "+s.id+": "+ex.getMessage());s.remove();it.remove();}}meowing.entrySet().removeIf(e->e.getValue()<System.currentTimeMillis());}
    void stopAll(){for(Scene scene:active)scene.remove();active.clear();meowing.clear();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void preventDamage(EntityDamageEvent e){if("scene_mob".equals(e.getEntity().getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING)))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void feed(PlayerInteractEntityEvent e){
        if(!(e.getRightClicked() instanceof Interaction i)||!"scene_cat".equals(i.getPersistentDataContainer().get(p.kindKey,PersistentDataType.STRING)))return;
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND)return;e.setCancelled(true);
        var fish=e.getPlayer().getInventory().getItemInMainHand();
        if(fish.getType()==Material.TROPICAL_FISH||fish.getType()==Material.COD){i.getWorld().spawnParticle(Particle.HEART,i.getLocation().add(0,1,0),5,.4,.3,.4,0);i.getWorld().playSound(i.getLocation(),Sound.ENTITY_CAT_PURR,1,1);if(e.getPlayer().getGameMode()!=GameMode.CREATIVE){fish.setAmount(fish.getAmount()-1);e.getPlayer().getInventory().setItemInMainHand(fish);}}
    }
    private static ItemStack model(String model){ItemStack item=new ItemStack(Material.PAPER);item.editMeta(m->m.setItemModel(new NamespacedKey("z_mems",model)));return item;}
    private record Actor(String model,ItemDisplay display,Vector offset,float size,double phase,boolean orbit){}
    private final class Scene {
        final Player owner;final String id;final Location center;final World world;final List<Entity> entities=new ArrayList<>();final List<Actor> actors=new ArrayList<>();final List<LivingEntity> dancers=new ArrayList<>();final Random random=new Random();
        final long maxTicks;long age=0;boolean struck=false;
        Scene(Player owner,String id,Location center){this.owner=owner;this.id=id;this.center=center;world=center.getWorld();maxTicks=Math.max(12,Math.min(90,p.getConfig().getInt("scenes.duration-seconds",30)))*20L;}
        void sound(String name,float pitch){float volume=(float)Math.max(0,Math.min(2,p.getConfig().getDouble("scenes.sound-volume",.8)));world.playSound(center,"z_mems:"+name,volume,pitch);}
        void effect(Entity entity,String kind){entity.setPersistent(false);entity.getPersistentDataContainer().set(p.kindKey,PersistentDataType.STRING,kind);entities.add(entity);}
        void actor(String name,double x,double y,double z,float size,double phase,boolean orbit){
            Location loc=center.clone().add(x,y,z);if(!world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))return;
            ItemDisplay d=world.spawn(loc,ItemDisplay.class,e->{e.setItemStack(model(name));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);e.setBrightness(new Display.Brightness(15,15));e.setTeleportDuration(3);e.setInterpolationDuration(3);e.setDisplayWidth(Math.max(3,size*2));e.setDisplayHeight(Math.max(3,size*2));var t=e.getTransformation();t.getScale().set(size,size,size);e.setTransformation(t);});effect(d,"scene_display");actors.add(new Actor(name,d,new Vector(x,y,z),size,phase,orbit));
        }
        void block(Material material,double x,double y,double z,float sx,float sy,float sz){
            Location loc=center.clone().add(x,y,z);if(!world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))return;
            BlockDisplay d=world.spawn(loc,BlockDisplay.class,e->{e.setBlock(material.createBlockData());e.setDisplayWidth(16);e.setDisplayHeight(16);var t=e.getTransformation();t.getScale().set(sx,sy,sz);e.setTransformation(t);});effect(d,"scene_display");
        }
        void sign(String title,double x,double y,double z){TextDisplay d=world.spawn(center.clone().add(x,y,z),TextDisplay.class,e->{e.text(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(title));e.setBillboard(Display.Billboard.CENTER);e.setSeeThrough(true);e.setShadowed(true);e.setDefaultBackground(false);e.setViewRange(1.5f);});effect(d,"scene_display");}
        void dancer(Class<? extends LivingEntity> type,double phase){
            Location loc=center.clone().add(Math.cos(phase)*4,-1,Math.sin(phase)*4);if(!world.isChunkLoaded(loc.getBlockX()>>4,loc.getBlockZ()>>4))return;
            try{LivingEntity d=world.spawn(loc,type,org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM,e->{e.setAI(false);e.setGravity(false);e.setSilent(true);e.setInvulnerable(true);e.setCollidable(false);});effect(d,"scene_mob");dancers.add(d);}catch(IllegalArgumentException ex){p.getLogger().fine("Танцор не создан: "+ex.getMessage());}
        }
        void stage(){
            // Neon cage and screen, 35 temporary displays instead of modifying the world.
            for(int x=-3;x<=3;x++)for(int z=-2;z<=2;z++)block((x+z)%2==0?Material.CYAN_CONCRETE:Material.MAGENTA_CONCRETE,x,-1,z,.95f,.18f,.95f);
            for(int x:new int[]{-3,3})for(int z:new int[]{-2,2})block(Material.IRON_BLOCK,x,-.8,z,.35f,5,.35f);
            for(int x=-3;x<=3;x++)block(Material.BLACK_CONCRETE,x,4,-2,1,.45f,.45f);
            sign("§d§lКАМЕРА 5G",0,4.8,-2);
        }
        void setup(){switch(id){
            case "cow_rift"->{actor("cow_boss",0,4.5,0,9,0,false);sound("portal",.7f);}
            case "city_5g"->{stage();actor("white_cat",0,1,0,3,0,false);sound("party",1);}
            case "verity_sugar"->{for(int y=0;y<10;y++)block(Material.GREEN_CONCRETE,0,y-1,0,.65f,1,.65f);actor("orb_yellow",0,5,3,6,0,false);for(int i=0;i<8;i++)actor(i%2==0?"orb_yellow":"orb_rainbow",Math.cos(i*Math.PI/4)*5,0,Math.sin(i*Math.PI/4)*5,1,i*Math.PI/4,true);sound("party",1);}
            case "orbital"->{sign("§c§lОРБИТАЛЬНЫЙ УДАР",0,2,0);sound("warning",1);}
            case "bell_party"->{actor("orb_yellow",0,2,0,3,0,false);dancer(Pig.class,0);dancer(Chicken.class,1);dancer(Sheep.class,2);dancer(Cow.class,3);dancer(Villager.class,4);dancer(Zombie.class,5);sound("party",1);}
            case "apocalypse"->{actor("orb_black",0,5,0,6,0,false);sound("portal",.5f);}
            case "pig_dance"->{actor("pig_body",0,4,0,8,0,false);actor("pig_arm",-4,4.6,0,4,0,false);actor("pig_arm",4,4.6,0,4,Math.PI,false);actor("pig_leg",-1.5,.1,0,3.5f,Math.PI,false);actor("pig_leg",1.5,.1,0,3.5f,0,false);for(int i=0;i<8;i++)actor("pig",Math.cos(i*Math.PI/4)*6,-.1,Math.sin(i*Math.PI/4)*6,1.2f,i*Math.PI/4,true);sound("party",1);}
            case "smiley_show"->{String[] names={"muscle","orb_rainbow","orb_yellow","orb_pink","orb_red","orb_blue","orb_cute","orb_black","orb_sad"};
                for(int i=0;i<names.length;i++)actor(names[i],(i-4)*1.35,0,0,1.25f,i*.5,false);
                for(int i=0;i<5;i++){actor(names[(i+1)%names.length],-2.5,i*1.2-1,3,1.05f,i,false);actor(names[(i+4)%names.length],2.5,i*1.2-1,3,1.05f,i+.5,false);}
                block(Material.RED_CONCRETE,-.45,-1,3,.9f,1,.9f);block(Material.ORANGE_CONCRETE,-.45,0,3,.9f,1,.9f);block(Material.YELLOW_CONCRETE,-.45,1,3,.9f,1,.9f);block(Material.LIME_CONCRETE,-.45,2,3,.9f,1,.9f);sound("chime",1);}
            case "cosmic_cat"->{actor("cosmic_cat",0,1,0,4,0,false);Interaction hit=world.spawn(center.clone().add(0,-1,0),Interaction.class,i->{i.setInteractionWidth(3);i.setInteractionHeight(4);i.setResponsive(true);});effect(hit,"scene_cat");sound("portal",1.4f);}
            case "skull_cat"->{actor("white_cat",0,2,0,4,0,false);for(int i=0;i<5;i++)actor("skull",Math.cos(i*2*Math.PI/5)*4,3+Math.sin(i),Math.sin(i*2*Math.PI/5)*4,2,i*2*Math.PI/5,true);
                for(int side:new int[]{-1,1})for(int i=0;i<4;i++){block(Material.BLACK_CONCRETE,side*(2+i),2+i,-1,.4f,2.4f,.4f);block(Material.RED_CONCRETE,side*(2+i),2+i,-.6,.15f,2.4f,.15f);}sound("portal",.65f);}
            case "city_cranes"->{for(int x=-4;x<=4;x++)for(int z=-1;z<=1;z++)block((x+z)%2==0?Material.GRAY_CONCRETE:Material.LIGHT_GRAY_CONCRETE,x,-1,z,.95f,.3f,.95f);
                for(int side:new int[]{-1,1})for(int row=-1;row<=1;row++){block(Material.LIGHT_GRAY_CONCRETE,side*5,-1,row*2.5,2,4+Math.abs(row),2);block(Material.LIGHT_BLUE_STAINED_GLASS,side*5,1,row*2.5-.05,2,.4f,2.1f);}
                actor("crane",-3,3,0,6,0,false);actor("crane",3,3,0,6,2,false);sound("spark",.7f);}
            case "fruit_festival"->{actor("banana",-3,3,0,6,0,false);actor("watermelon",3,3,0,6,2,false);for(int i=0;i<6;i++)actor(i%2==0?"banana":"watermelon",Math.cos(i*Math.PI/3)*6,0,Math.sin(i*Math.PI/3)*6,1,i*Math.PI/3,true);sound("party",1);}
            default->throw new IllegalArgumentException("Неизвестная сцена");
        }}
        void ring(double radius,double y,int count,double spin,boolean vertical){
            for(int i=0;i<count;i++){double a=i*Math.PI*2/count+spin;float h=(float)((a/(Math.PI*2)+age*.003)%1);Color c=Color.fromRGB(java.awt.Color.HSBtoRGB((h+1)%1,1,1)&0xffffff);Location at=center.clone().add(Math.cos(a)*radius,vertical?Math.sin(a)*radius+y:y,vertical?0:Math.sin(a)*radius);world.spawnParticle(Particle.DUST,at,1,0,0,0,0,new Particle.DustOptions(c,1.4f));}
        }
        void beam(){for(int y=0;y<35;y+=2)world.spawnParticle(Particle.END_ROD,center.clone().add(0,y-1,0),3,.3,.3,.3,0);}
        boolean step(){
            age+=2;if(!owner.isOnline()||!owner.getWorld().equals(world)||age>maxTicks||!world.isChunkLoaded(center.getBlockX()>>4,center.getBlockZ()>>4))return false;
            for(Actor a:actors){if(!a.display.isValid())continue;Vector off=a.offset.clone();if(a.orbit){double a0=a.phase+age*.025;double radius=Math.max(1,Math.hypot(off.getX(),off.getZ()));off.setX(Math.cos(a0)*radius);off.setZ(Math.sin(a0)*radius);}
                Location pos=center.clone().add(off).add(0,Math.sin(age*.09+a.phase)*.18,0);Vector toward=owner.getLocation().toVector().subtract(pos.toVector()).setY(0);if(toward.lengthSquared()>.001)pos.setDirection(toward);pos.setPitch(0);pos.setYaw(pos.getYaw()+(float)(Math.sin(age*.045+a.phase)*12));a.display.teleport(pos);
                if(age%8==0){var t=a.display.getTransformation();float swing=(float)(Math.sin(age*.12+a.phase)*(id.equals("pig_dance")&&(a.model.equals("pig_arm")||a.model.equals("pig_leg"))?.65:.07));t.getLeftRotation().set(new Quaternionf().rotateZ(swing));a.display.setInterpolationDelay(0);a.display.setTransformation(t);}}
            for(int i=0;i<dancers.size();i++){LivingEntity d=dancers.get(i);if(!d.isValid())continue;double a=i*Math.PI*2/Math.max(1,dancers.size())+age*.035;Location at=center.clone().add(Math.cos(a)*4,-1+Math.abs(Math.sin(age*.1+i))*.25,Math.sin(a)*4);at.setYaw((float)Math.toDegrees(-a));d.teleport(at);}
            if(age%8==0)switch(id){
                case "cow_rift"->{ring(6,5,28,age*.015,true);if(age>70)beam();}
                case "city_5g","bell_party","pig_dance","fruit_festival","verity_sugar"->ring(6,-.8,28,age*.025,false);
                case "apocalypse","skull_cat"->{ring(7,3,28,age*.02,true);world.spawnParticle(Particle.REVERSE_PORTAL,center,8,4,4,4,.03);}
                case "cosmic_cat"->{ring(6,5,28,age*.015,false);world.spawnParticle(Particle.END_ROD,center,6,5,4,5,.005);}
                case "orbital"->{ring(5,-1,28,age*.025,false);if(age>=180)beam();}
                case "city_cranes"->world.spawnParticle(Particle.CLOUD,center.clone().add(0,3,0),3,2,1,1,.01);
                default->{}
            }
            if(id.equals("orbital")&&age<=200&&age%20==0){long remain=(200-age)/20;owner.sendActionBar("§cОрбитальный удар: §f"+remain);sound("warning",remain<3?1.4f:1);}
            if(id.equals("orbital")&&!struck&&age>=200){struck=true;sound("beam",1);world.spawnParticle(Particle.EXPLOSION_EMITTER,center,1,0,0,0,0);double damage=Math.max(0,Math.min(100,p.getConfig().getDouble("scenes.orbital-damage",0)));if(damage>0)for(LivingEntity target:world.getNearbyEntitiesByType(LivingEntity.class,center,5))if(!target.equals(owner)&&!entities.contains(target))target.damage(damage,owner);
                if(p.getConfig().getBoolean("scenes.orbital-break-blocks",false)){float power=(float)Math.max(0,Math.min(4,p.getConfig().getDouble("scenes.orbital-explosion-power",3)));if(power>0)world.createExplosion(center,power,false,true,owner);}}
            if(age%80==0&&Set.of("city_5g","bell_party","pig_dance","fruit_festival","verity_sugar").contains(id))sound("party",1);
            return true;
        }
        void remove(){for(Entity e:entities)if(e!=null&&e.isValid())e.remove();entities.clear();actors.clear();dancers.clear();}
    }
}
