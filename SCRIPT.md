# AI Diary: A Modern Stoic Practice
## YouTube Video Script (30 minutes)

---

## PART 1: INTRODUCTION (1.5 minutes)

### Opening Hook

"We're going to build an AI Diary in Java—a system that combines journaling with AI-powered insights. The twist? It runs entirely on your machine, uses vector embeddings for semantic search, and integrates everything from Llama 3.2 to PostgreSQL.

In the next 20 minutes, we'll walk through a real, production-ready architecture using:
- **Java 21 & Quarkus** for the backend
- **Vaadin & Spring Boot** for the frontend  
- **PostgreSQL + pgvector** for vector storage
- **Ollama & Llama 3.2** for local LLM inference
- **LangChain4j** for seamless AI integration

Then we'll deep dive into the technical implementation—specifically how vectors, embeddings, and RAG patterns work together."

---

## PART 2: SYSTEM ARCHITECTURE (3 minutes)

### Docker Compose Overview

*[Visual: Show docker-compose.yml file]*

"Our system has four containerized services. Let's start with the compose file:

```yaml
services:
  ollama:
    image: ollama/ollama:latest
    ports: ["11434:11434"]
    environment:
      - OLLAMA_KEEP_ALIVE=-1
    deploy:
      resources:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
  
  db:
    image: postgres + pgvector extension
    ports: ["15432:5432"]
    environment:
      - POSTGRES_DB=ai-diary
      
  backend:
    image: quarkus-app
    ports: ["8080:8080"]
    depends_on: [ollama, db]
    environment:
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://db:15432/ai-diary
      - OLLAMA_HOST=http://ollama:11434
  
  frontend:
    image: spring-boot-vaadin
    ports: ["8081:8081"]
    environment:
      - BACKEND_URL=http://backend:8080
```

**Architecture:**
- **Ollama** (Port 11434): Runs Llama 3.2 locally with GPU acceleration. `OLLAMA_KEEP_ALIVE=-1` means the model stays loaded in memory.
- **PostgreSQL + pgvector** (Port 15432): Stores diary entries and their embeddings as 1024-dimensional vectors.
- **Quarkus Backend** (Port 8080): Handles REST endpoints, AI service orchestration, and vector ingestion.
- **Vaadin Frontend** (Port 8081): Pure Java web UI, handles user input and displays streaming responses."

---

## PART 3: THE TECHNICAL DEEP DIVE—VECTORS & RAG (14 minutes)

### Understanding Vector Embeddings

"When you submit a diary entry, the embedding model (part of Llama 3.2) converts text into a 1024-dimensional vector. Here's what that means practically:

Text: 'I felt defeated today. Nothing went right.'
↓
Vector: [-0.023, 0.456, -0.891, ..., 0.234] (1024 floats)

Each dimension captures semantic meaning. Similar texts produce nearby vectors in this high-dimensional space.

```
Text similarity by vector distance:
'I felt sad today' → Vector A
'I was depressed' → Very close to Vector A (small cosine distance)
'I felt incredibly happy' → Far from Vector A (large cosine distance)
```

**Why this matters:**
- You don't hardcode similarities. The model learns semantic relationships.
- Cosine similarity is fast to compute (dot product normalized).
- HNSW indexing in PostgreSQL makes even million-vector searches sub-millisecond.
- When you write 'I'm anxious,' the system instantly finds all similar past entries."

### Vector Storage in PostgreSQL with pgvector

"PostgreSQL has a `vector` type (via the pgvector extension):

```sql
ALTER TABLE documents ADD COLUMN embedding vector(1024);
CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops);
```

The HNSW (Hierarchical Navigable Small World) index is a graph structure that organizes vectors spatially. Searching is logarithmic instead of linear.

**Performance:**
- Linear scan: O(n) — slow for millions of entries
- HNSW index: O(log n) — blazing fast
- Exact distance: 0.0-2.0 for cosine similarity"

### Data Flow: From Input to Storage

### The Complete Request Flow

"Here's what happens end-to-end when you submit an entry:

```
1. Frontend: POST /newEntry
   {
     "entryText": "Today I felt anxious",
     "entryTimestamp": "2025-11-20T10:30:00"
   }

2. RagResource receives request
   ↓
3. Create RagAssistant (Quarkus injected proxy)
   ↓
4. Call analyzeDiary() → Streams Multi<String> to frontend
   ↓
5. While streaming, collect complete response
   ↓
6. On completion, trigger RagIngestService.embedAndStoreNewEntry()
   ↓
7. Store in PostgreSQL with embedding vector
```

This is non-blocking and reactive. The user sees responses immediately while background work completes."

### The Backend: RagResource

"The entry point is RagResource.java:

```java
@POST
@Path(\"/newEntry\")
public Multi<String> addNewEntry(NewEntry newEntry) {
    RagAssistant assistant = provider.createAssistant();
    
    // Step 1: Get streamed response from LLM
    Multi<String> aiResponse = assistant.analyzeDiary(
        \"Diary entry on %s:\\n%s\".formatted(
            newEntry.entryTimestamp(),
            newEntry.entryText()
        )
    );
    
    // Step 2: Collect chunks while streaming
    final StringBuilder completeResult = new StringBuilder();
    aiResponse.onItem().invoke(completeResult::append);
    
    // Step 3: On completion, embed and persist
    aiResponse.onCompletion().invoke(
        () -> ingestor.embedAndStoreNewEntry(
            new DiaryEntryEntity(
                newEntry.entryText(),
                newEntry.entryTimestamp(),
                completeResult.toString()
            )
        )
    );
    
    return aiResponse;  // Stream to client immediately
}
```

**Key points:**
- `Multi<String>`: Mutiny reactive stream. Each chunk is a separate item.
- `onItem()`: Called for each streamed token
- `onCompletion()`: Called after AI finishes, triggers async persistence
- Non-blocking: Frontend receives chunks instantly while database write happens in background"

### The RagAssistant Interface

"This is where LangChain4j handles the plumbing:

```java
@RegisterAiService
@ApplicationScoped
public interface RagAssistant {
    @SystemMessage(\"\"\"
        You are an empathetic and insightful personal growth coach.
        The user keeps a daily diary. Analyze today's entry in context 
        of all previous entries.
        
        Return:
        1. Mood: Identify emotional tone
        2. Key Scenario: Summarize the situation
        3. Growth Insight: Reflection based on patterns
        4. Actionable Advice: One suggestion for tomorrow
        
        Keep response under 200 words.
    \"\"\")
    Multi<String> analyzeDiary(@UserMessage String todaysEntry);
}
```

**What @RegisterAiService does:**
- Quarkus generates a proxy at runtime
- Automatically connects to Ollama (via environment config)
- Handles prompt management
- Manages the context window
- Streams responses via Multi<String>"

### The Embedding & Ingestion Service

"This is where vectors get created and stored:

```java
@ApplicationScoped
public class RagIngestService {
    
    @Inject EmbeddingModel embeddingModel;
    @Inject PgVectorEmbeddingStore embeddingStore;
    @Inject DiaryEntryRepository repository;
    
    @Transactional
    public void embedAndStoreNewEntry(DiaryEntryEntity newEntity) {
        // 1. Persist the diary entry
        repository.persist(newEntity);
        
        // 2. Setup the embedding ingestor
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
            .embeddingStore(embeddingStore)
            .embeddingModel(embeddingModel)
            .documentSplitter(recursive(500, 0))
            .build();
        
        // 3. Ingest into vector store with metadata
        ingestor.ingest(
            Document.from(newEntity.entryText,
                Metadata.from(Map.of(
                    \"Timestamp\", DateTimeFormatter.ISO_DATE_TIME.format(newEntity.timestamp),
                    \"AI hint\", newEntity.aiContent
                )))
        );
    }
}
```

**Breaking it down:**

1. **Persistence**: Save raw entry to PostgreSQL
2. **Document Splitting**: Recursive split at 500 tokens with 0 overlap
   - Why? Long entries contain multiple semantic topics
   - Splitting lets us find relevant *paragraphs*, not just whole entries
   - Example: A long entry about work AND relationships gets split into 2-3 chunks
3. **Embedding**: Each chunk → 1024-dimensional vector
4. **Storage**: Vector + metadata stored in pgvector
   - Metadata includes timestamp and AI analysis for later retrieval"

### Document Splitting Strategy

"The `recursive(500, 0)` splitter is critical:

