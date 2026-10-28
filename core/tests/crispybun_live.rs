//! Manuelle Probe gegen die echte CrispyBun-API, läuft nicht in der CI:
//! `cargo test -p crunchymate-core --test crispybun_live -- --ignored --nocapture`

use crunchymate_core::remote::catalog_source::CatalogSource;
use crunchymate_core::remote::crispybun::CrispyBunSource;

#[tokio::test]
#[ignore = "benötigt Netzwerk und die echte CrispyBun-API"]
async fn fetches_live_page() {
    let source = CrispyBunSource::new().unwrap();
    let country = source.session_country().await.unwrap();
    let page = source.fetch_page(0, 5, "de-DE").await.unwrap();
    println!(
        "total={} country={country} items={}",
        page.total,
        page.items.len()
    );
    assert!(page.total > 0);
    assert!(!country.is_empty());
    assert!(!page.items.is_empty());
}
