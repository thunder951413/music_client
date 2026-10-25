package com.eddyizm.tempus.discovery;

public enum ChartSource {
    NETEASE_HOT("网易云 · 热歌榜","3778678",null),
    NETEASE_NEW("网易云 · 新歌榜","3779629",null),
    NETEASE_RISING("网易云 · 飙升榜","19723756",null),
    APPLE_CN("Apple Music · 中国大陆",null,"cn"),
    APPLE_JP("Apple Music · 日本",null,"jp"),
    APPLE_US("Apple Music · 美国",null,"us");
    public final String label, playlistId, country;
    ChartSource(String label,String playlistId,String country) { this.label=label;this.playlistId=playlistId;this.country=country; }
    public String endpoint() {
        return playlistId!=null ? "https://music.163.com/api/playlist/detail?id="+playlistId
                : "https://rss.applemarketingtools.com/api/v2/"+country+"/music/most-played/100/songs.json";
    }
    public String page() { return playlistId!=null ? "https://music.163.com/discover/toplist?id="+playlistId : "https://music.apple.com/"+country+"/new"; }
    @Override public String toString() { return label; }
}
