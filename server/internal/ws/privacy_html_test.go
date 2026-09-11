package ws

import (
	"strings"
	"testing"
)

func TestPrivacyHTMLNamesGoogleAndHttps(t *testing.T) {
	if !strings.Contains(privacyHTML, "Google") {
		t.Fatal("privacy HTML must name Google as the sign-in processor")
	}
	if !strings.Contains(privacyHTML, "HTTPS") && !strings.Contains(privacyHTML, "https") {
		t.Fatal("privacy HTML must say traffic uses HTTPS")
	}
	if strings.Contains(strings.ToLower(privacyHTML), "todo") {
		t.Fatal("privacy HTML contains a placeholder")
	}
	if !strings.Contains(privacyHTML, "/account-deletion") {
		t.Fatal("privacy HTML must link account deletion")
	}
}
