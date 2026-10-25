package com.eddyizm.tempus.discovery;
import com.eddyizm.tempus.subsonic.models.Child;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class MusicDiscoveryRulesTest {
    private Child song(String id,String title,String artist,Long count) { Child s=new Child(id);s.setTitle(title);s.setArtist(artist);s.setPlayCount(count);return s; }
    private ChartTrack chart(String title,String artist,Integer duration) { ChartTrack t=new ChartTrack();t.title=title;t.artist=artist;t.duration=duration;return t; }
    @Test public void unheardExcludesKnownPlaysLocalHistoryShownAndDuplicates() {
        List<Child> pool=Arrays.asList(song("a","A","X",0L),song("b","B","X",2L),song("c","C","X",null),song("d","D","X",0L),song("a","A","X",0L));
        List<Child> result=MusicDiscoveryRules.unheard(pool,new HashSet<>(Collections.singleton("c")),new HashSet<>(Collections.singleton("d")),40);
        assertEquals(1,result.size());assertEquals("a",result.get(0).getId());
    }
    @Test public void unknownPlayCountsUseAvailableHistoryAndRespectLimit() {
        List<Child> result=MusicDiscoveryRules.unheard(Arrays.asList(song("a","A","X",null),song("b","B","X",null)),Collections.emptySet(),Collections.emptySet(),1);
        assertEquals(1,result.size());
    }
    @Test public void neverAutoMatchesAnotherArtistOrAnotherVersion() {
        ChartTrack t=chart("Song","Original Artist",180);
        Child wrong=song("a","Song","Cover Artist",0L), live=song("b","Song (Live)","Original Artist",0L), remix=song("c","Song Remix","Original Artist",0L);
        assertTrue(MusicDiscoveryRules.matches(t,Arrays.asList(wrong,live,remix)).isEmpty());
    }
    @Test public void normalizesUnicodeButKeepsVersionWordsAndChecksDuration() {
        ChartTrack t=chart("Ｓｏｎｇ！","Artist",180);Child correct=song("a","Song","ARTIST",0L);correct.setDuration(181);
        Child longVersion=song("b","Song","Artist",0L);longVersion.setDuration(250);
        var matches=MusicDiscoveryRules.matches(t,Arrays.asList(correct,longVersion));assertEquals(1,matches.size());assertEquals("a",matches.get(0).getId());
    }
    @Test public void preservesBandNamesContainingSlashesAndSupportsCollaboration() {
        ChartTrack band=chart("Song","AC/DC",null);band.artists.add("AC/DC");
        assertTrue(MusicDiscoveryRules.matches(band,Collections.singletonList(song("a","Song","AC",null))).isEmpty());
        assertEquals(1,MusicDiscoveryRules.matches(band,Collections.singletonList(song("b","Song","AC/DC",null))).size());
        ChartTrack collab=chart("Song","A / B",null);collab.artists.addAll(Arrays.asList("A","B"));
        assertEquals(1,MusicDiscoveryRules.matches(collab,Collections.singletonList(song("c","Song","A & B",null))).size());
    }
    @Test public void retainsMultipleExactVersionsForExplicitChoice() {
        ChartTrack t=chart("Song","Artist",null);
        assertEquals(2,MusicDiscoveryRules.matches(t,Arrays.asList(song("a","Song","Artist",null),song("b","Song","Artist",null),song("a","Song","Artist",null))).size());
    }
}
