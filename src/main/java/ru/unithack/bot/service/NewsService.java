package ru.unithack.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.unithack.bot.domain.model.NewsPost;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;
import ru.unithack.bot.infrastructure.repository.NewsPostRepository;
import ru.unithack.bot.infrastructure.repository.WorkshopRegistrationRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NewsService {

    private static final Logger logger = LoggerFactory.getLogger(NewsService.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Value("${app.uploads.news-images:/uploads/news}")
    private String newsImagesUploadPath;

    private final NewsPostRepository newsPostRepository;
    private final WorkshopRegistrationRepository workshopRegistrationRepository;
    private final NotificationService notificationService;

    @Autowired
    public NewsService(NewsPostRepository newsPostRepository,
                       WorkshopRegistrationRepository workshopRegistrationRepository,
                       NotificationService notificationService) {
        this.newsPostRepository = newsPostRepository;
        this.workshopRegistrationRepository = workshopRegistrationRepository;
        this.notificationService = notificationService;
    }

    /**
     * Сохраняет изображение для новости
     */
    public String saveImage(byte[] imageData, String originalFileName) throws IOException {
        // Создаем имя файла
        String fileExtension = getFileExtension(originalFileName);
        String uniqueFileName = UUID.randomUUID().toString() + fileExtension;
        
        // Создаем директорию для загрузки, если она не существует
        Path uploadPath = Paths.get(newsImagesUploadPath);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // Сохраняем файл
        Path filePath = uploadPath.resolve(uniqueFileName);
        Files.write(filePath, imageData);
        
        return filePath.toString();
    }
    
    /**
     * Получает расширение файла из его имени
     */
    private String getFileExtension(String fileName) {
        int lastIndexOf = fileName.lastIndexOf(".");
        if (lastIndexOf == -1) {
            return ""; // Нет расширения
        }
        return fileName.substring(lastIndexOf);
    }

    /**
     * Создает глобальную новость для всех пользователей
     */
    @Transactional
    public NewsPost createGlobalNews(String title, String content, String imagePath, User author) {
        NewsPost newsPost = new NewsPost();
        newsPost.setTitle(title);
        newsPost.setContent(content);
        newsPost.setImagePath(imagePath);
        newsPost.setGlobal(true);
        newsPost.setCreatedBy(author);
        newsPost.setCreatedAt(LocalDateTime.now());
        
        return newsPostRepository.save(newsPost);
    }

    /**
     * Создает новость для конкретного мастер-класса
     */
    @Transactional
    public NewsPost createWorkshopNews(String title, String content, String imagePath, 
                                     Workshop workshop, User author) {
        NewsPost newsPost = new NewsPost();
        newsPost.setTitle(title);
        newsPost.setContent(content);
        newsPost.setImagePath(imagePath);
        newsPost.setGlobal(false);
        newsPost.setWorkshop(workshop);
        newsPost.setCreatedBy(author);
        newsPost.setCreatedAt(LocalDateTime.now());
        
        return newsPostRepository.save(newsPost);
    }

    /**
     * Отправляет уведомления о новой глобальной новости всем пользователям
     */
    @Transactional(readOnly = true)
    public void notifyAllUsersAboutGlobalNews(NewsPost newsPost) {
        // Формируем сообщение
        String message = formatNewsMessage(newsPost);
        
        // Получаем всех пользователей с чат-идентификаторами
        List<Long> allUserChatIds = workshopRegistrationRepository.findAllUserChatIds();
        
        // Отправляем уведомления
        for (Long chatId : allUserChatIds) {
            if (chatId != null) {
                notificationService.sendMessageToUser(chatId, message);
            }
        }
        
        logger.info("Sent global news notification to {} users", allUserChatIds.size());
    }

    /**
     * Отправляет уведомления о новой новости для мастер-класса всем участникам
     */
    @Transactional(readOnly = true)
    public void notifyWorkshopParticipantsAboutNews(NewsPost newsPost) {
        // Формируем сообщение
        String message = formatNewsMessage(newsPost);
        
        // Получаем всех подтвержденных участников мастер-класса
        List<WorkshopRegistration> participants = workshopRegistrationRepository
                .findConfirmedByWorkshopId(newsPost.getWorkshop().getId());
        
        // Отправляем уведомления
        for (WorkshopRegistration registration : participants) {
            User user = registration.getUser();
            if (user.getUserInfo() != null && user.getUserInfo().getChatId() != null) {
                notificationService.sendMessageToUser(user.getUserInfo().getChatId(), message);
            }
        }
        
        logger.info("Sent workshop news notification about workshop {} to {} participants", 
                newsPost.getWorkshop().getId(), participants.size());
    }

    /**
     * Форматирует сообщение для отправки новости
     */
    private String formatNewsMessage(NewsPost newsPost) {
        StringBuilder message = new StringBuilder();
        
        message.append("📢 *").append(newsPost.getTitle()).append("*\n\n");
        message.append(newsPost.getContent()).append("\n\n");
        
        if (!newsPost.isGlobal() && newsPost.getWorkshop() != null) {
            message.append("🔹 Мастер-класс: ").append(newsPost.getWorkshop().getTitle()).append("\n");
        }
        
        message.append("🕒 ").append(newsPost.getCreatedAt().format(DATE_TIME_FORMATTER));
        
        return message.toString();
    }

    /**
     * Получает новость по ID
     */
    @Transactional(readOnly = true)
    public Optional<NewsPost> getNewsById(Long newsId) {
        return newsPostRepository.findById(newsId);
    }

    /**
     * Получает все глобальные новости
     */
    @Transactional(readOnly = true)
    public List<NewsPost> getAllGlobalNews() {
        return newsPostRepository.findByIsGlobalTrueOrderByCreatedAtDesc();
    }

    /**
     * Получает все новости для конкретного мастер-класса
     */
    @Transactional(readOnly = true)
    public List<NewsPost> getWorkshopNews(Workshop workshop) {
        return newsPostRepository.findByWorkshopOrderByCreatedAtDesc(workshop);
    }

    /**
     * Получает все релевантные новости для пользователя
     * (глобальные + новости мастер-классов, на которые он записан)
     */
    @Transactional
    public List<NewsPost> getRelevantNewsForUser(User user) {
        return newsPostRepository.findAllRelevantForUser(user);
    }
} 