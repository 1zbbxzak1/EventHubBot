package ru.unithack.bot.domain.model;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "roles_users")
public class RoleUser {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    @Column(name = "roles_users_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    public RoleUser(User user,
                    Role role) {
        setUser(user);
        setRole(role);
    }

    protected RoleUser() {

    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        if (this.user != null) {
            this.user.getRoleUser().remove(this);
        }

        this.user = user;

        if (user != null) {
            user.getRoleUser().add(this);
        }
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        if (this.role != null) {
            this.role.getRoleUser().remove(this);
        }

        this.role = role;

        if (role != null) {
            role.getRoleUser().add(this);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RoleUser roleUser = (RoleUser) o;

        return Objects.equals(getId(), roleUser.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String
    toString() {
        return "RoleUser{" +
                "id=" + id +
                ", user='" + user + '\'' +
                ", role=" + role +
                '}';
    }
}

