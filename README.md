# AI Diary

A **local-first, privacy-focused AI journal** that analyzes your thoughts using vector embeddings and semantic memory. Write privately, journal intelligently, discover patterns in your life.

Built with **Java 21**, **Quarkus**, **PostgreSQL + pgvector**, **Ollama (qwen3)**, and **LangChain4j**.

---

## Quick Start

### Prerequisites

- Docker + Docker Compose
- ~8GB RAM (4GB for model, 2GB for app, 2GB buffer)
- GPU optional (CPU mode works, slower)
- ~5GB disk space (for Qwen3 model)

### Run It

```bash
git clone https://github.com/JohannesRabauer/ai-journal.git
cd ai-journal
docker compose up --build
```

Then open **`http://localhost:8081`** and start journaling.

---

## What It Does

1. **Write privately** — Your diary stays on your machine. No cloud, no tracking.
2. **Get AI insights** — Llama 3.2 analyzes each entry: mood, key scenario, growth insight, actionable advice.
3. **Semantic memory** — Entries are converted to 1024-dimensional vectors and stored in PostgreSQL.
4. **Pattern discovery** — The system retrieves similar past entries and uses them as context for better analysis.

### Example Workflow

```
You write:  "Had a tense conversation with my manager, felt defensive."
           ↓
LLM analyzes (streamed):
  Mood: Guarded, reactive
  Scenario: Feedback felt personal
  Insight: You've felt this way before (finds 3 similar entries from past)
  Advice: Practice curiosity before reacting
           ↓
Entry + embedding stored in PostgreSQL for future retrieval
```

---

## Architecture

```
┌─────────────────────────────────────┐
│     Frontend (Vaadin + Spring Boot) │
│          localhost:8081             │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│   Backend (Quarkus + LangChain4j)   │
│          localhost:8080             │
└──────────┬──────────────┬───────────┘
           │              │
    ┌──────▼─────┐   ┌────▼──────┐
    │ PostgreSQL │   │   Ollama  │
    │ + pgvector │   │   Qwen 3  │
    │ :15432     │   │  :11434   │
    └────────────┘   └───────────┘
```

### Services

| Service        | Purpose                                                   | Port   |
|----------------|-----------------------------------------------------------|--------|
| **Ollama**     | LLM runtime (Llama 3.2 for local inference)               | 11434  |
| **PostgreSQL** | Entry storage + vector embeddings (pgvector extension)    | 15432  |
| **Quarkus**    | REST API, AI orchestration, vector ingestion              | 8080   |
| **Vaadin**     | Web UI (pure Java, reactive, streams tokens real-time)    | 8081   |

---

## Key Features

### 🔐 Privacy First
- Runs entirely locally. Your data never leaves your machine.
- Ollama keeps the LLM on your GPU or CPU.
- No external API calls.

### ⚡ Reactive & Fast
- Streaming responses: see the AI thinking in real-time.
- Non-blocking writes: UI stays responsive while embeddings are stored.
- Quarkus startup: ~1 second (JVM) or ~50ms (native).

### 🧠 Semantic Memory (RAG)
- **Retrieval-Augmented Generation**: each new entry retrieves top-k similar chunks from your history.
- LLM uses your own patterns as context—no fine-tuning needed.
- HNSW indexes in pgvector make nearest-neighbor search sub-millisecond.

### 🏗️ Production-Ready
- Docker Compose for reproducible deployments.
- Hibernate ORM + Panache for clean database access.
- LangChain4j for seamless LLM integration.

---

## Tech Stack

| Layer   | Technology                               |
|---------|------------------------------------------|
| **UI**  | Vaadin 24.9.2 + Spring Boot 3.5.6        |
| **API** | Quarkus 3.28.5 + LangChain4j + Mutiny    |
| **DB**  | PostgreSQL + pgvector (1024-d vectors)   |
| **LLM** | Ollama + Qwen3 (local inference)         |
| **Dep** | Maven, Docker Compose                    |

---

## API Endpoints

### Submit a New Entry

```bash
POST http://localhost:8080/newEntry
Content-Type: application/json

{
  "entryText": "Today I felt anxious about the presentation.",
  "entryTimestamp": "2025-11-30T14:30:00"
}
```

