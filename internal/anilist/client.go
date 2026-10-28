package anilist

import (
	"bytes"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	DefaultAniListEndpoint = "https://graphql.anilist.co"
	MaxRetries             = 3
	DefaultWaitSeconds     = 60
)

var cleanTitleRegex = regexp.MustCompile(`(?i)\s*(\(German\s*Dub\)|\[German\s*Dub\]|\(OmU\)|\(Dub\)|\(OV\)|\(Synchro\)|\(Staffel\s*\d+\))\s*`)

type AniListTitle struct {
	Romaji  string `json:"romaji"`
	English string `json:"english"`
	Native  string `json:"native"`
}

type AniListCoverImage struct {
	Large  string `json:"large"`
	Medium string `json:"medium"`
}

type AniListMediaTag struct {
	Name           string `json:"name"`
	Rank           int    `json:"rank"`
	IsMediaSpoiler bool   `json:"isMediaSpoiler"`
}

type AniListMedia struct {
	ID           int               `json:"id"`
	Title        AniListTitle      `json:"title"`
	Description  string            `json:"description"`
	Genres       []string          `json:"genres"`
	Tags         []AniListMediaTag `json:"tags"`
	AverageScore int               `json:"averageScore"`
	Status       string            `json:"status"`
	CoverImage   AniListCoverImage `json:"coverImage"`
	BannerImage  string            `json:"bannerImage"`
	SiteURL      string            `json:"siteUrl"`
}

type GraphQLResponse struct {
	Data struct {
		Media *AniListMedia `json:"Media"`
	} `json:"data"`
	Errors []struct {
		Message string `json:"message"`
		Status  int    `json:"status"`
	} `json:"errors"`
}

type Client struct {
	httpClient      *http.Client
	Endpoint        string
	mu              sync.Mutex
	blockedUntil    time.Time
	OnRateLimitWait func(waitDuration time.Duration, attempt int)
	OnRateLimitDone func()
}

func NewClient(httpClient *http.Client) *Client {
	if httpClient == nil {
		httpClient = &http.Client{Timeout: 15 * time.Second}
	}
	return &Client{
		httpClient: httpClient,
		Endpoint:   DefaultAniListEndpoint,
	}
}

func CleanTitle(title string) string {
	cleaned := cleanTitleRegex.ReplaceAllString(title, " ")
	return strings.TrimSpace(cleaned)
}

func parseWaitDuration(resp *http.Response) time.Duration {
	// 1. Check Retry-After header (seconds or HTTP date)
	if retryAfter := resp.Header.Get("Retry-After"); retryAfter != "" {
		if seconds, err := strconv.Atoi(strings.TrimSpace(retryAfter)); err == nil {
			if seconds <= 0 {
				return 10 * time.Millisecond
			}
			return time.Duration(seconds)*time.Second + time.Second
		}
		if t, err := http.ParseTime(retryAfter); err == nil {
			wait := time.Until(t) + time.Second
			if wait > 0 {
				return wait
			}
		}
	}

	// 2. Check X-RateLimit-Reset (Unix timestamp in seconds)
	if reset := resp.Header.Get("X-RateLimit-Reset"); reset != "" {
		if unixSec, err := strconv.ParseInt(strings.TrimSpace(reset), 10, 64); err == nil && unixSec > 0 {
			resetTime := time.Unix(unixSec, 0)
			wait := time.Until(resetTime) + time.Second
			if wait > 0 {
				return wait
			}
		}
	}

	// 3. Fallback default
	return DefaultWaitSeconds * time.Second
}

