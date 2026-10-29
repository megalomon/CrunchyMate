use std::sync::Arc;

use crunchymate_core::core_api::Core;
use crunchymate_core::db::Database;
use crunchymate_core::error::CoreError;
use crunchymate_core::model::{UpdateChannel, UpdateInfo};
use crunchymate_core::remote::github_releases::GitHubReleases;
use crunchymate_core::remote::update_source::UpdateSource;
use serde_json::json;
use wiremock::matchers::{header, header_exists, method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

mod common;

use common::{FakeFetcher, TempDir};

const NIGHTLY_PATH: &str = "/repos/megalomon/CrunchyMate/releases/tags/nightly";
const LATEST_PATH: &str = "/repos/megalomon/CrunchyMate/releases/latest";

fn source(server: &MockServer) -> GitHubReleases {
    GitHubReleases::with_base_url(server.uri()).unwrap()
}

fn nightly_body(build: u32) -> serde_json::Value {
    json!({
        "tag_name": "nightly",
        "published_at": "2026-10-30T03:12:45Z",
        "assets": [{ "name": format!("crunchymate-nightly-{build}.apk") }]
    })
}

fn release_body(tag: &str) -> serde_json::Value {
    json!({
        "tag_name": tag,
        "published_at": "2026-11-02T10:00:00Z",
        "assets": [{ "name": "crunchymate-1.2.0.apk" }]
    })
}

async fn serve(server: &MockServer, route: &str, response: ResponseTemplate) {
    Mock::given(method("GET"))
        .and(path(route))
        .respond_with(response)
        .mount(server)
        .await;
}

/// Kern mit der Update-Quelle gegen den Mock-Server; alle übrigen Quellen sind ungenutzt.
fn core_for(server: &MockServer) -> (Arc<Core>, TempDir) {
    struct Unused;
    #[async_trait::async_trait]
    impl crunchymate_core::remote::catalog_source::CatalogSource for Unused {
        async fn session_country(&self) -> Result<String, CoreError> {
            unreachable!("nicht genutzt")
        }

        async fn fetch_page(
            &self,
            _start: u32,
            _count: u32,
            _locale: &str,
        ) -> Result<crunchymate_core::model::CatalogPage, CoreError> {
            unreachable!("nicht genutzt")
        }
    }
    #[async_trait::async_trait]
    impl crunchymate_core::remote::metadata_source::MetadataSource for Unused {
        async fn search_batch(
            &self,
            _titles: &[String],
        ) -> Result<Vec<Option<crunchymate_core::model::MetadataItem>>, CoreError> {
            unreachable!("nicht genutzt")
        }
    }
    let dir = TempDir::new("update");
    let core = Core::with_sources(
        Database::open_in_memory().unwrap(),
        Arc::new(Unused),
        Arc::new(Unused),
        dir.store(),
        Arc::new(FakeFetcher::default()),
        Arc::new(source(server)),
        Default::default(),
    );
    (core, dir)
}

#[tokio::test]
async fn nightly_reads_build_from_asset_name_and_date_from_release() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path(NIGHTLY_PATH))
        .and(header("accept", "application/vnd.github+json"))
        .and(header_exists("user-agent"))
        .respond_with(ResponseTemplate::new(200).set_body_json(nightly_body(57)))
        .expect(1)
        .mount(&server)
        .await;
    let info = source(&server)
        .latest(UpdateChannel::Nightly)
        .await
        .unwrap();
    assert_eq!(
        info,
        Some(UpdateInfo::Nightly {
            build: 57,
            date: "2026-10-30".to_string()
        })
    );
}

#[tokio::test]
async fn release_reads_version_from_latest_tag() {
    let server = MockServer::start().await;
    serve(
        &server,
        LATEST_PATH,
        ResponseTemplate::new(200).set_body_json(release_body("v1.2.0")),
    )
    .await;
    let info = source(&server)
        .latest(UpdateChannel::Release)
        .await
        .unwrap();
    assert_eq!(
        info,
        Some(UpdateInfo::Release {
            version: "1.2.0".to_string()
        })
    );
}

#[tokio::test]
async fn newer_nightly_is_reported() {
    let server = MockServer::start().await;
    serve(
        &server,
        NIGHTLY_PATH,
        ResponseTemplate::new(200).set_body_json(nightly_body(57)),
    )
    .await;
    let (core, _dir) = core_for(&server);
    let update = core
        .check_for_update(UpdateChannel::Nightly, "56".to_string())
        .await
        .unwrap();
    assert_eq!(
        update,
        Some(UpdateInfo::Nightly {
            build: 57,
            date: "2026-10-30".to_string()
        })
    );
}

