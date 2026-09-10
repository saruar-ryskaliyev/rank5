package game

import "testing"

func TestScoreForLengthMatchesOfficialFiveOptionScoring(t *testing.T) {
	for d := 0; d <= MaxDisplacement; d++ {
		if got, want := ScoreForLength(d, 5), ScoreFromDisplacement(d); got != want {
			t.Fatalf("ScoreForLength(%d, 5) = %d, want %d", d, got, want)
		}
	}
}

func TestScorePredictionThreeOptionsSpansSamePointRange(t *testing.T) {
	actual := []string{"Maya", "Noah", "Iris"}
	if got := ScorePrediction(actual, actual); got != MaxScore {
		t.Fatalf("perfect three-option score = %d, want %d", got, MaxScore)
	}
	reverse := []string{"Iris", "Noah", "Maya"}
	if d := Displacement(actual, reverse); d != MaxDisplacementFor(3) {
		t.Fatalf("reverse displacement = %d, want %d", d, MaxDisplacementFor(3))
	}
	if got, want := ScorePrediction(actual, reverse), MaxScore-PenaltyRange; got != want {
		t.Fatalf("reverse three-option score = %d, want %d", got, want)
	}
}

func TestScorePredictionScalesWithGroupSize(t *testing.T) {
	// One adjacent swap out of eight players should cost far less than the same
	// swap out of three, because there is much more order left to get right.
	three := []string{"a", "b", "c"}
	threeSwapped := []string{"b", "a", "c"}
	eight := []string{"a", "b", "c", "d", "e", "f", "g", "h"}
	eightSwapped := []string{"b", "a", "c", "d", "e", "f", "g", "h"}

	threeScore := ScorePrediction(three, threeSwapped)
	eightScore := ScorePrediction(eight, eightSwapped)
	if eightScore <= threeScore {
		t.Fatalf("eight-player swap scored %d, want more than three-player swap %d", eightScore, threeScore)
	}
	if threeScore >= MaxScore || eightScore >= MaxScore {
		t.Fatalf("a displaced pair must cost points: three=%d eight=%d", threeScore, eightScore)
	}
}

// Stats derive a normalized displacement from the stored score. Any ranking
// length must stay inside the 0..MaxDisplacement band that accuracy assumes.
func TestScoreForLengthKeepsDerivedDisplacementInStatsRange(t *testing.T) {
	for _, n := range []int{3, 4, 5, 6, 8} {
		maxD := MaxDisplacementFor(n)
		for d := 0; d <= maxD; d++ {
			score := ScoreForLength(d, n)
			derived := (MaxScore - score) / PenaltyPerPosition
			if derived < 0 || derived > MaxDisplacement {
				t.Fatalf("n=%d d=%d score=%d derived displacement %d outside 0..%d",
					n, d, score, derived, MaxDisplacement)
			}
		}
	}
}
