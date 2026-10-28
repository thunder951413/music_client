mod charts;
mod model;
mod store;
mod subsonic;

use anyhow::{Result, ensure};
use axum::{
    Json, Router,
    extract::{DefaultBodyLimit, Path, Query, State},
    http::{HeaderMap, StatusCode},
    response::{IntoResponse, Response},
    routing::{get, post},
};
use base64::{
    Engine,
    engine::general_purpose::{STANDARD, URL_SAFE_NO_PAD},
};
use chrono::Timelike;
use model::{Auth, Song, Track};
use rand::seq::SliceRandom;
use rusqlite::{OptionalExtension, params};
use serde::{Deserialize, Serialize};
use serde_json::{Value, json};
use std::{collections::HashSet, sync::Arc};
use store::Store;
use subsonic::Subsonic;

#[derive(Clone)]
struct Hub {
    store: Arc<Store>,
    api: Subsonic,
    work: Arc<tokio::sync::Mutex<()>>,
    zone: chrono_tz::Tz,
}
struct ApiError(StatusCode, String);
impl From<anyhow::Error> for ApiError {
    fn from(e: anyhow::Error) -> Self {
        eprintln!("operation failed: {e}");
        Self(
            StatusCode::SERVICE_UNAVAILABLE,
            "operation failed; retry later".into(),
        )
    }
}
impl From<rusqlite::Error> for ApiError {
    fn from(e: rusqlite::Error) -> Self {
        anyhow::Error::from(e).into()
    }
}
impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        (self.0, Json(json!({"error":self.1}))).into_response()
    }
}
type Reply = std::result::Result<Json<Value>, ApiError>;
fn bad(text: &str) -> ApiError {
    ApiError(StatusCode::BAD_REQUEST, text.into())
}
fn missing() -> ApiError {
    ApiError(StatusCode::NOT_FOUND, "resource not found".into())
}
fn conflict() -> ApiError {
    ApiError(
        StatusCode::CONFLICT,
        "playlist changed; refresh and confirm on phone".into(),
    )
}
fn valid_id(s: &str) -> bool {
    !s.is_empty()
        && s.len() <= 128
        && s.chars()
            .all(|c| c.is_ascii_alphanumeric() || "-_".contains(c))
}

impl Hub {
    fn date(&self) -> String {
        chrono::Utc::now()
            .with_timezone(&self.zone)
            .format("%Y-%m-%d")
            .to_string()
    }
    async fn auth(&self, h: &HeaderMap) -> std::result::Result<Auth, ApiError> {
        let parse = || -> Option<Auth> {
            let value = h.get("X-Tempus-Auth")?.to_str().ok()?;
            if value.len() > 4096 {
                return None;
            }
            let a: Auth = serde_json::from_slice(&STANDARD.decode(value).ok()?).ok()?;
            if a.u.is_empty()
                || a.u.len() > 256
                || a.t.len() != 32
                || !a.t.chars().all(|c| c.is_ascii_hexdigit())
                || a.s.is_empty()
                || a.s.len() > 256
            {
                return None;
            }
            Some(a)
        };
        let a = parse().ok_or_else(|| {
            ApiError(
                StatusCode::UNAUTHORIZED,
                "Subsonic token authentication required".into(),
            )
        })?;
        self.api.call(&a, "ping", vec![]).await.map_err(|_| {
            ApiError(
                StatusCode::UNAUTHORIZED,
                "Navidrome authentication failed".into(),
            )
        })?;
        Ok(a)
    }
    fn kick(&self) {
        let hub = self.clone();
        tokio::spawn(async move {
            if let Err(e) = hub.run().await {
                eprintln!("background job failed: {e}");
            }
        });
    }
    fn owns_device(&self, user: &str, id: &str) -> std::result::Result<(), ApiError> {
        let exists: bool = self.store.db.lock().unwrap().query_row(
            "SELECT EXISTS(SELECT 1 FROM devices WHERE username=?1 AND id=?2)",
            params![user, id],
            |r| r.get(0),
        )?;
        if !exists {
            return Err(missing());
        }
        Ok(())
    }
}

