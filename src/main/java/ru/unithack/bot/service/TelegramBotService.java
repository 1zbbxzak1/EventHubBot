package ru.unithack.bot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.unithack.bot.domain.enums.UserRole;
import ru.unithack.bot.domain.model.UserInfo;
import ru.unithack.bot.infrastructure.repository.UserRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class TelegramBotService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${app.telegram-token}")
    private String telegramToken;

    private TelegramBot telegramBot;
    private final UserService userService;
    private final RoleService roleService;
    private final UserRepository userRepository;

    @Autowired
    public TelegramBotService(UserService userService,
                              RoleService roleService,
                              UserRepository userRepository) {
        this.userService = userService;
        this.roleService = roleService;
        this.userRepository = userRepository;
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
            if (update.message() != null && update.message().text() != null) {
                processMessage(update.message());
            }
        }
    }

    @Transactional
    protected void processMessage(Message message) {
        String text = message.text();
        User telegramUser = message.from();
        Long chatId = message.chat().id();
        String fullName = (telegramUser.firstName() + " " +
                (telegramUser.lastName() != null ? telegramUser.lastName() : "")).trim();
        String username = telegramUser.username();

        updateUserInfoIfChanged(chatId, fullName, username);

        if (text.startsWith("/start")) {
            processStartCommand(telegramUser, chatId, fullName, username);
        } else if (text.startsWith("/add_organizer")) {
            processAddOrganizerCommand(chatId, text);
        } else if (text.startsWith("/remove_organizer")) {
            processRemoveOrganizerCommand(chatId, text);
        } else if (text.startsWith("/my_roles")) {
            processMyRolesCommand(chatId);
        } else if (text.startsWith("/list_users")) {
            processListUsersCommand(chatId);
        } else {
            sendMessage(chatId, "Неизвестная команда. Доступные команды:\n" +
                    "/start - Регистрация пользователя\n" +
                    "/add_organizer <chatId> - Добавить организатора (только для админов)\n" +
                    "/remove_organizer <chatId> - Удалить организатора (только для админов)\n" +
                    "/my_roles - Проверить свои роли\n" +
                    "/list_users - Список пользователей (только для админов)");
        }
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

    private void processStartCommand(User telegramUser, Long chatId, String fullName, String username) {
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

    private void sendMessage(Long chatId, String text) {
        telegramBot.execute(new SendMessage(chatId, text));
    }
} 