func (c *Client) executeGraphQL(payload map[string]interface{}) (json.RawMessage, error) {
	bodyBytes, err := json.Marshal(payload)
	if err != nil {
		return nil, fmt.Errorf("failed to marshal graphql payload: %w", err)
	}

	for attempt := 0; attempt <= MaxRetries; attempt++ {
		// Proactively check if client is currently rate limit blocked
		c.mu.Lock()
		var waitProlonged time.Duration
		if !c.blockedUntil.IsZero() && time.Now().Before(c.blockedUntil) {
			waitProlonged = time.Until(c.blockedUntil)
		}
		c.mu.Unlock()

		if waitProlonged > 0 {
			if c.OnRateLimitWait != nil {
				c.OnRateLimitWait(waitProlonged, attempt)
			}
			time.Sleep(waitProlonged)
			if c.OnRateLimitDone != nil {
				c.OnRateLimitDone()
			}
			c.mu.Lock()
			c.blockedUntil = time.Time{}
			c.mu.Unlock()
		}

		req, err := http.NewRequest("POST", c.Endpoint, bytes.NewBuffer(bodyBytes))
		if err != nil {
			return nil, fmt.Errorf("failed to create graphql request: %w", err)
		}

		req.Header.Set("Content-Type", "application/json")
		req.Header.Set("Accept", "application/json")
		req.Header.Set("User-Agent", "CrunchyMate/1.0")

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("graphql request failed: %w", err)
		}

		// Proactively save reset time if remaining queries reached 0
		if rem := resp.Header.Get("X-RateLimit-Remaining"); rem == "0" {
			if reset := resp.Header.Get("X-RateLimit-Reset"); reset != "" {
				if unixSec, err := strconv.ParseInt(strings.TrimSpace(reset), 10, 64); err == nil {
					c.mu.Lock()
					c.blockedUntil = time.Unix(unixSec, 0).Add(time.Second)
					c.mu.Unlock()
				}
			}
		}

		if resp.StatusCode == http.StatusTooManyRequests {
			waitDuration := parseWaitDuration(resp)
			resp.Body.Close()

			if attempt < MaxRetries {
				log.Printf("[AniList Client] Rate limit exceeded (429). Waiting %v before retry %d/%d...", waitDuration, attempt+1, MaxRetries)
				if c.OnRateLimitWait != nil {
					c.OnRateLimitWait(waitDuration, attempt+1)
				}
				time.Sleep(waitDuration)
				if c.OnRateLimitDone != nil {
					c.OnRateLimitDone()
				}
				continue
			}
			return nil, fmt.Errorf("anilist rate limit exceeded (429) after %d retries", MaxRetries)
		}

		if resp.StatusCode == http.StatusNotFound {
			resp.Body.Close()
			return nil, nil
		}

		if resp.StatusCode != http.StatusOK {
			resp.Body.Close()
			return nil, fmt.Errorf("anilist returned status: %d", resp.StatusCode)
		}

		var rawResp struct {
			Data   json.RawMessage `json:"data"`
			Errors []struct {
				Message string `json:"message"`
				Status  int    `json:"status"`
			} `json:"errors"`
		}

		decodeErr := json.NewDecoder(resp.Body).Decode(&rawResp)
		resp.Body.Close()
		if decodeErr != nil {
			return nil, fmt.Errorf("failed to decode anilist response: %w", decodeErr)
		}

		if len(rawResp.Data) == 0 && len(rawResp.Errors) > 0 {
			return nil, fmt.Errorf("anilist graphql error: %s", rawResp.Errors[0].Message)
		}

		return rawResp.Data, nil
	}

	return nil, fmt.Errorf("anilist request failed after retries")
}

func (c *Client) SearchAnime(title string) (*AniListMedia, error) {
	results, err := c.SearchAnimeBatch([]string{title})
	if err != nil {
		return nil, err
	}
	if len(results) == 0 {
		return nil, nil
	}
	return results[0], nil
}

// SearchAnimeBatch queries up to 10-15 anime titles in a single HTTP request using GraphQL Aliases with Page.
func (c *Client) SearchAnimeBatch(titles []string) ([]*AniListMedia, error) {
	if len(titles) == 0 {
		return nil, nil
	}

	var sb strings.Builder
	sb.WriteString(`fragment MediaFields on Media {
  id
  title { romaji english native }
  description
  genres
  tags { name rank isMediaSpoiler }
  averageScore
  status
  coverImage { large medium }
  bannerImage
  siteUrl
}
query(`)

	vars := make(map[string]interface{})
	for i, title := range titles {
		if i > 0 {
			sb.WriteString(", ")
		}
		varName := fmt.Sprintf("s%d", i)
		sb.WriteString(fmt.Sprintf("$%s: String", varName))
		clean := CleanTitle(title)
		if clean == "" {
			clean = title
		}
		vars[varName] = clean
	}

	sb.WriteString(") {\n")
	for i := range titles {
		alias := fmt.Sprintf("a%d", i)
		varName := fmt.Sprintf("s%d", i)
		sb.WriteString(fmt.Sprintf("  %s: Page(page: 1, perPage: 1) { media(search: $%s, type: ANIME) { ...MediaFields } }\n", alias, varName))
	}
	sb.WriteString("}")

	payload := map[string]interface{}{
		"query":     sb.String(),
		"variables": vars,
	}

	dataBytes, err := c.executeGraphQL(payload)
	if err != nil {
		return nil, err
	}

	if len(dataBytes) == 0 {
		return make([]*AniListMedia, len(titles)), nil
	}

	var batchData map[string]struct {
		Media []AniListMedia `json:"media"`
	}
	if err := json.Unmarshal(dataBytes, &batchData); err != nil {
		return nil, fmt.Errorf("failed to unmarshal batch response: %w", err)
	}

	results := make([]*AniListMedia, len(titles))
	for i := range titles {
		alias := fmt.Sprintf("a%d", i)
		if page, ok := batchData[alias]; ok && len(page.Media) > 0 {
			media := page.Media[0]
			results[i] = &media
		} else {
			results[i] = nil
		}
	}

	return results, nil
}