**Response:** Server-Sent Events (streaming text/plain)
- Tokens from the LLM arrive in real-time.
- Backend concurrently embeds and stores the entry.

### List Previous Entries

```bash
GET http://localhost:8080/entries?amount=10&offset=0
```

**Response:** JSON array of past entries with AI analysis and timestamps.

---

## How Vectors Work (Technical Deep Dive)

### Embedding

When you submit an entry, the embedding model converts text → 1024-dimensional vector:

```
Text: "I felt defeated today"
           ↓
Vector: [-0.023, 0.456, -0.891, ..., 0.234] (1024 floats)
```

Similar texts produce nearby vectors in semantic space.

### Storage & Search

```sql
ALTER TABLE documents ADD COLUMN embedding vector(1024);
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops);
```

- **HNSW**: graph-based index for fast nearest-neighbor search (O(log n) lookup).
- **Cosine similarity**: angle between vectors (1 = identical, -1 = opposite).

### RAG Pattern

```
New entry → Embed → Find top-k similar chunks in DB
                              ↓
                    Pass them to LLM as context
                              ↓
                    LLM generates better analysis
```

Example:
- You write: "Nervous about tomorrow's review"
- System finds: 3 past entries about anxiety + reviews
- LLM sees them and says: *"You've handled this before. Two months ago you wrote..."*

---

## Database Schema

```sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    entry_text TEXT NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    ai_content TEXT,
    embedding vector(1024)
);

CREATE INDEX idx_embedding_hnsw ON documents 
USING hnsw (embedding vector_cosine_ops);
```

---

## Configuration

All config is in `docker-compose.yml` or Quarkus `application.properties`:

```properties
# Backend → Ollama
quarkus.langchain4j.ollama.base-url=http://ollama:11434

# Backend → PostgreSQL
quarkus.datasource.jdbc.url=jdbc:postgresql://db:15432/ai-diary
quarkus.datasource.username=ai-diary
quarkus.datasource.password=not-secure  # Change in production!

# Ollama model settings
OLLAMA_KEEP_ALIVE=-1  # Keep model loaded in memory
```

---

## Running in Production

### Native Build

For faster startup and lower memory, compile to native:

```bash
mvn clean package -Dnative -DskipTests -f backend/pom.xml
```

Backend binary: `backend/target/backend-runner` (~50ms startup).

### Resource Limits

Adjust in `docker-compose.yml`:

```yaml
services:
  ollama:
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1  # Use GPU if available
```

### Security Notes

- Change `POSTGRES_PASSWORD` in production.
- Use environment variables for sensitive config.
- Consider adding auth to the Vaadin frontend.
- Run behind a reverse proxy (nginx, Traefik) if exposed.

---

## Roadmap

- [ ] Multi-user support (shared / private diaries)
- [ ] Export/visualization of mood trends
- [ ] Wearable integration (heart rate, sleep correlation)
- [ ] Fine-tuning on personal journaling style
- [ ] Graph database for thought interconnection
- [ ] Mobile app with offline-first sync
- [ ] Custom LLM model support (beyond Ollama)

---

## Contributing

Contributions welcome! Please:

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit changes (`git commit -am 'Add feature'`)
4. Push to branch (`git push origin feature/my-feature`)
5. Open a Pull Request

---

## License

[Add your license here — MIT, Apache 2.0, etc.]

---

## Learning Resources

### Concepts

- [Embeddings & Semantic Search](https://www.pinecone.io/learn/vector-database/)
- [Retrieval-Augmented Generation (RAG)](https://arxiv.org/abs/2005.11401)
- [Quarkus Guide](https://quarkus.io/guides/)
- [LangChain4j Docs](https://docs.langchain4j.dev/)

### Related Projects

- [Ollama](https://ollama.ai) — Run local LLMs
- [pgvector](https://github.com/pgvector/pgvector) — PostgreSQL vector extension
- [LangChain4j](https://github.com/langchain4j/langchain4j) — LLM framework for Java
- [Quarkus](https://quarkus.io) — Kubernetes-native Java framework

---

## Questions?

Open an issue on GitHub or reach out. Enjoy journaling! 📝