#[derive(Deserialize)]
struct Device {
    id: String,
    name: String,
}
async fn device(State(hub): State<Hub>, headers: HeaderMap, Json(body): Json<Device>) -> Reply {
    let a = hub.auth(&headers).await?;
    if !valid_id(&body.id) || body.name.is_empty() || body.name.len() > 256 {
        return Err(bad("invalid device"));
    }
    hub.store.save_auth(&a)?;
    hub.store.db.lock().unwrap().execute("INSERT INTO devices VALUES(?1,?2,?3,?4) ON CONFLICT(username,id) DO UPDATE SET name=excluded.name,last_seen=excluded.last_seen",params![a.u,body.id,body.name,chrono::Utc::now().timestamp_millis()])?;
    Ok(Json(json!({"id":body.id,"username":a.u})))
}
#[derive(Clone, Serialize, Deserialize)]
struct Play {
    event_id: String,
    song_id: String,
    at: i64,
    completed: bool,
    position_ms: i64,
    duration_ms: i64,
}
#[derive(Deserialize)]
struct Plays {
    device: String,
    events: Vec<Play>,
}
async fn plays(State(hub): State<Hub>, headers: HeaderMap, Json(body): Json<Plays>) -> Reply {
    let a = hub.auth(&headers).await?;
    hub.owns_device(&a.u, &body.device)?;
    if body.events.len() > 200 {
        return Err(bad("maximum 200 events per batch"));
    }
    let now = chrono::Utc::now().timestamp_millis();
    if body.events.iter().any(|e| {
        !valid_id(&e.event_id)
            || e.song_id.is_empty()
            || e.song_id.len() > 256
            || e.at < 946684800000
            || e.at > now + 600000
            || e.position_ms < 0
            || e.duration_ms < 0
            || e.position_ms > 7 * 86400000
            || e.duration_ms > 7 * 86400000
    }) {
        return Err(bad("invalid playback event"));
    }
    let mut db = hub.store.db.lock().unwrap();
    let tx = db.transaction()?;
    for e in &body.events {
        let previous:Option<(String,i64,bool,i64,i64)>=tx.query_row("SELECT song_id,at,completed,position_ms,duration_ms FROM plays WHERE username=?1 AND device=?2 AND event_id=?3",params![a.u,body.device,e.event_id],|r|Ok((r.get(0)?,r.get(1)?,r.get(2)?,r.get(3)?,r.get(4)?))).optional()?;
        if previous.is_some_and(|old| {
            old != (
                e.song_id.clone(),
                e.at,
                e.completed,
                e.position_ms,
                e.duration_ms,
            )
        }) {
            return Err(bad("event id reused with different content"));
        }
        tx.execute(
            "INSERT OR IGNORE INTO plays VALUES(?1,?2,?3,?4,?5,?6,?7,?8)",
            params![
                a.u,
                body.device,
                e.event_id,
                e.song_id,
                e.at,
                e.completed,
                e.position_ms,
                e.duration_ms
            ],
        )?;
    }
    tx.execute(
        "UPDATE devices SET last_seen=?3 WHERE username=?1 AND id=?2",
        params![a.u, body.device, now],
    )?;
    tx.commit()?;
    Ok(Json(
        json!({"accepted":body.events.iter().map(|e|&e.event_id).collect::<Vec<_>>()}),
    ))
}
#[derive(Deserialize)]
struct DailyQuery {
    device: String,
}
async fn daily(State(hub): State<Hub>, headers: HeaderMap, Query(q): Query<DailyQuery>) -> Reply {
    let a = hub.auth(&headers).await?;
    hub.owns_device(&a.u, &q.device)?;
    let date = hub.date();
    let data=hub.store.get_json("SELECT data FROM daily_mixes WHERE username=?1 AND device=?2 AND playlist_id IS NOT NULL ORDER BY date DESC LIMIT 1",&[&a.u,&q.device])?;
    if data.as_ref().is_none_or(|v| v["date"] != date) {
        hub.kick();
    }
    let mut data =
        data.unwrap_or_else(|| json!({"date":date,"songs":[],"wishes":[],"playlist_id":null}));
    data["stale"] = json!(data["date"] != date);
    Ok(Json(data))
}
async fn wishes(State(hub): State<Hub>, headers: HeaderMap) -> Reply {
    let a = hub.auth(&headers).await?;
    let db = hub.store.db.lock().unwrap();
    let mut stmt =
        db.prepare("SELECT key,track,state,song_id FROM wishes WHERE username=?1 ORDER BY rowid")?;
    let rows = stmt
        .query_map([&a.u], |r| {
            Ok((
                r.get::<_, String>(0)?,
                r.get::<_, String>(1)?,
                r.get::<_, String>(2)?,
                r.get::<_, Option<String>>(3)?,
            ))
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    let mut out = Vec::new();
    for (key, track, state, song) in rows {
        let mut t: Value = serde_json::from_str(&track).map_err(anyhow::Error::from)?;
        t["wish_id"] = json!(URL_SAFE_NO_PAD.encode(key));
        t["state"] = json!(state);
        t["song_id"] = json!(song);
        out.push(t);
    }
    Ok(Json(json!({"wishes":out})))
}
#[derive(Deserialize)]
struct WishUpdate {
    state: String,
    #[serde(default)]
    song_id: Option<String>,
}
async fn update_wish(
    State(hub): State<Hub>,
    headers: HeaderMap,
    Path(id): Path<String>,
    Json(body): Json<WishUpdate>,
) -> Reply {
    let a = hub.auth(&headers).await?;
    let key = String::from_utf8(
        URL_SAFE_NO_PAD
            .decode(id)
            .map_err(|_| bad("invalid wish id"))?,
    )
    .map_err(|_| bad("invalid wish id"))?;
    let value = hub
        .store
        .get_json(
            "SELECT track FROM wishes WHERE username=?1 AND key=?2",
            &[&a.u, &key],
        )?
        .ok_or_else(missing)?;
    if !matches!(body.state.as_str(), "pending" | "ignored" | "completed") {
        return Err(bad("invalid wish state"));
    }
    if body.state == "completed" {
        let song = body
            .song_id
            .as_ref()
            .ok_or_else(|| bad("song_id required"))?;
        let v = hub
            .api
            .call(&a, "getSong", vec![("id", song.clone())])
            .await?;
        let track: Track = serde_json::from_value(value).map_err(anyhow::Error::from)?;
        let song: Song = serde_json::from_value(v["song"].clone()).map_err(anyhow::Error::from)?;
        if !track.matches(&song) {
            return Err(bad("song does not match requested recording"));
        }
    }
    hub.store.db.lock().unwrap().execute(
        "UPDATE wishes SET state=?3,song_id=?4 WHERE username=?1 AND key=?2",
        params![a.u, key, body.state, body.song_id],
    )?;
    hub.kick();
    Ok(Json(json!({"state":body.state})))
}

#[derive(Clone, Serialize, Deserialize)]
struct ImportItem {
    #[serde(flatten)]
    track: Track,
    #[serde(default)]
    song_id: Option<String>,
}
#[derive(Clone, Serialize, Deserialize)]
struct Import {
    playlist_id: String,
    request_id: String,
    tracks: Vec<ImportItem>,
    #[serde(default)]
    expected: Vec<String>,
    #[serde(default)]
    desired: Option<Vec<String>>,
    #[serde(default)]
    conflict: bool,
}
async fn imports(
    State(hub): State<Hub>,
    headers: HeaderMap,
    Json(mut body): Json<Import>,
) -> Reply {
    let a = hub.auth(&headers).await?;
    if body.tracks.len() > 5000
        || body.playlist_id.is_empty()
        || body.request_id.is_empty()
        || body.request_id.len() > 128
        || body.tracks.iter().any(|i| !i.track.valid())
    {
        return Err(bad("invalid import"));
    }
    let _guard = hub.work.lock().await;
    let playlist = hub.api.playlist(&a, &body.playlist_id).await?;
    if playlist["owner"] != a.u {
        return Err(missing());
    }
    // A lost response may be retried after the background worker has already filled tracks.
    let old = hub.store.get_json(
        "SELECT data FROM imports WHERE username=?1 AND playlist_id=?2",
        &[&a.u, &body.playlist_id],
    )?;
    if old
        .as_ref()
        .is_some_and(|v| v["request_id"] == body.request_id)
    {
        return Ok(Json(json!({"saved":true})));
    }
    let selected: Vec<_> = body
        .tracks
        .iter()
        .filter_map(|i| i.song_id.clone())
        .collect();
    if subsonic::ids(&playlist) != selected {
        return Err(conflict());
    }
    body.expected = selected;
    body.desired = None;
    body.conflict = false;
    let mut db = hub.store.db.lock().unwrap();
    let tx = db.transaction()?;
    for item in &body.tracks {
        if item.song_id.is_none() {
            tx.execute(
                "INSERT OR IGNORE INTO wishes(username,key,track,state) VALUES(?1,?2,?3,'pending')",
                params![
                    a.u,
                    item.track.key(),
                    serde_json::to_string(&item.track).map_err(anyhow::Error::from)?
                ],
            )?;
        }
    }
    tx.execute("INSERT INTO imports VALUES(?1,?2,?3) ON CONFLICT(username,playlist_id) DO UPDATE SET data=excluded.data",params![a.u,body.playlist_id,serde_json::to_string(&body).map_err(anyhow::Error::from)?])?;
    tx.commit()?;
    Ok(Json(json!({"saved":true})))
}
async fn refresh(State(hub): State<Hub>, headers: HeaderMap) -> Reply {
    hub.auth(&headers).await?;
    hub.kick();
    Ok(Json(json!({"queued":true})))
}

impl Hub {
    async fn run(&self) -> Result<()> {
        let Ok(_guard) = self.work.try_lock() else {
            return Ok(());
        };
        for a in self.store.accounts()? {
            if self.api.call(&a, "ping", vec![]).await.is_err() {
                eprintln!("account background authorization expired; sign in from phone again");
                continue;
            }
            if let Err(e) = self.migrate_wishes(&a).await {
                eprintln!("legacy task migration failed: {e}");
            }
            if let Err(e) = self.reconcile(&a).await {
                eprintln!("reconciliation failed: {e}");
            }
            let devices: Vec<(String, String)> = {
                let db = self.store.db.lock().unwrap();
                let mut stmt =
                    db.prepare("SELECT id,name FROM devices WHERE username=?1 AND last_seen>?2")?;
                stmt.query_map(
                    params![
                        a.u,
                        chrono::Utc::now().timestamp_millis() - 30 * 86400000i64
                    ],
                    |r| Ok((r.get(0)?, r.get(1)?)),
                )?
                .collect::<rusqlite::Result<_>>()?
            };
            for (id, name) in devices {
                if let Err(e) = self.mix(&a, &id, &name).await {
                    eprintln!("daily generation failed: {e}");
                }
            }
            if let Err(e) = self.cleanup(&a).await {
                eprintln!("playlist cleanup failed: {e}");
            }
        }
        Ok(())
    }

    async fn migrate_wishes(&self, a: &Auth) -> Result<()> {
        // Old comments lack original positions. Preserve their tasks; ordered auto-fill starts
        // after one phone refresh submits the full import. Never read another owner's tasks.
        for p in self.api.playlists(a).await? {
            if p["owner"] != a.u {
                continue;
            }
            let Some(comment) = p["comment"].as_str() else {
                continue;
            };
            let text = comment
                .strip_prefix("Tempus external playlist v1\n")
                .or_else(|| comment.strip_prefix("Tempus daily mix v1\n"));
            let Some(text) = text else {
                continue;
            };
            if text.len() > 1_000_000 {
                continue;
            }
            let Ok(v) = serde_json::from_str::<Value>(text) else {
                continue;
            };
            if v["version"] != 1 || v["complete"] != true {
                continue;
            }
            for row in v["missing"].as_array().into_iter().flatten() {
                let mut row = row.clone();
                if row.get("provider").is_none() {
                    row["provider"] = v["provider"].clone();
                }
                if let Ok(t) = serde_json::from_value::<Track>(row)
                    && t.valid()
                {
                    self.store.insert_wish(&a.u, &t)?;
                }
            }
        }
        Ok(())
    }

    async fn mix(&self, a: &Auth, device: &str, name: &str) -> Result<()> {
        let date = self.date();
        let saved = self.store.get_json(
            "SELECT data FROM daily_mixes WHERE username=?1 AND device=?2 AND date=?3",
            &[&a.u, &device, &date],
        )?;
        if saved.as_ref().is_some_and(|v| v["playlist_id"].is_string()) {
            return Ok(());
        }
        let mut data = if let Some(data) = saved {
            data
        } else {
            let p = self
                .store
                .profile(&a.u, device, chrono::Utc::now().timestamp_millis())?;
            let mut pool = self
                .api
                .songs(
                    a,
                    "getRandomSongs",
                    vec![("size", "500".into())],
                    "/randomSongs/song",
                )
                .await?;
            let mut own: Vec<_> = p.last.iter().collect();
            own.sort_by_key(|(_, at)| std::cmp::Reverse(**at));
            // Fetch seeds from this device, never account-level stars/playCount.
            let mut selected: Vec<String> =
                own.iter().take(40).map(|(id, _)| (*id).clone()).collect();
            let mut frequent: Vec<_> = p.counts.iter().collect();
            frequent.sort_by_key(|(_, n)| std::cmp::Reverse(**n));
            selected.extend(frequent.iter().take(20).map(|(id, _)| (*id).clone()));
            let mut seen: HashSet<_> = pool.iter().map(|s| s.id.clone()).collect();
            for id in selected {
                if seen.insert(id.clone())
                    && let Ok(v) = self.api.call(a, "getSong", vec![("id", id)]).await
                    && let Ok(song) = serde_json::from_value(v["song"].clone())
                {
                    pool.push(song);
                }
            }
            pool.shuffle(&mut rand::rng());
            let seeds: Vec<Song> = model::seeds(&pool, &p).into_iter().cloned().collect();
            let mut related = Vec::new();
            for seed in &seeds {
                // Similarity providers can stall independently of the local library.
                let mut rows = tokio::time::timeout(
                    std::time::Duration::from_secs(3),
                    self.api.songs(
                        a,
                        "getSimilarSongs2",
                        vec![("id", seed.id.clone()), ("count", "20".into())],
                        "/similarSongs2/song",
                    ),
                )
                .await
                .ok()
                .and_then(Result::ok)
                .unwrap_or_default();
                if rows.len() < 5 {
                    let artist = pool
                        .iter()
                        .filter(|s| {
                            model::artist_names(&s.artist)
                                .intersection(&model::artist_names(&seed.artist))
                                .next()
                                .is_some()
                        })
                        .take(10)
                        .cloned()
                        .collect::<Vec<_>>();
                    let genre = pool
                        .iter()
                        .filter(|s| seed.genre.is_some() && s.genre == seed.genre)
                        .take(10)
                        .cloned()
                        .collect::<Vec<_>>();
                    let era = pool
                        .iter()
                        .filter(|s| {
                            seed.year.is_some_and(|y| y >= 1900)
                                && s.year.map(|y| y / 10) == seed.year.map(|y| y / 10)
                        })
                        .take(10)
                        .cloned()
                        .collect::<Vec<_>>();
                    for i in 0..10 {
                        for list in [&artist, &genre, &era] {
                            if let Some(s) = list.get(i) {
                                rows.push(s.clone());
                            }
                        }
                    }
                }
                related.push(rows);
            }
            let mut songs = model::assemble(&seeds, &related, &pool, &p);
            songs.shuffle(&mut rand::rng());
            if songs.is_empty() {
                return Ok(());
            }
            let wishes = self.discover(a, &p, &pool).await?;
            let data = json!({"date":date,"songs":songs,"wishes":wishes,"playlist_id":null,"name":format!("每日推荐 · {} · {} · {}",&date[5..],name,device.chars().take(8).collect::<String>())});
            // Persist the exact plan before any remote write, so retries don't change its tracks.
            self.store.db.lock().unwrap().execute(
                "INSERT OR IGNORE INTO daily_mixes VALUES(?1,?2,?3,NULL,?4)",
                params![a.u, device, date, serde_json::to_string(&data)?],
            )?;
            data
        };
        let name = data["name"].as_str().unwrap();
        let playlists = self.api.playlists(a).await?;
        let owned: Vec<_> = playlists
            .iter()
            .filter(|v| v["owner"] == a.u && v["name"] == name)
            .collect();
        ensure!(
            owned.len() <= 1,
            "multiple daily draft playlists require review"
        );
        let id = if let Some(v) = owned.first() {
            v["id"].as_str().unwrap().to_owned()
        } else {
            let v = self
                .api
                .call(a, "createPlaylist", vec![("name", name.into())])
                .await?;
            v["playlist"]["id"]
                .as_str()
                .ok_or_else(|| anyhow::anyhow!("new playlist id missing"))?
                .to_owned()
        };
        self.api
            .call(
                a,
                "updatePlaylist",
                vec![
                    ("playlistId", id.clone()),
                    ("public", "false".into()),
                    (
                        "comment",
                        format!(
                            "Tempus hub daily v1\n{}",
                            json!({"date":date,"device":device})
                        ),
                    ),
                ],
            )
            .await?;
        let songs: Vec<Song> = serde_json::from_value(data["songs"].clone())?;
        let ids: Vec<_> = songs.iter().map(|s| s.id.clone()).collect();
        self.api.replace(a, &id, &ids).await?;
        ensure!(
            subsonic::ids(&self.api.playlist(a, &id).await?) == ids,
            "daily playlist verification failed"
        );
        data["playlist_id"] = json!(id);
        self.store.db.lock().unwrap().execute("UPDATE daily_mixes SET playlist_id=?4,data=?5 WHERE username=?1 AND device=?2 AND date=?3",params![a.u,device,date,id,serde_json::to_string(&data)?])?;
        Ok(())
    }

    async fn discover(&self, a: &Auth, p: &model::Profile, pool: &[Song]) -> Result<Vec<Track>> {
        let cached = self
            .store
            .json_rows("SELECT data FROM chart_cache ORDER BY id", &[])?;
        let mut tracks = Vec::<Track>::new();
        for v in cached {
            tracks.extend(serde_json::from_value::<Vec<Track>>(v)?);
        }
        let liked: HashSet<_> = pool
            .iter()
            .filter(|s| p.counts.contains_key(&s.id))
            .flat_map(|s| model::artist_names(&s.artist))
            .collect();
        let known: HashSet<_> = pool
            .iter()
            .flat_map(|s| model::artist_names(&s.artist))
            .collect();
        tracks.sort_by_key(|t| {
            if t.artists
                .iter()
                .any(|a| liked.contains(&model::normalize(a)))
            {
                0
            } else {
                1
            }
        });
        let mut found = Vec::new();
        let mut seen = HashSet::new();
        let mut checks = 0;
        for t in tracks {
            if found.len() >= 5 || checks >= 25 {
                break;
            }
            if !seen.insert(t.key())
                || !t
                    .artists
                    .iter()
                    .any(|a| known.contains(&model::normalize(a)))
            {
                continue;
            }
            let exists: bool = self.store.db.lock().unwrap().query_row(
                "SELECT EXISTS(SELECT 1 FROM wishes WHERE username=?1 AND key=?2)",
                params![a.u, t.key()],
                |r| r.get(0),
            )?;
            if exists {
                continue;
            }
            checks += 1;
            let Ok(songs) = self.api.search(a, &t.title).await else {
                continue;
            };
            if songs.iter().any(|s| t.matches(s)) {
                continue;
            }
            self.store.insert_wish(&a.u, &t)?;
            found.push(t);
        }
        Ok(found)
    }

    async fn reconcile(&self, a: &Auth) -> Result<()> {
        let pending = self.store.json_rows(
            "SELECT track FROM wishes WHERE username=?1 AND state IN ('pending','ambiguous')",
            &[&a.u],
        )?;
        for v in pending {
            let t: Track = serde_json::from_value(v)?;
            let songs = self.api.search(a, &t.title).await?;
            let mut seen = HashSet::new();
            let matches: Vec<_> = songs
                .iter()
                .filter(|s| t.matches(s) && seen.insert(s.id.clone()))
                .collect();
            let (state, id) = match matches.as_slice() {
                [s] => ("completed", Some(s.id.clone())),
                [] => ("pending", None),
                _ => ("ambiguous", None),
            };
            self.store.db.lock().unwrap().execute(
                "UPDATE wishes SET state=?3,song_id=?4 WHERE username=?1 AND key=?2",
                params![a.u, t.key(), state, id],
            )?;
        }
        let imports = self
            .store
            .json_rows("SELECT data FROM imports WHERE username=?1", &[&a.u])?;
        let owned: HashSet<String> = self
            .api
            .playlists(a)
            .await?
            .into_iter()
            .filter(|p| p["owner"] == a.u)
            .filter_map(|p| p["id"].as_str().map(str::to_owned))
            .collect();
        for v in imports {
            let mut import: Import = serde_json::from_value(v)?;
            if !owned.contains(&import.playlist_id) {
                self.store.db.lock().unwrap().execute(
                    "DELETE FROM imports WHERE username=?1 AND playlist_id=?2",
                    params![a.u, import.playlist_id],
                )?;
                continue;
            }
            if import.conflict {
                continue;
            }
            let mut ids = Vec::new();
            for item in &mut import.tracks {
                if item.song_id.is_none() {
                    item.song_id=self.store.db.lock().unwrap().query_row("SELECT song_id FROM wishes WHERE username=?1 AND key=?2 AND state='completed'",params![a.u,item.track.key()],|r|r.get::<_,Option<String>>(0)).optional()?.flatten();
                }
                if let Some(id) = &item.song_id {
                    ids.push(id.clone());
                }
            }
            let current = self.api.playlist(a, &import.playlist_id).await?;
            if current["owner"] != a.u {
                continue;
            }
            let live = subsonic::ids(&current);
            // A pending write may already have succeeded before a lost response/crash.
            if live != import.expected && import.desired.as_ref() != Some(&live) {
                import.conflict = true;
                self.save_import(a, &import)?;
                continue;
            }
            if ids == import.expected && import.desired.is_none() {
                continue;
            }
            import.desired = Some(ids.clone());
            self.save_import(a, &import)?;
            if live != ids {
                self.api.replace(a, &import.playlist_id, &ids).await?;
            }
            ensure!(
                subsonic::ids(&self.api.playlist(a, &import.playlist_id).await?) == ids,
                "import write verification failed"
            );
            // Keep old clients' comment-based missing list accurate during the transition.
            if let Some(comment) = current["comment"]
                .as_str()
                .and_then(|c| c.strip_prefix("Tempus external playlist v1\n"))
                && let Ok(mut binding) = serde_json::from_str::<Value>(comment)
            {
                binding["missing"] = json!(
                    import
                        .tracks
                        .iter()
                        .filter(|i| i.song_id.is_none())
                        .map(|i| &i.track)
                        .collect::<Vec<_>>()
                );
                self.api
                    .call(
                        a,
                        "updatePlaylist",
                        vec![
                            ("playlistId", import.playlist_id.clone()),
                            ("comment", format!("Tempus external playlist v1\n{binding}")),
                        ],
                    )
                    .await?;
            }
            import.expected = ids;
            import.desired = None;
            self.save_import(a, &import)?;
        }
        Ok(())
    }
    fn save_import(&self, a: &Auth, import: &Import) -> Result<()> {
        self.store.db.lock().unwrap().execute(
            "UPDATE imports SET data=?3 WHERE username=?1 AND playlist_id=?2",
            params![a.u, import.playlist_id, serde_json::to_string(import)?],
        )?;
        Ok(())
    }
    async fn cleanup(&self, a: &Auth) -> Result<()> {
        let cutoff = (chrono::NaiveDate::parse_from_str(&self.date(), "%Y-%m-%d")?
            - chrono::Duration::days(6))
        .to_string();
        let old: Vec<(String, String, Option<String>)> = {
            let db = self.store.db.lock().unwrap();
            let mut stmt = db.prepare(
                "SELECT device,date,playlist_id FROM daily_mixes WHERE username=?1 AND date<?2",
            )?;
            stmt.query_map(params![a.u, cutoff], |r| {
                Ok((r.get(0)?, r.get(1)?, r.get(2)?))
            })?
            .collect::<rusqlite::Result<_>>()?
        };
        for (device, date, id) in old {
            if let Some(id) = id {
                let playlists = self.api.playlists(a).await?;
                if let Some(p) = playlists.iter().find(|p| p["id"] == id) {
                    ensure!(
                        p["owner"] == a.u,
                        "refusing to delete another owner's playlist"
                    );
                    self.api.call(a, "deletePlaylist", vec![("id", id)]).await?;
                }
            }
            self.store.db.lock().unwrap().execute(
                "DELETE FROM daily_mixes WHERE username=?1 AND device=?2 AND date=?3",
                params![a.u, device, date],
            )?;
        }
        Ok(())
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    let state = std::env::var("HUB_STATE_DIR").unwrap_or_else(|_| "state".into());
    let args: Vec<String> = std::env::args().collect();
    if args.get(1).map(String::as_str) == Some("export-client") {
        ensure!(
            args.len() == 4,
            "usage: tempus-hub export-client USER HTTPS_URL"
        );
        let store = Store::open(std::path::Path::new(&state))?;
        let auth = store
            .accounts()?
            .into_iter()
            .find(|a| a.u == args[2])
            .ok_or_else(|| anyhow::anyhow!("account not enrolled; open phone app first"))?;
        // Admin-only stdout: redirect directly into a mode-600 file, never an access log.
        println!(
            "{}",
            json!({"url":args[3],"u":auth.u,"t":auth.t,"s":auth.s,"ssh_host":"mini_server"})
        );
        return Ok(());
    }
    let hub = Hub {
        store: Arc::new(Store::open(std::path::Path::new(&state))?),
        api: Subsonic::new(
            &std::env::var("NAVIDROME_URL").unwrap_or_else(|_| "http://127.0.0.1:4533".into()),
        )?,
        work: Arc::new(tokio::sync::Mutex::new(())),
        zone: std::env::var("HUB_TIMEZONE")
            .unwrap_or_else(|_| "Asia/Singapore".into())
            .parse()?,
    };
    hub.store.integrity()?;
    let worker = hub.clone();
    tokio::spawn(async move {
        let mut ticks = tokio::time::interval(std::time::Duration::from_secs(60));
        let mut last_charts = 0i64;
        loop {
            ticks.tick().await;
            if std::env::var("HUB_CHARTS_ENABLED").as_deref() != Ok("false")
                && chrono::Utc::now().timestamp() - last_charts >= 21600
            {
                if let Err(e) = charts::refresh(&worker.store, &worker.api.http).await {
                    eprintln!("charts refresh failed: {e}");
                }
                last_charts = chrono::Utc::now().timestamp();
            }
            // Catch up on restart after 05:00. Explicit phone requests can generate earlier.
            if chrono::Utc::now().with_timezone(&worker.zone).hour() >= 5
                && let Err(e) = worker.run().await
            {
                eprintln!("scheduled job failed: {e}");
            }
        }
    });
    let app = Router::new()
        .route(
            "/hub/v1/health",
            get(|| async { Json(json!({"service":"tempus-hub","version":1})) }),
        )
        .route("/hub/v1/devices", post(device))
        .route("/hub/v1/plays", post(plays))
        .route("/hub/v1/daily", get(daily))
        .route("/hub/v1/wishes", get(wishes))
        .route("/hub/v1/wishes/{id}", post(update_wish))
        .route("/hub/v1/imports", post(imports))
        .route("/hub/v1/refresh", post(refresh))
        .layer(DefaultBodyLimit::max(4 * 1024 * 1024))
        .with_state(hub);
    let address = std::env::var("HUB_LISTEN").unwrap_or_else(|_| "127.0.0.1:4534".into());
    let listener = tokio::net::TcpListener::bind(&address).await?;
    eprintln!("tempus-hub listening on {address}");
    axum::serve(listener, app)
        .with_graceful_shutdown(async {
            let _ = tokio::signal::ctrl_c().await;
        })
        .await?;
    Ok(())
}
