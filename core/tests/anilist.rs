use std::time::Duration;

use crunchymate_core::error::CoreError;
use crunchymate_core::remote::anilist::{AniListClient, RetryConfig};
use crunchymate_core::remote::metadata_source::MetadataSource;
use serde_json::json;
use wiremock::matchers::{header, method};
use wiremock::{Mock, MockServer, ResponseTemplate};

fn fast() -> RetryConfig {
    RetryConfig {
        max_retries: 3,
        default_wait: Duration::ZERO,
        min_wait: Duration::ZERO,
    }
}

fn client(server: &MockServer) -> AniListClient {
    AniListClient::new(server.uri(), fast()).unwrap()
}

fn titles(list: &[&str]) -> Vec<String> {
    list.iter().map(|t| t.to_string()).collect()
}

fn media(id: i64, tags: serde_json::Value) -> serde_json::Value {
    json!({
        "id": id,
        "title": { "romaji": format!("Romaji {id}"), "english": null, "native": "x" },
        "description": "Beschreibung",
        "genres": ["Action", null, " Drama "],
        "tags": tags,
        "averageScore": 82,
        "status": "FINISHED",
        "bannerImage": null,
        "siteUrl": format!("https://anilist.co/anime/{id}")
    })
}

#[tokio::test]
async fn batch_maps_results_in_input_order_with_none_for_missing() {
    let server = MockServer::start().await;
    let body = json!({ "data": {
        "a0": { "media": [media(1, json!([]))] },
        "a1": { "media": [] },
        "a2": { "media": [media(3, json!([]))] }
    }});
    Mock::given(method("POST"))
        .and(header("User-Agent", "CrunchyMate/2.0"))
        .respond_with(ResponseTemplate::new(200).set_body_json(body))
        .mount(&server)
        .await;

    let res = client(&server)
        .search_batch(&titles(&["A", "B (OmU) x", "C"]))
        .await
        .unwrap();
    assert_eq!(res.len(), 3);
    let first = res[0].as_ref().unwrap();
    assert_eq!(first.anilist_id, 1);
    assert_eq!(first.title_romaji, "Romaji 1");
    assert_eq!(first.title_english, "");
    assert_eq!(first.banner_image, "");
    assert_eq!(first.score, 82);
    assert_eq!(first.genres, vec!["Action", "Drama"]);
    assert!(res[1].is_none());
    assert_eq!(res[2].as_ref().unwrap().anilist_id, 3);

    let req = &server.received_requests().await.unwrap()[0];
    let sent: serde_json::Value = serde_json::from_slice(&req.body).unwrap();
    assert_eq!(sent["variables"]["s1"], "B x");
    assert!(sent["query"].as_str().unwrap().contains("a2: Page"));
}

#[tokio::test]
async fn batch_filters_spoiler_and_low_rank_tags() {
    let server = MockServer::start().await;
    let tags = json!([
        { "name": "Gut", "rank": 80, "isMediaSpoiler": false },
        { "name": "Grenze", "rank": 40, "isMediaSpoiler": false },
        { "name": "Niedrig", "rank": 39, "isMediaSpoiler": false },
        { "name": "Spoiler", "rank": 90, "isMediaSpoiler": true },
        { "name": null, "rank": 90, "isMediaSpoiler": false }
    ]);
    let body = json!({ "data": { "a0": { "media": [media(1, tags)] } } });
    Mock::given(method("POST"))
        .respond_with(ResponseTemplate::new(200).set_body_json(body))
        .mount(&server)
        .await;

    let res = client(&server).search_batch(&titles(&["A"])).await.unwrap();
    let names: Vec<_> = res[0]
        .as_ref()
        .unwrap()
        .tags
        .iter()
        .map(|t| (t.name.as_str(), t.rank))
        .collect();
    assert_eq!(names, vec![("Gut", 80), ("Grenze", 40)]);
}

#[tokio::test]
async fn retries_after_429_then_succeeds() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(ResponseTemplate::new(429).insert_header("Retry-After", "0"))
        .up_to_n_times(1)
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .respond_with(
            ResponseTemplate::new(200)
                .set_body_json(json!({ "data": { "a0": { "media": [media(7, json!([]))] } } })),
        )
        .expect(1)
        .mount(&server)
        .await;

    let res = client(&server).search_batch(&titles(&["A"])).await.unwrap();
    assert_eq!(res[0].as_ref().unwrap().anilist_id, 7);
    assert_eq!(server.received_requests().await.unwrap().len(), 2);
}

#[tokio::test]
async fn gives_up_after_max_retries() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(ResponseTemplate::new(429).insert_header("Retry-After", "0"))
        .mount(&server)
        .await;

    let err = client(&server)
        .search_batch(&titles(&["A"]))
        .await
        .unwrap_err();
    assert!(matches!(
        err,
        CoreError::RateLimited {
            retry_after_secs: 0
        }
    ));
    assert_eq!(server.received_requests().await.unwrap().len(), 4);
}

#[tokio::test]
async fn graphql_errors_without_data_are_parse_errors() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(
            ResponseTemplate::new(200)
                .set_body_json(json!({ "errors": [{ "message": "kaputt" }] })),
        )
        .mount(&server)
        .await;

    let err = client(&server)
        .search_batch(&titles(&["A"]))
        .await
        .unwrap_err();
    match err {
        CoreError::Parse { message } => assert!(message.contains("kaputt")),
        other => panic!("unerwartet: {other:?}"),
    }
}

#[tokio::test]
async fn not_found_yields_all_none() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(ResponseTemplate::new(404))
        .mount(&server)
        .await;

    let res = client(&server)
        .search_batch(&titles(&["A", "B"]))
        .await
        .unwrap();
    assert_eq!(res, vec![None, None]);
}

#[tokio::test]
async fn other_status_is_network_error() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .respond_with(ResponseTemplate::new(500))
        .mount(&server)
        .await;

    let err = client(&server)
        .search_batch(&titles(&["A"]))
        .await
        .unwrap_err();
    assert!(matches!(err, CoreError::Network { .. }));
}
