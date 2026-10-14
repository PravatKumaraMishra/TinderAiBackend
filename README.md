# Tinder AI Backend

Java 21 target, Spring Boot 4.1.1, and Boot-managed Spring Security 7.1.1.
JWT signing/verification uses Spring Security OAuth2 Resource Server and Nimbus JOSE,
not a custom servlet filter or a separately pinned JWT library.

Uses MongoDB for persistence, Spring AI for an OpenAI-compatible chat service, and
Pixazo for persona image generation. Companion
[React frontend](https://github.com/PravatKumaraMishra/TinderAiFrontend).

## Requirements and local startup

Use JDK 21 or a compatible newer JDK, a MongoDB database, and your AI provider
credentials. Maven needs network access to download dependencies.

```sh
git clone https://github.com/PravatKumaraMishra/TinderAiBackend.git
cd TinderAiBackend
# Export the required environment variables before starting.
sh mvnw spring-boot:run
```

The application has no dotenv loader. Export variables in your shell, source a
shell-compatible environment file, or configure your IDE. Creating `.env` alone
does not load it for Maven. MongoDB credentials belong in `DATASOURCE_URL`.

The default port is 8080. Configure frontend `VITE_BACKEND_URL=http://localhost:8080`.
Run seeding commands from the repository root so `profiles.json` can be found.

## Configuration

Keep the existing MongoDB and AI environment variables (`DATASOURCE_URL`, `LLM_BASE_URL`,
`CHAT_MODEL`, `LLM_API_KEY`, `LLM_TIMEOUT`, `LLM_MAX_RETRY`, `PIXAZO_API_KEY`).
Add:

| Variable | Purpose / default |
| --- | --- |
| `JWT_SECRET` | Required Base64-encoded secret of at least 32 random bytes |
| `JWT_ISSUER` | `tinder-ai-backend` |
| `JWT_AUDIENCE` | `tinder-ai-api` |
| `JWT_ACCESS_TOKEN_TTL` | `30m`; positive and at most one hour |
| `CORS_ALLOWED_ORIGINS` | Comma-separated frontend origins; localhost ports 3000 and 5173 by default |
| `GOOGLE_CLIENT_ID` | Google OAuth Web client ID; supplied public client ID is configured as the default |
| `SEED_PROFILES` | `false`; explicitly enable to load missing personas from `profiles.json` |
| `PROFILE_INITIALIZATION_STATUS` | `true`; existing behavior: false generates a new persona when seeding is enabled |

Generate a secret once with `openssl rand -base64 32`, store it outside Git, and reuse it
across restarts and application instances. Changing it invalidates existing tokens.
Use HTTPS outside local development. MongoDB needs permission to create the unique
email and per-user match indexes (automatic index creation is enabled).
`USER_PROFILE` is no longer used. Human accounts are stored separately from AI personas.

## Authentication

`POST /auth/register` accepts:

```json
{
  "email": "alice@example.com",
  "password": "a-long-unique-password",
  "profile": {
    "firstName": "Alice",
    "lastName": "Example",
    "age": 25,
    "ethnicity": "Indian",
    "gender": "FEMALE",
    "bio": "I enjoy hiking.",
    "imageUrl": "",
    "myersBriggsPersonalityType": "INTJ"
  }
}
```

Email is trimmed and lowercased. Passwords require 12–64 characters and at most
72 UTF-8 bytes because the delegating password encoder currently uses BCrypt.
Profile ages must be 18–120. Gender values: `MALE`, `FEMALE`, `NON_BINARY`.

`POST /auth/login` accepts `email` and `password`. Registration (201) and login (200) return:

```json
{"accessToken":"<jwt>","tokenType":"Bearer","expiresIn":1800}
```

Send `Authorization: Bearer <jwt>` on all application API requests.
JWTs contain an account ID as subject, issuer, audience, issued-at, expiry, and token ID.
Verification checks signature/algorithm, issuer, audience, expiry, and account existence.
Passwords and personal profile details are not placed in the JWT.

This first version provides access tokens only. After expiry, sign in again.
Client logout discards the token; an already issued token remains valid until expiry.
There is no refresh-token flow, immediate logout revocation, password reset, or email
verification yet. Keep tokens in frontend memory; if moving authentication to cookies,
revisit CSRF protection (currently disabled only for header-based bearer authentication).

## Application endpoints

| Method / path | Behavior |
| --- | --- |
| `GET /users/me` | Current account ID, email, and profile; no password hash |
| `PUT /users/me/profile` | Replace the current user's profile using the profile payload above |
| `GET /profiles/random` | Random shared AI persona; 404 when none are available |
| `GET /profiles/{id}` | View a shared AI persona |
| `POST /matches` | `{ "profileId": "persona-id" }`; creates a match and owned conversation |
| `GET /matches` | Only the current user's matches |
| `GET /conversations/{id}` | Only an owned conversation; otherwise 404 |
| `POST /conversations/{id}` | `{ "messageText": "Hello!" }`; only an owned conversation |

`authorId` and `messageTime` no longer belong in send-message requests; the server supplies
both. User IDs in request bodies cannot select another account. A persona may be matched
by multiple users, once per user. Duplicate matches return 409. Concurrent conversation
saves use optimistic locking and return 409 instead of silently losing messages.
On a conversation conflict, reload before deciding whether to resend.
An AI failure leaves the submitted message/reply unsaved.

Only registration, password/Google login, `GET /images/**`, and `GET /actuator/health` are public.
No session cookie or HTTP Basic login is enabled.

## Google sign-in

The React frontend uses the official Google Identity Services popup/callback button.
`POST /auth/google` accepts `{ "credential": "<Google ID token>" }` and returns the
same application token response as password login. The backend validates Google's
RS256 signature using its rotating public keys, issuer, client-ID audience, expiry,
subject, and verified email. Google credentials are never accepted as bearer tokens
for application APIs. No additional JWT dependency or client secret is needed.

The supplied client ID is
`356044456946-ebi4v73c1dtoug43gddf4s0k7s3bcsn3.apps.googleusercontent.com`.
It is public configuration. Override it with `GOOGLE_CLIENT_ID` and set the same
value in frontend `VITE_GOOGLE_CLIENT_ID` when changing Google projects.

New Google accounts have no password and no application profile. `/users/me`
returns `profile: null`; complete it with `PUT /users/me/profile` before matching
or chatting. Do not invent a user's age or gender from their Google identity.

Accounts are identified by Google's stable `sub`, not by a changeable email.
An existing password account with the same email is never automatically merged.
Sign in with the password, then call authenticated `POST /users/me/google` with
the Google credential (same email required); success returns 204. The React
**My profile** screen provides this linking action. `/users/me` now includes
`googleLinked` without exposing the provider subject or password hash.

In Google Cloud Console, authorize the frontend's actual JavaScript origin,
including `http://localhost:5173` for default Vite development and the deployed
frontend origin. This callback flow does not require an OAuth redirect URI.
The backend needs internet access to `https://www.googleapis.com/oauth2/v3/certs`.
Configure backend CORS with those frontend origins as well. For the Render backend,
deploy these code changes before connecting the updated frontend to it.

## Preserve existing demo data

Normal startup never deletes profiles, matches, or conversations. Old records without
`userId` are inaccessible until explicitly migrated. No live database migration is
performed by the code change itself.

1. Back up the database and stop other application instances for the migration.
2. Start the updated backend with migration disabled and register the designated owner
   using their chosen email/password and original human profile details.
3. Restart with `MIGRATE_LEGACY_DATA=true`, `LEGACY_OWNER_EMAIL=<registered email>`, and,
   when available, `LEGACY_USER_PROFILE_ID=<old USER_PROFILE id>`.
4. The migration assigns only unowned matches/conversations to that account and sets
   legacy conversation versions. Providing the old human ID also rewrites human chat
   author IDs and removes that human profile from shared AI discovery. It never removes
   matches or conversations. Without the old ID, that profile remains in discovery.
5. Disable migration and restart normally. Verify the owner's match/chat history and
   verify that a second account cannot access it.

The migration fails before writing if the designated account does not exist. It can
be rerun after a partial interruption. It is not a multi-document transaction; perform
it during maintenance. If old data violates a unique index, resolve those duplicates
before migration. Leave `SEED_PROFILES=false` while migrating.

## Frontend integration

Add registration/login screens, attach the bearer token to API calls, and obtain the
current user through `/users/me` instead of a configured singleton profile. Use
`PUT /users/me/profile` for profile edits. Handle 401 by returning to sign-in, 409 by
showing a conflict, and 404 for unavailable resources. Configure the actual frontend
origin in `CORS_ALLOWED_ORIGINS`. There is no frontend source in this repository.

## Verification

Run `./mvnw test`. Tests cover real JWT encoding/decoding through the security filter
chain, login/registration, validation, per-user match visibility, conversation ownership,
server-controlled message authors, CORS, migration preconditions, and non-destructive
seeding. Repository dependencies are mocked: production MongoDB indexes and the live
AI provider still need an environment smoke test before deployment.

## Build and Docker

```sh
sh mvnw package
docker build -t tinder-ai-backend .
# Use a Docker-compatible environment file containing KEY=value lines, without export.
docker run --env-file .env -p 8080:8080 tinder-ai-backend
```

Use a MongoDB URI reachable from the container: `localhost` inside Docker refers
to that container. The Dockerfile skips tests during its build, so run the test
suite separately. Supply `JWT_SECRET` and the other required variables to the container.

## Structure and troubleshooting

- `auth/`, `users/`: sign-in, JWTs, account linking, and user profiles.
- `profile/`: AI persona generation and persistence.
- `matches/`, `conversations/`: account-owned matches, messages, and AI replies.
- `config/`: security, migration, AI clients, and static images.
- `profiles.json`: persona seed data.

For startup failures, check environment variables, JWT secret format, MongoDB
connectivity/index permissions, and the working directory. For browser failures,
check CORS, the backend URL, bearer tokens, and Google-authorized origins.

Official references:
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Nimbus JWT encoder builder](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtEncoder.html)
- [Google ID token verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
- [Google sign-in button](https://developers.google.com/identity/gsi/web/guides/display-button)
