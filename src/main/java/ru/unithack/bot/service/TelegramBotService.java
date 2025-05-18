package ru.unithack.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.GetFileResponse;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.enums.UserRole;
import ru.unithack.bot.domain.model.NewsPost;
import ru.unithack.bot.domain.model.UserInfo;
import ru.unithack.bot.domain.model.Workshop;
import ru.unithack.bot.domain.model.WorkshopRegistration;
import ru.unithack.bot.infrastructure.repository.UserRepository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TelegramBotService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @Value("${app.telegram-token}")
    private String telegramToken;
    
    @Value("${app.telegram-bot-username:your_bot}")
    private String botUsername;

    private TelegramBot telegramBot;
    private final UserService userService;
    private final RoleService roleService;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final WorkshopService workshopService;
    private final NewsService newsService;
    
    // Map для хранения временных данных создания новостей (chatId -> NewsCreationState)
    private final Map<Long, NewsCreationState> newsCreationStates = new HashMap<>();

    @Autowired
    public TelegramBotService(UserService userService,
                              RoleService roleService,
                              UserRepository userRepository,
                              QrCodeService qrCodeService,
                              WorkshopService workshopService,
                              NewsService newsService) {
        this.userService = userService;
        this.roleService = roleService;
        this.userRepository = userRepository;
        this.qrCodeService = qrCodeService;
        this.workshopService = workshopService;
        this.newsService = newsService;
    }

    @PostConstruct
    public void init() {
        telegramBot = new TelegramBot(telegramToken);
        telegramBot.setUpdatesListener(updates -> {
            try {
                processUpdates(updates);
            } catch (Exception e) {
                logger.error("Error processing updates", e);
            }
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    private void processUpdates(List<Update> updates) {
        for (Update update : updates) {
            try {
                if (update.message() != null && update.message().text() != null) {
                    processMessage(update.message());
                } else if (update.callbackQuery() != null) {
                    processCallbackQuery(update);
                }
            } catch (Exception e) {
                logger.error("Error processing update", e);
            }
        }
    }

    /**
     * Обрабатывает нажатия на inline-кнопки
     */
    private void processCallbackQuery(Update update) {
        String callbackData = update.callbackQuery().data();
        Long chatId = update.callbackQuery().from().id();
        
        logger.info("Received callback: {} from chatId: {}", callbackData, chatId);
        
        if (callbackData.startsWith("mark_attendance:")) {
            // Формат: mark_attendance:workshop_id:user_id:status
            String[] parts = callbackData.split(":");
            if (parts.length >= 4) {
                try {
                    Long workshopId = Long.parseLong(parts[1]);
                    Long userId = Long.parseLong(parts[2]);
                    boolean status = Boolean.parseBoolean(parts[3]);
                    
                    markAttendanceFromCallback(chatId, workshopId, userId, status);
                } catch (Exception e) {
                    logger.error("Error processing mark_attendance callback", e);
                    sendMessage(chatId, "Ошибка при обработке запроса на отметку посещения");
                }
            }
        } else if (callbackData.startsWith("select_workshop_for_news:")) {
            // Формат: select_workshop_for_news:workshop_id
            String[] parts = callbackData.split(":");
            if (parts.length >= 2) {
                try {
                    Long workshopId = Long.parseLong(parts[1]);
                    selectWorkshopForNews(chatId, workshopId);
                } catch (Exception e) {
                    logger.error("Error processing select_workshop_for_news callback", e);
                    sendMessage(chatId, "Ошибка при выборе мастер-класса для новости");
                }
            }
        } else if (callbackData.startsWith("register_workshop:")) {
            // Формат: register_workshop:workshop_id
            String[] parts = callbackData.split(":");
            if (parts.length >= 2) {
                try {
                    Long workshopId = Long.parseLong(parts[1]);
                    registerWorkshopFromCallback(chatId, workshopId);
                } catch (Exception e) {
                    logger.error("Error processing register_workshop callback", e);
                    sendMessage(chatId, "Ошибка при обработке запроса на запись на мастер-класс");
                }
            }
        } else if (callbackData.startsWith("cancel_workshop:")) {
            // Формат: cancel_workshop:workshop_id
            String[] parts = callbackData.split(":");
            if (parts.length >= 2) {
                try {
                    Long workshopId = Long.parseLong(parts[1]);
                    cancelWorkshopFromCallback(chatId, workshopId);
                } catch (Exception e) {
                    logger.error("Error processing cancel_workshop callback", e);
                    sendMessage(chatId, "Ошибка при обработке запроса на отмену записи на мастер-класс");
                }
            }
        } else if (callbackData.startsWith("confirm_workshop:")) {
            // Формат: confirm_workshop:workshop_id
            String[] parts = callbackData.split(":");
            if (parts.length >= 2) {
                try {
                    Long workshopId = Long.parseLong(parts[1]);
                    confirmWorkshopFromCallback(chatId, workshopId);
                } catch (Exception e) {
                    logger.error("Error processing confirm_workshop callback", e);
                    sendMessage(chatId, "Ошибка при обработке запроса на подтверждение записи на мастер-класс");
                }
            }
        }
    }
    
    /**
     * Обрабатывает отметку посещения из колбэка кнопки
     */
    private void markAttendanceFromCallback(Long organizerChatId, Long workshopId, Long userId, boolean status) {
        userService.findUserByChatId(organizerChatId).ifPresentOrElse(
                organizer -> {
                    if (!(userService.hasRole(organizer.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(organizer.getId(), UserRole.ADMIN))) {
                        sendMessage(organizerChatId, "У вас нет прав на выполнение этой команды");
                        return;
                    }
                    
                    // Используем переданный статус из колбэка (теперь всегда true)
                    
                    workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                            workshop -> {
                                userService.findUserById(userId).ifPresentOrElse(
                                        participant -> {
                                            boolean success = workshopService.markAttendance(workshop, participant, status, organizer.getId());
                                            if (success) {
                                                String statusMsg = status ? "✅ отмечен как присутствующий" : "❌ отмечен как отсутствующий";
                                                sendMessage(organizerChatId, String.format("Участник %s %s на мастер-классе \"%s\"",
                                                        participant.getUserInfo().getName(),
                                                        statusMsg,
                                                        workshop.getTitle()));
                                            } else {
                                                sendMessage(organizerChatId, "Не удалось отметить посещение. Проверьте, зарегистрирован ли участник на мастер-класс");
                                            }
                                        },
                                        () -> sendMessage(organizerChatId, "Пользователь не найден")
                                );
                            },
                            () -> sendMessage(organizerChatId, "Мастер-класс не найден")
                    );
                },
                () -> sendMessage(organizerChatId, "Вы не зарегистрированы в системе")
        );
    }
    
    /**
     * Обрабатывает запись на мастер-класс из колбэка кнопки
     */
    @Transactional
    private void registerWorkshopFromCallback(Long chatId, Long workshopId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                            workshop -> {
                                if (!workshop.isActive()) {
                                    sendMessage(chatId, "Мастер-класс неактивен или отменен.");
                                    return;
                                }

                                workshopService.registerParticipant(workshop, user).ifPresentOrElse(
                                        registration -> {
                                            if (registration.isWaitlist()) {
                                                sendMessage(chatId, "Вы добавлены в лист ожидания на мастер-класс \"" +
                                                        workshop.getTitle() + "\".");
                                            } else {
                                                sendMessage(chatId, "Вы успешно записаны на мастер-класс \"" +
                                                        workshop.getTitle() + "\".");
                                            }
                                        },
                                        () -> sendMessage(chatId, "Вы уже записаны на этот мастер-класс.")
                                );
                            },
                            () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                    );
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }
    
    /**
     * Обрабатывает отмену записи на мастер-класс из колбэка кнопки
     */
    @Transactional
    private void cancelWorkshopFromCallback(Long chatId, Long workshopId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                            workshop -> {
                                boolean success = workshopService.cancelRegistration(workshop, user);
                                if (success) {
                                    sendMessage(chatId, "Запись на мастер-класс \"" +
                                            workshop.getTitle() + "\" успешно отменена.");
                                } else {
                                    sendMessage(chatId, "Вы не записаны на этот мастер-класс.");
                                }
                            },
                            () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                    );
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }
    
    /**
     * Обрабатывает подтверждение записи на мастер-класс из колбэка кнопки
     */
    @Transactional
    private void confirmWorkshopFromCallback(Long chatId, Long workshopId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                            workshop -> {
                                if (!workshop.isActive()) {
                                    sendMessage(chatId, "Мастер-класс неактивен или отменен.");
                                    return;
                                }

                                boolean confirmed = workshopService.confirmWorkshopRegistration(workshop, user);
                                if (confirmed) {
                                    sendMessage(chatId, "Вы успешно подтвердили участие в мастер-классе \"" +
                                            workshop.getTitle() + "\".");
                                } else {
                                    sendMessage(chatId, "Не удалось подтвердить участие. Возможно, вы не находитесь в листе ожидания или срок подтверждения истек.");
                                }
                            },
                            () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                    );
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    /**
     * Обрабатывает выбор мастер-класса для новости
     */
    private void selectWorkshopForNews(Long chatId, Long workshopId) {
        NewsCreationState state = newsCreationStates.get(chatId);
        if (state == null || state.getStep() != NewsCreationStep.WAITING_FOR_WORKSHOP_SELECTION) {
            sendMessage(chatId, "Произошла ошибка. Пожалуйста, начните создание новости заново.");
            newsCreationStates.remove(chatId);
            return;
        }
        
        // Получаем мастер-класс по ID
        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
            workshop -> {
                // Сохраняем ID мастер-класса в состоянии
                state.setWorkshopId(workshopId);
                
                // Публикуем новость для мастер-класса
                publishWorkshopNews(chatId, state, workshop);
            },
            () -> {
                sendMessage(chatId, "Мастер-класс с указанным ID не найден. Пожалуйста, попробуйте снова.");
                // Возвращаем кнопки выбора мастер-класса
                showWorkshopSelectionButtons(chatId, state);
            }
        );
    }

    /**
     * Обрабатывает входящие сообщения
     */
    @Transactional
    protected void processMessage(Message message) {
        if (message.text() == null) {
            processNonTextMessage(message);
            return;
        }

        String text = message.text().trim();
        User telegramUser = message.from();
        Long chatId = telegramUser.id();
        String username = telegramUser.username();
        String firstName = telegramUser.firstName() != null ? telegramUser.firstName() : "";
        String lastName = telegramUser.lastName() != null ? telegramUser.lastName() : "";
        String fullName = (firstName + " " + lastName).trim();

        // Обработка состояния создания новости
        if (newsCreationStates.containsKey(chatId)) {
            processNewsCreationState(chatId, text, message);
            return;
        }

        updateUserInfoIfChanged(chatId, fullName, username);
        
        // Обработка команд
        if (text.startsWith("/start")) {
            processStartCommand(telegramUser, chatId, fullName, username, text);
        } else if (text.equals("/help")) {
            processHelpCommand(chatId);
        } else if (text.equals("/my_roles")) {
            processMyRolesCommand(chatId);
        } else if (text.equals("/list_users") || text.equals("/users")) {
            processListUsersCommand(chatId);
        } else if (text.equals("/my_qr")) {
            processMyQrCommand(chatId);
        } else if (text.startsWith("/user_qr ")) {
            processUserQrCommand(chatId, text);
        } else if (text.equals("/workshops") || text.equals("/list_workshops")) {
            processListWorkshopsCommand(chatId);
        } else if (text.startsWith("/workshop_info ")) {
            processWorkshopInfoCommand(chatId, text);
        } else if (text.startsWith("/register_workshop ")) {
            processRegisterWorkshopCommand(chatId, text);
        } else if (text.startsWith("/cancel_workshop ")) {
            processCancelWorkshopCommand(chatId, text);
        } else if (text.equals("/my_workshops")) {
            processMyWorkshopsCommand(chatId);
        } else if (text.startsWith("/create_workshop ")) {
            processCreateWorkshopCommand(chatId, text);
        } else if (text.startsWith("/edit_workshop ")) {
            processEditWorkshopCommand(chatId, text);
        } else if (text.startsWith("/delete_workshop ")) {
            processDeleteWorkshopCommand(chatId, text);
        } else if (text.startsWith("/workshop_participants ")) {
            processWorkshopParticipantsCommand(chatId, text);
        } else if (text.startsWith("/add_participant ")) {
            processAddParticipantCommand(chatId, text);
        } else if (text.startsWith("/remove_participant ")) {
            processRemoveParticipantCommand(chatId, text);
        } else if (text.startsWith("/confirm_workshop ")) {
            processConfirmWorkshopCommand(chatId, text);
        } else if (text.startsWith("/scan_qr ")) {
            processScanQrCommand(chatId, text);
        } else if (text.startsWith("/add_organizer ")) {
            processAddOrganizerCommand(chatId, text);
        } else if (text.startsWith("/remove_organizer ")) {
            processRemoveOrganizerCommand(chatId, text);
        } else if (text.startsWith("/workshop_attendance ")) {
            processWorkshopAttendanceCommand(chatId, text);
        } else if (text.equals("/create_news")) {
            processCreateNewsCommand(chatId);
        } else if (text.equals("/create_workshop_news")) {
            processCreateWorkshopNewsCommand(chatId);
        } else if (text.equals("/my_news")) {
            processMyNewsCommand(chatId);
        } else {
            sendMessage(chatId, "Неизвестная команда. Введите /help для списка доступных команд.");
        }
    }

    /**
     * Обрабатывает не текстовые сообщения (фото и т.д.)
     */
    private void processNonTextMessage(Message message) {
        Long chatId = message.chat().id();
        
        // Обработка фото для новости
        if (message.photo() != null && newsCreationStates.containsKey(chatId)) {
            NewsCreationState state = newsCreationStates.get(chatId);
            if (state.getStep() == NewsCreationStep.WAITING_FOR_IMAGE) {
                processNewsImage(chatId, message);
            } else {
                sendMessage(chatId, "На данном этапе создания новости не требуется изображение.\n" +
                         "Пожалуйста, следуйте инструкциям в предыдущем сообщении.");
            }
        } else {
            sendMessage(chatId, "Я не могу обработать этот тип сообщения.");
        }
    }

    /**
     * Обрабатывает фотографию для новости
     */
    private void processNewsImage(Long chatId, Message message) {
        try {
            // Получаем объект NewsCreationState
            NewsCreationState state = newsCreationStates.get(chatId);
            
            // Получаем наибольшее по размеру фото
            com.pengrad.telegrambot.model.PhotoSize[] photoSizes = message.photo();
            com.pengrad.telegrambot.model.PhotoSize largestPhoto = photoSizes[photoSizes.length - 1];
            
            // Получаем file_id наибольшего фото
            String fileId = largestPhoto.fileId();
            
            // Получаем информацию о файле из Telegram
            GetFileResponse fileResponse = telegramBot.execute(new GetFile(fileId));
            if (!fileResponse.isOk()) {
                sendMessage(chatId, "Ошибка при получении файла. Пожалуйста, попробуйте другое изображение.");
                return;
            }
            
            // Получаем путь к файлу
            com.pengrad.telegrambot.model.File file = fileResponse.file();
            String filePath = file.filePath();
            
            // Формируем URL для скачивания
            String fileUrl = "https://api.telegram.org/file/bot" + telegramToken + "/" + filePath;
            
            // Отправляем сообщение о том, что изображение обрабатывается
            sendMessage(chatId, "Изображение получено, обрабатываю...");
            
            // Скачиваем файл - это должно быть реализовано в отдельном методе
            byte[] imageData = downloadFileFromUrl(fileUrl);
            
            // Сохраняем изображение
            String savedImagePath;
            try {
                savedImagePath = newsService.saveImage(imageData, filePath);
                // Устанавливаем путь к изображению в состояние
                state.setImagePath(savedImagePath);
                
                // Переходим к следующему шагу
                processNewsImageStep(chatId, state);
            } catch (IOException e) {
                logger.error("Error saving news image", e);
                sendMessage(chatId, "Произошла ошибка при сохранении изображения. Пожалуйста, попробуйте снова.");
                newsCreationStates.remove(chatId);
            }
        } catch (Exception e) {
            logger.error("Error processing news image", e);
            sendMessage(chatId, "Произошла ошибка при обработке изображения. Пожалуйста, попробуйте снова.");
            newsCreationStates.remove(chatId);
        }
    }
    
    /**
     * Скачивает файл по URL
     */
    private byte[] downloadFileFromUrl(String fileUrl) throws IOException {
        java.net.URL url = new java.net.URL(fileUrl);
        try (java.io.InputStream in = url.openStream()) {
            return in.readAllBytes();
        }
    }

    /**
     * Обрабатывает состояние создания новости
     */
    private void processNewsCreationState(Long chatId, String text, Message message) {
        NewsCreationState state = newsCreationStates.get(chatId);
        
        switch (state.getStep()) {
            case WAITING_FOR_TITLE:
                state.setTitle(text);
                state.setStep(NewsCreationStep.WAITING_FOR_CONTENT);
                sendMessage(chatId, "Введите содержание новости (текст):");
                break;
                
            case WAITING_FOR_CONTENT:
                state.setContent(text);
                state.setStep(NewsCreationStep.WAITING_FOR_IMAGE);
                sendMessage(chatId, "Отправьте изображение для новости (или введите /skip, чтобы пропустить):");
                break;
                
            case WAITING_FOR_IMAGE:
                if (text.equals("/skip")) {
                    processNewsImageStep(chatId, state);
                } else {
                    sendMessage(chatId, "Пожалуйста, отправьте изображение или введите /skip, чтобы пропустить этот шаг.");
                }
                break;
                
            case WAITING_FOR_WORKSHOP_SELECTION:
                // Если пользователь вводит текст вместо выбора из кнопок, пытаемся обработать как ввод ID
                try {
                    Long workshopId = Long.parseLong(text);
                    selectWorkshopForNews(chatId, workshopId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, выберите мастер-класс из предложенных или введите его ID.");
                }
                break;
            
            default:
                sendMessage(chatId, "Неизвестный шаг создания новости. Пожалуйста, начните заново.");
                newsCreationStates.remove(chatId);
                break;
        }
    }

    /**
     * Перечисление для шагов создания новости
     */
    private enum NewsCreationStep {
        WAITING_FOR_TITLE,
        WAITING_FOR_CONTENT,
        WAITING_FOR_IMAGE,
        WAITING_FOR_WORKSHOP_ID,
        WAITING_FOR_WORKSHOP_SELECTION
    }

    /**
     * Перечисление для типов новостей
     */
    private enum NewsType {
        GLOBAL,
        WORKSHOP
    }

    private void updateUserInfoIfChanged(Long chatId, String currentName, String username) {
        userService.findUserByChatId(chatId).ifPresent(user -> {
            UserInfo userInfo = user.getUserInfo();
            if (userInfo != null) {
                boolean changed = false;

                if (!currentName.equals(userInfo.getName())) {
                    logger.info("Updating user name from '{}' to '{}' for chatId {}",
                            userInfo.getName(), currentName, chatId);
                    userInfo.setName(currentName);
                    changed = true;
                }

                if (username != null && !username.equals(userInfo.getUsername())) {
                    logger.info("Updating username from '{}' to '{}' for chatId {}",
                            userInfo.getUsername(), username, chatId);
                    userInfo.setUsername(username);
                    changed = true;
                }

                if (changed) {
                    userRepository.save(user);
                }
            }
        });
    }

    private void processStartCommand(User telegramUser, Long chatId, String fullName, String username, String text) {
        // Проверяем наличие параметра attendance в команде /start
        if (text != null && text.startsWith("/start attendance_")) {
            // Обрабатываем QR-код для отметки посещения
            processAttendanceQrScan(chatId, text);
            return;
        }
        
        String qrCode = UUID.randomUUID().toString();

        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> sendMessage(chatId, "Вы уже зарегистрированы в системе!"),
                () -> {
                    ru.unithack.bot.domain.model.User user = userService.createUserWithRole(fullName, qrCode, chatId, UserRole.USER);
                    if (username != null && !username.isEmpty()) {
                        user.getUserInfo().setUsername(username);
                        userRepository.save(user);
                    }
                    sendMessage(chatId, "Вы успешно зарегистрированы как пользователь!");
                }
        );
    }

    /**
     * Обрабатывает QR-код для отметки посещения
     */
    private void processAttendanceQrScan(Long organizerChatId, String startCommand) {
        userService.findUserByChatId(organizerChatId).ifPresentOrElse(
                organizer -> {
                    if (!(userService.hasRole(organizer.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(organizer.getId(), UserRole.ADMIN))) {
                        sendMessage(organizerChatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }
                    
                    try {
                        // Извлекаем ID пользователя из параметра
                        String param = startCommand.substring("/start attendance_".length()).trim();
                        Long userId = Long.parseLong(param);
                        
                        // Находим пользователя по ID
                        userService.findUserById(userId).ifPresentOrElse(
                                participant -> {
                                    UserInfo userInfo = participant.getUserInfo();
                                    if (userInfo != null) {
                                        // Получаем список мастер-классов, на которые записан пользователь
                                        List<WorkshopRegistration> registrations = workshopService.getUserRegistrations(participant)
                                                .stream()
                                                .filter(reg -> !reg.isWaitlist())
                                                .toList();

                                        if (registrations.isEmpty()) {
                                            sendMessage(organizerChatId, String.format(
                                                    "✅ QR код отсканирован!\n\n" +
                                                    "👤 %s\n" +
                                                    "🆔 ID: %d\n\n" +
                                                    "❌ Пользователь не записан ни на один мастер-класс.",
                                                    userInfo.getName(),
                                                    userId
                                            ));
                                            return;
                                        }

                                        StringBuilder sb = new StringBuilder();
                                        sb.append("✅ QR код отсканирован!\n\n")
                                                .append("👤 ").append(userInfo.getName()).append("\n");
                                                
                                        if (userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
                                            sb.append("👤 @").append(userInfo.getUsername()).append("\n");
                                        }
                                        
                                        sb.append("🆔 ID: ").append(userId).append("\n\n")
                                                .append("Выберите мастер-класс для отметки посещения:\n\n");

                                        for (WorkshopRegistration reg : registrations) {
                                            Workshop workshop = reg.getWorkshop();
                                            String attendanceStatus = reg.isAttended() ? "✅ Присутствовал" : "❌ Не отмечен";
                                            sb.append(String.format(
                                                    "%d. %s\n%s\nСтатус: %s\n\n",
                                                    workshop.getId(),
                                                    workshop.getTitle(),
                                                    workshop.getStartTime().format(DATE_TIME_FORMATTER),
                                                    attendanceStatus
                                            ));
                                        }

                                        // Создаем сообщение
                                        SendMessage message = new SendMessage(organizerChatId, sb.toString());
                                        
                                        // Создаем клавиатуру с inline кнопками для каждого мастер-класса
                                        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                                        InlineKeyboardButton[] buttons = new InlineKeyboardButton[registrations.size()];
                                        
                                        for (int i = 0; i < registrations.size(); i++) {
                                            Workshop workshop = registrations.get(i).getWorkshop();
                                            // Используем true в качестве стандартного статуса
                                            buttons[i] = new InlineKeyboardButton("Отметить - " + workshop.getTitle())
                                                    .callbackData("mark_attendance:" + workshop.getId() + ":" + userId + ":true");
                                        }
                                        
                                        // Добавляем по одной кнопке в ряду
                                        for (InlineKeyboardButton button : buttons) {
                                            keyboardMarkup.addRow(button);
                                        }
                                        
                                        // Прикрепляем клавиатуру к сообщению
                                        message.replyMarkup(keyboardMarkup);
                                        
                                        // Отправляем сообщение с клавиатурой
                                        telegramBot.execute(message);
                                    } else {
                                        sendMessage(organizerChatId, "Пользователь найден, но информация о нем отсутствует.");
                                    }
                                },
                                () -> sendMessage(organizerChatId, "Пользователь с ID " + userId + " не найден.")
                        );
                    } catch (Exception e) {
                        logger.error("Error processing attendance QR scan", e);
                        sendMessage(organizerChatId, "Ошибка при обработке QR-кода: " + e.getMessage());
                    }
                },
                () -> sendMessage(organizerChatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    private void processAddOrganizerCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (userService.hasRole(user.getId(), UserRole.ADMIN)) {
                        String[] parts = text.split(" ", 2);
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            try {
                                Long organizerChatId = Long.parseLong(parts[1].trim());

                                boolean success = userService.addRoleToUserByChatId(organizerChatId, UserRole.ORGANIZER);
                                if (success) {
                                    userService.findUserByChatId(organizerChatId).ifPresent(organizer -> {
                                        sendMessage(chatId, "Пользователь " + organizer.getUserInfo().getName() +
                                                " (ID: " + organizerChatId + ") успешно назначен организатором!");
                                    });
                                } else {
                                    sendMessage(chatId, "Пользователь с ID " + organizerChatId + " не найден. " +
                                            "Пользователь должен сначала зарегистрироваться через /start");
                                }
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "Пожалуйста, укажите корректный ID чата пользователя: /add_organizer <chatId>");
                            }
                        } else {
                            sendMessage(chatId, "Пожалуйста, укажите ID чата организатора: /add_organizer <chatId>");
                        }
                    } else {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль администратора.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    private void processRemoveOrganizerCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (userService.hasRole(user.getId(), UserRole.ADMIN)) {
                        String[] parts = text.split(" ", 2);
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            try {
                                Long organizerChatId = Long.parseLong(parts[1].trim());

                                boolean success = userService.removeRoleFromUserByChatId(organizerChatId, UserRole.ORGANIZER);
                                if (success) {
                                    userService.findUserByChatId(organizerChatId).ifPresent(organizer -> {
                                        sendMessage(chatId, "Роль организатора успешно удалена у пользователя " +
                                                organizer.getUserInfo().getName() + " (ID: " + organizerChatId + ")");
                                    });
                                } else {
                                    sendMessage(chatId, "Не удалось удалить роль. Возможно, пользователь с ID " +
                                            organizerChatId + " не найден или не имеет роли организатора.");
                                }
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "Пожалуйста, укажите корректный ID чата пользователя: /remove_organizer <chatId>");
                            }
                        } else {
                            sendMessage(chatId, "Пожалуйста, укажите ID чата организатора: /remove_organizer <chatId>");
                        }
                    } else {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль администратора.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processMyRolesCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    Set<String> roles = roleService.getUserRoles(user.getId());
                    if (roles.isEmpty()) {
                        sendMessage(chatId, "У вас нет ролей в системе.");
                    } else {
                        sendMessage(chatId, "Ваши роли: " + String.join(", ", roles));
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processListUsersCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (userService.hasRole(user.getId(), UserRole.ADMIN)) {
                        List<ru.unithack.bot.domain.model.User> users = userService.getAllUsers();

                        if (users.isEmpty()) {
                            sendMessage(chatId, "В системе нет зарегистрированных пользователей.");
                        } else {
                            StringBuilder messageBuilder = new StringBuilder("Список пользователей:\n\n");

                            for (ru.unithack.bot.domain.model.User u : users) {
                                UserInfo info = u.getUserInfo();
                                if (info != null) {
                                    Set<String> roles = roleService.getUserRoles(u.getId());
                                    String roleStr = roles.isEmpty() ? "Нет ролей" : String.join(", ", roles);
                                    String usernameStr = info.getUsername() != null ? "@" + info.getUsername() : "Не указан";

                                    messageBuilder.append(String.format("Имя: %s\nUsername: %s\nID чата: %s\nРоли: %s\n\n",
                                            info.getName(),
                                            usernameStr,
                                            info.getChatId() != null ? info.getChatId() : "Не указан",
                                            roleStr));
                                }
                            }

                            sendMessage(chatId, messageBuilder.toString());
                        }
                    } else {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль администратора.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processMyQrCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    UserInfo userInfo = user.getUserInfo();
                    if (userInfo != null) {
                        String qrCodeContent = generateQrCodeContent(user);
                        byte[] qrCodeImage = qrCodeService.generateQrCode(qrCodeContent);

                        if (!qrCodeContent.equals(userInfo.getQrCode())) {
                            userInfo.setQrCode(qrCodeContent);
                            userRepository.save(user);
                        }

                        sendPhoto(chatId, qrCodeImage, "Ваш QR-код");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processUserQrCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                currentUser -> {
                    if (userService.hasRole(currentUser.getId(), UserRole.ADMIN) ||
                            userService.hasRole(currentUser.getId(), UserRole.ORGANIZER)) {

                        String[] parts = text.split(" ", 2);
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            try {
                                Long targetChatId = Long.parseLong(parts[1].trim());

                                userService.findUserByChatId(targetChatId).ifPresentOrElse(
                                        targetUser -> {
                                            UserInfo targetUserInfo = targetUser.getUserInfo();
                                            String qrCodeContent = generateQrCodeContent(targetUser);
                                            byte[] qrCodeImage = qrCodeService.generateQrCode(qrCodeContent);

                                            if (!qrCodeContent.equals(targetUserInfo.getQrCode())) {
                                                targetUserInfo.setQrCode(qrCodeContent);
                                                userRepository.save(targetUser);
                                            }

                                            sendPhoto(chatId, qrCodeImage, "QR-код пользователя " + targetUserInfo.getName());
                                        },
                                        () -> sendMessage(chatId, "Пользователь с ID " + targetChatId + " не найден.")
                                );
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "Пожалуйста, укажите корректный ID чата пользователя: /user_qr <chatId>");
                            }
                        } else {
                            sendMessage(chatId, "Пожалуйста, укажите ID чата пользователя: /user_qr <chatId>");
                        }
                    } else {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль администратора или организатора.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    

    @Transactional
    protected void processListWorkshopsCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    List<Workshop> workshops = workshopService.getAllActiveWorkshops();
                    if (workshops.isEmpty()) {
                        sendMessage(chatId, "В данный момент нет доступных мастер-классов.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder("Доступные мастер-классы:\n\n");
                    
                    // Получаем регистрации пользователя для проверки статуса
                    List<WorkshopRegistration> userRegistrations = workshopService.getUserRegistrations(user);
                    
                    // Создаем клавиатуру с inline кнопками для каждого мастер-класса
                    InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                    
                    for (Workshop workshop : workshops) {
                        int registeredCount = workshopService.getWorkshopParticipants(workshop).size();
                        sb.append(workshopService.formatWorkshopListItemSafe(workshop, registeredCount)).append("\n\n");
                        
                        // Проверяем статус регистрации пользователя на этот мастер-класс
                        boolean isRegistered = false;
                        boolean isInWaitlist = false;
                        
                        for (WorkshopRegistration reg : userRegistrations) {
                            if (reg.getWorkshop().getId().equals(workshop.getId())) {
                                if (reg.isWaitlist()) {
                                    isInWaitlist = true;
                                } else {
                                    isRegistered = true;
                                }
                                break;
                            }
                        }
                        
                        // Добавляем соответствующую кнопку для этого мастер-класса
                        InlineKeyboardButton button;
                        if (isRegistered || isInWaitlist) {
                            button = new InlineKeyboardButton("❌ Отменить - " + workshop.getTitle())
                                    .callbackData("cancel_workshop:" + workshop.getId());
                        } else {
                            button = new InlineKeyboardButton("✅ Записаться - " + workshop.getTitle())
                                    .callbackData("register_workshop:" + workshop.getId());
                        }
                        
                        keyboardMarkup.addRow(button);
                    }
                    
                    sb.append("\nДля получения подробной информации используйте команду /workshop_info <id>");
                    
                    // Создаем и отправляем сообщение с кнопками
                    SendMessage message = new SendMessage(chatId, sb.toString());
                    message.replyMarkup(keyboardMarkup);
                    telegramBot.execute(message);
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processWorkshopInfoCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /workshop_info <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    int registeredCount = workshopService.getWorkshopParticipants(workshop).size();
                                    int waitlistCount = workshopService.getWorkshopWaitlist(workshop).size();
                                    String info = workshopService.formatWorkshopInfoSafe(workshop, registeredCount, waitlistCount);
                                    
                                    // Проверяем статус регистрации пользователя
                                    List<WorkshopRegistration> userRegistrations = workshopService.getUserRegistrations(user);
                                    boolean isRegistered = false;
                                    boolean isInWaitlist = false;
                                    boolean canConfirm = false;
                                    
                                    for (WorkshopRegistration reg : userRegistrations) {
                                        if (reg.getWorkshop().getId().equals(workshop.getId())) {
                                            if (reg.isWaitlist()) {
                                                isInWaitlist = true;
                                                // Для простоты: предполагаем, что пользователь может подтвердить участие, 
                                                // если он в листе ожидания. Точная логика должна быть реализована 
                                                // в workshopService.confirmWorkshopRegistration
                                                canConfirm = true;
                                            } else {
                                                isRegistered = true;
                                            }
                                            break;
                                        }
                                    }
                                    
                                    // Создаем сообщение с кнопками
                                    SendMessage message = new SendMessage(chatId, info);
                                    InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                                    
                                    // Добавляем соответствующие кнопки в зависимости от статуса пользователя
                                    if (isRegistered || isInWaitlist) {
                                        // Пользователь уже зарегистрирован - показываем кнопку отмены
                                        InlineKeyboardButton cancelButton = new InlineKeyboardButton("Отменить запись")
                                                .callbackData("cancel_workshop:" + workshop.getId());
                                        keyboardMarkup.addRow(cancelButton);
                                        
                                        if (canConfirm) {
                                            // Пользователь в листе ожидания и может подтвердить участие
                                            InlineKeyboardButton confirmButton = new InlineKeyboardButton("Подтвердить участие")
                                                    .callbackData("confirm_workshop:" + workshop.getId());
                                            keyboardMarkup.addRow(confirmButton);
                                        }
                                    } else if (workshop.isActive()) {
                                        // Пользователь не зарегистрирован - показываем кнопку записи
                                        InlineKeyboardButton registerButton = new InlineKeyboardButton("Записаться")
                                                .callbackData("register_workshop:" + workshop.getId());
                                        keyboardMarkup.addRow(registerButton);
                                    }
                                    
                                    // Прикрепляем клавиатуру к сообщению
                                    message.replyMarkup(keyboardMarkup);
                                    
                                    // Отправляем сообщение с кнопками
                                    telegramBot.execute(message);
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /workshop_info <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processRegisterWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /register_workshop <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    if (!workshop.isActive()) {
                                        sendMessage(chatId, "Мастер-класс неактивен или отменен.");
                                        return;
                                    }

                                    workshopService.registerParticipant(workshop, user).ifPresentOrElse(
                                            registration -> {
                                                if (registration.isWaitlist()) {
                                                    sendMessage(chatId, "Вы добавлены в лист ожидания на мастер-класс \"" +
                                                            workshop.getTitle() + "\".");
                                                } else {
                                                    sendMessage(chatId, "Вы успешно записаны на мастер-класс \"" +
                                                            workshop.getTitle() + "\".");
                                                }
                                            },
                                            () -> sendMessage(chatId, "Вы уже записаны на этот мастер-класс.")
                                    );
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /register_workshop <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processCancelWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /cancel_workshop <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    boolean success = workshopService.cancelRegistration(workshop, user);
                                    if (success) {
                                        sendMessage(chatId, "Запись на мастер-класс \"" +
                                                workshop.getTitle() + "\" успешно отменена.");
                                    } else {
                                        sendMessage(chatId, "Вы не записаны на этот мастер-класс.");
                                    }
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /cancel_workshop <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processMyWorkshopsCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    List<Object[]> workshopsWithCounts = workshopService.getUserWorkshopsWithCounts(user);
                    if (workshopsWithCounts.isEmpty()) {
                        sendMessage(chatId, "Вы не записаны ни на один мастер-класс.");
                        return;
                    }

                    StringBuilder sb = new StringBuilder("Ваши записи на мастер-классы:\n\n");
                    
                    // Создаем клавиатуру с inline кнопками
                    InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
                    
                    for (Object[] data : workshopsWithCounts) {
                        Workshop workshop = (Workshop) data[0];
                        int registeredCount = (int) data[1];
                        boolean isWaitlist = (boolean) data[3];
                        Integer waitlistPosition = (Integer) data[4];

                        sb.append(workshopService.formatWorkshopListItemSafe(workshop, registeredCount));

                        if (isWaitlist) {
                            if (waitlistPosition != null) {
                                sb.append(String.format(" (в листе ожидания, позиция: %d)", waitlistPosition));
                            } else {
                                sb.append(" (в листе ожидания)");
                            }
                        }

                        sb.append("\n\n");
                        
                        // Добавляем кнопку отмены для каждого мастер-класса
                        InlineKeyboardButton cancelButton = new InlineKeyboardButton("❌ Отменить - " + workshop.getTitle())
                                .callbackData("cancel_workshop:" + workshop.getId());
                        keyboardMarkup.addRow(cancelButton);
                        
                        // Если пользователь в листе ожидания и может подтвердить участие, добавляем кнопку подтверждения
                        // Здесь предполагаем простую логику: если в листе ожидания, то может подтвердить
                        // Настоящую логику подтверждения после приглашения нужно реализовать в методе confirmWorkshopFromCallback
                        if (isWaitlist) {
                            InlineKeyboardButton confirmButton = new InlineKeyboardButton("✅ Подтвердить - " + workshop.getTitle())
                                    .callbackData("confirm_workshop:" + workshop.getId());
                            keyboardMarkup.addRow(confirmButton);
                        }
                    }

                    // Создаем и отправляем сообщение с кнопками
                    SendMessage message = new SendMessage(chatId, sb.toString());
                    message.replyMarkup(keyboardMarkup);
                    telegramBot.execute(message);
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processCreateWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    // Check if this is the initial call without parameters
                    if (text.trim().equals("/create_workshop")) {
                        sendMessage(chatId, "Для создания мастер-класса используйте формат:\n" +
                                "/create_workshop <название>|<описание>|<дата (дд.мм.гггг)>|<время начала (чч:мм)>|<время окончания (чч:мм)>|<количество мест>\n\n" +
                                "Например:\n" +
                                "/create_workshop Мастер-класс по программированию|Научимся создавать простую игру|20.07.2023|14:00|16:00|20");
                        return;
                    }

                    // Parse parameters
                    try {
                        String paramString = text.substring("/create_workshop".length()).trim();
                        String[] params = paramString.split("\\|");

                        if (params.length < 6) {
                            sendMessage(chatId, "Недостаточно параметров. Используйте формат:\n" +
                                    "/create_workshop <название>|<описание>|<дата (дд.мм.гггг)>|<время начала (чч:мм)>|<время окончания (чч:мм)>|<количество мест>");
                            return;
                        }

                        String title = params[0].trim();
                        String description = params[1].trim();
                        String dateStr = params[2].trim();
                        String startTimeStr = params[3].trim();
                        String endTimeStr = params[4].trim();
                        int capacity;

                        try {
                            capacity = Integer.parseInt(params[5].trim());
                        } catch (NumberFormatException e) {
                            sendMessage(chatId, "Некорректное количество мест. Укажите целое число.");
                            return;
                        }

                        if (capacity <= 0) {
                            sendMessage(chatId, "Количество мест должно быть положительным числом.");
                            return;
                        }

                        // Parse date and time
                        LocalDateTime startDateTime, endDateTime;
                        try {
                            LocalDateTime date = LocalDateTime.parse(
                                    dateStr + " " + startTimeStr,
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                            startDateTime = date;

                            LocalDateTime endDate = LocalDateTime.parse(
                                    dateStr + " " + endTimeStr,
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                            endDateTime = endDate;
                        } catch (Exception e) {
                            sendMessage(chatId, "Ошибка в формате даты или времени. Используйте формат дд.мм.гггг для даты и чч:мм для времени.");
                            return;
                        }

                        if (endDateTime.isBefore(startDateTime)) {
                            sendMessage(chatId, "Время окончания мастер-класса не может быть раньше времени начала.");
                            return;
                        }

                        // Create workshop
                        Workshop workshop = workshopService.createWorkshop(title, description, startDateTime, endDateTime, capacity);

                        sendMessage(chatId, "Мастер-класс успешно создан!\n\n" +
                                workshopService.formatWorkshopInfo(workshop) +
                                "\nID мастер-класса: " + workshop.getId());

                    } catch (Exception e) {
                        logger.error("Error creating workshop", e);
                        sendMessage(chatId, "Произошла ошибка при создании мастер-класса. Проверьте формат ввода.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processEditWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    // Check if this is the initial call with just ID
                    String[] mainParts = text.split(" ", 2);
                    if (mainParts.length < 2 || mainParts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /edit_workshop <id>");
                        return;
                    }

                    try {
                        String idPart = mainParts[1].trim();
                        Long workshopId;

                        // Check if we have full parameters or just ID
                        if (!idPart.contains("|")) {
                            workshopId = Long.parseLong(idPart);
                            workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                    workshop -> {
                                        // Show current info and format for editing
                                        String startDate = workshop.getStartTime().format(DATE_FORMATTER);
                                        String startTime = workshop.getStartTime().format(TIME_FORMATTER);
                                        String endTime = workshop.getEndTime().format(TIME_FORMATTER);

                                        String editFormat = String.format(
                                                "/edit_workshop %d|%s|%s|%s|%s|%s|%d|%b",
                                                workshop.getId(),
                                                workshop.getTitle(),
                                                workshop.getDescription(),
                                                startDate,
                                                startTime,
                                                endTime,
                                                workshop.getCapacity(),
                                                workshop.isActive()
                                        );

                                        int registeredCount = workshopService.getWorkshopParticipants(workshop).size();
                                        int waitlistCount = workshopService.getWorkshopWaitlist(workshop).size();

                                        sendMessage(chatId, "Текущая информация о мастер-классе:\n\n" +
                                                workshopService.formatWorkshopInfoSafe(workshop, registeredCount, waitlistCount) +
                                                "\n\nДля редактирования используйте следующий формат (скопируйте и измените нужные поля):\n" +
                                                editFormat);
                                    },
                                    () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                            );
                            return;
                        }

                        // Parse all parameters
                        String[] params = idPart.split("\\|");
                        if (params.length < 8) {
                            sendMessage(chatId, "Недостаточно параметров. Используйте формат:\n" +
                                    "/edit_workshop <id>|<название>|<описание>|<дата (дд.мм.гггг)>|<время начала (чч:мм)>|<время окончания (чч:мм)>|<количество мест>|<активен (true/false)>");
                            return;
                        }

                        workshopId = Long.parseLong(params[0].trim());
                        String title = params[1].trim();
                        String description = params[2].trim();
                        String dateStr = params[3].trim();
                        String startTimeStr = params[4].trim();
                        String endTimeStr = params[5].trim();
                        int capacity;
                        boolean active;

                        try {
                            capacity = Integer.parseInt(params[6].trim());
                        } catch (NumberFormatException e) {
                            sendMessage(chatId, "Некорректное количество мест. Укажите целое число.");
                            return;
                        }

                        if (capacity <= 0) {
                            sendMessage(chatId, "Количество мест должно быть положительным числом.");
                            return;
                        }

                        try {
                            active = Boolean.parseBoolean(params[7].trim());
                        } catch (Exception e) {
                            sendMessage(chatId, "Некорректное значение активности. Укажите true или false.");
                            return;
                        }

                        // Parse date and time
                        LocalDateTime startDateTime, endDateTime;
                        try {
                            LocalDateTime date = LocalDateTime.parse(
                                    dateStr + " " + startTimeStr,
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                            startDateTime = date;

                            LocalDateTime endDate = LocalDateTime.parse(
                                    dateStr + " " + endTimeStr,
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
                            endDateTime = endDate;
                        } catch (Exception e) {
                            sendMessage(chatId, "Ошибка в формате даты или времени. Используйте формат дд.мм.гггг для даты и чч:мм для времени.");
                            return;
                        }

                        if (endDateTime.isBefore(startDateTime)) {
                            sendMessage(chatId, "Время окончания мастер-класса не может быть раньше времени начала.");
                            return;
                        }

                        // Update workshop
                        workshopService.updateWorkshop(workshopId, title, description, startDateTime, endDateTime, capacity, active)
                                .ifPresentOrElse(
                                        workshop -> {
                                            int registeredCount = workshopService.getWorkshopParticipants(workshop).size();
                                            int waitlistCount = workshopService.getWorkshopWaitlist(workshop).size();
                                            sendMessage(chatId, "Мастер-класс успешно обновлен!\n\n" +
                                                    workshopService.formatWorkshopInfoSafe(workshop, registeredCount, waitlistCount));
                                        },
                                        () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                                );

                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Некорректный ID мастер-класса.");
                    } catch (Exception e) {
                        logger.error("Error editing workshop", e);
                        sendMessage(chatId, "Произошла ошибка при редактировании мастер-класса. Проверьте формат ввода.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processDeleteWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /delete_workshop <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    int registeredCount = workshopService.getWorkshopParticipants(workshop).size();
                                    int waitlistCount = workshopService.getWorkshopWaitlist(workshop).size();
                                    String workshopInfo = workshopService.formatWorkshopInfoSafe(workshop, registeredCount, waitlistCount);
                                    boolean success = workshopService.deleteWorkshop(workshopId);
                                    if (success) {
                                        sendMessage(chatId, "Мастер-класс успешно удален:\n\n" + workshopInfo);
                                    } else {
                                        sendMessage(chatId, "Не удалось удалить мастер-класс.");
                                    }
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /delete_workshop <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processWorkshopParticipantsCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /workshop_participants <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    List<WorkshopRegistration> participants = workshopService.getWorkshopParticipants(workshop);
                                    List<WorkshopRegistration> waitlist = workshopService.getWorkshopWaitlist(workshop);

                                    StringBuilder sb = new StringBuilder();
                                    sb.append("Мастер-класс: ").append(workshop.getTitle()).append("\n");
                                    sb.append("Вместимость: ").append(workshop.getCapacity()).append("\n\n");

                                    if (participants.isEmpty()) {
                                        sb.append("На мастер-класс пока никто не записался.\n\n");
                                    } else {
                                        sb.append("Участники (").append(participants.size()).append("):\n");
                                        int i = 1;
                                        for (WorkshopRegistration reg : participants) {
                                            UserInfo info = reg.getUser().getUserInfo();
                                            sb.append(i++).append(". ")
                                                    .append(info.getName());

                                            // Добавляем username, если он существует
                                            if (info.getUsername() != null && !info.getUsername().isEmpty()) {
                                                sb.append(" (@").append(info.getUsername()).append(")");
                                            }

                                            sb.append(" - chatId: ").append(info.getChatId())
                                                    .append("\n");
                                        }
                                        sb.append("\n");
                                    }

                                    if (!waitlist.isEmpty()) {
                                        sb.append("Лист ожидания (").append(waitlist.size()).append("):\n");
                                        int i = 1;
                                        for (WorkshopRegistration reg : waitlist) {
                                            UserInfo info = reg.getUser().getUserInfo();
                                            sb.append(i++).append(". ")
                                                    .append(info.getName());

                                            // Добавляем username, если он существует
                                            if (info.getUsername() != null && !info.getUsername().isEmpty()) {
                                                sb.append(" (@").append(info.getUsername()).append(")");
                                            }

                                            sb.append(" - chatId: ").append(info.getChatId());

                                            // Добавляем позицию в листе ожидания, если она существует
                                            if (reg.getWaitlistPosition() != null) {
                                                sb.append(", позиция: ").append(reg.getWaitlistPosition());
                                            }

                                            sb.append("\n");
                                        }
                                    }

                                    sendMessage(chatId, sb.toString());
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /workshop_participants <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processAddParticipantCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    // Check if this is the initial call without full parameters
                    if (text.trim().equals("/add_participant")) {
                        sendMessage(chatId, "Для добавления участника используйте формат:\n" +
                                "/add_participant <workshop_id>|<user_chatId>|<waitlist>\n\n" +
                                "Где:\n" +
                                "<workshop_id> - ID мастер-класса\n" +
                                "<user_chatId> - ID чата пользователя\n" +
                                "<waitlist> - добавить в лист ожидания (true) или как основного участника (false)");
                        return;
                    }

                    try {
                        String paramString = text.substring("/add_participant".length()).trim();

                        // If only workshop ID is provided
                        if (!paramString.contains("|")) {
                            try {
                                Long workshopId = Long.parseLong(paramString);
                                workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                        workshop -> {
                                            sendMessage(chatId, "Укажите полные параметры для добавления участника:\n" +
                                                    "/add_participant " + workshopId + "|<user_chatId>|<waitlist>");
                                        },
                                        () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                                );
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "Некорректный ID мастер-класса.");
                            }
                            return;
                        }

                        // Parse all parameters
                        String[] params = paramString.split("\\|");
                        if (params.length < 3) {
                            sendMessage(chatId, "Недостаточно параметров. Используйте формат:\n" +
                                    "/add_participant <workshop_id>|<user_chatId>|<waitlist>");
                            return;
                        }

                        Long workshopId = Long.parseLong(params[0].trim());
                        Long participantChatId = Long.parseLong(params[1].trim());
                        boolean waitlist = Boolean.parseBoolean(params[2].trim());

                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    userService.findUserByChatId(participantChatId).ifPresentOrElse(
                                            participant -> {
                                                workshopService.manuallyAddParticipant(workshop, participant, waitlist)
                                                        .ifPresentOrElse(
                                                                registration -> {
                                                                    String status = registration.isWaitlist() ? "в лист ожидания" : "как участник";
                                                                    sendMessage(chatId, "Пользователь " + participant.getUserInfo().getName() +
                                                                            " успешно добавлен " + status + " на мастер-класс \"" +
                                                                            workshop.getTitle() + "\".");
                                                                },
                                                                () -> sendMessage(chatId, "Не удалось добавить пользователя на мастер-класс.")
                                                        );
                                            },
                                            () -> sendMessage(chatId, "Пользователь с ID чата " + participantChatId + " не найден.")
                                    );
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Некорректный формат числовых параметров. Проверьте ID мастер-класса и ID чата пользователя.");
                    } catch (Exception e) {
                        logger.error("Error adding participant", e);
                        sendMessage(chatId, "Произошла ошибка при добавлении участника. Проверьте формат ввода.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processRemoveParticipantCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    // Check if this is the initial call without full parameters
                    if (text.trim().equals("/remove_participant")) {
                        sendMessage(chatId, "Для удаления участника используйте формат:\n" +
                                "/remove_participant <workshop_id>|<user_chatId>\n\n" +
                                "Где:\n" +
                                "<workshop_id> - ID мастер-класса\n" +
                                "<user_chatId> - ID чата пользователя");
                        return;
                    }

                    try {
                        String paramString = text.substring("/remove_participant".length()).trim();

                        // If only workshop ID is provided
                        if (!paramString.contains("|")) {
                            try {
                                Long workshopId = Long.parseLong(paramString);
                                workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                        workshop -> {
                                            sendMessage(chatId, "Укажите полные параметры для удаления участника:\n" +
                                                    "/remove_participant " + workshopId + "|<user_chatId>");
                                        },
                                        () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                                );
                            } catch (NumberFormatException e) {
                                sendMessage(chatId, "Некорректный ID мастер-класса.");
                            }
                            return;
                        }

                        // Parse all parameters
                        String[] params = paramString.split("\\|");
                        if (params.length < 2) {
                            sendMessage(chatId, "Недостаточно параметров. Используйте формат:\n" +
                                    "/remove_participant <workshop_id>|<user_chatId>");
                            return;
                        }

                        Long workshopId = Long.parseLong(params[0].trim());
                        Long participantChatId = Long.parseLong(params[1].trim());

                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    userService.findUserByChatId(participantChatId).ifPresentOrElse(
                                            participant -> {
                                                boolean success = workshopService.cancelRegistration(workshop, participant);
                                                if (success) {
                                                    sendMessage(chatId, "Пользователь " + participant.getUserInfo().getName() +
                                                            " успешно удален из мастер-класса \"" + workshop.getTitle() + "\".");
                                                } else {
                                                    sendMessage(chatId, "Пользователь не зарегистрирован на этот мастер-класс.");
                                                }
                                            },
                                            () -> sendMessage(chatId, "Пользователь с ID чата " + participantChatId + " не найден.")
                                    );
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Некорректный формат числовых параметров. Проверьте ID мастер-класса и ID чата пользователя.");
                    } catch (Exception e) {
                        logger.error("Error removing participant", e);
                        sendMessage(chatId, "Произошла ошибка при удалении участника. Проверьте формат ввода.");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    @Transactional
    protected void processConfirmWorkshopCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Для подтверждения участия используйте формат:\n" +
                                "/confirm_workshop <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    if (!workshop.isActive()) {
                                        sendMessage(chatId, "Мастер-класс неактивен или отменен.");
                                        return;
                                    }

                                    boolean confirmed = workshopService.confirmWorkshopRegistration(workshop, user);
                                    if (confirmed) {
                                        sendMessage(chatId, "Вы успешно подтвердили участие в мастер-классе \"" +
                                                workshop.getTitle() + "\".");
                                    } else {
                                        sendMessage(chatId, "Не удалось подтвердить участие. Возможно, вы не находитесь в листе ожидания или срок подтверждения истек.");
                                    }
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /confirm_workshop <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    /**
     * Обрабатывает команду сканирования QR-кода
     */
    @Transactional
    protected void processScanQrCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    // Проверяем, это начальная команда или уже содержит QR-код
                    if (text.trim().equals("/scan_qr")) {
                        sendMessage(chatId, "Пожалуйста, отсканируйте QR-код участника и отправьте его содержимое.\n\n" +
                                "Формат QR-кода: ID:USER_ID:NAME:CHAT_ID\n\n" +
                                "После отправки содержимого QR-кода, вы получите информацию о пользователе и сможете отметить его посещение.");
                        return;
                    }

                    // Пробуем извлечь содержимое QR-кода
                    String qrContent = text.substring("/scan_qr".length()).trim();
                    workshopService.findUserByQrContent(qrContent).ifPresentOrElse(
                            scannedUser -> {
                                UserInfo userInfo = scannedUser.getUserInfo();
                                if (userInfo != null) {
                                    // Получаем список мастер-классов, на которые записан пользователь
                                    List<WorkshopRegistration> registrations = workshopService.getUserRegistrations(scannedUser)
                                            .stream()
                                            .filter(reg -> !reg.isWaitlist())
                                            .toList();

                                    if (registrations.isEmpty()) {
                                        sendMessage(chatId, String.format(
                                                "✅ Пользователь идентифицирован:\n" +
                                                "👤 %s\n" +
                                                "🆔 ID чата: %d\n\n" +
                                                "❌ Пользователь не записан ни на один мастер-класс.",
                                                userInfo.getName(),
                                                userInfo.getChatId()
                                        ));
                                        return;
                                    }

                                    StringBuilder sb = new StringBuilder();
                                    sb.append("✅ Пользователь идентифицирован:\n")
                                            .append("👤 ").append(userInfo.getName()).append("\n");
                                    
                                    if (userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
                                        sb.append("👤 @").append(userInfo.getUsername()).append("\n");
                                    }
                                    
                                    sb.append("🆔 ID чата: ").append(userInfo.getChatId()).append("\n\n")
                                            .append("Мастер-классы, на которые записан пользователь:\n\n");

                                    for (WorkshopRegistration reg : registrations) {
                                        Workshop workshop = reg.getWorkshop();
                                        String attendanceStatus = reg.isAttended() ? "✅ Присутствовал" : "❌ Не отмечен";
                                        sb.append(String.format(
                                                "%d. %s\n%s (%s)\nСтатус: %s\n\n",
                                                workshop.getId(),
                                                workshop.getTitle(),
                                                workshop.getStartTime().format(DATE_TIME_FORMATTER),
                                                workshop.getEndTime().format(DATE_TIME_FORMATTER),
                                                attendanceStatus
                                        ));
                                    }

                                    sb.append("Для отметки посещения используйте команду:\n")
                                            .append("/mark_attendance <workshop_id>|<статус>\n\n")
                                            .append("Где статус: true - присутствовал, false - не присутствовал\n\n")
                                            .append("Например:\n")
                                            .append("/mark_attendance ").append(registrations.get(0).getWorkshop().getId()).append("|true");

                                    sendMessage(chatId, sb.toString());
                                } else {
                                    sendMessage(chatId, "Пользователь найден, но информация о нем отсутствует.");
                                }
                            },
                            () -> sendMessage(chatId, "QR-код не распознан или пользователь не найден. Проверьте формат и попробуйте еще раз.")
                    );
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }
    
    /**
     * Обрабатывает команду просмотра отчета о посещении мастер-класса
     */
    @Transactional
    protected void processWorkshopAttendanceCommand(Long chatId, String text) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
                user -> {
                    if (!(userService.hasRole(user.getId(), UserRole.ORGANIZER) ||
                            userService.hasRole(user.getId(), UserRole.ADMIN))) {
                        sendMessage(chatId, "У вас нет прав на выполнение этой команды. Требуется роль организатора или администратора.");
                        return;
                    }

                    String[] parts = text.split(" ", 2);
                    if (parts.length < 2 || parts[1].trim().isEmpty()) {
                        sendMessage(chatId, "Пожалуйста, укажите ID мастер-класса: /workshop_attendance <id>");
                        return;
                    }

                    try {
                        Long workshopId = Long.parseLong(parts[1].trim());
                        workshopService.getWorkshopById(workshopId).ifPresentOrElse(
                                workshop -> {
                                    List<WorkshopRegistration> registrations = workshopService.getWorkshopAttendance(workshop);
                                    if (registrations.isEmpty()) {
                                        sendMessage(chatId, "На мастер-класс \"" + workshop.getTitle() + "\" не записан ни один участник.");
                                        return;
                                    }

                                    int totalRegistered = registrations.size();
                                    int totalAttended = 0;
                                    
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("📊 Отчет о посещении мастер-класса\n\n")
                                            .append("📌 ").append(workshop.getTitle()).append("\n")
                                            .append("🕒 ").append(workshop.getStartTime().format(DATE_TIME_FORMATTER)).append("\n")
                                            .append("👨‍👩‍👧‍👦 Участники (").append(totalRegistered).append("):\n\n");

                                    for (int i = 0; i < registrations.size(); i++) {
                                        WorkshopRegistration reg = registrations.get(i);
                                        UserInfo userInfo = reg.getUser().getUserInfo();
                                        
                                        if (reg.isAttended()) {
                                            totalAttended++;
                                        }
                                        
                                        String attendanceStatus = reg.isAttended() ? "✅ Присутствовал" : "❌ Не отмечен";
                                        String attendanceTime = reg.getAttendanceTime() != null ? 
                                                reg.getAttendanceTime().format(DATE_TIME_FORMATTER) : "-";
                                        
                                        sb.append(String.format("%d. %s", (i + 1), userInfo.getName()));
                                        
                                        if (userInfo.getUsername() != null && !userInfo.getUsername().isEmpty()) {
                                            sb.append(String.format(" (@%s)", userInfo.getUsername()));
                                        }
                                        
                                        sb.append(String.format(
                                                "\nСтатус: %s\nОтметил: %s\nВремя: %s\n\n",
                                                attendanceStatus,
                                                reg.getMarkedByUserId() != null ? 
                                                        userService.findUserById(reg.getMarkedByUserId())
                                                                .map(u -> u.getUserInfo().getName())
                                                                .orElse("Неизвестно") : "-",
                                                attendanceTime
                                        ));
                                    }
                                    
                                    sb.append(String.format(
                                            "📌 Итого: %d/%d участников присутствовало (%.1f%%)",
                                            totalAttended,
                                            totalRegistered,
                                            totalRegistered > 0 ? (100.0 * totalAttended / totalRegistered) : 0.0
                                    ));

                                    sendMessage(chatId, sb.toString());
                                },
                                () -> sendMessage(chatId, "Мастер-класс с ID " + workshopId + " не найден.")
                        );
                    } catch (NumberFormatException e) {
                        sendMessage(chatId, "Пожалуйста, укажите корректный ID мастер-класса: /workshop_attendance <id>");
                    }
                },
                () -> sendMessage(chatId, "Вы не зарегистрированы. Используйте /start для регистрации.")
        );
    }

    private void sendMessage(Long chatId, String text) {
        telegramBot.execute(new SendMessage(chatId, text));
    }

    /**
     * Генерирует содержимое QR-кода для пользователя.
     * Формат: t.me/BOT_USERNAME?start=attendance_USER_ID
     * Это создаст deep link, который при сканировании откроет чат с ботом
     * и автоматически отправит команду /start с параметром
     */
    private String generateQrCodeContent(ru.unithack.bot.domain.model.User user) {
        // Сохраняем оригинальные данные пользователя в закодированном виде
        String encodedData = "attendance_" + user.getId();
        
        // Формируем deep link для Telegram
        return "https://t.me/" + botUsername + "?start=" + encodedData;
    }

    private void sendPhoto(Long chatId, byte[] photoData, String caption) {
        SendPhoto sendPhoto = new SendPhoto(chatId, photoData)
                .caption(caption);
        telegramBot.execute(sendPhoto);
    }

    private void processNewsImageStep(Long chatId, NewsCreationState state) {
        // Для глобальной новости переходим к публикации
        if (state.getNewsType() == NewsType.GLOBAL) {
            publishGlobalNews(chatId, state);
        } else {
            // Для новости мастер-класса показываем список активных мастер-классов
            showWorkshopSelectionButtons(chatId, state);
        }
    }

    /**
     * Показывает кнопки выбора мастер-класса для создания новости
     */
    private void showWorkshopSelectionButtons(Long chatId, NewsCreationState state) {
        List<Workshop> workshops = workshopService.getAllActiveWorkshops();
        
        if (workshops.isEmpty()) {
            sendMessage(chatId, "Нет активных мастер-классов. Новость не может быть создана.");
            newsCreationStates.remove(chatId);
            return;
        }
        
        // Создаем сообщение с кнопками выбора мастер-класса
        String messageText = "Выберите мастер-класс, для которого создаётся новость:";
        SendMessage message = new SendMessage(chatId, messageText);
        
        // Создаем клавиатуру с кнопками
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        
        // Добавляем кнопку для каждого мастер-класса
        for (Workshop workshop : workshops) {
            InlineKeyboardButton button = new InlineKeyboardButton(workshop.getTitle())
                    .callbackData("select_workshop_for_news:" + workshop.getId());
            keyboardMarkup.addRow(button);
        }
        
        // Прикрепляем клавиатуру к сообщению
        message.replyMarkup(keyboardMarkup);
        
        // Отправляем сообщение с кнопками
        telegramBot.execute(message);
        
        // Обновляем шаг создания новости
        state.setStep(NewsCreationStep.WAITING_FOR_WORKSHOP_SELECTION);
    }

    /**
     * Публикует глобальную новость
     */
    private void publishGlobalNews(Long chatId, NewsCreationState state) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                try {
                    // Создаем новость
                    NewsPost newsPost = newsService.createGlobalNews(
                        state.getTitle(),
                        state.getContent(),
                        state.getImagePath(),
                        user
                    );
                    
                    // Отправляем уведомления пользователям
                    newsService.notifyAllUsersAboutGlobalNews(newsPost);
                    
                    // Отправляем подтверждение создателю
                    sendMessage(chatId, "✅ Глобальная новость успешно создана и разослана всем пользователям!");
                    
                    // Удаляем состояние
                    newsCreationStates.remove(chatId);
                } catch (Exception e) {
                    logger.error("Error creating global news", e);
                    sendMessage(chatId, "Произошла ошибка при создании новости. Пожалуйста, попробуйте снова.");
                    newsCreationStates.remove(chatId);
                }
            },
            () -> {
                sendMessage(chatId, "Пользователь не найден.");
                newsCreationStates.remove(chatId);
            }
        );
    }

    /**
     * Публикует новость для мастер-класса
     */
    private void publishWorkshopNews(Long chatId, NewsCreationState state, Workshop workshop) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                try {
                    // Создаем новость
                    NewsPost newsPost = newsService.createWorkshopNews(
                        state.getTitle(),
                        state.getContent(),
                        state.getImagePath(),
                        workshop,
                        user
                    );
                    
                    // Отправляем уведомления участникам мастер-класса
                    newsService.notifyWorkshopParticipantsAboutNews(newsPost);
                    
                    // Отправляем подтверждение создателю
                    sendMessage(chatId, "✅ Новость для мастер-класса \"" + workshop.getTitle() + 
                              "\" успешно создана и разослана всем участникам!");
                    
                    // Удаляем состояние
                    newsCreationStates.remove(chatId);
                } catch (Exception e) {
                    logger.error("Error creating workshop news", e);
                    sendMessage(chatId, "Произошла ошибка при создании новости. Пожалуйста, попробуйте снова.");
                    newsCreationStates.remove(chatId);
                }
            },
            () -> {
                sendMessage(chatId, "Пользователь не найден.");
                newsCreationStates.remove(chatId);
            }
        );
    }

    /**
     * Обрабатывает команду создания глобальной новости
     */
    @Transactional
    protected void processCreateNewsCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                // Проверяем, имеет ли пользователь роль ADMIN или ORGANIZER
                if (!(userService.hasRole(user.getId(), UserRole.ADMIN) || 
                      userService.hasRole(user.getId(), UserRole.ORGANIZER))) {
                    sendMessage(chatId, "У вас нет прав для создания новостей.");
                    return;
                }
                
                // Инициируем процесс создания новости
                NewsCreationState state = new NewsCreationState();
                state.setStep(NewsCreationStep.WAITING_FOR_TITLE);
                state.setNewsType(NewsType.GLOBAL);
                newsCreationStates.put(chatId, state);
                
                sendMessage(chatId, "Создание новости для всех пользователей.\n\n" +
                        "Введите заголовок новости:");
            },
            () -> sendMessage(chatId, "Вы не зарегистрированы в системе.")
        );
    }

    /**
     * Обрабатывает команду создания новости для мастер-класса
     */
    @Transactional
    protected void processCreateWorkshopNewsCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                // Проверяем, имеет ли пользователь роль ADMIN или ORGANIZER
                if (!(userService.hasRole(user.getId(), UserRole.ADMIN) || 
                      userService.hasRole(user.getId(), UserRole.ORGANIZER))) {
                    sendMessage(chatId, "У вас нет прав для создания новостей.");
                    return;
                }
                
                // Инициируем процесс создания новости
                NewsCreationState state = new NewsCreationState();
                state.setStep(NewsCreationStep.WAITING_FOR_TITLE);
                state.setNewsType(NewsType.WORKSHOP);
                newsCreationStates.put(chatId, state);
                
                sendMessage(chatId, "Создание новости для мастер-класса.\n\n" +
                        "Введите заголовок новости:");
            },
            () -> sendMessage(chatId, "Вы не зарегистрированы в системе.")
        );
    }

    /**
     * Обрабатывает команду просмотра доступных новостей
     */
    @Transactional
    protected void processMyNewsCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                List<NewsPost> news = newsService.getRelevantNewsForUser(user);
                
                if (news.isEmpty()) {
                    sendMessage(chatId, "На данный момент нет доступных новостей.");
                    return;
                }
                
                StringBuilder message = new StringBuilder("Доступные новости:\n\n");
                
                for (NewsPost post : news) {
                    message.append("📢 *").append(post.getTitle()).append("*\n");
                    message.append(post.getContent().length() > 100 ? 
                                  post.getContent().substring(0, 97) + "..." : post.getContent())
                           .append("\n");
                    
                    if (!post.isGlobal() && post.getWorkshop() != null) {
                        message.append("🔹 Мастер-класс: ").append(post.getWorkshop().getTitle()).append("\n");
                    }
                    
                    message.append("🕒 ").append(post.getCreatedAt().format(DATE_TIME_FORMATTER))
                           .append("\n\n");
                }
                
                sendMessage(chatId, message.toString());
            },
            () -> sendMessage(chatId, "Вы не зарегистрированы в системе.")
        );
    }
    
    /**
     * Обновляет вывод помощи, добавляя команды для новостей
     */
    private void processHelpCommand(Long chatId) {
        userService.findUserByChatId(chatId).ifPresentOrElse(
            user -> {
                StringBuilder help = new StringBuilder();
                help.append("Доступные команды:\n\n");
                
                // Основные команды для всех пользователей
                help.append("📋 *Основные команды:*\n");
                help.append("/my_qr - Показать ваш QR-код\n");
                help.append("/workshops - Список доступных мастер-классов\n");
                help.append("/workshop_info [id] - Информация о конкретном мастер-классе\n");
                help.append("/register_workshop [id] - Записаться на мастер-класс\n");
                help.append("/cancel_workshop [id] - Отменить запись на мастер-класс\n");
                help.append("/my_workshops - Ваши мастер-классы\n");
                help.append("/my_roles - Ваши роли в системе\n");
                help.append("/my_news - Доступные новости\n\n");
                
                // Дополнительные команды для организаторов и администраторов
                if (userService.hasRole(user.getId(), UserRole.ORGANIZER) || 
                    userService.hasRole(user.getId(), UserRole.ADMIN)) {
                    help.append("🔧 *Команды для организаторов:*\n");
                    help.append("/create_workshop [название]|[описание]|[дата]|[время]|[место]|[макс.участников] - Создать мастер-класс\n");
                    help.append("/edit_workshop [id]|[название]|[описание]|[дата]|[время]|[место]|[макс.участников] - Изменить мастер-класс\n");
                    help.append("/delete_workshop [id] - Удалить мастер-класс\n");
                    help.append("/workshop_participants [id] - Список участников мастер-класса\n");
                    help.append("/add_participant [workshop_id]|[user_id] - Добавить участника на мастер-класс\n");
                    help.append("/remove_participant [workshop_id]|[user_id] - Удалить участника с мастер-класса\n");
                    help.append("/workshop_attendance [id] - Отметить присутствие участников мастер-класса\n");
                    help.append("/scan_qr [qr_content] - Сканировать QR-код участника для отметки присутствия\n");
                    help.append("/create_news - Создать глобальную новость для всех пользователей\n");
                    help.append("/create_workshop_news - Создать новость для мастер-класса\n\n");
                }
                
                // Административные команды
                if (userService.hasRole(user.getId(), UserRole.ADMIN)) {
                    help.append("🔑 *Административные команды:*\n");
                    help.append("/users - Список пользователей\n");
                    help.append("/user_qr [user_id] - Получить QR-код пользователя\n");
                    help.append("/add_organizer [user_id] - Назначить пользователя организатором\n");
                    help.append("/remove_organizer [user_id] - Удалить роль организатора у пользователя\n");
                }
                
                sendMessage(chatId, help.toString());
            },
            () -> {
                String message = "Для начала работы с ботом введите команду /start, " +
                        "чтобы зарегистрироваться в системе.";
                sendMessage(chatId, message);
            }
        );
    }

    /**
     * Класс для хранения состояния создания новости
     */
    private static class NewsCreationState {
        private NewsCreationStep step;
        private NewsType newsType;
        private String title;
        private String content;
        private String imagePath;
        private Long workshopId;
        
        public NewsCreationState() {
        }
        
        public NewsCreationStep getStep() {
            return step;
        }
        
        public void setStep(NewsCreationStep step) {
            this.step = step;
        }
        
        public NewsType getNewsType() {
            return newsType;
        }
        
        public void setNewsType(NewsType newsType) {
            this.newsType = newsType;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getContent() {
            return content;
        }
        
        public void setContent(String content) {
            this.content = content;
        }
        
        public String getImagePath() {
            return imagePath;
        }
        
        public void setImagePath(String imagePath) {
            this.imagePath = imagePath;
        }
        
        public Long getWorkshopId() {
            return workshopId;
        }
        
        public void setWorkshopId(Long workshopId) {
            this.workshopId = workshopId;
        }
    }
}