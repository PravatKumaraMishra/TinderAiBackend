package me.pravat.tinder_ai_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import me.pravat.tinder_ai_backend.auth.AuthService;
import me.pravat.tinder_ai_backend.users.UserRepository;
import me.pravat.tinder_ai_backend.matches.Match;
import me.pravat.tinder_ai_backend.conversations.Conversation;
import me.pravat.tinder_ai_backend.profile.Profile;

/** Explicit, repeatable migration. Never infer an owner from the first login. */
@Component
@ConditionalOnProperty(name = "migration.legacy.enabled", havingValue = "true")
public class LegacyOwnershipMigration implements ApplicationRunner {
    private final MongoTemplate mongo;
    private final UserRepository users;
    private final String email;
    private final String oldProfileId;
    public LegacyOwnershipMigration(MongoTemplate mongo, UserRepository users,
            @Value("${migration.legacy.owner-email}") String email,
            @Value("${migration.legacy.old-profile-id:}") String oldProfileId) {
        this.mongo = mongo; this.users = users; this.email = email; this.oldProfileId = oldProfileId;
    }
    @Override
    public void run(ApplicationArguments args) {
        var owner = users.findByEmail(AuthService.normalizeEmail(email)).orElseThrow(() ->
                new IllegalStateException("Register the legacy owner account before enabling migration"));
        Query unowned = Query.query(Criteria.where("userId").is(null));
        mongo.updateMulti(unowned, new Update().set("userId", owner.id()), Match.class);
        mongo.updateMulti(unowned, new Update().set("userId", owner.id()).set("version", 0L), Conversation.class);
        if (!oldProfileId.isBlank()) {
            // Convert old human message authors to the new account ID.
            mongo.getCollection(mongo.getCollectionName(Conversation.class)).updateMany(
                    new org.bson.Document("userId", owner.id()),
                    new org.bson.Document("$set", new org.bson.Document("messages.$[message].authorId", owner.id())),
                    new com.mongodb.client.model.UpdateOptions().arrayFilters(java.util.List.of(
                            new org.bson.Document("message.authorId", oldProfileId))));
            mongo.remove(Query.query(Criteria.where("id").is(oldProfileId)), Profile.class);
        }
    }
}
