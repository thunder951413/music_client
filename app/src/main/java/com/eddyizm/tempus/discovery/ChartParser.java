package com.eddyizm.tempus.discovery;

import com.google.gson.*;
import java.util.*;

public final class ChartParser {
    public static final class Result {
        public final List<ChartTrack> tracks=new ArrayList<>();
        public String updated;
        public boolean cached;
    }
    private ChartParser() {}
    public static Result parse(ChartSource source,String json) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject(); Result result=new Result();
        JsonArray tracks;
        if (source.provider==ChartSource.Provider.QQ) return parseQq(root,result);
        if (source.provider==ChartSource.Provider.NETEASE) {
            if (!root.has("code") || root.get("code").getAsInt()!=200) throw new IllegalArgumentException("Chart unavailable");
            JsonObject list=root.has("playlist") ? root.getAsJsonObject("playlist") : root.getAsJsonObject("result");
            if (list==null) throw new IllegalArgumentException("Chart missing");
            result.updated=value(list,"updateTime"); tracks=list.getAsJsonArray("tracks");
        } else {
            JsonObject feed=root.getAsJsonObject("feed");
            result.updated=value(feed,"updated"); tracks=feed.getAsJsonArray("results");
        }
        if (tracks==null || tracks.isEmpty()) throw new IllegalArgumentException("Empty chart");
        for (JsonElement element:tracks) {
            JsonObject row=element.getAsJsonObject(); ChartTrack item=new ChartTrack();
            item.id=value(row,"id"); item.title=value(row,"name"); item.rank=result.tracks.size()+1;
            if (source.provider==ChartSource.Provider.NETEASE) {
                JsonArray artists=row.has("ar")?row.getAsJsonArray("ar"):row.getAsJsonArray("artists");
                List<String> names=new ArrayList<>(); if (artists!=null) for (JsonElement artist:artists) names.add(value(artist.getAsJsonObject(),"name"));
                item.artists=names;item.artist=String.join(" / ",names);
                JsonObject album=row.has("al")?row.getAsJsonObject("al"):row.getAsJsonObject("album");
                if (album!=null) item.artwork=value(album,"picUrl");
                String duration=row.has("dt")?value(row,"dt"):value(row,"duration");
                if (duration!=null) item.duration=(int)(Long.parseLong(duration)/1000);
                item.url="https://music.163.com/song?id="+item.id;
            } else {
                item.artist=value(row,"artistName"); item.artwork=value(row,"artworkUrl100"); item.url=value(row,"url");
            }
            if (item.id!=null && item.title!=null && item.artist!=null) result.tracks.add(item);
            if (result.tracks.size()>=100) break;
        }
        if (result.tracks.isEmpty()) throw new IllegalArgumentException("No chart entries");
        return result;
    }
    /** QQ Music toplist: songlist[].data with songname, singer[].name, interval and mids. */
    private static Result parseQq(JsonObject root,Result result) {
        if (!root.has("code") || root.get("code").getAsInt()!=0) throw new IllegalArgumentException("Chart unavailable");
        result.updated=value(root,"update_time");
        JsonArray rows=root.getAsJsonArray("songlist");
        if (rows==null || rows.isEmpty()) throw new IllegalArgumentException("Empty chart");
        for (JsonElement element:rows) {
            JsonObject data=element.getAsJsonObject().getAsJsonObject("data");
            if (data==null) continue;
            ChartTrack item=new ChartTrack();
            item.id=value(data,"songmid"); item.title=value(data,"songname"); item.rank=result.tracks.size()+1;
            JsonArray singers=data.getAsJsonArray("singer");
            List<String> names=new ArrayList<>(); if (singers!=null) for (JsonElement singer:singers) names.add(value(singer.getAsJsonObject(),"name"));
            item.artists=names; item.artist=String.join(" / ",names);
            String interval=value(data,"interval"); if (interval!=null) item.duration=Integer.parseInt(interval);
            String album=value(data,"albummid");
            if (album!=null && !album.isEmpty()) item.artwork="https://y.gtimg.cn/music/photo_new/T002R300x300M000"+album+".jpg";
            item.url="https://y.qq.com/n/ryqq/songDetail/"+item.id;
            if (item.id!=null && item.title!=null && !names.isEmpty()) result.tracks.add(item);
            if (result.tracks.size()>=100) break;
        }
        if (result.tracks.isEmpty()) throw new IllegalArgumentException("No chart entries");
        return result;
    }

    private static String value(JsonObject object,String field) {
        return object!=null && object.has(field) && !object.get(field).isJsonNull()?object.get(field).getAsString():null;
    }
}
