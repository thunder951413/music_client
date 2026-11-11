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
    #[serde(default)]
    pub original_title: Option<String>,
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
        regex::Regex::new(r"(?i)\s+(?:feat\.?|ft\.?)\s+|\s+[•·]\s+|[/,;&、，]").unwrap()
    });
    SPLIT
        .split(text)
        .chain(std::iter::once(text))
        .flat_map(artist_aliases)
        .filter(|n| !n.is_empty())
        .collect()
}

fn artist_aliases(text: &str) -> HashSet<String> {
    static ALIAS: std::sync::LazyLock<regex::Regex> =
        std::sync::LazyLock::new(|| regex::Regex::new(r"^\s*(.+?)\s*\(([^()]+)\)\s*$").unwrap());
    let full = text.nfkc().collect::<String>();
    let mut names = HashSet::from([normalize(&full)]);
    if let Some(captures) = ALIAS.captures(&full) {
        names.insert(normalize(&captures[1]));
        names.insert(normalize(&captures[2]));
    }
    names
}

fn title_names(title: &str, original: Option<&str>) -> HashSet<String> {
    static PAREN: std::sync::LazyLock<regex::Regex> =
        std::sync::LazyLock::new(|| regex::Regex::new(r"\([^()]+\)").unwrap());
    static VERSION: std::sync::LazyLock<regex::Regex> = std::sync::LazyLock::new(|| {
        regex::Regex::new(r"(?i)live|remix|mix|version|explicit|clean|acoustic|instrumental|\binst\b|remaster|edit|\bdj\b|版|伴奏|现场|翻唱|合唱|剪辑|加速|钢琴").unwrap()
    });
    let full = title.nfkc().collect::<String>();
    let mut names = HashSet::from([normalize(&full)]);
    if let Some(original) = original {
        let mut variants = vec![full.clone()];
        for group in PAREN
            .find_iter(&full)
            .take(4)
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
        {
            if !VERSION.is_match(group.as_str()) {
                let additions = variants
                    .iter()
                    .map(|v| format!("{}{}", &v[..group.start()], &v[group.end()..]))
                    .collect::<Vec<_>>();
                variants.extend(additions);
            }
        }
        let original = normalize(original);
        if variants.iter().any(|v| normalize(v) == original) {
            names.insert(original);
        }
    }
    names
}

impl Track {
    pub fn search_titles(&self) -> Vec<&str> {
        let mut titles = vec![self.title.as_str()];
        if let Some(original) = self.original_title.as_deref()
            && normalize(original) != normalize(&self.title)
            && title_names(&self.title, Some(original)).contains(&normalize(original))
        {
            titles.push(original);
        }
        titles
    }
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
            && self
                .original_title
                .as_ref()
                .is_none_or(|s| !normalize(s).is_empty() && s.len() <= 2000)
    }
    pub fn matches(&self, song: &Song) -> bool {
        !song.is_dir
            && !song.is_video
            && title_names(&self.title, self.original_title.as_deref())
                .contains(&normalize(&song.title))
            && self
                .artists
                .iter()
                .any(|a| !artist_aliases(a).is_disjoint(&artist_names(&song.artist)))
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
    pub recent_mix: HashSet<String>,
}

