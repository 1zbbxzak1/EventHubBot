package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "survey_results")
public class SurveyResult {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "survey_result_id")
    private Long id;

    @Column(name = "time")
    private ZonedDateTime time;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo userInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "survey_option_answer_id", nullable = false)
    private SurveyOptionAnswer optionAnswer;

    public SurveyResult(ZonedDateTime time,
                        UserInfo userInfo,
                        SurveyOptionAnswer optionAnswer) {
        setTime(time);
        setUserInfo(userInfo);
        setOptionAnswer(optionAnswer);
    }

    protected SurveyResult() {}

    public Long getId() {
        return id;
    }

    public ZonedDateTime getTime() {
        return time;
    }

    public void setTime(ZonedDateTime time) {
        this.time = time;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        if (this.userInfo != null) {
            this.userInfo.getSurveyResults().remove(this);
        }

        this.userInfo = userInfo;

        if (userInfo != null) {
            userInfo.getSurveyResults().add(this);
        }
    }

    public SurveyOptionAnswer getOptionAnswer() {
        return optionAnswer;
    }

    public void setOptionAnswer(SurveyOptionAnswer optionAnswer) {
        if (this.optionAnswer != null) {
            this.optionAnswer.getSurveyResults().remove(this);
        }

        this.optionAnswer = optionAnswer;

        if (userInfo != null) {
            userInfo.getSurveyResults().add(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SurveyResult that = (SurveyResult) o;

        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "SurveyResult{" +
                "id=" + id +
                ", time=" + time +
                '}';
    }
}
