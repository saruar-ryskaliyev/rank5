package store

import (
	"testing"
)

func TestVisibilityConstants(t *testing.T) {
	if VisibilityPrivate != "private" || VisibilityPublic != "public" || VisibilityRemoved != "removed" {
		t.Fatalf("unexpected visibility constants")
	}
}

func TestDeckIsPublic(t *testing.T) {
	d := &Deck{Visibility: VisibilityPublic}
	if !d.IsPublic() {
		t.Fatal("expected public")
	}
	d.Visibility = VisibilityPrivate
	if d.IsPublic() {
		t.Fatal("expected not public")
	}
}
