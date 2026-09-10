package game

// MaxDisplacement for 5 ranked items is 12 (e.g. reverse order).
const MaxDisplacement = 12
const MaxScore = 2000

// Official rank5.io scoring: each of 5 cards is worth 400 points,
// losing 50 per position out of place. Perfect = 2000; reverse (D=12) = 1400.
const PointsPerCard = 400
const PenaltyPerPosition = 50

// PenaltyRange is the total number of points a prediction can lose. Five
// options lose 50 per displaced position over a maximum displacement of 12,
// which is 600. Rankings of other lengths reuse this range so every question
// is worth the same, and so stored scores stay comparable across game modes.
const PenaltyRange = MaxDisplacement * PenaltyPerPosition

// MaxDisplacementFor returns the largest possible L1 distance between two
// orderings of n items, which is reached by reversing the ranking.
func MaxDisplacementFor(n int) int {
	if n < 2 {
		return 0
	}
	return n * n / 2
}

// Displacement returns the L1 distance between two rankings of the same options.
// Each ranking is a slice of option texts ordered best→worst (index 0 = rank 1).
func Displacement(actual, predicted []string) int {
	pos := make(map[string]int, len(actual))
	for i, opt := range actual {
		pos[opt] = i
	}
	d := 0
	for i, opt := range predicted {
		if a, ok := pos[opt]; ok {
			diff := i - a
			if diff < 0 {
				diff = -diff
			}
			d += diff
		}
	}
	return d
}

// ScoreFromDisplacement converts displacement into points out of MaxScore
// using the official formula: 2000 - 50*D (floored at 0).
func ScoreFromDisplacement(d int) int {
	if d < 0 {
		d = 0
	}
	score := MaxScore - PenaltyPerPosition*d
	if score < 0 {
		score = 0
	}
	return score
}

// ScoreForLength scores a displacement for a ranking of n options, spreading
// PenaltyRange across that length's maximum displacement. For n = 5 this is
// identical to the official ScoreFromDisplacement.
func ScoreForLength(d, n int) int {
	if d < 0 {
		d = 0
	}
	maxD := MaxDisplacementFor(n)
	if maxD <= 0 {
		return MaxScore
	}
	// Rounded to the nearest point so a perfect reverse always costs exactly
	// PenaltyRange regardless of length.
	penalty := (PenaltyRange*d + maxD/2) / maxD
	score := MaxScore - penalty
	if score < 0 {
		score = 0
	}
	return score
}

// ScorePrediction scores a single prediction against the subject's ranking.
func ScorePrediction(actual, predicted []string) int {
	return ScoreForLength(Displacement(actual, predicted), len(actual))
}
