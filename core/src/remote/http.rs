//! Gemeinsamer HTTP-Client-Aufbau für alle Remote-Quellen.

use std::time::Duration;

use crate::error::CoreResult;

/// Liefert einen `ClientBuilder` mit Timeout und TLS-Konfiguration.
///
/// Mitgelieferte Mozilla-Root-Zertifikate statt Plattform-Verifier: dieser
/// bräuchte unter Android eine JNI-Initialisierung samt Kotlin-Artefakt.
pub fn http_client_builder(timeout: Duration) -> CoreResult<reqwest::ClientBuilder> {
    let roots = rustls::RootCertStore {
        roots: webpki_roots::TLS_SERVER_ROOTS.to_vec(),
    };
    let config = rustls::ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    Ok(reqwest::Client::builder()
        .timeout(timeout)
        .tls_backend_preconfigured(config))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn baut_client_ohne_netzwerk() {
        let client = http_client_builder(Duration::from_secs(5)).unwrap().build();
        assert!(client.is_ok());
    }
}
