//! Atomic cache publication recovery and PVGD big-endian depth checkpoint I/O.

use std::fs::{self, File};
use std::io::{self, BufReader, BufWriter, Read, Write};
use std::path::{Path, PathBuf};

pub struct ImagePublication {
    pub id: String,
    pub work: PathBuf,
    pub target: PathBuf,
    pub journal: PathBuf,
    pub done: PathBuf,
    pub backups: Vec<(PathBuf, PathBuf)>,
}

impl ImagePublication {
    pub fn commit(&self) -> Result<(), String> {
        let mut preserved = 0;
        let result = (|| {
            for (old, backup) in &self.backups {
                fs::rename(old, backup)
                    .map_err(|error| format!("Cannot preserve previous image cache: {error}"))?;
                preserved += 1;
            }
            fs::rename(&self.work, &self.target)
                .map_err(|error| format!("Cannot commit generated image: {error}"))
        })();
        if let Err(mut error) = result {
            if let Err(restore) = restore_backups(&self.backups[..preserved]) {
                error.push_str("; recovery journal retained: ");
                error.push_str(&restore.to_string());
            } else {
                let _ = fs::remove_file(&self.journal);
            }
            return Err(error);
        }
        let _ = File::create(&self.done).and_then(|mut file| {
            file.write_all(self.id.as_bytes())?;
            file.sync_all()
        });
        self.clean_committed();
        Ok(())
    }

    pub fn recover(&self) -> io::Result<()> {
        let published =
            fs::read_to_string(self.target.join(".pvg-publication")).ok().as_deref() == Some(self.id.as_str());
        if self.done.is_file() || published {
            self.clean_committed();
        } else {
            restore_backups(&self.backups)?;
            let _ = fs::remove_file(&self.journal);
        }
        Ok(())
    }

    fn clean_committed(&self) {
        for (_, backup) in &self.backups {
            if let Ok(metadata) = fs::symlink_metadata(backup) {
                let _ = if metadata.is_dir() {
                    fs::remove_dir_all(backup)
                } else {
                    fs::remove_file(backup)
                };
            }
        }
        if self.backups.iter().all(|(_, backup)| !backup.exists()) && fs::remove_file(&self.journal).is_ok() {
            let _ = fs::remove_file(&self.done);
        }
    }
}

fn restore_backups(backups: &[(PathBuf, PathBuf)]) -> io::Result<()> {
    let mut failure = None;
    for (old, backup) in backups.iter().rev() {
        if !backup.exists() {
            continue;
        }
        if old.exists() {
            failure.get_or_insert(io::Error::new(
                io::ErrorKind::AlreadyExists,
                "Recovery conflict; both copies retained",
            ));
            continue;
        }
        let result = old
            .parent()
            .map_or(Ok(()), fs::create_dir_all)
            .and_then(|_| fs::rename(backup, old));
        if let Err(error) = result {
            failure.get_or_insert(error);
        }
    }
    failure.map_or(Ok(()), Err)
}

const DEPTH_MAGIC: u32 = 0x50564744;
const MAX_DEPTH_PIXELS: usize = 16_777_216;

pub fn write_depth_checkpoint(path: &Path, width: usize, height: usize, data: &[f32]) -> io::Result<()> {
    let count = depth_count(width, height)?;
    if count != data.len() || data.iter().any(|value| !value.is_finite()) {
        return Err(invalid_checkpoint());
    }
    let mut output = BufWriter::new(File::create(path)?);
    for value in [DEPTH_MAGIC, width as u32, height as u32] {
        output.write_all(&value.to_be_bytes())?;
    }
    for value in data {
        output.write_all(&value.to_bits().to_be_bytes())?;
    }
    output.flush()?;
    output.get_ref().sync_all()
}

pub fn read_depth_checkpoint(path: &Path) -> io::Result<(usize, usize, Vec<f32>)> {
    let file = File::open(path)?;
    let length = file.metadata()?.len();
    let mut input = BufReader::new(file);
    let mut read_word = || {
        let mut bytes = [0; 4];
        input.read_exact(&mut bytes)?;
        Ok::<u32, io::Error>(u32::from_be_bytes(bytes))
    };
    if read_word()? != DEPTH_MAGIC {
        return Err(invalid_checkpoint());
    }
    let (width, height) = (read_word()? as usize, read_word()? as usize);
    let count = depth_count(width, height)?;
    if length != 12 + count as u64 * 4 {
        return Err(invalid_checkpoint());
    }
    let mut values = Vec::new();
    values.try_reserve_exact(count).map_err(io::Error::other)?;
    for _ in 0..count {
        let value = f32::from_bits(read_word()?);
        if !value.is_finite() {
            return Err(invalid_checkpoint());
        }
        values.push(value);
    }
    Ok((width, height, values))
}

fn depth_count(width: usize, height: usize) -> io::Result<usize> {
    width
        .checked_mul(height)
        .filter(|size| (1..=MAX_DEPTH_PIXELS).contains(size))
        .ok_or_else(invalid_checkpoint)
}

fn invalid_checkpoint() -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, "Invalid video depth checkpoint")
}

pub fn reclaim_frame_jpegs(root: &Path, first: i32, end: i32) {
    for frame in first..end {
        let _ = fs::remove_file(root.join(format!("left_{frame:06}.jpg")));
        let _ = fs::remove_file(root.join(format!("right_{frame:06}.jpg")));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::atomic::{AtomicUsize, Ordering};

    fn private_dir() -> PathBuf {
        static NEXT: AtomicUsize = AtomicUsize::new(0);
        let path = std::env::temp_dir().join(format!(
            "pvg-core-cache-{}-{}",
            std::process::id(),
            NEXT.fetch_add(1, Ordering::Relaxed)
        ));
        fs::create_dir(&path).unwrap();
        path
    }

    #[test]
    fn depth_checkpoint_round_trip_and_publication_rollback() {
        let root = private_dir();
        let file = root.join("temporal-depth.bin");
        write_depth_checkpoint(&file, 2, 1, &[0.5, -0.0]).unwrap();
        assert_eq!(
            fs::read(&file).unwrap(),
            b"PVGD\x00\x00\x00\x02\x00\x00\x00\x01\x3f\x00\x00\x00\x80\x00\x00\x00"
        );
        let (w, h, values) = read_depth_checkpoint(&file).unwrap();
        assert_eq!((w, h), (2, 1));
        assert_eq!(
            values.iter().map(|v| v.to_bits()).collect::<Vec<_>>(),
            [0.5_f32.to_bits(), (-0.0_f32).to_bits()]
        );

        let publication = ImagePublication {
            id: "test".into(),
            work: root.join("missing-work"),
            target: root.join("old"),
            journal: root.join("transaction.properties"),
            done: root.join("transaction.done"),
            backups: vec![(root.join("old"), root.join("backup"))],
        };
        fs::write(&publication.target, b"old payload").unwrap();
        fs::write(&publication.journal, b"journal").unwrap();
        assert!(publication.commit().is_err());
        assert_eq!(fs::read(&publication.target).unwrap(), b"old payload");
        assert!(!publication.journal.exists());
        fs::remove_dir_all(root).unwrap();
    }
}
