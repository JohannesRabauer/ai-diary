package dev.rabauer.ai.diary;

import dev.rabauer.ai.diary.dto.NewEntry;
import dev.rabauer.ai.diary.dto.ProcessedEntry;
import dev.rabauer.ai.diary.rag.RagIngestService;
import dev.rabauer.ai.diary.rag.DiaryEntryEntity;
import dev.rabauer.ai.diary.rag.DiaryEntryRepository;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.inject.Inject;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MainResource {

    private final RagIngestService ingestor;
    private final DiaryAiAssistantProvider provider;
    private final DiaryEntryRepository diaryEntryRepository;

    public MainResource(RagIngestService ingestor, DiaryAiAssistantProvider provider, DiaryEntryRepository diaryEntryRepository) {
        this.ingestor = ingestor;
        this.provider = provider;
        this.diaryEntryRepository = diaryEntryRepository;
    }

    @POST
    @Path("/newEntry")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    @Blocking
    public Multi<String> addNewEntry(NewEntry newEntry) {
        DiaryAiAssistant assistant = provider.createAssistant();
        Multi<String> aiResponse = assistant.analyzeDiary(
                "Diary entry on %s:\n%s"
                        .formatted(
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(newEntry.entryTimestamp()),
                                newEntry.entryText()
                        )
        );

        final StringBuilder completeResult = new StringBuilder();

        return aiResponse
                .onItem().invoke(completeResult::append)
                .emitOn(Infrastructure.getDefaultWorkerPool())
                .onCompletion().invoke(
                () ->
                        ingestor.embedAndStoreNewEntry(
                                new DiaryEntryEntity(
                                        newEntry.entryText(),
                                        newEntry.entryTimestamp(),
                                        completeResult.toString()
                                )
                        )
        );
    }

    @GET
    @Path("/entries")
    @Produces(MediaType.APPLICATION_JSON)
    public List<ProcessedEntry> ask(
            @QueryParam("amount") int amount,
            @QueryParam("offset") int offset
    ) {
        //TODO: Apply fiilter
        return diaryEntryRepository.findAll()
                .stream().map(
                        entry -> new ProcessedEntry(
                                entry.timestamp,
                                entry.entryText,
                                entry.aiContent
                        )
                ).toList();
    }
}
