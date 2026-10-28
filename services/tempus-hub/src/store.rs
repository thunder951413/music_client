use crate::model::{Auth, Profile};
use anyhow::{Context, Result};
use chacha20poly1305::{
    ChaCha20Poly1305, KeyInit,
    aead::{Aead, Payload},
};
use rand::RngCore;
use rusqlite::{Connection, params};
use serde_json::Value;
#[cfg(unix)]
use std::os::unix::fs::{OpenOptionsExt, PermissionsExt};
use std::{fs, path::Path, sync::Mutex};

pub struct Store {
    pub db: Mutex<Connection>,
    key: [u8; 32],
}
impl Store {
    pub fn open(dir: &Path) -> Result<Self> {
        fs::create_dir_all(dir)?;
        #[cfg(unix)]
        fs::set_permissions(dir, fs::Permissions::from_mode(0o700))?;
        let path = dir.join("master.key");
        if !path.exists() {
            let mut key = [0u8; 32];
            rand::rng().fill_bytes(&mut key);
            let mut options = fs::OpenOptions::new();
            options.write(true).create_new(true);
            #[cfg(unix)]
            options.mode(0o600);
            use std::io::Write;
            options.open(&path)?.write_all(&key)?;
        }
        let key: [u8; 32] = fs::read(path)?
            .try_into()
            .map_err(|_| anyhow::anyhow!("invalid master.key"))?;
        let path = dir.join("hub.sqlite");
        let conn = Connection::open(&path)?;
        #[cfg(unix)]
        fs::set_permissions(path, fs::Permissions::from_mode(0o600))?;
        conn.busy_timeout(std::time::Duration::from_secs(5))?;
        conn.execute_batch(include_str!("../migrations/001.sql"))?;
        Ok(Self {
            db: Mutex::new(conn),
            key,
        })
    }
    pub fn save_auth(&self, a: &Auth) -> Result<()> {
        let mut nonce = [0u8; 12];
        rand::rng().fill_bytes(&mut nonce);
        let cipher = ChaCha20Poly1305::new((&self.key).into());
        let mut bytes = nonce.to_vec();
        bytes.extend(
            cipher
                .encrypt(
                    (&nonce).into(),
                    Payload {
                        msg: &serde_json::to_vec(a)?,
                        aad: a.u.as_bytes(),
                    },
                )
                .map_err(|_| anyhow::anyhow!("credential encryption failed"))?,
        );
        self.db.lock().unwrap().execute("INSERT INTO accounts(username,credential) VALUES(?1,?2) ON CONFLICT(username) DO UPDATE SET credential=excluded.credential",params![a.u,bytes])?;
        Ok(())
    }
    pub fn accounts(&self) -> Result<Vec<Auth>> {
        let db = self.db.lock().unwrap();
        let mut stmt = db.prepare("SELECT username,credential FROM accounts")?;
        let rows = stmt
            .query_map([], |r| {
                Ok((r.get::<_, String>(0)?, r.get::<_, Vec<u8>>(1)?))
            })?
            .collect::<rusqlite::Result<Vec<_>>>()?;
        rows.into_iter()
            .map(|(u, bytes)| {
                anyhow::ensure!(bytes.len() >= 12, "invalid encrypted credential");
                let cipher = ChaCha20Poly1305::new((&self.key).into());
                let plain = cipher
                    .decrypt(
                        bytes[..12].into(),
                        Payload {
                            msg: &bytes[12..],
                            aad: u.as_bytes(),
                        },
                    )
                    .map_err(|_| anyhow::anyhow!("credential decryption failed"))?;
                Ok(serde_json::from_slice(&plain)?)
            })
            .collect()
    }
    pub fn profile(&self, user: &str, device: &str, now: i64) -> Result<Profile> {
        let db = self.db.lock().unwrap();
        let mut stmt=db.prepare("SELECT song_id,at,completed FROM plays WHERE username=?1 AND device=?2 ORDER BY at DESC")?;
        let mut p = Profile::default();
        for row in stmt.query_map(params![user, device], |r| {
            Ok((
                r.get::<_, String>(0)?,
                r.get::<_, i64>(1)?,
                r.get::<_, bool>(2)?,
            ))
        })? {
            let (id, at, completed) = row?;
            p.heard.insert(id.clone());
            if at >= now - 2 * 86400000 {
                p.recent.insert(id.clone());
            }
            if completed {
                p.last.entry(id.clone()).or_insert(at);
            }
            if at >= now - 30 * 86400000 {
                *if completed {
                    p.counts.entry(id)
                } else {
                    p.skipped.entry(id)
                }
                .or_default() += 1;
            }
        }
        Ok(p)
    }
    pub fn json_rows(&self, sql: &str, args: &[&dyn rusqlite::ToSql]) -> Result<Vec<Value>> {
        let db = self.db.lock().unwrap();
        let mut stmt = db.prepare(sql)?;
        stmt.query_map(args, |r| r.get::<_, String>(0))?
            .map(|r| Ok(serde_json::from_str(&r?)?))
            .collect()
    }
    pub fn get_json(&self, sql: &str, args: &[&dyn rusqlite::ToSql]) -> Result<Option<Value>> {
        Ok(self.json_rows(sql, args)?.into_iter().next())
    }
    pub fn insert_wish(&self, user: &str, track: &crate::model::Track) -> Result<()> {
        anyhow::ensure!(track.valid(), "invalid wish");
        self.db.lock().unwrap().execute(
            "INSERT OR IGNORE INTO wishes(username,key,track,state) VALUES(?1,?2,?3,'pending')",
            params![user, track.key(), serde_json::to_string(track)?],
        )?;
        Ok(())
    }
    pub fn integrity(&self) -> Result<()> {
        let result: String = self
            .db
            .lock()
            .unwrap()
            .query_row("PRAGMA integrity_check", [], |r| r.get(0))
            .context("database integrity check")?;
        anyhow::ensure!(result == "ok", "database integrity check failed");
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn credentials_survive_restart_and_are_encrypted() {
        let d = tempfile::tempdir().unwrap();
        let a = Auth {
            u: "alice".into(),
            t: "private-token-value".into(),
            s: "salt".into(),
        };
        {
            let s = Store::open(d.path()).unwrap();
            s.save_auth(&a).unwrap();
            s.integrity().unwrap();
        }
        assert_eq!(Store::open(d.path()).unwrap().accounts().unwrap(), vec![a]);
        assert!(
            !fs::read(d.path().join("hub.sqlite"))
                .unwrap()
                .windows(19)
                .any(|w| w == b"private-token-value")
        );
    }
    #[test]
    fn histories_isolate_accounts_devices_and_repeated_plays() {
        let d = tempfile::tempdir().unwrap();
        let s = Store::open(d.path()).unwrap();
        let db = s.db.lock().unwrap();
        for (u, device, event) in [
            ("a", "phone", "1"),
            ("a", "phone", "2"),
            ("a", "tablet", "3"),
            ("b", "phone", "4"),
        ] {
            db.execute(
                "INSERT INTO plays VALUES(?1,?2,?3,'song',1000,1,100,200)",
                params![u, device, event],
            )
            .unwrap();
        }
        drop(db);
        assert_eq!(s.profile("a", "phone", 2000).unwrap().counts["song"], 2);
        assert_eq!(s.profile("a", "tablet", 2000).unwrap().counts["song"], 1);
        assert!(s.profile("b", "tablet", 2000).unwrap().heard.is_empty());
    }
}
