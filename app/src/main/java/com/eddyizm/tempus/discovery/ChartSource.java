package com.eddyizm.tempus.discovery;

/**
 * Public charts from NetEase and QQ Music, whose song and artist names match how Chinese
 * libraries are usually tagged.
 */
public enum ChartSource {
    NETEASE_HOT(Provider.NETEASE,"网易云 · 热歌榜","3778678"),
    NETEASE_NEW(Provider.NETEASE,"网易云 · 新歌榜","3779629"),
    NETEASE_RISING(Provider.NETEASE,"网易云 · 飙升榜","19723756"),
    NETEASE_ORIGINAL(Provider.NETEASE,"网易云 · 原创榜","2884035"),
    QQ_HOT(Provider.QQ,"QQ音乐 · 热歌榜","26"),
    QQ_NEW(Provider.QQ,"QQ音乐 · 新歌榜","27"),
    QQ_RISING(Provider.QQ,"QQ音乐 · 飙升榜","62"),
    QQ_POPULAR(Provider.QQ,"QQ音乐 · 流行指数榜","4");

    public enum Provider { NETEASE, QQ }

    public final Provider provider;
    /** NetEase playlist id or QQ Music toplist id. */
    public final String label, id;

    ChartSource(Provider provider,String label,String id) { this.provider=provider;this.label=label;this.id=id; }

    public String endpoint() {
        return provider==Provider.NETEASE ? "https://music.163.com/api/playlist/detail?id="+id
                : "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?tpl=3&page=detail&type=top&topid="+id
                        +"&song_begin=0&song_num=100&format=json&inCharset=utf8&outCharset=utf-8";
    }

    public String referer() {
        return provider==Provider.NETEASE ? "https://music.163.com/" : "https://y.qq.com/";
    }

    public String page() {
        return provider==Provider.NETEASE ? "https://music.163.com/discover/toplist?id="+id : "https://y.qq.com/n/ryqq/toplist/"+id;
    }

    @Override public String toString() { return label; }
}
