package ru.unithack.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
    private static final int CONFIRMATION_MINUTES = 15;

    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationRepository registrationRepository;
    private final NotificationService notificationService;

    @Autowired
    public WorkshopService(WorkshopRepository workshopRepository,
                           WorkshopRegistrationRepository registrationRepository,
                           NotificationService notificationService) {
        this.workshopRepository = workshopRepository;
        this.registrationRepository = registrationRepository;
        this.notificationService = notificationService;
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
            logger.info("Updating workshop with ID {}: {}", id, workshop.getTitle());
            
            // Save old values for comparison
            boolean wasActive = workshop.isActive();
            
            // Update workshop properties
            workshop.setTitle(title);
            workshop.setDescription(description);
            workshop.setStartTime(startTime);
            workshop.setEndTime(endTime);
            workshop.setCapacity(capacity);
            workshop.setActive(active);
            
            Workshop updatedWorkshop = workshopRepository.save(workshop);
            logger.info("Workshop saved with ID {}: {}", updatedWorkshop.getId(), updatedWorkshop.getTitle());
            
            // Get all participants (confirmed + waitlist) and notify them about the change
            List<WorkshopRegistration> confirmedParticipants = registrationRepository.findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(workshop);
            List<WorkshopRegistration> waitlistParticipants = registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
            
            List<WorkshopRegistration> allParticipants = new ArrayList<>();
            allParticipants.addAll(confirmedParticipants);
            allParticipants.addAll(waitlistParticipants);
            
            int participantCount = confirmedParticipants.size();
            int waitlistCount = waitlistParticipants.size();
            
            logger.info("Found {} participants and {} waitlisted users for workshop {}", 
                participantCount, waitlistCount, workshop.getId());
            
            if (!allParticipants.isEmpty()) {
                logger.info("Sending update notifications to {} users for workshop {}", 
                    allParticipants.size(), workshop.getId());
                notificationService.sendWorkshopUpdatedNotification(updatedWorkshop, allParticipants);
            } else {
                logger.info("No participants to notify for workshop {}", workshop.getId());
            }
            
            return updatedWorkshop;
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
            logger.info("Deleting workshop with ID {}", id);
            
            // First get the workshop with all participants
            Optional<Workshop> workshopOpt = workshopRepository.findByIdWithRegistrations(id);
            if (workshopOpt.isPresent()) {
                Workshop workshop = workshopOpt.get();
                logger.info("Found workshop for deletion: {}", workshop.getTitle());
                
                // Get all participants (confirmed + waitlist) for notification
                List<WorkshopRegistration> confirmedParticipants = registrationRepository.findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(workshop);
                List<WorkshopRegistration> waitlistParticipants = registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
                
                List<WorkshopRegistration> allParticipants = new ArrayList<>();
                allParticipants.addAll(confirmedParticipants);
                allParticipants.addAll(waitlistParticipants);
                
                int participantCount = confirmedParticipants.size();
                int waitlistCount = waitlistParticipants.size();
                
                logger.info("Found {} participants and {} waitlisted users for workshop {}", 
                    participantCount, waitlistCount, workshop.getId());
                
                // Send notifications before deleting
                if (!allParticipants.isEmpty()) {
                    logger.info("Sending deletion notifications to {} users for workshop {}", 
                        allParticipants.size(), workshop.getId());
                    notificationService.sendWorkshopDeletedNotification(workshop, allParticipants);
                } else {
                    logger.info("No participants to notify for workshop {}", workshop.getId());
                }
                
                // Delete the workshop
                workshopRepository.deleteById(id);
                logger.info("Workshop with ID {} successfully deleted", id);
                return true;
            }
        }
        logger.info("Workshop with ID {} not found for deletion", id);
        return false;
    }

    /**
     * Регистрирует участника на мастер-класс или добавляет в лист ожидания, если мест нет
     */
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
            // Add to waitlist
            registration.setWaitlist(true);
            
            // Set waitlist position
            Optional<Integer> maxPositionOpt = registrationRepository.findMaxWaitlistPosition(workshop);
            int position = maxPositionOpt.orElse(0) + 1;
            registration.setWaitlistPosition(position);
            
            WorkshopRegistration savedRegistration = registrationRepository.save(registration);
            
            // Send waitlist notification with position
            notificationService.sendWaitlistNotification(savedRegistration);
            
            logger.info("Added user {} to waitlist for workshop {} at position {}", 
                user.getId(), workshop.getId(), position);
            
            return Optional.of(savedRegistration);
        } else {
            // Regular registration
            registration = registrationRepository.save(registration);
            logger.info("Registered user {} for workshop {}", user.getId(), workshop.getId());
            return Optional.of(registration);
        }
    }

    /**
     * Отменяет регистрацию пользователя на мастер-класс
     */
    @Transactional
    public boolean cancelRegistration(Workshop workshop, User user) {
        Optional<WorkshopRegistration> registrationOpt = registrationRepository.findByWorkshopAndUser(workshop, user);
        if (registrationOpt.isEmpty()) {
            return false;
        }

        WorkshopRegistration registration = registrationOpt.get();
        boolean wasWaitlisted = registration.isWaitlist();
        int waitlistPosition = registration.getWaitlistPosition() != null ? registration.getWaitlistPosition() : 0;
        
        registrationRepository.delete(registration);
        
        // If it was a waitlist registration, decrement the position of all following waitlist entries
        if (wasWaitlisted && waitlistPosition > 0) {
            registrationRepository.decrementWaitlistPositionsAfter(workshop, waitlistPosition);
        } 
        // If it was a regular registration, offer the spot to all users in the waitlist
        else if (!wasWaitlisted) {
            notifyAllWaitlistUsers(workshop);
        }

        return true;
    }
    
    /**
     * Уведомляет всех пользователей в листе ожидания о появлении свободного места
     */
    @Transactional
    public void notifyAllWaitlistUsers(Workshop workshop) {
        List<WorkshopRegistration> waitlist = registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
        if (waitlist.isEmpty()) {
            return;
        }

        // Установить флаг открытой регистрации для всех в листе ожидания
        LocalDateTime deadline = LocalDateTime.now().plusMinutes(CONFIRMATION_MINUTES);
        
        for (WorkshopRegistration waitlistReg : waitlist) {
            waitlistReg.setPendingConfirmation(true);
            waitlistReg.setConfirmationDeadline(deadline);
            registrationRepository.save(waitlistReg);
            
            // Отправить уведомление каждому пользователю
            notificationService.sendConfirmationRequestToAll(waitlistReg, waitlist.size());
            
            logger.info("Sent confirmation request to user {} for workshop {}, deadline: {}",
                    waitlistReg.getUser().getId(), workshop.getId(), deadline);
        }
    }
    
    /**
     * Подтверждает участие пользователя в мастер-классе после предложения места из листа ожидания
     */
    @Transactional
    public boolean confirmWorkshopRegistration(Workshop workshop, User user) {
        Optional<WorkshopRegistration> registrationOpt = registrationRepository.findByWorkshopAndUser(workshop, user);
        if (registrationOpt.isEmpty()) {
            return false;
        }
        
        WorkshopRegistration registration = registrationOpt.get();
        
        // Check if registration is pending confirmation and deadline hasn't passed
        if (!registration.isPendingConfirmation() || 
                registration.getConfirmationDeadline() == null ||
                registration.getConfirmationDeadline().isBefore(LocalDateTime.now())) {
            return false;
        }
        
        // Проверка на количество участников, чтобы убедиться, что место всё ещё доступно
        int registeredCount = registrationRepository.countRegisteredParticipants(workshop);
        if (registeredCount >= workshop.getCapacity()) {
            // Место уже занято другим участником из листа ожидания
            notificationService.sendSpotTakenNotification(registration);
            
            // Сбросить флаг подтверждения
            registration.setPendingConfirmation(false);
            registration.setConfirmationDeadline(null);
            registrationRepository.save(registration);
            
            return false;
        }
        
        // Move from waitlist to confirmed participants
        int position = registration.getWaitlistPosition() != null ? registration.getWaitlistPosition() : 0;
        
        registration.setWaitlist(false);
        registration.setPendingConfirmation(false);
        registration.setConfirmationDeadline(null);
        registration.setWaitlistPosition(null);
        
        registrationRepository.save(registration);
        
        // Update waitlist positions for others
        if (position > 0) {
            registrationRepository.decrementWaitlistPositionsAfter(workshop, position);
        }
        
        // Send confirmation notification
        notificationService.sendRegistrationConfirmedNotification(registration);
        
        // Закрыть возможность подтверждения для остальных
        closeConfirmationForOthers(workshop, user);
        
        logger.info("User {} confirmed registration for workshop {}", user.getId(), workshop.getId());
        return true;
    }
    
    /**
     * Закрывает возможность подтверждения для всех остальных пользователей в листе ожидания
     */
    @Transactional
    private void closeConfirmationForOthers(Workshop workshop, User confirmedUser) {
        List<WorkshopRegistration> waitlistUsers = registrationRepository.findByWorkshopAndWaitlistTrueOrderByRegistrationTimeAsc(workshop);
        
        for (WorkshopRegistration waitlistReg : waitlistUsers) {
            if (!waitlistReg.getUser().getId().equals(confirmedUser.getId()) && waitlistReg.isPendingConfirmation()) {
                waitlistReg.setPendingConfirmation(false);
                waitlistReg.setConfirmationDeadline(null);
                registrationRepository.save(waitlistReg);
                
                // Уведомить пользователя, что место уже занято
                notificationService.sendSpotTakenNotification(waitlistReg);
                
                logger.info("Closed confirmation for user {} as spot was taken by user {}", 
                        waitlistReg.getUser().getId(), confirmedUser.getId());
            }
        }
    }
    
    /**
     * Проверяет истекшие подтверждения каждую минуту и удаляет пользователей из листа ожидания
     */
    @Scheduled(fixedRate = 60000) // Каждую минуту (60000 мс)
    @Transactional
    public void processExpiredConfirmations() {
        logger.debug("Checking for expired confirmations");
        LocalDateTime now = LocalDateTime.now();
        
        // Get all active workshops
        List<Workshop> activeWorkshops = workshopRepository.findByActiveTrue();
        
        for (Workshop workshop : activeWorkshops) {
            // Find expired confirmations for this workshop
            List<WorkshopRegistration> expiredConfirmations = 
                registrationRepository.findExpiredConfirmations(workshop, now);
            
            for (WorkshopRegistration expiredReg : expiredConfirmations) {
                // Send notification about expired confirmation
                notificationService.sendConfirmationExpiredNotification(expiredReg);
                
                // Если пользователь не подтвердил участие, удаляем его из листа ожидания
                if (expiredReg.isWaitlist()) {
                    int position = expiredReg.getWaitlistPosition() != null ? expiredReg.getWaitlistPosition() : 0;
                    
                    // Удаляем пользователя
                    registrationRepository.delete(expiredReg);
                    
                    // Обновляем позиции в листе ожидания
                    if (position > 0) {
                        registrationRepository.decrementWaitlistPositionsAfter(workshop, position);
                    }
                    
                    logger.info("Removed user {} from waitlist due to expired confirmation", 
                        expiredReg.getUser().getId());
                } else {
                    // Сбрасываем флаг подтверждения
                    expiredReg.setPendingConfirmation(false);
                    expiredReg.setConfirmationDeadline(null);
                    registrationRepository.save(expiredReg);
                    
                    logger.info("Reset confirmation status for user {} for workshop {}", 
                        expiredReg.getUser().getId(), workshop.getId());
                }
            }
            
            // Проверяем, есть ли свободные места для следующего уведомления
            int registeredCount = registrationRepository.countRegisteredParticipants(workshop);
            if (registeredCount < workshop.getCapacity() && !expiredConfirmations.isEmpty()) {
                // Если есть свободные места и истекли какие-то подтверждения, 
                // уведомляем всех оставшихся в листе ожидания
                notifyAllWaitlistUsers(workshop);
            }
        }
    }

    @Transactional
    public Optional<WorkshopRegistration> manuallyAddParticipant(Workshop workshop, User user, boolean addToWaitlist) {
        Optional<WorkshopRegistration> existingRegistration = registrationRepository.findByWorkshopAndUser(workshop, user);

        if (existingRegistration.isPresent()) {
            WorkshopRegistration registration = existingRegistration.get();
            // Update waitlist status if different
            if (registration.isWaitlist() != addToWaitlist) {
                if (addToWaitlist) {
                    // Add to waitlist
                    Optional<Integer> maxPositionOpt = registrationRepository.findMaxWaitlistPosition(workshop);
                    int position = maxPositionOpt.orElse(0) + 1;
                    registration.setWaitlistPosition(position);
                } else {
                    // Remove from waitlist
                    int oldPosition = registration.getWaitlistPosition() != null ? registration.getWaitlistPosition() : 0;
                    registration.setWaitlistPosition(null);
                    
                    if (oldPosition > 0) {
                        registrationRepository.decrementWaitlistPositionsAfter(workshop, oldPosition);
                    }
                }
                
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
        
        if (addToWaitlist) {
            // Set waitlist position
            Optional<Integer> maxPositionOpt = registrationRepository.findMaxWaitlistPosition(workshop);
            int position = maxPositionOpt.orElse(0) + 1;
            registration.setWaitlistPosition(position);
        }

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
                results.add(new Object[]{
                    workshop, 
                    registeredCount, 
                    waitlistCount, 
                    reg.isWaitlist(),
                    reg.getWaitlistPosition()
                });
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