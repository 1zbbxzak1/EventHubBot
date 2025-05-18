package ru.unithack.bot.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.unithack.bot.domain.model.NewsPost;
import ru.unithack.bot.domain.model.User;
import ru.unithack.bot.domain.model.Workshop;

import java.util.List;

public interface NewsPostRepository extends JpaRepository<NewsPost, Long> {
    
    List<NewsPost> findByIsGlobalTrueOrderByCreatedAtDesc();
    
    List<NewsPost> findByWorkshopOrderByCreatedAtDesc(Workshop workshop);
    
    @Query("SELECT np FROM NewsPost np WHERE np.isGlobal = true OR np.workshop IN " +
           "(SELECT wr.workshop FROM WorkshopRegistration wr WHERE wr.user = :user AND wr.waitlist = false AND wr.pendingConfirmation = false) " +
           "ORDER BY np.createdAt DESC")
    List<NewsPost> findAllRelevantForUser(@Param("user") User user);
} 