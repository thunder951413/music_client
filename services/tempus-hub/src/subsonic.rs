use crate::model::{Auth, Song};
use anyhow::{Result, bail};
use serde_json::Value;
use std::time::Duration;

#[derive(Clone)]
pub struct Subsonic {
    pub http: reqwest::Client,
    base: String,
}
impl Subsonic {
    pub fn new(base: &str) -> Result<Self> {
        let url = reqwest::Url::parse(base)?;
        anyhow::ensure!(
            matches!(url.scheme(), "http" | "https") && url.host_str().is_some(),
            "invalid Navidrome URL"
        );
        Ok(Self {
            http: reqwest::Client::builder()
                .timeout(Duration::from_secs(30))
                .redirect(reqwest::redirect::Policy::none())
                .build()?,
            base: base.trim_end_matches('/').into(),
        })
    }
    pub async fn call(
        &self,
        auth: &Auth,
        method: &str,
        fields: Vec<(&str, String)>,
    ) -> Result<Value> {
        let mut form = vec![
            ("u", auth.u.clone()),
            ("t", auth.t.clone()),
            ("s", auth.s.clone()),
            ("v", "1.16.1".into()),
            ("c", "tempus-hub".into()),
            ("f", "json".into()),
        ];
        form.extend(fields);
        // Auth and long playlist bodies never appear in access-log URLs.
        let response = self
            .http
            .post(format!("{}/rest/{}.view", self.base, method))
            .form(&form)
            .send()
            .await
            .map_err(|_| anyhow::anyhow!("Navidrome connection failed"))?;
        if !response.status().is_success() {
            bail!("Navidrome HTTP {}", response.status());
        }
        let length = response.content_length().unwrap_or(0);
        anyhow::ensure!(length <= 16 * 1024 * 1024, "Navidrome response too large");
        let bytes = response
            .bytes()
            .await
            .map_err(|_| anyhow::anyhow!("Navidrome body failed"))?;
        anyhow::ensure!(
            bytes.len() <= 16 * 1024 * 1024,
            "Navidrome response too large"
        );
        let value: Value = serde_json::from_slice(&bytes)?;
        let root = &value["subsonic-response"];
        if root["status"] != "ok" {
            bail!(
                "Navidrome rejected request (code {})",
                root["error"]["code"]
            );
        }
        Ok(root.clone())
    }
    pub async fn songs(
        &self,
        a: &Auth,
        method: &str,
        fields: Vec<(&str, String)>,
        field: &str,
    ) -> Result<Vec<Song>> {
        let v = self.call(a, method, fields).await?;
        Ok(serde_json::from_value(
            v.pointer(field)
                .cloned()
                .unwrap_or_else(|| serde_json::json!([])),
        )?)
    }
    pub async fn search(&self, a: &Auth, title: &str) -> Result<Vec<Song>> {
        let mut result = Vec::new();
        for offset in (0..10000).step_by(100) {
            let batch = self
                .songs(
                    a,
                    "search3",
                    vec![
                        ("query", title.into()),
                        ("songCount", "100".into()),
                        ("songOffset", offset.to_string()),
                        ("artistCount", "0".into()),
                        ("albumCount", "0".into()),
                    ],
                    "/searchResult3/song",
                )
                .await?;
            let finished = batch.len() < 100;
            result.extend(batch);
            if finished {
                return Ok(result);
            }
        }
        bail!("search results truncated; automatic matching deferred")
    }
    pub async fn playlist(&self, a: &Auth, id: &str) -> Result<Value> {
        Ok(self.call(a, "getPlaylist", vec![("id", id.into())]).await?["playlist"].clone())
    }
    pub async fn playlists(&self, a: &Auth) -> Result<Vec<Value>> {
        Ok(
            self.call(a, "getPlaylists", vec![]).await?["playlists"]["playlist"]
                .as_array()
                .cloned()
                .unwrap_or_default(),
        )
    }
    pub async fn replace(&self, a: &Auth, id: &str, ids: &[String]) -> Result<()> {
        let mut fields = vec![("playlistId", id.into())];
        fields.extend(ids.iter().map(|id| ("songId", id.clone())));
        self.call(a, "createPlaylist", fields).await?;
        Ok(())
    }
}

pub fn ids(playlist: &Value) -> Vec<String> {
    playlist["entry"]
        .as_array()
        .into_iter()
        .flatten()
        .filter_map(|s| s["id"].as_str().map(str::to_owned))
        .collect()
}
