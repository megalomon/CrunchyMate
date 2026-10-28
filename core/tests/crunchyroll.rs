use crunchymate_core::error::CoreError;
use crunchymate_core::remote::catalog_source::CatalogSource;
use crunchymate_core::remote::crunchyroll::CrunchyrollSource;
use serde_json::json;
use wiremock::matchers::{body_string, header, header_exists, method, path, query_param};
use wiremock::{Mock, MockServer, ResponseTemplate};

fn token_body(token: &str, country: &str) -> serde_json::Value {
    json!({ "access_token": token, "expires_in": 300, "country": country })
}

fn source(server: &MockServer) -> CrunchyrollSource {
    CrunchyrollSource::with_endpoints(
        format!("{}/auth/v1/token", server.uri()),
        format!("{}/content/v2/discover/browse", server.uri()),
    )
    .unwrap()
}

fn browse_body() -> serde_json::Value {
    json!({
        "total": 1686,
        "data": [
            {
                "id": "GR1",
                "title": "Serie (German Dub)",
                "description": "Beschreibung",
                "images": {
                    "poster_tall": [[
                        { "source": "https://img/tall-300", "width": 300, "height": 450 },
                        { "source": "https://img/tall-600", "width": 600, "height": 900 }
                    ]],
                    "poster_wide": [[
                        { "source": "https://img/wide-800", "width": 800, "height": 450 }
                    ]]
                },
                "series_metadata": {
                    "episode_count": 24,
                    "series_launch_year": 2019,
                    "is_subbed": true,
                    "is_dubbed": true,
                    "audio_locales": ["ja-JP", "de-DE"],
                    "subtitle_locales": ["en-US"],
                    "tenant_categories": [" Action ", "", "Drama"]
                }
            },
            { "id": "GR2", "title": "Leer" },
            { "title": "Ohne ID" }
        ]
    })
}

#[tokio::test]
async fn reads_country_from_token() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t1", "de")))
        .mount(&server)
        .await;
    let src = source(&server);
    assert_eq!(src.session_country().await.unwrap(), "DE");
}

#[tokio::test]
async fn missing_country_is_parse_error() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(
            ResponseTemplate::new(200)
                .set_body_json(json!({ "access_token": "t1", "expires_in": 300 })),
        )
        .mount(&server)
        .await;
    let src = source(&server);
    assert!(matches!(
        src.session_country().await,
        Err(CoreError::Parse { .. })
    ));
}

#[tokio::test]
async fn maps_browse_page() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t1", "DE")))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(path("/content/v2/discover/browse"))
        .and(query_param("locale", "de-DE"))
        .and(query_param("start", "50"))
        .and(query_param("n", "50"))
        .and(query_param("sort_by", "popularity"))
        .and(header("Accept-Language", "de-DE"))
        .and(header("Authorization", "Bearer t1"))
        .respond_with(ResponseTemplate::new(200).set_body_json(browse_body()))
        .expect(1)
        .mount(&server)
        .await;
    let src = source(&server);
    let page = src.fetch_page(50, 50, "de-DE").await.unwrap();
    assert_eq!(page.total, 1686);
    // Eintrag ohne ID entfällt, total bleibt unverändert.
    assert_eq!(page.items.len(), 2);
    let first = &page.items[0];
    assert_eq!(first.id, "GR1");
    assert_eq!(first.poster_tall, "https://img/tall-600");
    assert_eq!(first.poster_wide, "https://img/wide-800");
    assert_eq!(first.episode_count, 24);
    assert_eq!(first.launch_year, 2019);
    assert!(first.is_subbed && first.is_dubbed);
    assert!(first.has_target_dub);
    assert!(!first.has_target_sub);
    assert_eq!(
        first.genres,
        vec!["Action".to_string(), "Drama".to_string()]
    );
    let second = &page.items[1];
    assert_eq!(second.poster_tall, "");
    assert_eq!(second.episode_count, 0);
}

#[tokio::test]
async fn reauthenticates_once_on_401() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("alt", "DE")))
        .up_to_n_times(1)
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("neu", "DE")))
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(header("Authorization", "Bearer alt"))
        .respond_with(ResponseTemplate::new(401))
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .and(header("Authorization", "Bearer neu"))
        .respond_with(ResponseTemplate::new(200).set_body_json(browse_body()))
        .expect(1)
        .mount(&server)
        .await;
    let src = source(&server);
    let page = src.fetch_page(0, 50, "de-DE").await.unwrap();
    assert_eq!(page.total, 1686);
}

#[tokio::test]
async fn second_401_is_auth_error() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t", "DE")))
        .expect(2)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .respond_with(ResponseTemplate::new(401))
        .expect(2)
        .mount(&server)
        .await;
    let src = source(&server);
    let err = src.fetch_page(0, 50, "de-DE").await.unwrap_err();
    assert!(matches!(err, CoreError::Auth { .. }));
}

#[tokio::test]
async fn forbidden_is_network_error() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t", "DE")))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .respond_with(ResponseTemplate::new(403).set_body_string("<html>Cloudflare</html>"))
        .mount(&server)
        .await;
    let src = source(&server);
    match src.fetch_page(0, 50, "de-DE").await.unwrap_err() {
        CoreError::Network { message } => assert!(message.contains("403")),
        other => panic!("unerwarteter Fehler: {other:?}"),
    }
}

#[tokio::test]
async fn non_json_body_is_parse_error() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t", "DE")))
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .respond_with(ResponseTemplate::new(200).set_body_string("<html>kein JSON</html>"))
        .mount(&server)
        .await;
    let src = source(&server);
    let err = src.fetch_page(0, 50, "de-DE").await.unwrap_err();
    assert!(matches!(err, CoreError::Parse { .. }));
}

#[tokio::test]
async fn construction_without_network_then_first_fetch_fails_with_network() {
    // Port 1 ist nicht erreichbar; der Konstruktor darf trotzdem nicht scheitern.
    let src = CrunchyrollSource::with_endpoints(
        "http://127.0.0.1:1/auth".to_string(),
        "http://127.0.0.1:1/browse".to_string(),
    )
    .unwrap();
    let err = src.fetch_page(0, 50, "de-DE").await.unwrap_err();
    assert!(matches!(err, CoreError::Network { .. }));
}

#[tokio::test]
async fn failed_login_is_retried_on_next_call() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(500))
        .up_to_n_times(1)
        .mount(&server)
        .await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t", "DE")))
        .mount(&server)
        .await;
    let src = source(&server);
    assert!(src.session_country().await.is_err());
    assert_eq!(src.session_country().await.unwrap(), "DE");
}

#[tokio::test]
async fn token_is_cached_and_credentials_are_sent() {
    let server = MockServer::start().await;
    Mock::given(method("POST"))
        .and(path("/auth/v1/token"))
        .and(header_exists("Authorization"))
        .and(header_exists("User-Agent"))
        .and(header("Content-Type", "application/x-www-form-urlencoded"))
        .and(body_string("grant_type=client_id"))
        .respond_with(ResponseTemplate::new(200).set_body_json(token_body("t1", "DE")))
        .expect(1)
        .mount(&server)
        .await;
    Mock::given(method("GET"))
        .respond_with(ResponseTemplate::new(200).set_body_json(browse_body()))
        .expect(2)
        .mount(&server)
        .await;
    let src = source(&server);
    src.fetch_page(0, 50, "de-DE").await.unwrap();
    src.fetch_page(50, 50, "de-DE").await.unwrap();
}
