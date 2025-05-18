package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "workshop_registrations")
public class WorkshopRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDateTime registrationTime;

    @Column(nullable = false)
    private boolean waitlist = false;

    public WorkshopRegistration() {
        this.registrationTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Workshop getWorkshop() {
        return workshop;
    }

    public void setWorkshop(Workshop workshop) {
        this.workshop = workshop;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getRegistrationTime() {
        return registrationTime;
    }

    public void setRegistrationTime(LocalDateTime registrationTime) {
        this.registrationTime = registrationTime;
    }

    public boolean isWaitlist() {
        return waitlist;
    }

    public void setWaitlist(boolean waitlist) {
        this.waitlist = waitlist;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorkshopRegistration that = (WorkshopRegistration) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public String toString() {
        return "WorkshopRegistration{" +
                "id=" + id +
                ", workshop=" + (workshop != null ? workshop.getId() : null) +
                ", user=" + (user != null ? user.getId() : null) +
                ", registrationTime=" + registrationTime +
                ", waitlist=" + waitlist +
                '}';
    }
} 