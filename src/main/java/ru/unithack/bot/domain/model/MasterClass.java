package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "master_classes")
public class MasterClass {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "master_class_id")
    private Long id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @Column(name = "time_start", nullable = false)
    private ZonedDateTime timeStart;

    @Column(name = "number_seats", nullable = false)
    private Integer numberSeats;

    @Column(name = "location", nullable = false)
    private String location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo userInfo;

    @OneToMany(mappedBy = "masterClass", cascade = CascadeType.ALL, orphanRemoval = true)
    private final Set<Attend> attendees = new HashSet<>();

    public MasterClass(UserInfo userInfo,
                       String name,
                       ZonedDateTime timeStart,
                       Integer numberSeats,
                       String location) {
        setUserInfo(userInfo);
        setName(name);
        setTimeStart(timeStart);
        setNumberSeats(numberSeats);
        setLocation(location);
    }

    protected MasterClass() {}

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ZonedDateTime getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(ZonedDateTime timeStart) {
        this.timeStart = timeStart;
    }

    public Integer getNumberSeats() {
        return numberSeats;
    }

    public void setNumberSeats(Integer numberSeats) {
        this.numberSeats = numberSeats;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        if (this.userInfo != null) {
            this.userInfo.getMasterClasses().remove(this);
        }

        this.userInfo = userInfo;

        if (userInfo != null) {
            userInfo.getMasterClasses().add(this);
        }
    }

    public Set<Attend> getAttendees() {
        return attendees;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MasterClass that = (MasterClass) o;

        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getId());
    }

    @Override
    public String toString() {
        return "MasterClass{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", timeStart=" + timeStart +
                ", numberSeats=" + numberSeats +
                ", location='" + location + '\'' +
                '}';
    }
}