#[tokio::test]
async fn same_or_older_nightly_is_not_reported() {
    let server = MockServer::start().await;
    serve(
        &server,
        NIGHTLY_PATH,
        ResponseTemplate::new(200).set_body_json(nightly_body(57)),
    )
    .await;
    let (core, _dir) = core_for(&server);
    for current in ["57", "58"] {
        let update = core
            .check_for_update(UpdateChannel::Nightly, current.to_string())
            .await
            .unwrap();
        assert_eq!(update, None, "{current}");
    }
}

#[tokio::test]
async fn release_versions_compare_semantically() {
    let server = MockServer::start().await;
    serve(
        &server,
        LATEST_PATH,
        ResponseTemplate::new(200).set_body_json(release_body("v1.10.0")),
    )
    .await;
    let (core, _dir) = core_for(&server);
    let newer = core
        .check_for_update(UpdateChannel::Release, "1.9.0".to_string())
        .await
        .unwrap();
    assert_eq!(
        newer,
        Some(UpdateInfo::Release {
            version: "1.10.0".to_string()
        })
    );
    let same = core
        .check_for_update(UpdateChannel::Release, "1.10.0".to_string())
        .await
        .unwrap();
    assert_eq!(same, None);
    let older = core
        .check_for_update(UpdateChannel::Release, "2.0.0".to_string())
        .await
        .unwrap();
    assert_eq!(older, None);
}

#[tokio::test]
async fn missing_release_is_no_update() {
    let server = MockServer::start().await;
    serve(&server, NIGHTLY_PATH, ResponseTemplate::new(404)).await;
    let (core, _dir) = core_for(&server);
    let update = core
        .check_for_update(UpdateChannel::Nightly, "1".to_string())
        .await
        .unwrap();
    assert_eq!(update, None);
}

#[tokio::test]
async fn broken_bodies_are_parse_errors() {
    let server = MockServer::start().await;
    serve(
        &server,
        NIGHTLY_PATH,
        ResponseTemplate::new(200).set_body_string("{kaputt"),
    )
    .await;
    serve(
        &server,
        LATEST_PATH,
        ResponseTemplate::new(200).set_body_json(release_body("1.2.0")),
    )
    .await;
    let source = source(&server);
    assert!(matches!(
        source.latest(UpdateChannel::Nightly).await,
        Err(CoreError::Parse { .. })
    ));
    // Tag ohne „v“ passt nicht zum Schema
    assert!(matches!(
        source.latest(UpdateChannel::Release).await,
        Err(CoreError::Parse { .. })
    ));
}

#[tokio::test]
async fn nightly_without_build_number_is_a_parse_error() {
    let server = MockServer::start().await;
    let body = json!({
        "tag_name": "nightly",
        "published_at": "2026-10-30T03:12:45Z",
        "assets": [{ "name": "app-release.apk" }]
    });
    serve(
        &server,
        NIGHTLY_PATH,
        ResponseTemplate::new(200).set_body_json(body),
    )
    .await;
    assert!(matches!(
        source(&server).latest(UpdateChannel::Nightly).await,
        Err(CoreError::Parse { .. })
    ));
}

#[tokio::test]
async fn error_statuses_map_to_core_errors() {
    let server = MockServer::start().await;
    serve(&server, NIGHTLY_PATH, ResponseTemplate::new(500)).await;
    serve(
        &server,
        LATEST_PATH,
        ResponseTemplate::new(403).insert_header("retry-after", "120"),
    )
    .await;
    let source = source(&server);
    assert!(matches!(
        source.latest(UpdateChannel::Nightly).await,
        Err(CoreError::Network { .. })
    ));
    assert!(matches!(
        source.latest(UpdateChannel::Release).await,
        Err(CoreError::RateLimited {
            retry_after_secs: 120
        })
    ));
}

#[tokio::test]
async fn unreadable_current_build_is_a_parse_error() {
    let server = MockServer::start().await;
    serve(
        &server,
        NIGHTLY_PATH,
        ResponseTemplate::new(200).set_body_json(nightly_body(57)),
    )
    .await;
    let (core, _dir) = core_for(&server);
    let result = core
        .check_for_update(UpdateChannel::Nightly, "dev".to_string())
        .await;
    assert!(matches!(result, Err(CoreError::Parse { .. })));
}
