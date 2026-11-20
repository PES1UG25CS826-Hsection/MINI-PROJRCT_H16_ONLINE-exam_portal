package edu.pes.exam.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnswerGrader {
    private final ObjectMapper json;
    public AnswerGrader(ObjectMapper json) { this.json=json; }

    public Grade grade(List<QuestionSubmission> questions,BigDecimal penaltyFactor,BigDecimal passingMarks) {
        BigDecimal maximum=BigDecimal.ZERO,score=BigDecimal.ZERO;
        for(QuestionSubmission question:questions) {
            maximum=maximum.add(question.points());
            if(question.answerJson()==null || "null".equals(question.answerJson())) continue;
            score=score.add(isCorrect(question.answerJson(),question.correctAnswerJson())
                    ? question.points() : question.points().multiply(penaltyFactor).negate());
        }
        if(maximum.signum()<=0) throw new IllegalArgumentException("An exam must contain at least one positive-mark question");
        BigDecimal roundedMaximum=maximum.setScale(2,RoundingMode.HALF_UP);
        BigDecimal roundedScore=score.max(BigDecimal.ZERO).min(maximum).setScale(2,RoundingMode.HALF_UP);
        BigDecimal percentage=roundedScore.multiply(BigDecimal.valueOf(100)).divide(roundedMaximum,2,RoundingMode.HALF_UP);
        return new Grade(roundedScore,roundedMaximum,percentage,roundedScore.compareTo(passingMarks)>=0);
    }

    public boolean isCorrect(String answer,String correctAnswer) {
        try {
            JsonNode left=json.readTree(answer),right=json.readTree(correctAnswer);
            if(left!=null&&right!=null&&left.isTextual()&&right.isTextual()) return left.asText().trim().equalsIgnoreCase(right.asText().trim());
            if(left!=null&&right!=null&&left.isArray()&&right.isArray()) return arrayValues(left).equals(arrayValues(right));
            return Objects.equals(left,right);
        } catch(Exception ex) { return false; }
    }

    private Set<String> arrayValues(JsonNode values) {
        var normalized = new HashSet<String>();
        values.forEach(value -> normalized.add(value.isTextual()
                ? value.asText().trim().toLowerCase(Locale.ROOT) : value.toString()));
        return normalized;
    }

    public record QuestionSubmission(BigDecimal points,String answerJson,String correctAnswerJson) {}
    public record Grade(BigDecimal score,BigDecimal maxScore,BigDecimal percentage,boolean passed) {}
}
