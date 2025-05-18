package ru.unithack.bot.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopRegistrationRepository extends JpaRepository<WorkshopRegistration, Long> {
    
    List<WorkshopRegistration> findByWorkshopOrderByRegistrationTimeAsc(Workshop workshop);
    
    List<WorkshopRegistration> findByUserOrderByRegistrationTimeDesc(User user);
    
    Optional<WorkshopRegistration> findByWorkshopAndUser(Workshop workshop, User user);
    
    List<WorkshopRegistration> findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(Workshop workshop);
    
    List<WorkshopRegistration> findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(Workshop workshop);
    
    @Query("SELECT COUNT(wr) FROM WorkshopRegistration wr WHERE wr.workshop = :workshop AND wr.waitlist = false")
    int countRegisteredParticipants(Workshop workshop);
    
    @Query("SELECT COUNT(wr) FROM WorkshopRegistration wr WHERE wr.workshop = :workshop AND wr.waitlist = true")
    int countWaitlistParticipants(Workshop workshop);
} 