```
Original diary entry: 2000 tokens

↓ Split at 500-token boundaries

Chunk 1 (tokens 1-500) → Embedding 1
Chunk 2 (tokens 500-1000) → Embedding 2
Chunk 3 (tokens 1000-1500) → Embedding 3
Chunk 4 (tokens 1500-2000) → Embedding 4

All stored in pgvector with their metadata
```

Later, when the user writes something new, the system can find specific relevant paragraphs from weeks ago. This is RAG (Retrieval Augmented Generation)."

### The Entity Model

```java
@Entity
@Table(name = \"documents\")
public class DiaryEntryEntity extends PanacheEntity {
    
    @Column(columnDefinition = \"TEXT\")
    public String entryText;
    
    public LocalDateTime timestamp;
    
    @Column(columnDefinition = \"TEXT\")
    public String aiContent;
    
    @Column(columnDefinition = \"vector(1024)\")
    public float[] embedding;
}
```

The `vector(1024)` column:
- Not a string or JSON
- Native PostgreSQL type optimized for similarity queries
- Indexed with HNSW for fast nearest-neighbor searches
- Cosine distance: measures angle between vectors (0 = identical, 2 = opposite)"

### RAG in Action

"When a new entry comes in, LangChain4j automatically:

1. Embeds the new entry
2. Queries PostgreSQL for similar vectors
3. Passes top results as context to the LLM

```
New entry: \"I'm nervous about tomorrow's presentation\"

↓ Embed to vector

↓ Query PostgreSQL:
   SELECT * FROM documents 
   ORDER BY embedding <=> query_vector 
   LIMIT 5

↓ Get back 5 most similar past entries

↓ LLM sees them in context, can say:
   \"I notice you've been anxious about presentations before.
    Two months ago you said...[similar entry]...
    You handled it well then.\"
```

This is continuous learning without fine-tuning. The LLM has full history available."

## PART 4: FRONTEND & DEPLOYMENT (3 minutes)

### Vaadin Frontend

"Frontend is Spring Boot + Vaadin. Key points:

1. **100% Java** — No JavaScript frameworks needed
2. **Component-based** — Buttons, text areas, grids are objects
3. **Two-way binding** — Changes sync automatically
4. **Reactive backend** — Streams responses from backend

User flow:
- Text area for diary input
- POST to `/newEntry` on submit
- Receive `Multi<String>` response
- Display tokens as they arrive
- Button to view previous entries"

### Deployment with Docker

"Everything runs in containers:

```bash
docker-compose up --build
```

Services start in order:
1. PostgreSQL initializes pgvector extension
2. Ollama pulls and runs Llama 3.2
3. Backend connects to both
4. Frontend connects to backend
5. System ready in ~30 seconds (cold start)

**Performance:**
- Quarkus JVM mode: ~1 second startup
- Quarkus native: ~50ms startup
- Backend memory: ~300MB
- Complete diary analysis: 15-30 seconds (LLM inference time)"

## PART 5: CLOSING & NEXT STEPS (2 minutes)

### Getting Started

"To run this yourself:

```bash
git clone <repo>
cd ai-diary
docker-compose up --build
```

Then navigate to `localhost:8081` and start writing.

**Requirements:**
- Docker + Docker Compose
- ~8GB RAM (4GB for Ollama, 2GB for app, 2GB buffer)
- GPU recommended but not required (CPU mode works, slower)
- ~5GB disk space (for Llama 3.2 model)"

### Key Takeaways

"What we built:
1. **End-to-end AI system** — Fully local, no external API calls
2. **Vector-based memory** — Semantic similarity without hardcoding
3. **Reactive streaming** — Real-time UI feedback
4. **Production-ready** — Uses battle-tested frameworks
5. **Scalable** — Architecture supports millions of entries

Technical highlights:
- Quarkus + LangChain4j for AI integration
- PostgreSQL pgvector for semantic search
- Ollama for local LLM inference
- Vaadin for pure Java frontend
- Docker for reproducible deployment"

---

## PART 8: TECHNICAL REFERENCE (2 minutes)

### Stack Summary

