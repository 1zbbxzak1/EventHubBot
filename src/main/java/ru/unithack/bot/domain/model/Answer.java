package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

@Entity
@Table(name = "answers")
public class Answer {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "answer_id")
    private Long id;

    @Column(name = "text", nullable = false)
    private String text;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo userInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    public Answer(String text,
                  UserInfo userInfo,
                  Question question) {
        setText(text);
        setUserInfo(userInfo);
        setQuestion(question);
    }

    protected Answer() {}

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        if (this.userInfo != null) {
            this.userInfo.getAnswers().remove(this);
        }

        this.userInfo = userInfo;

        if (userInfo != null) {
            userInfo.getAnswers().add(this);
        }
    }

    public Question getQuestion() {
        return question;
    }

    public void setQuestion(Question question) {
        if (this.question != null) {
            this.question.getAnswers().remove(this);
        }

        this.question = question;

        if (question != null) {
            question.getAnswers().add(this);
        }
    }
}
