package edu.pes.exam.exam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class ExamScoringService {
    public BigDecimal score(BigDecimal points, Set<String> expected, Set<String> submitted, int negativePercent) {
        if (expected == null || submitted == null || submitted.isEmpty()) return BigDecimal.ZERO.setScale(2);
        if (expected.equals(submitted)) return points.setScale(2, RoundingMode.HALF_UP);
        return points.multiply(BigDecimal.valueOf(negativePercent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP).negate();
    }

    public BigDecimal total(Iterable<BigDecimal> questionScores) {
        BigDecimal result = BigDecimal.ZERO;
        for (BigDecimal score : questionScores) result = result.add(score);
        return result.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
