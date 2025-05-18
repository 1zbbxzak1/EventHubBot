package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "users_info")
public class UserInfo {
    @Id
    @Column(name = "user_id")
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "qr_code")
    private String qrCode;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Card> cards = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<MasterClass> masterClasses = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Attend> attendees = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Question> questions = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Answer> answers = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Survey> surveys = new HashSet<>();

    @OneToMany(mappedBy = "userInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<SurveyResult> surveyResults = new HashSet<>();

    public UserInfo(User user,
                    ZonedDateTime createdAt) {
        setUser(user);
        setCreatedAt(createdAt);
    }

    protected UserInfo() {

    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        if (this.user == user) {
            return;
        }

        if (this.user != null) {
            this.user.setUserInfo(null);
        }

        this.user = user;

        if (user != null && user.getUserInfo() != this) {
            user.setUserInfo(this);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQrCode() {
        return qrCode;
    }

    public void setQrCode(String qrCode) {
        this.qrCode = qrCode;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(ZonedDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Set<Card> getCards() {
        return cards;
    }

    public Set<MasterClass> getMasterClasses() {
        return masterClasses;
    }

    public Set<Attend> getAttendees() {
        return attendees;
    }

    public Set<Question> getQuestions() {
        return questions;
    }

    public Set<Answer> getAnswers() {
        return answers;
    }

    public Set<Survey> getSurveys() {
        return surveys;
    }

    public Set<SurveyResult> getSurveyResults() {
        return surveyResults;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserInfo userInfo = (UserInfo) o;

        return Objects.equals(getId(), userInfo.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
