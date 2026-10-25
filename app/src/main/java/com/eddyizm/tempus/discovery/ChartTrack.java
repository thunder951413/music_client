package com.eddyizm.tempus.discovery;

import androidx.annotation.Keep;
import com.eddyizm.tempus.subsonic.models.Child;
import java.util.ArrayList;
import java.util.List;

@Keep
public final class ChartTrack {
    public String id, title, artist, artwork, url;
    public int rank;
    public Integer duration;
    public boolean checked, matchFailed;
    public List<String> artists = new ArrayList<>();
    public List<Child> matches = new ArrayList<>();
    public List<Child> alternatives = new ArrayList<>();
    public ChartTrack matched(List<Child> verified, List<Child> candidates) {
        ChartTrack next=new ChartTrack();
        next.id=id; next.title=title; next.artist=artist; next.artwork=artwork; next.url=url;
        next.rank=rank; next.duration=duration; next.checked=true;next.artists=artists;
        next.matches=new ArrayList<>(verified); next.alternatives=new ArrayList<>(candidates);
        return next;
    }
}
