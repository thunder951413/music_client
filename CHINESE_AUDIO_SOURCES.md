# 应用内置中文电台与播客

应用安装包随带这份目录。首次打开相关列表时自动加入本机订阅，保留已有来源；退订不会在重启或升级后被自动恢复。后续目录版本只补充新的条目。

核验日期：2026-10-25。本次包含 42 个电台和 36 档播客。公开 RSS、音频 enclosure 和直播分段均已作网络抽查；核验只代表当时的可访问性。完整核验可运行 `python3 bin/verify_audio_catalog.py --output /tmp/audio-source-health.json`。

电台地址来自 Radio Browser，再按广播台名称、平台地址和音频响应核对。播客使用节目方在 Apple Podcasts 或官网发布的公开 RSS；音频由原发布者直接提供。目录不复制、托管或解锁收费音频。

## 电台

| 名称 | 类别 | 地区／定位 | 来源 |
| --- | --- | --- | --- |
| 央广中国之声 | 新闻资讯 | 全国 | [订阅／直播](https://lhttp.qtfm.cn/live/15318317/64k.mp3) · [发布来源](https://www.radio-browser.info/history/835070cd-bf45-4e48-88a4-e9f9a0d7dd9e) |
| 央广经济之声 | 财经商业 | 全国 | [订阅／直播](https://ngcdn002.cnr.cn/live/jjzs/index.m3u8) · [发布来源](https://www.radio-browser.info/history/431eb0c9-c84c-11e8-a54a-52543be04c81) |
| 央广音乐之声 | 音乐 | 全国 | [订阅／直播](https://ngcdn001.cnr.cn/live/yyzs/index.m3u8) · [发布来源](https://www.radio-browser.info/history/a09db942-832d-4932-8c05-494e17dc37e0) |
| 央广中国交通广播 | 交通生活 | 全国 | [订阅／直播](https://ngcdn002.cnr.cn/live/gsgljtgb/index.m3u8) · [发布来源](https://www.radio-browser.info/history/ef5a00a5-3ce5-42e2-a2cc-6678a7b9039e) |
| 北京新闻广播 | 新闻资讯 | 北京 | [订阅／直播](https://brtv-radiolive.rbc.cn/alive/fm945.m3u8) · [发布来源](https://www.radio-browser.info/history/331b2b44-eced-4e35-88ed-361bfe99553b) |
| 北京音乐广播 | 音乐 | 北京 | [订阅／直播](https://brtv-radiolive.rbc.cn/alive/fm974.m3u8) · [发布来源](https://www.radio-browser.info/history/ecbe92bf-a35b-439f-8bcf-55da0ece14ee) |
| 北京文艺广播 | 故事文艺 | 北京 | [订阅／直播](https://brtv-radiolive.rbc.cn/alive/fm876.m3u8) · [发布来源](https://www.radio-browser.info/history/5f7ac40f-1575-4790-9e85-14be4698bda4) |
| 北京交通广播 | 交通生活 | 北京 | [订阅／直播](https://lhttp.qingting.fm/live/336/64k.mp3) · [发布来源](https://www.radio-browser.info/history/6d83cb72-52aa-4b26-83ee-010d44e8ad2d) |
| 上海新闻广播 | 新闻资讯 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/270/64k.mp3) · [发布来源](https://www.radio-browser.info/history/eea091f2-750b-11e9-af37-52543be04c81) |
| 动感101 | 音乐 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/274/64k.mp3) · [发布来源](https://www.radio-browser.info/history/7bd359bf-9653-11e8-a767-52543be04c81) |
| 上海经典音乐广播 | 音乐 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/267/64k.mp3) · [发布来源](https://www.radio-browser.info/history/84907b87-750b-11e9-af37-52543be04c81) |
| 上海经典金曲广播 | 音乐 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/273/64k.mp3) · [发布来源](https://www.radio-browser.info/history/01c9306d-750d-11e9-af37-52543be04c81) |
| 第一财经 | 财经商业 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/276/64k.mp3) · [发布来源](https://www.radio-browser.info/history/44cea5a0-7466-11e9-af37-52543be04c81) |
| 上海交通广播 | 交通生活 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/266/64k.mp3) · [发布来源](https://www.radio-browser.info/history/4922dc73-750c-11e9-af37-52543be04c81) |
| 上海戏剧曲艺广播 | 故事文艺 | 上海 | [订阅／直播](https://lhttp.qingting.fm/live/269/64k.mp3) · [发布来源](https://www.radio-browser.info/history/739a3d6b-5c50-472f-8f0a-6f2d6158a19b) |
| 故事广播-阿基米德故事会 | 故事文艺 | 上海 | [订阅／直播](https://lhttp.qtfm.cn/live/20500182/64k.mp3) · [发布来源](https://www.radio-browser.info/history/85cc79df-510b-42c6-a4c2-9c7c1e347f1f) |
| 广东新闻广播 | 新闻资讯 | 广东 | [订阅／直播](https://lhttp.qtfm.cn/live/1254/64k.mp3) · [发布来源](https://www.radio-browser.info/history/6420a400-38fe-47e4-ab5c-28f0d00667bf) |
| 广东音乐之声 | 音乐 | 广东 | [订阅／直播](https://lhttp.qtfm.cn/live/1260/64k.mp3) · [发布来源](https://www.radio-browser.info/history/2a196f5e-1a14-4f97-b1e3-c4b67d092cfe) |
| 广东珠江经济台 | 财经商业 | 广东 | [订阅／直播](https://lhttp.qtfm.cn/live/1259/64k.mp3) · [发布来源](https://www.radio-browser.info/history/90cefd55-a82f-4ef8-96da-cc1723f7f544) |
| 广东交通之声 | 交通生活 | 广东 | [订阅／直播](https://lhttp.qtfm.cn/live/1262/64k.mp3) · [发布来源](https://www.radio-browser.info/history/5562c062-8a94-47dc-857f-73430c6d4020) |
| 广东股市广播 | 财经商业 | 广东 | [订阅／直播](https://lhttp.qtfm.cn/live/4847/64k.mp3) · [发布来源](https://www.radio-browser.info/history/1ae419ad-2dd8-4b00-8bfb-cce2291a894b) |
| 深圳新闻广播 | 新闻资讯 | 深圳 | [订阅／直播](https://lhttp.qingting.fm/live/1270/64k.mp3) · [发布来源](https://www.radio-browser.info/history/2de5c313-8500-11e9-aa30-52543be04c81) |
| 深圳综艺广播 | 音乐 | 深圳 | [订阅／直播](https://lhttp.qtfm.cn/live/1271/64k.mp3) · [发布来源](https://www.radio-browser.info/history/a9922aba-8435-4ba3-a207-8704817f6dbc) |
| 深圳交通广播 | 交通生活 | 深圳 | [订阅／直播](https://lhttp.qingting.fm/live/1272/64k.mp3) · [发布来源](https://www.radio-browser.info/history/6a3851b4-8500-11e9-aa30-52543be04c81) |
| 四川新闻广播 | 新闻资讯 | 四川 | [订阅／直播](https://lhttp.qtfm.cn/live/4906/64k.mp3) · [发布来源](https://www.radio-browser.info/history/ca431b13-ca35-454b-a4a6-7e7cf6afadd2) |
| 四川交通广播 | 交通生活 | 四川 | [订阅／直播](https://lhttp.qtfm.cn/live/4886/64k.mp3) · [发布来源](https://www.radio-browser.info/history/34d03c7b-8352-40e0-831c-0477a0d507f4) |
| 四川音乐广播 | 音乐 | 四川 | [订阅／直播](https://lhttp.qtfm.cn/live/1110/64k.mp3) · [发布来源](https://www.radio-browser.info/history/2dd8574f-d5bf-4015-94a6-5ac32c61bff3) |
| 浙江之声 | 新闻资讯 | 浙江 | [订阅／直播](https://ali-m-l.cztv.com/channels/lantian/fm88/128k.m3u8) · [发布来源](https://www.radio-browser.info/history/e0d24665-89dd-4adb-892c-5a6b96baca4a) |
| 浙江交通之声 | 交通生活 | 浙江 | [订阅／直播](https://ali-m-l.cztv.com/channels/lantian/fm93/128k.m3u8) · [发布来源](https://www.radio-browser.info/history/bfe2cdb4-712c-4f75-9adc-e5c533861f97) |
| 浙江音乐调频 | 音乐 | 浙江 | [订阅／直播](https://ali-m-l.cztv.com/channels/lantian/fm968/128k.m3u8) · [发布来源](https://www.radio-browser.info/history/57b34085-ed43-45f9-883c-3c18554ddeef) |
| 浙江城市之声 | 交通生活 | 浙江 | [订阅／直播](https://ali-m-l.cztv.com/channels/lantian/fm107/128k.m3u8) · [发布来源](https://www.radio-browser.info/history/872f73b9-4ffb-4e0f-9ee7-bb7aea2b3f6c) |
| 江苏新闻广播 | 新闻资讯 | 江苏 | [订阅／直播](https://lzlive.vojs.cn/5Fo8mMX/92/live.m3u8) · [发布来源](https://www.radio-browser.info/history/612d94bc-fe01-45fd-a84a-e3e495d23ce9) |
| 江苏经典流行音乐广播 | 音乐 | 江苏 | [订阅／直播](https://lzlive.vojs.cn/2wtSfrS/92/live.m3u8) · [发布来源](https://www.radio-browser.info/history/6ee61dc9-f7ed-41a1-b483-b664c610c01c) |
| 江苏故事广播 | 故事文艺 | 江苏 | [订阅／直播](https://lzlive.vojs.cn/rWjyus9/92/live.m3u8) · [发布来源](https://www.radio-browser.info/history/5bbeb892-0f47-4992-99f5-f20f1bdbba61) |
| 江苏交通广播网 | 交通生活 | 江苏 | [订阅／直播](https://lzlive.vojs.cn/4TaHTeL/92/live.m3u8) · [发布来源](https://www.radio-browser.info/history/45d70f9c-94ab-45cd-8760-96da8ee24501) |
| 江苏音乐广播 | 音乐 | 江苏 | [订阅／直播](https://lzlive.vojs.cn/jAmO6Ng/92/live.m3u8) · [发布来源](https://www.radio-browser.info/history/2f380320-0fdf-4225-8498-810c65879867) |
| 山东交通广播 | 交通生活 | 山东 | [订阅／直播](http://audiolive302.iqilu.com/sdradioJiaotong/sdradio05/playlist.m3u8) · [发布来源](https://www.radio-browser.info/history/43d39584-59d2-48de-8b7d-504b3657dfd6) |
| 山东经典音乐广播 | 音乐 | 山东 | [订阅／直播](http://audiolive302.iqilu.com/sdradioShenghuo/sdradio04/playlist.m3u8) · [发布来源](https://www.radio-browser.info/history/6c5edef4-6a79-4ec7-84f7-b8430bcd0e4f) |
| 河北交通广播 | 交通生活 | 河北 | [订阅／直播](https://radio.pull.hebtv.com/live/hebjt.m3u8) · [发布来源](https://www.radio-browser.info/history/e997777b-0954-4409-a38a-e54d94bf2bd5) |
| 河北经济广播（故事广播） | 故事文艺 | 河北 | [订阅／直播](https://radio.pull.hebtv.com/live/hebjj.m3u8) · [发布来源](https://www.radio-browser.info/history/5bdffec0-f96f-43c6-9305-693c971c2444) |
| 河南交通广播 | 交通生活 | 河南 | [订阅／直播](https://stream.hndt.com/live/jiaotong/playlist.m3u8) · [发布来源](https://www.radio-browser.info/history/420e2393-bcdf-4979-9adc-9d79e8c6fe1a) |
| 合肥故事广播 | 故事文艺 | 合肥 | [订阅／直播](https://lhttp.qingting.fm/live/1961/64k.mp3) · [发布来源](https://www.radio-browser.info/history/ee300b74-17b4-11ea-a620-52543be04c81) |

## 播客

| 名称 | 类别 | 地区／定位 | 来源 |
| --- | --- | --- | --- |
| 日谈公园 | 谈话故事 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/5574153.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%97%A5%E8%B0%88%E5%85%AC%E5%9B%AD/id1166949390?uo=4) |
| 故事FM | 谈话故事 | 中国大陆 | [订阅／直播](https://feeds.storyfm.cn/storyfm.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%95%85%E4%BA%8Bfm/id1256399960?uo=4) |
| 忽左忽右 | 人文文化 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/cv4bkgpuglwp) · [发布来源](https://podcasts.apple.com/us/podcast/%E5%BF%BD%E5%B7%A6%E5%BF%BD%E5%8F%B3/id1493503146?uo=4) |
| 文化有限 | 人文文化 | 中国大陆 | [订阅／直播](https://s1.proxy.wavpub.com/weknownothing.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%96%87%E5%8C%96%E6%9C%89%E9%99%90/id1482731836?uo=4) |
| 随机波动 | 人文文化 | 中国大陆 | [订阅／直播](https://feeds.fireside.fm/stovol/rss) · [发布来源](https://www.stovol.com/subscribe) |
| 一席 | 人文文化 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/jq7xytwpykrg) · [发布来源](https://podcasts.apple.com/cn/podcast/%E4%B8%80%E5%B8%AD/id676106704?uo=4) |
| 展开讲讲 | 影视娱乐 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/24672021.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%B1%95%E5%BC%80%E8%AE%B2%E8%AE%B2/id1470561691?uo=4) |
| 螺丝在拧紧 | 人文文化 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/47008946.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E8%9E%BA%E4%B8%9D%E5%9C%A8%E6%8B%A7%E7%B4%A7/id1555601768?uo=4) |
| 大内密谈 | 影视娱乐 | 中国大陆 | [订阅／直播](https://rss.lizhi.fm/rss/14275.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%A4%A7%E5%86%85%E5%AF%86%E8%B0%88/id657765158?uo=4) |
| 无聊斋 | 影视娱乐 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/njwyhpcjqn9t) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%97%A0%E8%81%8A%E6%96%8B/id1433530822?uo=4) |
| 钱粮胡同 | 谈话故事 | 中国大陆 | [订阅／直播](https://s1.proxy.wavpub.com/qianlianghutong.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E9%92%B1%E7%B2%AE%E8%83%A1%E5%90%8Cfm/id1522623900?uo=4) |
| 得意忘形 | 谈话故事 | 海外中文 | [订阅／直播](https://feed.xyzfm.space/klaak6nmc3ux) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%BE%97%E6%84%8F%E5%BF%98%E5%BD%A2/id1200767928?uo=4) |
| 声东击西 | 新闻观察 | 海外中文 | [订阅／直播](https://feeds.fireside.fm/shengdongjixi/rss) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%A3%B0%E4%B8%9C%E5%87%BB%E8%A5%BF/id1183662640?uo=4) |
| 东亚观察局 | 新闻观察 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/eye-on-east-asia) · [发布来源](https://podcasts.apple.com/cn/podcast/%E4%B8%9C%E4%BA%9A%E8%A7%82%E5%AF%9F%E5%B1%80/id1508293790?uo=4) |
| 不合时宜 | 新闻观察 | 海外中文 | [订阅／直播](https://feed.xyzfm.space/ww7cqnybekty) · [发布来源](https://podcasts.apple.com/cn/podcast/%E4%B8%8D%E5%90%88%E6%97%B6%E5%AE%9C/id1487143507?uo=4) |
| 新闻酸菜馆 | 新闻观察 | 海外中文 | [订阅／直播](https://wzzgg.com/feed/wasai) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%96%B0%E9%97%BB%E9%85%B8%E8%8F%9C%E9%A6%86/id473501583?uo=4) |
| 不明白播客 | 新闻观察 | 海外中文 | [订阅／直播](https://feeds.acast.com/public/shows/68004395b4ef799a7a410371) · [发布来源](https://podcasts.apple.com/us/podcast/%E4%B8%8D%E6%98%8E%E7%99%BD%E6%92%AD%E5%AE%A2/id1625856906?uo=4) |
| 百靈果 News | 新闻观察 | 台湾 | [订阅／直播](https://feed.firstory.me/rss/user/cmjaz594i0000hdvpdpnd4fw8) · [发布来源](https://podcasts.apple.com/us/podcast/%E7%99%BE%E9%9D%88%E6%9E%9C-news/id1106847606?uo=4) |
| 敏迪選讀 | 新闻观察 | 台湾 | [订阅／直播](https://feeds.soundon.fm/podcasts/44833083-490d-4f97-a782-fd5e34c0abef.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E6%95%8F%E8%BF%AA%E9%81%B8%E8%AE%80/id1475418379?uo=4) |
| 轉角國際 | 新闻观察 | 台湾 | [订阅／直播](https://feeds.soundon.fm/podcasts/4f4a009b-d95b-4590-85cf-d5e050ead84e.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E8%BD%89%E8%A7%92%E5%9C%8B%E9%9A%9B%E6%96%B0%E8%81%9E-daily-podcast/id1469553043?uo=4) |
| 美國台灣觀測站 | 新闻观察 | 海外中文 | [订阅／直播](https://feeds.soundon.fm/podcasts/6cdfccc6-7c47-4c35-8352-7f634b1b6f71.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E7%BE%8E%E5%9C%8B%E5%8F%B0%E7%81%A3%E8%A7%80%E6%B8%AC%E7%AB%99/id1508245836?uo=4) |
| 硅谷101 | 科技互联网 | 海外中文 | [订阅／直播](https://feeds.fireside.fm/sv101/rss) · [发布来源](https://podcasts.apple.com/cn/podcast/%E7%A1%85%E8%B0%B7101/id1498541229?uo=4) |
| 科技早知道 | 科技互联网 | 海外中文 | [订阅／直播](https://feeds.fireside.fm/guiguzaozhidao/rss) · [发布来源](https://podcasts.apple.com/cn/podcast/whats-next-%E7%A7%91%E6%8A%80%E6%97%A9%E7%9F%A5%E9%81%93/id1494812579?uo=4) |
| 津津乐道 | 科技互联网 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/3785430.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E6%B4%A5%E6%B4%A5%E4%B9%90%E9%81%93%E4%B8%AD%E5%9B%BD%E7%89%88/id1466652109?uo=4) |
| 硬地骇客 | 科技互联网 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/byhkljlbep9j) · [发布来源](https://podcasts.apple.com/us/podcast/%E7%A1%AC%E5%9C%B0%E9%AA%87%E5%AE%A2/id1678465783?uo=4) |
| 捕蛇者说 | 科技互联网 | 中国大陆 | [订阅／直播](https://pythonhunter.org/feed/audio.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E6%8D%95%E8%9B%87%E8%80%85%E8%AF%B4/id1460475182?uo=4) |
| 一天世界 | 科技互联网 | 海外中文 | [订阅／直播](https://yitianshijie.net/feed/audio.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E4%B8%80%E5%A4%A9%E4%B8%96%E7%95%8C/id1099645400?uo=4) |
| 商业就是这样 | 商业财经 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/46587439.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%95%86%E4%B8%9A%E5%B0%B1%E6%98%AF%E8%BF%99%E6%A0%B7/id1552904790?uo=4) |
| 声动早咖啡 | 商业财经 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/51076156.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E5%A3%B0%E5%8A%A8%E6%97%A9%E5%92%96%E5%95%A1/id1573189055?uo=4) |
| 疯投圈 | 商业财经 | 中国大陆 | [订阅／直播](https://crazy.capital/feed) · [发布来源](https://podcasts.apple.com/cn/podcast/%E7%96%AF%E6%8A%95%E5%9C%88/id1088178402?uo=4) |
| 晚点聊 | 商业财经 | 中国大陆 | [订阅／直播](https://feeds.fireside.fm/latetalk/rss) · [发布来源](https://podcasts.apple.com/cn/podcast/%E6%99%9A%E7%82%B9%E8%81%8A-latetalk/id1564877433?uo=4) |
| 知行小酒馆 | 商业财经 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/j8yp8gxkmgqr) · [发布来源](https://podcasts.apple.com/cn/podcast/%E7%9F%A5%E8%A1%8C%E5%B0%8F%E9%85%92%E9%A6%86/id1559695855?uo=4) |
| 面基 | 商业财经 | 中国大陆 | [订阅／直播](https://feed.xyzfm.space/6hpdgggtxpxb) · [发布来源](https://podcasts.apple.com/cn/podcast/%E9%9D%A2%E5%9F%BA/id1686741064?uo=4) |
| 贝望录 | 商业财经 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/42715423.xml) · [发布来源](https://podcasts.apple.com/cn/podcast/%E8%B4%9D%E6%9C%9B%E5%BD%95/id1492415440?uo=4) |
| 文化土豆 | 人文文化 | 海外中文 | [订阅／直播](https://feeds.acast.com/public/shows/5e0b6ece81be870a61107c66) · [发布来源](https://podcasts.apple.com/us/podcast/%E6%96%87%E5%8C%96%E5%9C%9F%E8%B1%86-culture-potato/id1243945491?uo=4) |
| 打个招呼 | 谈话故事 | 中国大陆 | [订阅／直播](https://www.ximalaya.com/album/40025879.xml) · [发布来源](https://podcasts.apple.com/us/podcast/%E6%89%93%E4%B8%AA%E6%8B%9B%E5%91%BC/id1526246044?uo=4) |

## 播客刷新与存储

- 客户端直接读取 RSS；服务端不支持播客时也可使用本机订阅。原有服务端播客仍保留。
- 每档保存最近 60 期节目列表，默认六小时内复用缓存。页面可手动刷新。首次拉取最多三个并发请求，并在获得足够节目后结束读取。
- “缓存节目列表”只保存标题、简介和播放地址；音频在点播时从原发布者获取。不会自动下载 36 档节目音频。
- 本机 RSS 节目支持播放、快进／快退、加入队列和本机继续收听；没有连接尚未实现的离线下载或服务端删除按钮。
- 内置来源可编辑或取消订阅；修改过的来源不会被目录覆盖。相同 RSS／直播 URL 不重复加入。
- 港台与海外条目按节目定位标注，网络、地区、发布者迁移或停更都可能影响可访问性。一天世界等节目保留公开的历史内容。
