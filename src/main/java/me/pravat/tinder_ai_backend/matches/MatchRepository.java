package me.pravat.tinder_ai_backend.matches;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MatchRepository extends MongoRepository<Match, String> {
    Optional<Match> findByProfile_Id(String profileId);

    boolean existsByProfile_Id(String profileId);

}
