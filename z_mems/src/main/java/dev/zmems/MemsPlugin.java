package dev.zmems;

import java.util.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class MemsPlugin extends JavaPlugin implements Listener,CommandExecutor,TabCompleter {
    NamespacedKey itemKey,kindKey,linkKey,ownerKey; SceneRunner scenes; OrbManager orbs;
    final Map<UUID,Long> cooldown=new HashMap<>();
    @Override public void onEnable(){
        saveDefaultConfig();itemKey=new NamespacedKey(this,"item");kindKey=new NamespacedKey(this,"kind");linkKey=new NamespacedKey(this,"link");ownerKey=new NamespacedKey(this,"owner");
        scenes=new SceneRunner(this);orbs=new OrbManager(this);var pm=Bukkit.getPluginManager();pm.registerEvents(this,this);pm.registerEvents(orbs,this);pm.registerEvents(scenes,this);
        var cmd=Objects.requireNonNull(getCommand("zmems"));cmd.setExecutor(this);cmd.setTabCompleter(this);scenes.start();getLogger().info("Z_Mems готов. Нужен общий ресурс-пак 1.18.");
    }
    @Override public void onDisable(){if(scenes!=null)scenes.stopAll();}
    static String msg(String text){return "§8[§dZ_Mems§8] §r"+text;}
    ItemStack make(String id){
        if(!Catalog.known(id))throw new IllegalArgumentException("Неизвестный предмет: "+id);
        var stack=new ItemStack(Material.PAPER);var meta=stack.getItemMeta();meta.displayName(Component.text(Catalog.label(id)));
        String model=id.equals("orbital")?"orbital_device":Catalog.scene(id)!=null?"token_"+id:id;meta.setItemModel(new NamespacedKey("z_mems",model));
        meta.getPersistentDataContainer().set(itemKey,PersistentDataType.STRING,id);
        meta.lore(List.of(Component.text(Catalog.scene(id)!=null?"ПКМ — запустить сцену":"ПКМ — разместить смайл")));
        stack.setItemMeta(meta);return stack;
    }
    String id(ItemStack stack){if(stack==null||stack.getType()!=Material.PAPER||!stack.hasItemMeta())return null;String id=stack.getItemMeta().getPersistentDataContainer().get(itemKey,PersistentDataType.STRING);return id!=null&&Catalog.known(id)?id:null;}
    void give(CommandSender sender,Player target,String id,int count){
        if(count<1||count>64)throw new IllegalArgumentException("Количество: от 1 до 64");ItemStack stack=make(id);stack.setAmount(count);
        for(ItemStack remainder:target.getInventory().addItem(stack).values())target.getWorld().dropItemNaturally(target.getLocation(),remainder);
        sender.sendMessage(msg("§aВыдано: §f"+Catalog.label(id)+" × "+count+" → "+target.getName()));
    }
    boolean play(Player player,String id){
        if(!player.hasPermission("zmems.use")){player.sendMessage(msg("§cНет права zmems.use."));return false;}
        if(id.equals("meow_jump")){scenes.toggleMeow(player);return true;}
        long seconds=Math.max(0,Math.min(3600,getConfig().getLong("scenes.cooldown-seconds",12))),now=System.currentTimeMillis();
        long until=cooldown.getOrDefault(player.getUniqueId(),0L);if(until>now){player.sendMessage(msg("§eПодожди ещё "+((until-now+999)/1000)+" сек."));return false;}
        if(!scenes.launch(player,id))return false;cooldown.put(player.getUniqueId(),now+seconds*1000);return true;
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void use(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick())return;String id=id(e.getItem());if(id==null||Catalog.scene(id)==null)return;
        e.setCancelled(true);play(e.getPlayer(),id);
    }
    @EventHandler public void quit(PlayerQuitEvent e){cooldown.remove(e.getPlayer().getUniqueId());scenes.playerQuit(e.getPlayer());}
    @Override public boolean onCommand(CommandSender sender,Command cmd,String label,String[] args){
        if(args.length==0||args[0].equalsIgnoreCase("help")){
            sender.sendMessage(msg("§f/zmems menu §7— получить предметы из меню"));sender.sendMessage("§f/zmems give <ник|@s> <id> [количество] §7— выдать предмет");sender.sendMessage("§f/zmems run <сцена> §7— запустить сцену; §f/zmems stop §7— остановить сцены");sender.sendMessage("§7Смайл: ПКМ по блоку — поставить; Shift + ПКМ по смайлу пустой рукой — забрать.");return true;
        }
        if(!sender.hasPermission("zmems.admin")){sender.sendMessage(msg("§cНет доступа."));return true;}
        try{
            switch(args[0].toLowerCase(Locale.ROOT)){
                case "menu"->{if(!(sender instanceof Player player))throw new IllegalArgumentException("Меню доступно в игре");openMenu(player);}
                case "list"->{sender.sendMessage(msg("§fСцены: §7"+String.join(", ",Catalog.SCENES.stream().map(Catalog.Entry::id).toList())));sender.sendMessage("§fСмайлы: §7"+String.join(", ",Catalog.ORBS.keySet()));}
                case "run"->{if(!(sender instanceof Player player)||args.length!=2||Catalog.scene(args[1])==null)throw new IllegalArgumentException("/zmems run <сцена>");play(player,args[1]);}
                case "give"->{if(args.length<3||args.length>4)throw new IllegalArgumentException("/zmems give <ник|@s> <id> [количество]");Player target=args[1].equals("@s")&&sender instanceof Player pl?pl:Bukkit.getPlayerExact(args[1]);if(target==null)throw new IllegalArgumentException("Игрок должен быть онлайн");int count=1;if(args.length==4)try{count=Integer.parseInt(args[3]);}catch(NumberFormatException ex){throw new IllegalArgumentException("Количество: от 1 до 64");}give(sender,target,args[2],count);}
                case "stop"->{scenes.stopAll();sender.sendMessage(msg("§aАктивные сцены остановлены."));}
                default->throw new IllegalArgumentException("Неизвестная команда. /zmems help");
            }
        }catch(IllegalArgumentException ex){sender.sendMessage(msg("§c"+ex.getMessage()));}return true;
    }
    static final class Menu implements InventoryHolder {Inventory inv;@Override public Inventory getInventory(){return inv;}}
    void openMenu(Player player){var holder=new Menu();holder.inv=Bukkit.createInventory(holder,27,Component.text("Z_Mems — предметы"));int i=0;for(String id:Catalog.ids())holder.inv.setItem(i++,make(id));player.openInventory(holder.inv);}
    @EventHandler public void menu(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player player)||!player.hasPermission("zmems.admin"))return;
        if(e.getClickedInventory()!=e.getView().getTopInventory())return;String id=id(e.getCurrentItem());if(id!=null)give(player,player,id,1);
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args){
        if(!sender.hasPermission("zmems.admin"))return List.of();List<String> choices=new ArrayList<>();
        if(args.length==1)choices.addAll(List.of("help","menu","list","give","run","stop"));
        if(args.length==2&&args[0].equalsIgnoreCase("give")){if(sender instanceof Player)choices.add("@s");Bukkit.getOnlinePlayers().forEach(p->choices.add(p.getName()));}
        if(args.length==3&&args[0].equalsIgnoreCase("give"))choices.addAll(Catalog.ids());
        if(args.length==2&&args[0].equalsIgnoreCase("run"))choices.addAll(Catalog.SCENES.stream().map(Catalog.Entry::id).toList());
        if(args.length==4&&args[0].equalsIgnoreCase("give"))choices.addAll(List.of("1","4","16","64"));
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);return choices.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
