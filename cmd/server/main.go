package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"
	"time"

	"crunchymate/internal/anilist"
	"crunchymate/internal/api"
	"crunchymate/internal/crunchyroll"
	"crunchymate/internal/db"
	"crunchymate/web"
)

func main() {
	port := flag.Int("port", 8080, "Port for the web server to listen on")
	dbPath := flag.String("db", "crunchymate.db", "Path to the SQLite database file")
	syncOnStart := flag.Bool("sync-on-start", false, "Automatically sync Crunchyroll DE catalog on startup")
	syncLimit := flag.Int("sync-limit", 0, "Max items to fetch during startup sync (0 for full catalog)")
	flag.Parse()

	resolvedDBPath := *dbPath
	if !filepath.IsAbs(resolvedDBPath) {
		if exe, err := os.Executable(); err == nil {
			resolvedDBPath = filepath.Join(filepath.Dir(exe), resolvedDBPath)
		}
	}

	log.Println("==================================================")
	log.Println("            CrunchyMate - Anime Tracker           ")
	log.Println("==================================================")
	log.Printf("Initializing database at %s...", resolvedDBPath)

	store, err := db.NewStore(resolvedDBPath)
	if err != nil {
		log.Fatalf("Fatal: Failed to initialize SQLite store: %v", err)
	}
	defer store.Close()

	crClient := crunchyroll.NewClient(nil)
	syncer := crunchyroll.NewSyncer(crClient, store)

	aniClient := anilist.NewClient(nil)
	enricher := anilist.NewEnricher(aniClient, store)

	apiHandler := api.NewRouter(store, syncer, enricher)
	staticHandler := web.StaticHandler()

	// Root Handler: dispatch /api/ to API and everything else to static UI
	rootMux := http.NewServeMux()
	rootMux.Handle("/api/", apiHandler)
	rootMux.Handle("/", staticHandler)

	addr := fmt.Sprintf(":%d", *port)
	server := &http.Server{
		Addr:         addr,
		Handler:      rootMux,
		ReadTimeout:  30 * time.Second,
		WriteTimeout: 60 * time.Second,
		IdleTimeout:  120 * time.Second,
	}

	// Startup sync in background
	if *syncOnStart {
		go func() {
			time.Sleep(500 * time.Millisecond)
			log.Println("[Startup] Initiating Crunchyroll DE catalog sync...")
			if err := syncer.StartSync(*syncLimit); err != nil {
				log.Printf("[Startup] Could not start sync: %v", err)
			}
		}()
	}

	// Server startup
	go func() {
		log.Printf("CrunchyMate is running!")
		log.Printf("Open your browser at: http://localhost:%d", *port)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server error: %v", err)
		}
	}()

	// Graceful shutdown handling
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit

	log.Println("\nShutting down server gracefully...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := server.Shutdown(ctx); err != nil {
		log.Printf("Server shutdown error: %v", err)
	}
	log.Println("CrunchyMate stopped successfully.")
}

// Unused import helper to avoid build issues
var _ = strings.TrimSpace
