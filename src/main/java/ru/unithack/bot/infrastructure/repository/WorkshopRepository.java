package ru.unithack.bot.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.unithack.bot.domain.model.Workshop;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkshopRepository extends JpaRepository<Workshop, Long> {
    
    List<Workshop> findByActiveTrue();
    
    List<Workshop> findByActiveTrueOrderByStartTimeAsc();
    
    @Query("SELECT w FROM Workshop w WHERE w.active = true AND w.startTime > :now ORDER BY w.startTime ASC")
    List<Workshop> findUpcomingWorkshops(LocalDateTime now);
    
    @Query("SELECT w FROM Workshop w WHERE w.active = true AND w.startTime BETWEEN :start AND :end ORDER BY w.startTime ASC")
    List<Workshop> findWorkshopsBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT w FROM Workshop w LEFT JOIN FETCH w.registrations WHERE w.active = true ORDER BY w.startTime ASC")
    List<Workshop> findByActiveTrueWithRegistrations();
    
    @Query("SELECT w FROM Workshop w LEFT JOIN FETCH w.registrations WHERE w.id = :id")
    Optional<Workshop> findByIdWithRegistrations(Long id);
} 