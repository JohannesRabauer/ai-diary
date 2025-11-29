INTRO — OPENING (≈2:00)

Hi. I recently got back into Stoicism and have been journaling again — nothing heroic, just trying to notice how I react.
[pause]
Today I'm showing you an AI diary I built: local-first, private, vector-backed, and pragmatic.
I want you to leave with something you can run and understand.
[pause]
Quick tech callout: Java 21, Quarkus, Vaadin, PostgreSQL + pgvector, Ollama (Llama 3.2), LangChain4j.
[pause]
No fluff. We'll do infra, vector magic, and a short demo. Ready? Let's go.

---

SYSTEM ARCHITECTURE (≈3:00)

Show `docker-compose.yml` on screen now.
[pause — point to file]
There are four containers: frontend, backend, db, and ollama.
I'll say the ports and purpose slowly.

Ollama — model runtime on port 11434. Keep it local for privacy and latency.
If you have a GPU, use it. If not: CPU works, just slower. [small grin]

Postgres + pgvector — port 15432. Stores raw entries and 1024-d vectors.
We add a `vector(1024)` column and an HNSW index for fast search.

Quarkus backend — port 8080. REST API, LangChain4j wiring, ingestion pipeline.
It orchestrates LLM calls and vector store lookups.

Vaadin frontend — port 8081. Pure Java UI. Streams AI tokens to the user.

Show the docker compose env lines: `OLLAMA_KEEP_ALIVE=-1` and DB mapping.
[pause]
That's infra — small, focused, single responsibility. Now, the core: vectors.

---

VECTORS & RAG — CORE (≈10:00)

Say this slowly. Use hands to indicate "numbers":
An embedding is just a float array. For us: 1024 floats.
Text → embedding. Similar text → nearby vectors.

Example: read this out loud:
"I’m nervous about tomorrow" → vector: [-0.01, 0.34, -0.9, ...]
[pause]
Explain cosine similarity briefly:
Cosine measures the angle. Closer to 1 = very similar. Simple and fast.

Display SQL snippet on screen:
`ALTER TABLE documents ADD COLUMN embedding vector(1024);`
`CREATE INDEX ON documents USING hnsw (embedding vector_cosine_ops);`
[pause]
Explain HNSW in a short sentence:
HNSW builds a graph of vectors so nearest-neighbor lookups are very fast — think logarithmic.

Now RAG in plain steps. Read each slowly:
1) New entry arrives.
2) Backend asks LLM for analysis (mood, scenario, short advice) — streamed back.
3) Backend embeds the text and stores vectors (chunks if the entry is long).
4) On future requests, backend retrieves top-k similar chunks and sends them as context to the LLM.

Show sequence mermaid on screen while you read the steps.
[pause]

LangChain4j: the Java glue. Key bits to point out:
`@RegisterAiService` binds an interface to Ollama.
Method returns `Multi<String>` from Mutiny for streaming tokens.

Show the `RagAssistant` interface briefly and read this line:
"You are an empathetic coach. Provide: Mood, Key Scenario, Insight, Actionable Advice."

Talk about document splitting for 30 seconds.
We split long entries into 500-token chunks. Why? Because a long journal often contains multiple distinct thoughts — splitting lets retrieval match exact paragraphs.
Example: 2000 tokens = 4 chunks, each gets an embedding and metadata.

Show `RagIngestService` code and read the flow slowly:
Persist entry; build EmbeddingStoreIngestor; ingest Document.from(text, metadata).
[pause]
Explain outcome: private, incremental memory. No fine-tuning required. The model uses your own history as context.

Small joke: "Vectors are boring arrays that make you less boring in practice." [smile, short pause]

---

WORKFLOW & LIVE EXAMPLE (≈4:00)

Now the demo. Show your terminal running `docker compose up --build`.
Say these lines as the services start:
- Postgres initializes and sets up pgvector.
- Ollama loads the model (this takes time for big models).
- Backend registers the LLM service.
- Frontend serves on 8081.

Open `localhost:8081` on screen.
[pause — point to input box]
Type this entry slowly while on camera: "Had a tense conversation with my manager, felt defensive."
Hit Submit.

While the UI streams, narrate: "See the tokens arriving? That's Mutiny streaming `Multi<String>`.
The backend is receiving tokens from Ollama and at the same time preparing embeddings to store.
The UI doesn't block — UX stays responsive."

After the response completes, show a quick DB query:
`SELECT id, substring(entry_text,1,80) AS preview, ai_content FROM documents ORDER BY id DESC LIMIT 5;`
Point to the `ai_content` column — that's the LLM's analysis stored with the entry.
Point to the `embedding` column and say: "Not human-friendly, but indexed and searchable."

Second example: type: "I'm anxious about the review tomorrow." Submit.
Highlight how the backend retrieves similar past chunks and the LLM references them.
Say: "Now it can say: 'You've felt this before; three weeks ago you wrote...,' and that comes from similarity search."

Show the `RagResource` orchestration method and read the key parts slowly:
- `aiResponse.onItem().invoke(completeResult::append);`
- `aiResponse.onCompletion().invoke(() -> ingestor.embedAndStoreNewEntry(...));`

Emphasize: streaming first, persist later. Good UX, no blocking writes.

---

CLOSING & HANDY DETAILS (≈1:00)

Now wrap up with a small checklist and sign-off.
Read calmly, with a smile:
Checklist to run this yourself:
- Docker + Docker Compose
- ~8GB RAM (model dependent)
- GPU optional
- Clone repo, `docker compose up --build`, open `localhost:8081`

Final stoic-flavored sign-off:
"The tool won't make you wise. But it helps you see patterns faster. Journal, reflect, and try not to replay your dumb moments on loop."
[pause — smile]
"That's it. If you want a prompt-only teleprompter version or tighter timing, tell me which parts to trim and I’ll make a short cut-up for the camera."

---

DELIVERY NOTES (for reference on-camera)

- Read slowly. One sentence per breath.
- Pause when a diagram appears or when you switch to code view.
- Use small, natural jokes; don't overdo it.
- Keep hands visible for gestures when you say "vectors" or "graph".
- Demo flow: terminal → UI → DB → back to explanation.

EOF
