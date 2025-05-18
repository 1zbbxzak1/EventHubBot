package ru.unithack.bot.domain.model;

import jakarta.persistence.*;
import ru.unithack.bot.domain.enums.AnswerStatus;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "survey_option_answers")
public class SurveyOptionAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "survey_option_answer_id")
    private Long id;

    @Column(name = "text", nullable = false)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AnswerStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_id", nullable = false)
    private Survey survey;

    @OneToMany(mappedBy = "optionAnswer", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<SurveyResult> surveyResults = new HashSet<>();

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public AnswerStatus getStatus() {
        return status;
    }

    public void setStatus(AnswerStatus status) {
        this.status = status;
    }

    public Survey getSurvey() {
        return survey;
    }

    public Set<SurveyResult> getSurveyResults() {
        return surveyResults;
    }

    public void setSurvey(Survey survey) {
        if (this.survey != null) {
            this.survey.getOptionAnswers().remove(this);
        }

        this.survey = survey;

        if (survey != null) {
            survey.getOptionAnswers().add(this);
        }
    }
}