pub fn seeds<'a>(pool: &'a [Song], p: &Profile) -> Vec<&'a Song> {
    let eligible: Vec<_> = pool
        .iter()
        .filter(|s| !s.is_dir && !s.is_video && p.skipped.get(&s.id).copied().unwrap_or(0) < 3)
        .collect();
    let mut recent: Vec<_> = eligible
        .iter()
        .copied()
        .filter(|s| p.last.contains_key(&s.id))
        .collect();
    // Coarse 3-day and play-count tiers let daily pool shuffles rotate seeds naturally.
    recent.sort_by_key(|s| {
        std::cmp::Reverse(p.last.get(&s.id).copied().unwrap_or(0) / (3 * 86_400_000))
    });
    let mut frequent: Vec<_> = eligible
        .iter()
        .copied()
        .filter(|s| p.counts.contains_key(&s.id))
        .collect();
    frequent.sort_by_key(|s| {
        let c = p.counts.get(&s.id).copied().unwrap_or(0);
        std::cmp::Reverse(match c {
            0 => 0,
            1..=2 => 1,
            3..=5 => 2,
            _ => 3,
        })
    });
    let forgotten: Vec<_> = frequent
        .iter()
        .copied()
        .filter(|s| !p.recent.contains(&s.id) && !p.recent_mix.contains(&s.id))
        .collect();
    let mut picked = Vec::new();
    for list in [&recent, &frequent, &forgotten, &eligible] {
        if let Some(s) = list
            .iter()
            .find(|s| !picked.iter().any(|old: &&Song| old.id == s.id))
        {
            picked.push(*s);
        }
    }
    for s in frequent.iter().chain(eligible.iter()) {
        if picked.len() >= 4 {
            break;
        }
        if !picked.iter().any(|old: &&Song| old.id == s.id) {
            picked.push(*s);
        }
    }
    picked
}

pub fn assemble(seeds: &[Song], related: &[Vec<Song>], pool: &[Song], p: &Profile) -> Vec<Song> {
    let mut out = Vec::new();
    let mut seen = HashSet::new();
    let mut artists: HashMap<String, usize> = HashMap::new();
    let mut add = |song: &Song, cap: usize, allow_recent_mix: bool, out: &mut Vec<Song>| {
        if out.len() >= cap
            || song.is_dir
            || song.is_video
            || p.recent.contains(&song.id)
            || (!allow_recent_mix && p.recent_mix.contains(&song.id))
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
    for i in 0..related.iter().map(Vec::len).max().unwrap_or(0) {
        for list in related {
            if let Some(s) = list.get(i) {
                add(s, 24, false, &mut out);
            }
        }
    }
    if let Some(anchor) = seeds
        .iter()
        .find(|s| !p.recent.contains(&s.id) && !p.recent_mix.contains(&s.id))
    {
        add(anchor, 25, false, &mut out);
    }
    for s in pool.iter().filter(|s| !p.heard.contains(&s.id)) {
        add(s, 30, false, &mut out);
    }
    for i in 0..related.iter().map(Vec::len).max().unwrap_or(0) {
        for list in related {
            if let Some(s) = list.get(i) {
                add(s, 30, false, &mut out);
            }
        }
    }
    for s in pool {
        add(s, 30, false, &mut out);
    }
    if out.len() < 30 {
        for list in related {
            for s in list {
                add(s, 25, true, &mut out);
            }
        }
        for s in seeds {
            add(s, 25, true, &mut out);
        }
        for s in pool.iter().filter(|s| !p.heard.contains(&s.id)) {
            add(s, 30, true, &mut out);
        }
        for s in pool {
            add(s, 30, true, &mut out);
        }
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
            original_title: None,
        };
        assert!(t.matches(&song(1, "歌手")));
        assert!(t.matches(&song(1, "歌手 • 嘉宾")));
        assert!(t.matches(&song(1, "歌手 · 嘉宾")));
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

    #[test]
    fn provider_original_names_keep_version_words() {
        let mut t = Track {
            provider: "QQ".into(),
            id: "Mid".into(),
            title: "Night Crusing(夜间巡航) (Inst.)".into(),
            artists: vec!["歌手 (Artist)".into()],
            duration: Some(200),
            original_title: Some("Night Crusing (Inst.)".into()),
        };
        let mut s = song(1, "Artist • Guest");
        s.title = "Night Crusing (Inst.)".into();
        assert!(t.matches(&s));
        s.title = "Night Crusing".into();
        assert!(!t.matches(&s));
        for version in ["Live", "Remix", "Explicit", "伴奏", "钢琴版"] {
            t.title = format!("Song ({version})");
            t.original_title = Some("Song".into());
            s.title = "Song".into();
            assert!(!t.matches(&s));
        }
    }
}
