package dev.zmems;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
class CatalogTest {
 @Test void thirteenClipsHaveUniqueStableScenes(){assertEquals(13,Catalog.SCENES.size());assertEquals(13,new HashSet<>(Catalog.SCENES.stream().map(Catalog.Entry::id).toList()).size());for(int i=0;i<13;i++)assertEquals(i+1,Catalog.SCENES.get(i).video());}
 @Test void orbAndSceneIdentifiersDoNotCollide(){assertEquals(Catalog.ids().size(),new HashSet<>(Catalog.ids()).size());assertNull(Catalog.scene("orb_yellow"));assertFalse(Catalog.known("nonsense"));}
}
