use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};
use std::collections::{HashMap, HashSet};
use unicode_normalization::UnicodeNormalization;

#[derive(Clone, Serialize, Deserialize, Debug, PartialEq)]
pub struct Auth {
    pub u: String,
    pub t: String,
    pub s: String,
}

#[derive(Clone, Default, Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Song {
    pub id: String,
    #[serde(default)]
    pub title: String,
    #[serde(default)]
    pub artist: String,
    #[serde(default)]
    pub genre: Option<String>,
    #[serde(default)]
    pub year: Option<i32>,
    #[serde(default)]
    pub duration: Option<i64>,
    #[serde(default)]
    pub is_dir: bool,
    #[serde(default)]
    pub is_video: bool,
    #[serde(flatten)]
    pub extra: Map<String, Value>,
}

#[derive(Clone, Serialize, Deserialize, Debug)]
pub struct Track {
    pub provider: String,
    pub id: String,
    pub title: String,
    pub artists: Vec<String>,
    pub duration: Option<i64>,
}

// Keep version words and symbols significant; punctuation/width/case follow the Android rules.
pub fn normalize(text: &str) -> String {
    static PUNCT: std::sync::LazyLock<regex::Regex> =
        std::sync::LazyLock::new(|| regex::Regex::new(r"[\p{P}\p{Z}\s]").unwrap());
    PUNCT
        .replace_all(&text.nfkc().collect::<String>().to_lowercase(), "")
        .into_owned()
}

pub fn artist_names(text: &str) -> HashSet<String> {
    static SPLIT: std::sync::LazyLock<regex::Regex> = std::sync::LazyLock::new(|| {
        regex::Regex::new(r"(?i)\s+(?:feat\.?|ft\.?)\s+|[/,;&、，]").unwrap()
    });
    SPLIT
        .split(text)
        .chain(std::iter::once(text))
        .map(normalize)
        .filter(|n| !n.is_empty())
        .collect()
}

impl Track {
    pub fn key(&self) -> String {
        // JSON avoids delimiter collisions. Cross-provider identity retains version and lead artist.
        serde_json::to_string(&(
            normalize(&self.title),
            normalize(self.artists.first().map(String::as_str).unwrap_or("")),
        ))
        .unwrap()
    }
    pub fn valid(&self) -> bool {
        let id_ok = !self.id.is_empty()
            && self.id.len() <= 64
            && self.id.chars().all(|c| {
                if self.provider == "NETEASE" {
                    c.is_ascii_digit()
                } else {
                    c.is_ascii_alphanumeric()
                }
            });
        matches!(self.provider.as_str(), "QQ" | "NETEASE")
            && id_ok
            && !normalize(&self.title).is_empty()
            && self.title.len() <= 2000
            && !self.artists.is_empty()
            && self.artists.len() <= 30
            && self
                .artists
                .iter()
                .all(|a| !normalize(a).is_empty() && a.len() <= 1000)
            && self.duration.is_none_or(|d| (0..=86400).contains(&d))
    }
    pub fn matches(&self, song: &Song) -> bool {
        !song.is_dir
            && !song.is_video
            && normalize(&self.title) == normalize(&song.title)
            && self
                .artists
                .iter()
                .any(|a| artist_names(&song.artist).contains(&normalize(a)))
            && self
                .duration
                .zip(song.duration)
                .is_none_or(|(a, b)| (a - b).abs() <= 12)
    }
}

#[derive(Default, Debug)]
pub struct Profile {
    pub counts: HashMap<String, usize>,
    pub skipped: HashMap<String, usize>,
    pub last: HashMap<String, i64>,
    pub heard: HashSet<String>,
    pub recent: HashSet<String>,
}

