package dev.rabauer.ai.diary;

import dev.rabauer.ai.diary.dto.NewEntry;
import dev.rabauer.ai.diary.dto.ProcessedEntry;
import dev.rabauer.ai.diary.storage.RagIngestService;
import dev.rabauer.ai.diary.storage.entities.DiaryEntryEntity;
import dev.rabauer.ai.diary.storage.entities.DiaryEntryRepository;
import dev.rabauer.ai.diary.storage.rag.RagAssistantProvider;
import io.smallrye.mutiny.Multi;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.inject.Inject;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class RagResource {

    @Inject
    RagIngestService ingestor;

    @Inject
    RagAssistantProvider provider;

    @Inject
    DiaryEntryRepository diaryEntryRepository;

    @POST
    @Path("/newEntry")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.TEXT_PLAIN)
    public Multi<String> addNewEntry(NewEntry newEntry) {
        RagAssistant assistant = provider.createAssistant();
        Multi<String> aiResponse = assistant.analyzeDiary(
                "Diary entry on %s:\n%s"
                        .formatted(
                                DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(newEntry.entryTimestamp()),
                                newEntry.entryText()
                        )
        );

        final StringBuilder completeResult = new StringBuilder();
        aiResponse.onItem().invoke(completeResult::append);

        aiResponse.onCompletion().invoke(
                () ->
                        ingestor.embedAndStoreNewEntry(
                                new DiaryEntryEntity(
                                        newEntry.entryText(),
                                        newEntry.entryTimestamp(),
                                        completeResult.toString()
                                )
                        )
        );

        return aiResponse;
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
