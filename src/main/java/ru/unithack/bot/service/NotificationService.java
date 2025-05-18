package ru.unithack.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;
import ru.unithack.bot.infrastructure.repository.WorkshopRepository;
import ru.unithack.bot.infrastructure.repository.WorkshopRegistrationRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    
    @Value("${app.telegram-token}")
    private String telegramToken;
    
    private TelegramBot telegramBot;
    private final WorkshopRepository workshopRepository;
    private final WorkshopRegistrationRepository registrationRepository;
    
    @Autowired
    public NotificationService(WorkshopRepository workshopRepository,
                              WorkshopRegistrationRepository registrationRepository,
                              @Value("${app.telegram-token}") String telegramToken) {
        this.workshopRepository = workshopRepository;
        this.registrationRepository = registrationRepository;
        this.telegramToken = telegramToken;
        this.telegramBot = new TelegramBot(telegramToken);
        logger.info("NotificationService initialized with Telegram token");
    }
    
    /**
     * Отправляет сообщение пользователю по его chatId
     */
    public void sendMessageToUser(Long chatId, String message) {
        try {
            // Ensure telegramBot is initialized
            if (telegramBot == null) {
                telegramBot = new TelegramBot(telegramToken);
                logger.info("Re-initialized telegramBot in sendMessageToUser");
            }
            
            // Log the message being sent
            logger.info("Sending message to chatId {}: {}", chatId, message.substring(0, Math.min(50, message.length())) + "...");
            
            // Используем без parseMode для совместимости, как в TelegramBotService
            telegramBot.execute(new SendMessage(chatId, message));
            logger.info("Message sent successfully to chatId {}", chatId);
        } catch (Exception e) {
            logger.error("Error sending message to chatId {}: {}", chatId, e.getMessage(), e);
        }
    }
    
    /**
     * Отправляет уведомление о добавлении в лист ожидания с указанием позиции
     */
    public void sendWaitlistNotification(WorkshopRegistration registration) {
        User user = registration.getUser();
        Workshop workshop = registration.getWorkshop();
        Integer position = registration.getWaitlistPosition();
        
        if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null && position != null) {
            String message = String.format(
                "⏳ Вы добавлены в лист ожидания на мастер-класс:\n" +
                "📌 %s\n" +
                "🕒 %s\n\n" +
                "Ваш текущий номер в очереди: %d\n\n" +
                "Мы уведомим вас, когда появится свободное место.",
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                position
            );
            
            sendMessageToUser(user.getUserInfo().getChatId(), message);
            logger.info("Sent waitlist notification to user {}, position {}", user.getId(), position);
        }
    }
    
    /**
     * Отправляет запрос на подтверждение участия в мастер-классе всем пользователям в листе ожидания
     */
    public void sendConfirmationRequestToAll(WorkshopRegistration registration, int totalWaitingUsers) {
        User user = registration.getUser();
        Workshop workshop = registration.getWorkshop();
        LocalDateTime deadline = registration.getConfirmationDeadline();
        
        if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null && deadline != null) {
            String message = String.format(
                "🎉 Появилось свободное место!\n\n" +
                "На мастер-классе освободилось место. Вы и ещё %d человек(а) в листе ожидания получили это уведомление:\n" +
                "📌 %s\n" +
                "🕒 %s\n\n" +
                "⚠️ Внимание! Место получит тот, кто первым подтвердит участие.\n\n" +
                "Вы можете подтвердить участие в мастер-классе в течение 15 минут.\n" +
                "Для подтверждения введите команду:\n\n" +
                "/confirm_workshop %d\n\n" +
                "Если вы не подтвердите участие до %s или другой участник подтвердит быстрее вас, вы останетесь в листе ожидания.",
                totalWaitingUsers - 1,
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                workshop.getId(),
                deadline.format(DATE_TIME_FORMATTER)
            );
            
            sendMessageToUser(user.getUserInfo().getChatId(), message);
            logger.info("Sent confirmation request to all waitlisted users, including user {} for workshop {}", 
                user.getId(), workshop.getId());
        }
    }
    
    /**
     * Отправляет уведомление о том, что место уже занято другим участником
     */
    public void sendSpotTakenNotification(WorkshopRegistration registration) {
        User user = registration.getUser();
        Workshop workshop = registration.getWorkshop();
        
        if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
            String message = String.format(
                "⏱ Место уже занято\n\n" +
                "К сожалению, другой участник из листа ожидания подтвердил участие раньше вас:\n" +
                "📌 %s\n" +
                "🕒 %s\n\n" +
                "Вы остаётесь в листе ожидания. Мы уведомим вас, когда появится новое свободное место.",
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER)
            );
            
            sendMessageToUser(user.getUserInfo().getChatId(), message);
            logger.info("Sent spot taken notification to user {} for workshop {}", 
                user.getId(), workshop.getId());
        }
    }
    
    /**
     * Отправляет уведомление об истечении срока подтверждения и удалении из листа ожидания
     */
    public void sendConfirmationExpiredNotification(WorkshopRegistration registration) {
        User user = registration.getUser();
        Workshop workshop = registration.getWorkshop();
        
        if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
            String message = String.format(
                "⌛ Срок подтверждения истек\n\n" +
                "К сожалению, вы не подтвердили участие в мастер-классе в течение 15 минут:\n" +
                "📌 %s\n" +
                "🕒 %s\n\n" +
                "Вы были удалены из листа ожидания. Если вы всё ещё хотите посетить мастер-класс, " +
                "вам нужно будет заново записаться в лист ожидания командой:\n\n" +
                "/register_workshop %d",
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                workshop.getId()
            );
            
            sendMessageToUser(user.getUserInfo().getChatId(), message);
            logger.info("Sent confirmation expired notification to user {} for workshop {}", 
                user.getId(), workshop.getId());
        }
    }
    
    /**
     * Отправляет подтверждение успешной регистрации на мастер-класс
     */
    public void sendRegistrationConfirmedNotification(WorkshopRegistration registration) {
        User user = registration.getUser();
        Workshop workshop = registration.getWorkshop();
        
        if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
            String message = String.format(
                "✅ Участие подтверждено!\n\n" +
                "Вы успешно подтвердили участие в мастер-классе:\n" +
                "📌 %s\n" +
                "🕒 %s\n\n" +
                "Ждем вас на мероприятии!",
                workshop.getTitle(),
                workshop.getStartTime().format(DATE_TIME_FORMATTER)
            );
            
            sendMessageToUser(user.getUserInfo().getChatId(), message);
            logger.info("Sent registration confirmed notification to user {} for workshop {}", 
                user.getId(), workshop.getId());
        }
    }
    
    /**
     * Отправляет уведомление о изменении мастер-класса всем участникам и ожидающим
     */
    public void sendWorkshopUpdatedNotification(Workshop workshop, List<WorkshopRegistration> allParticipants) {
        for (WorkshopRegistration registration : allParticipants) {
            User user = registration.getUser();
            if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
                String waitlistInfo = registration.isWaitlist() ? 
                    String.format(" (вы в листе ожидания, позиция: %d)", 
                        registration.getWaitlistPosition() != null ? registration.getWaitlistPosition() : 0) : 
                    "";
                
                String message = String.format(
                    "📝 Изменение в мастер-классе\n\n" +
                    "Мастер-класс, на который вы записаны%s, был обновлен:\n\n" +
                    "📌 %s\n" +
                    "🕒 %s - %s\n" +
                    "📄 %s\n\n" +
                    "Вы по-прежнему записаны на этот мастер-класс. Если вы хотите отменить запись, используйте команду:\n" +
                    "/cancel_workshop %d",
                    waitlistInfo,
                    workshop.getTitle(),
                    workshop.getStartTime().format(DATE_TIME_FORMATTER),
                    workshop.getEndTime().format(DATE_TIME_FORMATTER),
                    workshop.getDescription(),
                    workshop.getId()
                );
                
                sendMessageToUser(user.getUserInfo().getChatId(), message);
                logger.info("Sent workshop update notification to user {} for workshop {}", 
                    user.getId(), workshop.getId());
            }
        }
    }
    
    /**
     * Отправляет уведомление об удалении мастер-класса всем участникам и ожидающим
     */
    public void sendWorkshopDeletedNotification(Workshop workshop, List<WorkshopRegistration> allParticipants) {
        for (WorkshopRegistration registration : allParticipants) {
            User user = registration.getUser();
            if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
                String waitlistInfo = registration.isWaitlist() ? 
                    " (вы были в листе ожидания)" : "";
                
                String message = String.format(
                    "❌ Мастер-класс отменен\n\n" +
                    "К сожалению, мастер-класс, на который вы были записаны%s, был отменен:\n\n" +
                    "📌 %s\n" +
                    "🕒 %s\n\n" +
                    "Ваша запись была автоматически удалена.",
                    waitlistInfo,
                    workshop.getTitle(),
                    workshop.getStartTime().format(DATE_TIME_FORMATTER)
                );
                
                sendMessageToUser(user.getUserInfo().getChatId(), message);
                logger.info("Sent workshop deletion notification to user {} for workshop {}", 
                    user.getId(), workshop.getId());
            }
        }
    }
    
    /**
     * Отправляет напоминание о мастер-классе всем зарегистрированным участникам
     */
    public void sendWorkshopReminder(Workshop workshop, List<WorkshopRegistration> participants) {
        for (WorkshopRegistration registration : participants) {
            if (registration.getUser().getUserInfo() != null && 
                registration.getUser().getUserInfo().getChatId() != null) {
                
                Long chatId = registration.getUser().getUserInfo().getChatId();
                String message = String.format(
                    "⏰ Напоминание о мастер-классе\n\n" +
                    "Вы записаны на мастер-класс, который скоро начнется:\n" +
                    "📌 %s\n" +
                    "🕒 %s\n" +
                    "📝 %s\n\n" +
                    "Ждем вас!",
                    workshop.getTitle(),
                    workshop.getStartTime().format(DATE_TIME_FORMATTER),
                    workshop.getDescription()
                );
                
                sendMessageToUser(chatId, message);
                logger.info("Sent workshop reminder to user {} for workshop {}", 
                           registration.getUser().getId(), workshop.getId());
            }
        }
    }
    
    /**
     * Проверка и отправка напоминаний по расписанию (каждый час)
     */
    @Scheduled(fixedRate = 3600000) // Каждый час (3600000 мс)
    @Transactional(readOnly = true)
    public void sendScheduledReminders() {
        logger.info("Running scheduled workshop reminders check");
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneDayFromNow = now.plusDays(1);
        
        // Получаем активные мастер-классы, которые начнутся в течение ближайших 24 часов
        List<Workshop> upcomingWorkshops = workshopRepository.findByActiveTrueAndStartTimeBetween(
            now, oneDayFromNow);
        
        for (Workshop workshop : upcomingWorkshops) {
            List<WorkshopRegistration> participants = 
                registrationRepository.findByWorkshopAndWaitlistFalseOrderByRegistrationTimeAsc(workshop);
            
            Duration timeUntilStart = Duration.between(now, workshop.getStartTime());
            long hoursUntilStart = timeUntilStart.toHours();
            
            // Отправляем напоминание за 24 часа до начала
            if (hoursUntilStart <= 24 && hoursUntilStart >= 23) {
                sendDayBeforeReminder(workshop, participants);
            }
            
            // Отправляем напоминание за 1 час до начала
            if (hoursUntilStart <= 1 && timeUntilStart.toMinutes() >= 55) {
                sendWorkshopReminder(workshop, participants);
            }
        }
    }
    
    /**
     * Отправляет напоминание за день до мастер-класса
     */
    private void sendDayBeforeReminder(Workshop workshop, List<WorkshopRegistration> participants) {
        for (WorkshopRegistration registration : participants) {
            if (registration.getUser().getUserInfo() != null && 
                registration.getUser().getUserInfo().getChatId() != null) {
                
                Long chatId = registration.getUser().getUserInfo().getChatId();
                String message = String.format(
                    "📅 Напоминание о мастер-классе завтра\n\n" +
                    "Напоминаем, что завтра у вас мастер-класс:\n" +
                    "📌 %s\n" +
                    "🕒 %s\n" +
                    "📝 %s\n\n" +
                    "Если у вас изменились планы, вы можете отменить запись командой:\n" +
                    "/cancel_workshop %d",
                    workshop.getTitle(),
                    workshop.getStartTime().format(DATE_TIME_FORMATTER),
                    workshop.getDescription(),
                    workshop.getId()
                );
                
                sendMessageToUser(chatId, message);
                logger.info("Sent day-before reminder to user {} for workshop {}", 
                           registration.getUser().getId(), workshop.getId());
            }
        }
    }
} 