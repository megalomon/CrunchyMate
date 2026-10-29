use crate::error::{CoreError, CoreResult};
use crate::model::{UpdateChannel, UpdateInfo};

/// Quelle für den neuesten veröffentlichten Stand eines Kanals. `None` bedeutet, dass der
/// Kanal noch nichts veröffentlicht hat.
#[async_trait::async_trait]
pub trait UpdateSource: Send + Sync {
    async fn latest(&self, channel: UpdateChannel) -> CoreResult<Option<UpdateInfo>>;
}

/// Zerlegt „MAJOR.MINOR.PATCH“; Zusätze wie „-rc.1“ oder „+Build“ gelten als ungültig,
/// weil Release-Tags und Release-Versionsnamen genau dieses Schema haben.
pub fn parse_version(text: &str) -> Option<(u64, u64, u64)> {
    let mut parts = text.split('.').map(|part| {
        let numeric = !part.is_empty() && part.bytes().all(|b| b.is_ascii_digit());
        numeric.then(|| part.parse::<u64>().ok()).flatten()
    });
    let version = (parts.next()??, parts.next()??, parts.next()??);
    parts.next().is_none().then_some(version)
}

/// Prüft, ob `latest` neuer ist als der installierte Stand `current` (Nightly: Laufnummer,
/// Release: Version).
pub fn is_newer(latest: &UpdateInfo, current: &str) -> CoreResult<bool> {
    match latest {
        UpdateInfo::Nightly { build, .. } => {
            let installed: i64 = current
                .trim()
                .parse()
                .map_err(|_| invalid_current(current))?;
            Ok(*build > installed)
        }
        UpdateInfo::Release { version } => {
            let newest = parse_version(version).ok_or_else(|| invalid_current(version))?;
            let installed =
                parse_version(current.trim()).ok_or_else(|| invalid_current(current))?;
            Ok(newest > installed)
        }
    }
}

fn invalid_current(text: &str) -> CoreError {
    CoreError::Parse {
        detail: format!("Version nicht lesbar: {text}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn nightly(build: i64) -> UpdateInfo {
        UpdateInfo::Nightly {
            build,
            date: "2026-10-30".to_string(),
        }
    }

    fn release(version: &str) -> UpdateInfo {
        UpdateInfo::Release {
            version: version.to_string(),
        }
    }

    #[test]
    fn parses_plain_versions_only() {
        assert_eq!(parse_version("1.2.3"), Some((1, 2, 3)));
        assert_eq!(parse_version("10.0.12"), Some((10, 0, 12)));
        for invalid in [
            "",
            "1.2",
            "1.2.3.4",
            "1.2.x",
            "1.2.3-rc.1",
            "v1.2.3",
            "1..3",
            "+1.2.3",
        ] {
            assert_eq!(parse_version(invalid), None, "{invalid}");
        }
    }

    #[test]
    fn compares_nightly_builds_numerically() {
        assert!(is_newer(&nightly(57), "56").unwrap());
        assert!(!is_newer(&nightly(57), "57").unwrap());
        assert!(!is_newer(&nightly(9), "10").unwrap());
    }

    #[test]
    fn compares_release_versions_semantically() {
        assert!(is_newer(&release("1.10.0"), "1.9.9").unwrap());
        assert!(is_newer(&release("2.0.0"), "1.99.99").unwrap());
        assert!(!is_newer(&release("1.2.0"), "1.2.0").unwrap());
        assert!(!is_newer(&release("1.2.0"), "1.3.0").unwrap());
    }

    #[test]
    fn rejects_unreadable_current_version() {
        assert!(matches!(
            is_newer(&nightly(1), "dev"),
            Err(CoreError::Parse { .. })
        ));
        assert!(matches!(
            is_newer(&release("1.0.0"), "2.0.0-nightly"),
            Err(CoreError::Parse { .. })
        ));
    }
}
