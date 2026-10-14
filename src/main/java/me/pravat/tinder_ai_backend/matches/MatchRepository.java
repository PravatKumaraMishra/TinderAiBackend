package me.pravat.tinder_ai_backend.matches;

import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<Match, String> {
    boolean existsByUserIdAndProfile_Id(String userId, String profileId);
    List<Match> findByUserId(String userId);
}
