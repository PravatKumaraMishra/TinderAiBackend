package me.pravat.tinder_ai_backend.users;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import me.pravat.tinder_ai_backend.profile.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;

@Service
public class UserService {
    private final UserRepository repository;
    private final MongoTemplate mongo;
    public UserService(UserRepository repository, MongoTemplate mongo) {
        this.repository = repository; this.mongo = mongo;
    }

    public UserAccount get(String id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists"));
    }
    public Profile getProfile(String id) {
        Profile profile = get(id).profile();
        if (profile == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Complete your profile first");
        return profile;
    }
    public Profile updateProfile(String id, ProfileRequest request) {
        get(id);
        Profile profile = request.toProfile(id);
        var result = mongo.updateFirst(Query.query(Criteria.where("id").is(id)),
                new Update().set("profile", profile), UserAccount.class);
        if (result.getMatchedCount() != 1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Account no longer exists");
        return profile;
    }
}
