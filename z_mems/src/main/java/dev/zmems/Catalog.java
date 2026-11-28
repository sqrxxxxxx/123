package dev.zmems;

import java.util.*;

/** Explicit stable IDs. Their order follows the 13 supplied reference clips. */
public final class Catalog {
    private Catalog(){}
    public record Entry(String id,String label,int video){}
    public static final List<Entry> SCENES=List.of(
        new Entry("cow_rift","Корова и красный разлом",1),
        new Entry("city_5g","Город и камера 5G",2),
        new Entry("meow_jump","Мяуканье при прыжках",3),
        new Entry("verity_sugar","Верити и сахарный тростник",4),
        new Entry("orbital","Орбитальный удар",5),
        new Entry("bell_party","Колокольчик и танцы мобов",6),
        new Entry("apocalypse","Космическая катастрофа",7),
        new Entry("pig_dance","Танцующая свинья",8),
        new Entry("smiley_show","Шоу смайлов",9),
        new Entry("cosmic_cat","Космический кот",10),
        new Entry("skull_cat","Кот и черепа",11),
        new Entry("city_cranes","Город и строительные краны",12),
        new Entry("fruit_festival","Банан и арбуз",13)
    );
    public static final Map<String,String> ORBS;
    static{
        var m=new LinkedHashMap<String,String>();m.put("orb_yellow","Жёлтый смайл");m.put("orb_blue","Синий смайл");m.put("orb_red","Красный смайл");m.put("orb_pink","Розовый смайл");m.put("orb_rainbow","Радужный смайл");m.put("orb_black","Чёрный смайл");m.put("orb_white","Белый смайл");m.put("orb_sad","Грустный смайл");m.put("orb_cute","Милый смайл");m.put("muscle","Сильный смайл");ORBS=Collections.unmodifiableMap(m);
    }
    public static Entry scene(String id){return SCENES.stream().filter(s->s.id.equals(id)).findFirst().orElse(null);}
    public static boolean known(String id){return scene(id)!=null||ORBS.containsKey(id);}
    public static List<String> ids(){var out=new ArrayList<String>();SCENES.forEach(s->out.add(s.id));out.addAll(ORBS.keySet());return out;}
    public static String label(String id){var scene=scene(id);return scene==null?ORBS.get(id):scene.label;}
}
