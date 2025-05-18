package ru.unithack.bot.domain.model;

import jakarta.persistence.*;
import ru.unithack.bot.domain.enums.AttendStatus;

import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "attendees")
public class Attend {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "attend_id")
    private Long id;

    @Column(name = "register_time", nullable = false)
    private ZonedDateTime registerTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserInfo userInfo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "master_class_id", nullable = false)
    private MasterClass masterClass;

    public Attend(ZonedDateTime registerTime,
                  AttendStatus status,
                  UserInfo userInfo,
                  MasterClass masterClass) {
        setRegisterTime(registerTime);
        setStatus(status);
        setUserInfo(userInfo);
        setMasterClass(masterClass);
    }

    protected Attend() {}

    public Long getId() {
        return id;
    }

    public ZonedDateTime getRegisterTime() {
        return registerTime;
    }

    public void setRegisterTime(ZonedDateTime registerTime) {
        this.registerTime = registerTime;
    }

    public AttendStatus getStatus() {
        return status;
    }

    public void setStatus(AttendStatus status) {
        this.status = status;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(UserInfo userInfo) {
        if (this.userInfo != null) {
            this.userInfo.getAttendees().remove(this);
        }

        this.userInfo = userInfo;

        if (userInfo != null) {
            userInfo.getAttendees().add(this);
        }
    }

    public MasterClass getMasterClass() {
        return masterClass;
    }

    public void setMasterClass(MasterClass masterClass) {
        if (this.masterClass != null) {
            this.masterClass.getAttendees().remove(this);
        }

        this.masterClass = masterClass;

        if (masterClass != null) {
            masterClass.getAttendees().add(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Attend attend = (Attend) o;

        return Objects.equals(id, attend.id) ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Attend{" +
                "id=" + id +
                ", register_time=" + registerTime +
                ", status=" + status +
                '}';
    }
}
