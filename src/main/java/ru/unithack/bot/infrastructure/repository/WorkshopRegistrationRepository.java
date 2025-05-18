package ru.unithack.bot.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopRegistrationRepository extends JpaRepository<WorkshopRegistration, Long> {
    
    List<WorkshopRegistration> findByWorkshopOrderByRegistrationTimeAsc(Workshop workshop);
    
    List<WorkshopRegistration> findByUserOrderByRegistrationTimeDesc(User user);
    
    Optional<WorkshopRegistration> findByWorkshopAndUser(Workshop workshop, User user);
    
    List<WorkshopRegistration> findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(Workshop workshop);
    
    List<WorkshopRegistration> findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(Workshop workshop);
    
    @Query("SELECT COUNT(r) FROM WorkshopRegistration r WHERE r.workshop = :workshop AND r.waitlist = false")
    int countRegisteredParticipants(Workshop workshop);
    
    @Query("SELECT COUNT(r) FROM WorkshopRegistration r WHERE r.workshop = :workshop AND r.waitlist = true")
    int countWaitlistParticipants(Workshop workshop);
    
    @Query("SELECT r FROM WorkshopRegistration r WHERE r.workshop = :workshop AND r.pendingConfirmation = true AND r.confirmationDeadline < :now")
    List<WorkshopRegistration> findExpiredConfirmations(Workshop workshop, LocalDateTime now);
    
    @Query("SELECT r FROM WorkshopRegistration r WHERE r.workshop = :workshop AND r.waitlist = true AND r.pendingConfirmation = false ORDER BY r.waitlistPosition ASC")
    List<WorkshopRegistration> findNextInWaitlist(Workshop workshop);
    
    @Modifying
    @Query("UPDATE WorkshopRegistration r SET r.waitlistPosition = r.waitlistPosition - 1 WHERE r.workshop = :workshop AND r.waitlist = true AND r.waitlistPosition > :position")
    void decrementWaitlistPositionsAfter(Workshop workshop, int position);
    
    @Query("SELECT MAX(r.waitlistPosition) FROM WorkshopRegistration r WHERE r.workshop = :workshop AND r.waitlist = true")
    Optional<Integer> findMaxWaitlistPosition(Workshop workshop);
} 