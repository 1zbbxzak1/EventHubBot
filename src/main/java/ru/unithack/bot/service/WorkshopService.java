package ru.unithack.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;
import ru.unithack.bot.infrastructure.repository.WorkshopRegistrationRepository;
import ru.unithack.bot.infrastructure.repository.WorkshopRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class WorkshopService {

    private static final Logger logger = LoggerFactory.getLogger(WorkshopService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationRepository registrationRepository;

    @Autowired
    public WorkshopService(WorkshopRepository workshopRepository,
                           WorkshopRegistrationRepository registrationRepository) {
        this.workshopRepository = workshopRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public Workshop createWorkshop(String title, String description, LocalDateTime startTime,
                                   LocalDateTime endTime, int capacity) {
        Workshop workshop = new Workshop();
        workshop.setTitle(title);
        workshop.setDescription(description);
        workshop.setStartTime(startTime);
        workshop.setEndTime(endTime);
        workshop.setCapacity(capacity);
        workshop.setActive(true);

        return workshopRepository.save(workshop);
    }

    @Transactional
    public Optional<Workshop> updateWorkshop(Long id, String title, String description,
                                             LocalDateTime startTime, LocalDateTime endTime,
                                             int capacity, boolean active) {
        return workshopRepository.findById(id).map(workshop -> {
            workshop.setTitle(title);
            workshop.setDescription(description);
            workshop.setStartTime(startTime);
            workshop.setEndTime(endTime);
            workshop.setCapacity(capacity);
            workshop.setActive(active);
            return workshopRepository.save(workshop);
        });
    }

    @Transactional(readOnly = true)
    public List<Workshop> getAllActiveWorkshops() {
        return workshopRepository.findByActiveTrueWithRegistrations();
    }

    @Transactional(readOnly = true)
    public List<Workshop> getUpcomingWorkshops() {
        return workshopRepository.findUpcomingWorkshops(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Optional<Workshop> getWorkshopById(Long id) {
        return workshopRepository.findByIdWithRegistrations(id);
    }

    @Transactional
    public boolean deleteWorkshop(Long id) {
        if (workshopRepository.existsById(id)) {
            workshopRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @Transactional
    public Optional<WorkshopRegistration> registerParticipant(Workshop workshop, User user) {
        if (registrationRepository.findByWorkshopAndUser(workshop, user).isPresent()) {
            logger.info("User {} is already registered for workshop {}", user.getId(), workshop.getId());
            return Optional.empty();
        }

        WorkshopRegistration registration = new WorkshopRegistration();
        registration.setWorkshop(workshop);
        registration.setUser(user);
        registration.setRegistrationTime(LocalDateTime.now());

        // Check if there's still capacity
        int registeredCount = registrationRepository.countRegisteredParticipants(workshop);
        if (registeredCount >= workshop.getCapacity()) {
            registration.setWaitlist(true);
            logger.info("Added user {} to waitlist for workshop {}", user.getId(), workshop.getId());
        } else {
            logger.info("Registered user {} for workshop {}", user.getId(), workshop.getId());
        }

        return Optional.of(registrationRepository.save(registration));
    }

    @Transactional
    public boolean cancelRegistration(Workshop workshop, User user) {
        Optional<WorkshopRegistration> registrationOpt = registrationRepository.findByWorkshopAndUser(workshop, user);
        if (registrationOpt.isEmpty()) {
            return false;
        }

        WorkshopRegistration registration = registrationOpt.get();
        registrationRepository.delete(registration);

        // If the cancelled registration was not on the waitlist, promote someone from the waitlist
        if (!registration.isWaitlist()) {
            promoteFromWaitlist(workshop);
        }

        return true;
    }

    @Transactional
    public boolean promoteFromWaitlist(Workshop workshop) {
        List<WorkshopRegistration> waitlist = registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
        if (waitlist.isEmpty()) {
            return false;
        }

        WorkshopRegistration nextInLine = waitlist.get(0);
        nextInLine.setWaitlist(false);
        registrationRepository.save(nextInLine);

        logger.info("Promoted user {} from waitlist for workshop {}",
                nextInLine.getUser().getId(), workshop.getId());
        return true;
    }

    @Transactional
    public Optional<WorkshopRegistration> manuallyAddParticipant(Workshop workshop, User user, boolean addToWaitlist) {
        Optional<WorkshopRegistration> existingRegistration = registrationRepository.findByWorkshopAndUser(workshop, user);

        if (existingRegistration.isPresent()) {
            WorkshopRegistration registration = existingRegistration.get();
            // Update waitlist status if different
            if (registration.isWaitlist() != addToWaitlist) {
                registration.setWaitlist(addToWaitlist);
                return Optional.of(registrationRepository.save(registration));
            }
            return existingRegistration;
        }

        WorkshopRegistration registration = new WorkshopRegistration();
        registration.setWorkshop(workshop);
        registration.setUser(user);
        registration.setRegistrationTime(LocalDateTime.now());
        registration.setWaitlist(addToWaitlist);

        return Optional.of(registrationRepository.save(registration));
    }

    @Transactional(readOnly = true)
    public List<WorkshopRegistration> getWorkshopParticipants(Workshop workshop) {
        return registrationRepository.findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(workshop);
    }

    @Transactional(readOnly = true)
    public List<WorkshopRegistration> getWorkshopWaitlist(Workshop workshop) {
        return registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
    }

    @Transactional(readOnly = true)
    public List<WorkshopRegistration> getUserRegistrations(User user) {
        return registrationRepository.findByUserOrderByRegistrationTimeDesc(user);
    }

    /**
     * Получает список мастер-классов, на которые зарегистрирован пользователь,
     * с жадной загрузкой всех необходимых данных для безопасного отображения
     */
    @Transactional(readOnly = true)
    public List<Object[]> getUserWorkshopsWithCounts(User user) {
        List<WorkshopRegistration> registrations = registrationRepository.findByUserOrderByRegistrationTimeDesc(user);
        List<Object[]> results = new ArrayList<>();
        
        for (WorkshopRegistration reg : registrations) {
            Workshop workshop = getWorkshopById(reg.getWorkshop().getId()).orElse(null);
            if (workshop != null) {
                int registeredCount = getWorkshopParticipants(workshop).size();
                int waitlistCount = getWorkshopWaitlist(workshop).size();
                results.add(new Object[]{workshop, registeredCount, waitlistCount, reg.isWaitlist()});
            }
        }
        
        return results;
    }

    public String formatWorkshopInfo(Workshop workshop) {
        StringBuilder sb = new StringBuilder();
        sb.append("Мастер-класс: ").append(workshop.getTitle()).append("\n")
                .append("Описание: ").append(workshop.getDescription()).append("\n")
                .append("Начало: ").append(workshop.getStartTime().format(DATE_TIME_FORMATTER)).append("\n")
                .append("Конец: ").append(workshop.getEndTime().format(DATE_TIME_FORMATTER)).append("\n")
                .append("Свободных мест: ").append(workshop.getAvailableSpots())
                .append("/").append(workshop.getCapacity()).append("\n");

        if (workshop.getWaitlistCount() > 0) {
            sb.append("В листе ожидания: ").append(workshop.getWaitlistCount()).append("\n");
        }

        return sb.toString();
    }

    public String formatWorkshopListItem(Workshop workshop) {
        return String.format("%d. %s (%s) - %d/%d мест свободно",
                workshop.getId(),
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                workshop.getAvailableSpots(),
                workshop.getCapacity());
    }

    // Безопасные методы форматирования, не зависящие от ленивых коллекций
    public String formatWorkshopInfoSafe(Workshop workshop, int registeredCount, int waitlistCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("Мастер-класс: ").append(workshop.getTitle()).append("\n")
          .append("Описание: ").append(workshop.getDescription()).append("\n")
          .append("Начало: ").append(workshop.getStartTime().format(DATE_TIME_FORMATTER)).append("\n")
          .append("Конец: ").append(workshop.getEndTime().format(DATE_TIME_FORMATTER)).append("\n")
          .append("Свободных мест: ").append(workshop.getCapacity() - registeredCount)
          .append("/").append(workshop.getCapacity()).append("\n");
        
        if (waitlistCount > 0) {
            sb.append("В листе ожидания: ").append(waitlistCount).append("\n");
        }
        
        return sb.toString();
    }
    
    public String formatWorkshopListItemSafe(Workshop workshop, int registeredCount) {
        return String.format("%d. %s (%s) - %d/%d мест свободно",
                workshop.getId(),
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                workshop.getCapacity() - registeredCount,
                workshop.getCapacity());
    }
} 