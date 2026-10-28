package web

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestStaticHandler(t *testing.T) {
	handler := StaticHandler()

	// 1. Index
	req := httptest.NewRequest("GET", "/", nil)
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for root, got %d", rec.Code)
	}

	// 2. CSS
	req = httptest.NewRequest("GET", "/style.css", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for style.css, got %d", rec.Code)
	}

	// 3. JS
	req = httptest.NewRequest("GET", "/app.js", nil)
	rec = httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200 for app.js, got %d", rec.Code)
	}
}
