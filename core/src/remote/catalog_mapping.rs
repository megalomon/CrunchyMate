use crate::model::CatalogItem;

/// Bevorzugte Breite (min, max) für hochformatige Poster.
pub const POSTER_TALL_WIDTH: (u32, u32) = (480, 800);
/// Bevorzugte Breite (min, max) für querformatige Poster.
pub const POSTER_WIDE_WIDTH: (u32, u32) = (640, 1280);
/// Inhalts-Locales, die der Katalog liefert; die Standardsprache steht vorn.
pub const SUPPORTED_CONTENT_LOCALES: &[&str] = &[
    "de-DE", "en-US", "es-419", "es-ES", "fr-FR", "it-IT", "pt-BR", "ru-RU", "ar-SA", "hi-IN",
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ImageCandidate {
    pub source: String,
    pub width: u32,
}

/// Ab dieser Altersstufe gilt ein Eintrag als „18+“, auch wenn `is_mature` nicht gesetzt ist.
pub const ADULT_MIN_AGE: u32 = 18;

/// Rohwerte der Altersfreigabe, wie die Quelle sie liefert.
#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct RawMaturity {
    /// `extended_maturity_rating.rating`, z. B. "16".
    pub extended_rating: Option<String>,
    /// `maturity_ratings`, z. B. ["TV-14"].
    pub maturity_ratings: Vec<String>,
    pub is_mature: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RawSeries {
    pub id: String,
    pub title: String,
    pub description: String,
    pub poster_tall: Vec<ImageCandidate>,
    pub poster_wide: Vec<ImageCandidate>,
    pub episode_count: u32,
    pub launch_year: u32,
    pub is_subbed: bool,
    pub is_dubbed: bool,
    pub audio_locales: Vec<String>,
    pub subtitle_locales: Vec<String>,
    pub categories: Vec<String>,
    pub maturity: RawMaturity,
    pub content_descriptors: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LanguageFlags {
    pub dub: bool,
    pub sub: bool,
}

/// Primärsubtag einer Locale in Kleinschreibung („de-DE“ -> „de“).
fn primary_subtag(locale: &str) -> String {
    locale
        .split(['-', '_'])
        .next()
        .unwrap_or_default()
        .trim()
        .to_lowercase()
}

fn contains_language(locales: &[String], primary: &str) -> bool {
    locales.iter().any(|l| primary_subtag(l) == primary)
}

/// Bestimmt Dub/Sub für die Inhaltssprache. Die Titelheuristik gilt nur für Deutsch.
pub fn language_flags(
    audio: &[String],
    subtitles: &[String],
    title: &str,
    content_locale: &str,
) -> LanguageFlags {
    let primary = primary_subtag(content_locale);
    let mut dub = contains_language(audio, &primary);
    let mut sub = contains_language(subtitles, &primary);
    if primary == "de" {
        let lower = title.to_lowercase();
        if !dub && (lower.contains("german dub") || lower.contains("synchro")) {
            dub = true;
        }
        if !sub && (lower.contains("omu") || lower.contains("(ov)")) {
            sub = true;
        }
    }
    LanguageFlags { dub, sub }
}

/// Erster Kandidat im Breitenbereich, sonst der letzte; leer ohne Kandidaten.
pub fn pick_image(candidates: &[ImageCandidate], min_width: u32, max_width: u32) -> String {
    candidates
        .iter()
        .find(|c| (min_width..=max_width).contains(&c.width))
        .or_else(|| candidates.last())
        .map(|c| c.source.clone())
        .unwrap_or_default()
}

/// Erste zusammenhängende Ziffernfolge als Zahl („TV-14“ -> 14, „16“ -> 16).
fn age_from_text(text: &str) -> Option<u32> {
    let digits: String = text
        .chars()
        .skip_while(|c| !c.is_ascii_digit())
        .take_while(char::is_ascii_digit)
        .collect();
    digits.parse().ok()
}

/// Altersstufe: primär die erweiterte Freigabe, sonst `maturity_ratings`, sofern alle
/// Einträge dieselbe Zahl ergeben. Mehrdeutige oder zahllose Angaben (z. B. „TV-MA“) bleiben leer.
pub fn age_rating(maturity: &RawMaturity) -> Option<u32> {
    if let Some(age) = maturity.extended_rating.as_deref().and_then(age_from_text) {
        return Some(age);
    }
    let mut ages = maturity.maturity_ratings.iter().map(|r| age_from_text(r));
    let first = ages.next()??;
    ages.all(|age| age == Some(first)).then_some(first)
}

pub fn is_adult(age: Option<u32>, is_mature: bool) -> bool {
    is_mature || age.is_some_and(|age| age >= ADULT_MIN_AGE)
}

pub fn to_catalog_item(raw: RawSeries, content_locale: &str) -> CatalogItem {
    let flags = language_flags(
        &raw.audio_locales,
        &raw.subtitle_locales,
        &raw.title,
        content_locale,
    );
    let genres = raw
        .categories
        .iter()
        .map(|c| c.trim())
        .filter(|c| !c.is_empty())
        .map(str::to_string)
        .collect();
    let age_rating = age_rating(&raw.maturity);
    let content_descriptors = raw
        .content_descriptors
        .iter()
        .map(|d| d.trim())
        .filter(|d| !d.is_empty())
        .map(str::to_string)
        .collect();
    CatalogItem {
        age_rating,
        is_adult: is_adult(age_rating, raw.maturity.is_mature),
        content_descriptors,
        poster_tall: pick_image(&raw.poster_tall, POSTER_TALL_WIDTH.0, POSTER_TALL_WIDTH.1),
        poster_wide: pick_image(&raw.poster_wide, POSTER_WIDE_WIDTH.0, POSTER_WIDE_WIDTH.1),
        id: raw.id,
        title: raw.title,
        description: raw.description,
        episode_count: raw.episode_count,
        launch_year: raw.launch_year,
        is_subbed: raw.is_subbed,
        is_dubbed: raw.is_dubbed,
        has_target_sub: flags.sub,
        has_target_dub: flags.dub,
        genres,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn s(list: &[&str]) -> Vec<String> {
        list.iter().map(|v| v.to_string()).collect()
    }

    fn img(source: &str, width: u32) -> ImageCandidate {
        ImageCandidate {
            source: source.to_string(),
            width,
        }
    }

    fn raw() -> RawSeries {
        RawSeries {
            id: "G1".into(),
            title: "Titel".into(),
            description: "Text".into(),
            poster_tall: vec![img("t-small", 200), img("t-ok", 600), img("t-big", 1200)],
            poster_wide: vec![img("w-ok", 800)],
            episode_count: 12,
            launch_year: 2020,
            is_subbed: true,
            is_dubbed: false,
            audio_locales: s(&["de-DE"]),
            subtitle_locales: vec![],
            categories: s(&[" Action ", "", "  ", "Drama"]),
            maturity: RawMaturity::default(),
            content_descriptors: s(&[" Gewalt ", ""]),
        }
    }

    #[test]
    fn flags_from_locales() {
        let f = language_flags(&s(&["de-DE"]), &[], "Titel", "de-DE");
        assert_eq!(
            f,
            LanguageFlags {
                dub: true,
                sub: false
            }
        );
    }

    #[test]
    fn flags_use_primary_subtag() {
        let f = language_flags(&s(&["FR-fr"]), &s(&["fr-CA"]), "Titel", "fr-FR");
        assert_eq!(
            f,
            LanguageFlags {
                dub: true,
                sub: true
            }
        );
    }

    #[test]
    fn title_heuristic_only_for_german() {
        let t = "X (German Dub)";
        assert!(language_flags(&[], &[], t, "de-DE").dub);
        assert!(language_flags(&[], &[], "X Synchro", "de-DE").dub);
        assert!(!language_flags(&[], &[], t, "en-US").dub);
    }

    #[test]
    fn title_heuristic_sub_markers() {
        assert!(language_flags(&[], &[], "Y (OmU)", "de-DE").sub);
        assert!(language_flags(&[], &[], "Y (OV)", "de-DE").sub);
        assert!(!language_flags(&[], &[], "Y (OmU)", "en-US").sub);
    }

    #[test]
    fn pick_image_prefers_width_range_else_last() {
        let c = vec![img("a", 100), img("b", 500), img("c", 900)];
        assert_eq!(pick_image(&c, 480, 800), "b");
        assert_eq!(pick_image(&c, 2000, 3000), "c");
    }

    #[test]
    fn pick_image_empty() {
        assert_eq!(pick_image(&[], 480, 800), "");
    }

    #[test]
    fn to_catalog_item_trims_categories() {
        let item = to_catalog_item(raw(), "de-DE");
        assert_eq!(item.genres, s(&["Action", "Drama"]));
        assert_eq!(item.poster_tall, "t-ok");
        assert_eq!(item.poster_wide, "w-ok");
        assert!(item.has_target_dub);
        assert!(!item.has_target_sub);
        assert!(item.is_subbed && !item.is_dubbed);
        assert_eq!(item.episode_count, 12);
    }

    fn maturity(extended: Option<&str>, ratings: &[&str], is_mature: bool) -> RawMaturity {
        RawMaturity {
            extended_rating: extended.map(str::to_string),
            maturity_ratings: s(ratings),
            is_mature,
        }
    }

    #[test]
    fn age_prefers_extended_rating() {
        assert_eq!(
            age_rating(&maturity(Some("16"), &["TV-14"], false)),
            Some(16)
        );
    }

    #[test]
    fn age_falls_back_to_unique_maturity_rating() {
        assert_eq!(age_rating(&maturity(None, &["TV-14"], false)), Some(14));
        assert_eq!(
            age_rating(&maturity(Some("MA"), &["TV-14", "TV-14"], false)),
            Some(14)
        );
    }

    #[test]
    fn age_is_unknown_when_ambiguous_or_without_number() {
        assert_eq!(
            age_rating(&maturity(None, &["TV-14", "TV-MA"], false)),
            None
        );
        assert_eq!(
            age_rating(&maturity(None, &["TV-14", "TV-16"], false)),
            None
        );
        assert_eq!(age_rating(&maturity(None, &["TV-MA"], false)), None);
        assert_eq!(age_rating(&maturity(None, &[], false)), None);
    }

    #[test]
    fn adult_by_flag_or_age_threshold() {
        assert!(is_adult(None, true));
        assert!(is_adult(Some(ADULT_MIN_AGE), false));
        assert!(!is_adult(Some(ADULT_MIN_AGE - 1), false));
        assert!(!is_adult(None, false));
    }

    #[test]
    fn to_catalog_item_maps_maturity_and_trims_descriptors() {
        let mut series = raw();
        series.maturity = maturity(Some("18"), &[], false);
        let item = to_catalog_item(series, "de-DE");
        assert_eq!(item.age_rating, Some(18));
        assert!(item.is_adult);
        assert_eq!(item.content_descriptors, s(&["Gewalt"]));
    }
}