```
┌────────────────────────────────────┐
│      COMPLETE TECH STACK           │
├────────────────────────────────────┤
│ Language: Java 21                  │
│ Backend: Quarkus 3.28.5            │
│ Frontend: Spring Boot 3.5.6 + Vaadin
│ Database: PostgreSQL + pgvector    │
│ LLM Framework: LangChain4j         │
│ LLM Runtime: Ollama + Llama 3.2   │
│ Build: Maven                       │
│ Deployment: Docker + Docker Compose│
└────────────────────────────────────┘
```

### Key Dependencies

Backend:
- `io.quarkus:quarkus-rest` — REST endpoints
- `io.quarkus:quarkus-hibernate-orm-panache` — ORM
- `io.quarkiverse.langchain4j:quarkus-langchain4j-ollama` — LLM integration
- `io.quarkiverse.langchain4j:quarkus-langchain4j-pgvector` — Vector store
- `io.quarkus:quarkus-mutiny` — Reactive streams

Frontend:
- `com.vaadin:vaadin-spring-boot-starter` — UI components
- `org.springframework:spring-webflux` — Reactive web

### Database Schema

```sql
CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    entry_text TEXT,
    timestamp TIMESTAMP,
    ai_content TEXT,
    embedding vector(1024)
);

CREATE INDEX ON documents 
USING hnsw (embedding vector_cosine_ops);
```

The HNSW index is created automatically by LangChain4j."

### Configuration

```properties
# Quarkus config
quarkus.datasource.jdbc.url=jdbc:postgresql://db:15432/ai-diary
quarkus.datasource.username=ai-diary
quarkus.langchain4j.ollama.base-url=http://ollama:11434
```

### API Endpoints

```
POST /newEntry
  Content-Type: application/json
  Body: {
    "entryText": "string",
    "entryTimestamp": "2025-11-20T10:30:00"
  }
  Response: Server-Sent Events (text/plain, streaming)

GET /entries?amount=10&offset=0
  Response: List<ProcessedEntry> (JSON)
```

## END SCRIPT

**Total Length: ~20 minutes**

### Timing Breakdown
- Introduction: 1.5 min
- Architecture: 3 min
- Technical Deep Dive: 14 min
- Frontend & Deployment: 3 min
- Closing: 2 min
- Technical Reference: 2 min

---

## VISUAL AIDS NEEDED

1. Docker Compose architecture diagram
2. Vector embedding visualization (1024-dimensional space)
3. Data flow: input → embedding → storage → retrieval
4. HNSW index structure
5. Code walkthrough with syntax highlighting
6. Performance metrics comparison
7. Database schema diagram

---

## SPEAKER NOTES & TIPS

### Pacing

- **Architecture section**: Slow down here. Many viewers won't know Docker/containers.
- **Vector section**: This is the "magic" moment. Emphasize why cosine distance matters.
- **RAG section**: Show concrete example (anxious entry finds previous similar entries).
- **Code snippets**: Read them aloud, explain each annotation.

### Live Demo (Optional)

If recording live:
1. Show `docker-compose up` starting
2. Show frontend at `localhost:8081`
3. Write a test entry
4. Show AI response streaming
5. Check PostgreSQL with `psql` to show embedded vectors

### Key Concepts to Hammer Home

1. **Embeddings are not magic** — They're just vectors, math we understand
2. **pgvector is a game-changer** — Vector search in your database, no external service
3. **RAG means never losing context** — Old entries inform new analysis
4. **Streaming matters** — UX and psychological impact of seeing responses unfold
5. **Local-first is powerful** — Privacy + control + speed

### Common Questions

**Q: Why Ollama instead of OpenAI?**
- Privacy, cost, control, speed, local processing

**Q: Why Quarkus vs Spring Boot?**
- Startup time, memory footprint, cloud-native design

**Q: Can this scale to millions of entries?**
- Yes. HNSW indexing is logarithmic. pgvector can handle billions.

**Q: How long are typical AI responses?**
- 15-30 seconds (Llama 3.2 inference, not framework overhead)

**Q: Can I use a different LLM?**
- Yes. Ollama supports many models. Change one config line.

---

## OPTIONAL EXTENSIONS

### Future improvements to mention:
- Multi-user diaries (shared reflection spaces)
- Wearable integration (mood + heart rate correlation)
- Export/visualization of emotional trends
- Multi-language support
- Fine-tuning on personal style
- Graph database for connection analysis
- Mobile app with offline-first sync
