package com.eddyizm.tempus.discovery;

/**
 * Public charts. NetEase and QQ Music come first: Chinese libraries are usually tagged with their
 * song and artist names, so their charts match far better than Apple Music's.
 */
public enum ChartSource {
    NETEASE_HOT(Provider.NETEASE,"网易云 · 热歌榜","3778678"),
    NETEASE_NEW(Provider.NETEASE,"网易云 · 新歌榜","3779629"),
    NETEASE_RISING(Provider.NETEASE,"网易云 · 飙升榜","19723756"),
    NETEASE_ORIGINAL(Provider.NETEASE,"网易云 · 原创榜","2884035"),
    QQ_HOT(Provider.QQ,"QQ音乐 · 热歌榜","26"),
    QQ_NEW(Provider.QQ,"QQ音乐 · 新歌榜","27"),
    QQ_RISING(Provider.QQ,"QQ音乐 · 飙升榜","62"),
    QQ_POPULAR(Provider.QQ,"QQ音乐 · 流行指数榜","4"),
    APPLE_CN(Provider.APPLE,"Apple Music · 中国大陆","cn"),
    APPLE_JP(Provider.APPLE,"Apple Music · 日本","jp"),
    APPLE_US(Provider.APPLE,"Apple Music · 美国","us");

    public enum Provider { NETEASE, QQ, APPLE }

    public final Provider provider;
    /** NetEase playlist id, QQ Music toplist id, or Apple Music storefront. */
    public final String label, id;

    ChartSource(Provider provider,String label,String id) { this.provider=provider;this.label=label;this.id=id; }

    public String endpoint() {
        switch (provider) {
            case NETEASE: return "https://music.163.com/api/playlist/detail?id="+id;
            case QQ: return "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?tpl=3&page=detail&type=top&topid="+id
                    +"&song_begin=0&song_num=100&format=json&inCharset=utf8&outCharset=utf-8";
            default: return "https://rss.applemarketingtools.com/api/v2/"+id+"/music/most-played/100/songs.json";
        }
    }

    public String referer() {
        switch (provider) {
            case NETEASE: return "https://music.163.com/";
            case QQ: return "https://y.qq.com/";
            default: return "https://music.apple.com/";
        }
    }

    public String page() {
        switch (provider) {
            case NETEASE: return "https://music.163.com/discover/toplist?id="+id;
            case QQ: return "https://y.qq.com/n/ryqq/toplist/"+id;
            default: return "https://music.apple.com/"+id+"/new";
        }
    }

    @Override public String toString() { return label; }
}
