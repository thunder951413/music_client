use crate::{model::Track, store::Store};
use anyhow::{Result, ensure};
use rusqlite::params;
use serde_json::Value;

const SOURCES: [(&str, &str); 8] = [
    ("NETEASE", "3778678"),
    ("NETEASE", "3779629"),
    ("NETEASE", "19723756"),
    ("NETEASE", "2884035"),
    ("QQ", "26"),
    ("QQ", "27"),
    ("QQ", "62"),
    ("QQ", "4"),
];

pub fn parse(provider: &str, root: &Value) -> Result<Vec<Track>> {
    let rows = if provider == "NETEASE" {
        ensure!(root["code"] == 200, "chart unavailable");
        root.pointer("/playlist/tracks")
            .or_else(|| root.pointer("/result/tracks"))
    } else {
        ensure!(root["code"] == 0, "chart unavailable");
        root.get("songlist")
    };
    let rows = rows
        .and_then(Value::as_array)
        .ok_or_else(|| anyhow::anyhow!("chart missing tracks"))?;
    let mut out = Vec::new();
    for row in rows.iter().take(100) {
        let data = if provider == "NETEASE" {
            row
        } else {
            &row["data"]
        };
        let value = |a: &str, b: &str| data.get(a).or_else(|| data.get(b));
        let id = value(
            if provider == "NETEASE" {
                "id"
            } else {
                "songmid"
            },
            "mid",
        )
        .map(|v| {
            v.as_str()
                .map(str::to_owned)
                .unwrap_or_else(|| v.to_string())
        })
        .unwrap_or_default();
        let title = value("name", "songname")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_owned();
        let artists = value(
            if provider == "NETEASE" {
                "ar"
            } else {
                "singer"
            },
            "artists",
        )
        .and_then(Value::as_array)
        .into_iter()
        .flatten()
        .filter_map(|a| a["name"].as_str().map(str::to_owned))
        .collect();
        let duration = if provider == "NETEASE" {
            value("dt", "duration")
                .and_then(Value::as_i64)
                .map(|d| d / 1000)
        } else {
            data["interval"].as_i64()
        };
        let t = Track {
            provider: provider.into(),
            id,
            title,
            artists,
            duration,
        };
        if t.valid() {
            out.push(t);
        }
    }
    ensure!(!out.is_empty(), "empty chart");
    Ok(out)
}

pub async fn refresh(store: &Store, http: &reqwest::Client) -> Result<()> {
    let now = chrono::Utc::now().timestamp();
    for (provider, id) in SOURCES {
        let key = format!("{provider}:{id}");
        let fresh: bool = store.db.lock().unwrap().query_row(
            "SELECT EXISTS(SELECT 1 FROM chart_cache WHERE id=?1 AND fetched_at>?2)",
            params![key, now - 21600],
            |r| r.get(0),
        )?;
        if fresh {
            continue;
        }
        let url = if provider == "NETEASE" {
            format!("https://music.163.com/api/playlist/detail?id={id}")
        } else {
            format!(
                "https://c.y.qq.com/v8/fcg-bin/fcg_v8_toplist_cp.fcg?tpl=3&page=detail&type=top&topid={id}&song_begin=0&song_num=100&format=json&inCharset=utf8&outCharset=utf-8"
            )
        };
        let result: Result<Vec<Track>> = async {
            let mut response = http
                .get(url)
                .header("User-Agent", "Mozilla/5.0 Tempus/1.0")
                .header(
                    "Referer",
                    if provider == "NETEASE" {
                        "https://music.163.com/"
                    } else {
                        "https://y.qq.com/"
                    },
                )
                .send()
                .await
                .map_err(|_| anyhow::anyhow!("chart connection failed"))?;
            ensure!(response.status().is_success(), "chart HTTP failed");
            let mut bytes = Vec::new();
            while let Some(chunk) = response
                .chunk()
                .await
                .map_err(|_| anyhow::anyhow!("chart body failed"))?
            {
                ensure!(
                    bytes.len() + chunk.len() <= 4 * 1024 * 1024,
                    "chart too large"
                );
                bytes.extend(chunk);
            }
            parse(provider, &serde_json::from_slice(&bytes)?)
        }
        .await;
        match result {
            Ok(tracks) => {
                store.db.lock().unwrap().execute("INSERT INTO chart_cache VALUES(?1,?2,?3) ON CONFLICT(id) DO UPDATE SET fetched_at=excluded.fetched_at,data=excluded.data",params![key,now,serde_json::to_string(&tracks)?])?;
            }
            Err(_) => eprintln!("chart {key} refresh failed; keeping last successful cache"),
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn platforms_have_same_track_contract() {
        let a = serde_json::json!({"code":200,"result":{"tracks":[{"id":123,"name":"Song","artists":[{"name":"A"}],"duration":200000}]}});
        let b = serde_json::json!({"code":0,"songlist":[{"data":{"songmid":"Mid","songname":"Song","singer":[{"name":"A"}],"interval":200}}]});
        assert_eq!(
            parse("NETEASE", &a).unwrap()[0].key(),
            parse("QQ", &b).unwrap()[0].key()
        );
        assert!(parse("QQ", &serde_json::json!({"code":0,"songlist":[]})).is_err());
    }
}