pub fn seeds<'a>(pool: &'a [Song], p: &Profile) -> Vec<&'a Song> {
    let eligible: Vec<_> = pool
        .iter()
        .filter(|s| p.skipped.get(&s.id).copied().unwrap_or(0) < 3)
        .collect();
    let mut recent = eligible.clone();
    recent.sort_by_key(|s| std::cmp::Reverse(p.last.get(&s.id).copied().unwrap_or(0)));
    let mut frequent = eligible.clone();
    frequent.sort_by_key(|s| std::cmp::Reverse(p.counts.get(&s.id).copied().unwrap_or(0)));
    let mut forgotten = frequent.clone();
    forgotten.retain(|s| p.counts.contains_key(&s.id) && !p.recent.contains(&s.id));
    let mut picked = Vec::new();
    for list in [&recent, &frequent, &forgotten, &eligible] {
        if let Some(s) = list
            .iter()
            .find(|s| !picked.iter().any(|old: &&Song| old.id == s.id))
        {
            picked.push(*s);
        }
    }
    picked
}

pub fn assemble(seeds: &[Song], related: &[Vec<Song>], pool: &[Song], p: &Profile) -> Vec<Song> {
    let mut out = Vec::new();
    let mut seen = HashSet::new();
    let mut artists: HashMap<String, usize> = HashMap::new();
    let mut add = |song: &Song, cap: usize, out: &mut Vec<Song>| {
        if out.len() >= cap
            || song.is_dir
            || song.is_video
            || p.recent.contains(&song.id)
            || p.skipped.get(&song.id).copied().unwrap_or(0) >= 3
            || seen.contains(&song.id)
        {
            return;
        }
        let names = artist_names(&song.artist);
        let names = if names.is_empty() {
            HashSet::from([String::new()])
        } else {
            names
        };
        if names
            .iter()
            .any(|a| artists.get(a).copied().unwrap_or(0) >= 3)
        {
            return;
        }
        for a in names {
            *artists.entry(a).or_default() += 1;
        }
        seen.insert(song.id.clone());
        out.push(song.clone());
    };
    for s in seeds {
        add(s, 25, &mut out);
    }
    for i in 0..related.iter().map(Vec::len).max().unwrap_or(0) {
        for list in related {
            if let Some(s) = list.get(i) {
                add(s, 25, &mut out);
            }
        }
    }
    for s in pool.iter().filter(|s| !p.heard.contains(&s.id)) {
        add(s, 30, &mut out);
    }
    for s in pool {
        add(s, 30, &mut out);
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    fn song(id: usize, artist: &str) -> Song {
        Song {
            id: id.to_string(),
            title: "歌 (Live)".into(),
            artist: artist.into(),
            ..Default::default()
        }
    }
    #[test]
    fn cold_start_and_device_history() {
        let songs: Vec<_> = (0..50).map(|i| song(i, &format!("artist{i}"))).collect();
        assert!(!seeds(&songs, &Profile::default()).is_empty());
        let mut p = Profile::default();
        p.recent.insert("0".into());
        p.heard.extend((0..45).map(|i| i.to_string()));
        let out = assemble(&songs[..1], &[songs[..45].to_vec()], &songs, &p);
        assert_eq!(out.len(), 30);
        assert!(!out.iter().any(|s| s.id == "0"));
        for id in 45..50 {
            assert!(out.iter().any(|s| s.id == id.to_string()));
        }
        assert!(Profile::default().heard.is_empty());
    }
    #[test]
    fn artist_caps_apply_to_collaborations() {
        let songs: Vec<_> = (0..50)
            .map(|i| song(i, &format!("Same / Guest{i}")))
            .collect();
        assert_eq!(assemble(&[], &[], &songs, &Profile::default()).len(), 3);
    }
    #[test]
    fn matching_preserves_versions_and_duration() {
        let t = Track {
            provider: "QQ".into(),
            id: "Ab12".into(),
            title: "歌（Live）".into(),
            artists: vec!["歌手".into()],
            duration: Some(200),
        };
        assert!(t.matches(&song(1, "歌手")));
        let mut s = song(1, "歌手");
        s.title = "歌".into();
        assert!(!t.matches(&s));
        s.title = "歌 (Live)".into();
        s.duration = Some(30);
        assert!(!t.matches(&s));
        s.duration = Some(200);
        s.artist = "翻唱者".into();
        assert!(!t.matches(&s));
    }
}
