package crunchyroll

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	DefaultAuthURL   = "https://beta-api.crunchyroll.com/auth/v1/token"
	DefaultBrowseURL = "https://beta-api.crunchyroll.com/content/v2/discover/browse"
	BasicAuthHeader  = "Basic Y3Jfd2ViOg=="
	UserAgentHeader  = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

type ImageDetail struct {
	Width  int    `json:"width"`
	Height int    `json:"height"`
	Type   string `json:"type"`
	Source string `json:"source"`
}

type ItemImages struct {
	PosterTall [][]ImageDetail `json:"poster_tall"`
	PosterWide [][]ImageDetail `json:"poster_wide"`
}

type SeriesMeta struct {
	EpisodeCount     int      `json:"episode_count"`
	SeasonCount      int      `json:"season_count"`
	SeriesLaunchYear int      `json:"series_launch_year"`
	IsSubbed         bool     `json:"is_subbed"`
	IsDubbed         bool     `json:"is_dubbed"`
	AudioLocales     []string `json:"audio_locales"`
	SubtitleLocales  []string `json:"subtitle_locales"`
	TenantCategories []string `json:"tenant_categories"`
}

type SeriesItem struct {
	ID             string     `json:"id"`
	Title          string     `json:"title"`
	Slug           string     `json:"slug"`
	SlugTitle      string     `json:"slug_title"`
	Description    string     `json:"description"`
	Images         ItemImages `json:"images"`
	SeriesMetadata SeriesMeta `json:"series_metadata"`
}

type BrowseResponse struct {
	Total int          `json:"total"`
	Data  []SeriesItem `json:"data"`
}

type TokenResponse struct {
	AccessToken string `json:"access_token"`
	ExpiresIn   int    `json:"expires_in"`
	TokenType   string `json:"token_type"`
}

type Client struct {
	httpClient     *http.Client
	BaseAuthURL    string
	BaseBrowseURL  string
	token          string
	tokenExpiresAt time.Time
	mu             sync.RWMutex
}

func NewClient(httpClient *http.Client) *Client {
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 30 * time.Second}
	}
	return &Client{
		httpClient:    httpClient,
		BaseAuthURL:   DefaultAuthURL,
		BaseBrowseURL: DefaultBrowseURL,
	}
}

func (c *Client) Authenticate() error {
	c.mu.Lock()
	defer c.mu.Unlock()

	data := url.Values{}
	data.Set("grant_type", "client_id")

	req, err := http.NewRequest("POST", c.BaseAuthURL, strings.NewReader(data.Encode()))
	if err != nil {
		return fmt.Errorf("failed to create token request: %w", err)
	}

	req.Header.Set("Authorization", BasicAuthHeader)
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("User-Agent", UserAgentHeader)

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("token request failed: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return fmt.Errorf("token request returned status %d: %s", resp.StatusCode, string(body))
	}

	var tokenResp TokenResponse
	if err := json.NewDecoder(resp.Body).Decode(&tokenResp); err != nil {
		return fmt.Errorf("failed to decode token response: %w", err)
	}

	c.token = tokenResp.AccessToken
	expiresIn := tokenResp.ExpiresIn
	if expiresIn <= 0 {
		expiresIn = 3600
	}
	c.tokenExpiresAt = time.Now().Add(time.Duration(expiresIn-60) * time.Second)

	return nil
}

func (c *Client) ensureToken() error {
	c.mu.RLock()
	hasToken := c.token != "" && time.Now().Before(c.tokenExpiresAt)
	c.mu.RUnlock()

	if hasToken {
		return nil
	}
	return c.Authenticate()
}

func (c *Client) FetchCatalogPage(start, count int) (*BrowseResponse, error) {
	if err := c.ensureToken(); err != nil {
		return nil, fmt.Errorf("failed to ensure token: %w", err)
	}

	c.mu.RLock()
	token := c.token
	c.mu.RUnlock()

	u, err := url.Parse(c.BaseBrowseURL)
	if err != nil {
		return nil, err
	}

	q := u.Query()
	q.Set("locale", "de-DE")
	q.Set("start", strconv.Itoa(start))
	q.Set("n", strconv.Itoa(count))
	q.Set("sort_by", "popularity")
	u.RawQuery = q.Encode()

	req, err := http.NewRequest("GET", u.String(), nil)
	if err != nil {
		return nil, err
	}

	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("User-Agent", UserAgentHeader)
	req.Header.Set("Accept-Language", "de-DE")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch browse page: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnauthorized {
		// Try re-authenticating once
		if authErr := c.Authenticate(); authErr == nil {
			c.mu.RLock()
			token = c.token
			c.mu.RUnlock()
			req.Header.Set("Authorization", "Bearer "+token)
			respRetry, retryErr := c.httpClient.Do(req)
			if retryErr == nil && respRetry.StatusCode == http.StatusOK {
				defer respRetry.Body.Close()
				var browseResp BrowseResponse
				if err := json.NewDecoder(respRetry.Body).Decode(&browseResp); err != nil {
					return nil, err
				}
				return &browseResp, nil
			}
		}
		return nil, fmt.Errorf("unauthorized request to browse endpoint")
	}

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("browse request failed with status %d: %s", resp.StatusCode, string(body))
	}

	var browseResp BrowseResponse
	if err := json.NewDecoder(resp.Body).Decode(&browseResp); err != nil {
		return nil, fmt.Errorf("failed to decode browse response: %w", err)
	}

	return &browseResp, nil
}
