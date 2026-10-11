# Tinder AI Backend

Spring Boot backend for generated dating profiles, matches, and AI conversations. Uses MongoDB for persistence, Spring AI for an OpenAI-compatible chat service, and Pixazo for profile image generation.

## Features

- Random profile retrieval and match creation.
- Conversation storage and AI-generated replies.
- Profile generation and import from `profiles.json`.
- Profile images served at `/images/`.
- Companion [React frontend](https://github.com/PravatKumaraMishra/TinderAiFrontend).

## Requirements

- JDK 21 and network access to Maven repositories.
- A dedicated MongoDB database for this demo.
- An OpenAI-compatible model endpoint and its API credentials.
- A Pixazo key when generating new profile images.

## Configuration

Use `.env.example` as a checklist. The application does not include a dotenv loader: export variables in your shell, configure your IDE environment, or pass them to Docker. Simply creating `.env` does not load it for Maven.

| Variable | Purpose |
| --- | --- |
| `DATASOURCE_URL` | MongoDB URI, including the database name. |
| `LLM_BASE_URL`, `CHAT_MODEL`, `LLM_API_KEY` | Model endpoint, model identifier, and API credential. |
| `LLM_TIMEOUT`, `LLM_MAX_RETRY` | Model request timeout (a duration such as `60s`) and integer retry configuration. |
| `PIXAZO_API_KEY` | Image generation credential; required configuration even if generation is skipped. |
| `PROFILE_INITIALIZATION_STATUS` | `true` skips generation; `false` generates a profile at startup. Use a boolean, rather than the template’s YES/NO notation. |
| `LOOKING_FOR_GENDER` | Profile generation preference, such as `FEMALE`, `MALE`, or `NON_BINARY`. |
| `MIN_AGE`, `MAX_AGE` | Integer age range used by generation. |
| `ETHNICITIES` | Nonempty comma-separated list used by generation. |
| `USER_PROFILE` | Spring-expression map containing the demo user’s profile. |

`USER_PROFILE` is parsed as a Spring expression, not as arbitrary JSON. For example, export a map like this, using fictional profile data:

```sh
export USER_PROFILE="{'id':'demo-user','firstName':'Demo','lastName':'User','age':'25','ethnicity':'Demo','gender':'MALE','bio':'Demo profile','imageUrl':'','myersBriggsPersonalityType':'INTJ'}"
```

`PUTER_API_KEY`, `DATASOURCE_USERNAME`, and `DATASOURCE_PASSWORD` appear in the template but are not referenced by `application.properties`. MongoDB authentication belongs in `DATASOURCE_URL`.

**Startup replaces the profiles collection:** `saveProfilesToDB()` deletes all existing profiles and imports `profiles.json`, then adds the configured demo user. This runs even when generation is skipped. Use a disposable database and run from the repository root so `profiles.json` can be found.

## Run locally

```sh
git clone https://github.com/PravatKumaraMishra/TinderAiBackend.git
cd TinderAiBackend
# Set the required environment variables before starting.
sh mvnw spring-boot:run
```

The default Spring Boot port is 8080. Configure the frontend’s `VITE_BACKEND_URL=http://localhost:8080`.

## API overview

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/profiles/random` | Retrieve a profile. |
| POST | `/matches` | Create a match from `{"profileId":"..."}`. |
| GET | `/matches` | List matches. |
| GET | `/conversations/{conversationId}` | Retrieve conversation history. |
| POST | `/conversations/{conversationId}` | Add a message and generate a reply. |

Example after initialization:

```sh
curl http://localhost:8080/profiles/random
```

Messages use `messageText` and `authorId`; the frontend sends `authorId: "user"`.

## Build and Docker

```sh
sh mvnw package
# With all required variables in a Docker-compatible .env file:
docker build -t tinder-ai-backend .
docker run --env-file .env -p 8080:8080 tinder-ai-backend
```

Use a MongoDB URI reachable from the container. `localhost` inside Docker refers to that container. The Dockerfile skips tests during its build. Model and image calls depend on provider access and may incur usage charges.

## Structure and troubleshooting

- `profile/`: generation, profile persistence, and random selection.
- `matches/`: match APIs and storage.
- `conversations/`: messages, persistence, and AI replies.
- `config/`: AI clients and static images.
- `profiles.json`: startup import data.

For startup failures, check missing variables, USER_PROFILE syntax, MongoDB connectivity, and the working directory. Generation requires valid AI and image credentials. This is a demo with permissive CORS and no application authentication configured; it is not a production dating service.
