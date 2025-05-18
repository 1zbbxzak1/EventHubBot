package ru.unithack.bot.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.UserInfo;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserInfo_QrCode(String qrCode);
    Optional<User> findByUserInfo_ChatId(Long chatId);
    Optional<User> findByUserInfo_Name(String name);
} 