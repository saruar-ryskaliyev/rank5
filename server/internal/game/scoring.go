package game

// MaxDisplacement for 5 ranked items is 12 (e.g. reverse order).
const MaxDisplacement = 12
const MaxScore = 2000

// Official rank5.io scoring: each of 5 cards is worth 400 points,
// losing 50 per position out of place. Perfect = 2000; reverse (D=12) = 1400.
const PointsPerCard = 400
const PenaltyPerPosition = 50

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

// ScorePrediction scores a single prediction against the subject's ranking.
func ScorePrediction(actual, predicted []string) int {
	return ScoreFromDisplacement(Displacement(actual, predicted))
}
