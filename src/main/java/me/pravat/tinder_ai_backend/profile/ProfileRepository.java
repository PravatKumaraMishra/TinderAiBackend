package me.pravat.tinder_ai_backend.profile;

import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProfileRepository extends MongoRepository<Profile, String> {

    @Aggregation(pipeline = { "{ $match: { gender: ?0 } }", "{ $sample: { size: 1 } }" })
    Profile getRandomProfileByGender(Gender gender);

}
