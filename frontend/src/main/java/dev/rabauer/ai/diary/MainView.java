package dev.rabauer.ai.diary;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.Route;
import dev.rabauer.ai.diary.dto.NewEntry;
import dev.rabauer.ai.diary.dto.ProcessedEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Route("")
public class MainView extends VerticalLayout {

    @Value("${backend.url}")
    private String backendUrl;

    public MainView() {
        setSizeFull();
        UI currentUi = UI.getCurrent();

        TextArea inputArea = new TextArea("New entry");

        Button saveButton = new Button("Save", e -> {
            String input = inputArea.getValue();
            LocalDateTime now = LocalDateTime.now();

            inputArea.clear();
            UiEntry newUiEntry = new UiEntry(now);
            newUiEntry.entryText.setValue(input);
            this.addComponentAtIndex(1, newUiEntry.container);

            storeNewEntry(input, now)
                    .subscribe(
                            token -> currentUi.access(() -> newUiEntry.entryAiText().setValue(newUiEntry.entryAiText().getValue() + token))
                    );
        });

        this.add(new HorizontalLayout(inputArea, saveButton));

        this.getEntries(10, 0)
                .subscribe(entry -> this.add(new UiEntry(entry).container())
                );
    }

    public record UiEntry(
            TextArea entryText,
            TextArea entryAiText,
            HorizontalLayout container
    ) {
        public UiEntry(LocalDateTime entryTime) {
            this(
                    new TextArea("Entry - " + DateTimeFormatter.ofPattern("dd.MM.yyyy-hh:mm").format(entryTime)),
                    new TextArea("Ai Remarks"),
                    new HorizontalLayout()
            );
            this.entryAiText.setReadOnly(true);
            this.entryText.setReadOnly(true);
            this.container.add(this.entryText, this.entryAiText());
        }

        public UiEntry(ProcessedEntry entry) {
            this(entry.entryTimestamp());
            this.entryText().setValue(entry.entryText());
            this.entryAiText.setValue(entry.entryAiText());
        }
    }

    private WebClient buildRestClient() {
        return WebClient
                .builder()
                .baseUrl(backendUrl)
                .build();
    }

    private Flux<String> storeNewEntry(String entryText, LocalDateTime entryTimestamp) {
        return buildRestClient()
                .post()
                .uri("/newEntry")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new NewEntry(entryText, entryTimestamp))
                .accept(MediaType.TEXT_PLAIN)
                .retrieve()
                .bodyToFlux(String.class);
    }

    private Flux<ProcessedEntry> getEntries(int amount, int offset) {
        return buildRestClient()
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/entries")
                        .queryParam("amount", amount)
                        .queryParam("offset", offset)
                        .build()
                )
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(ProcessedEntry.class);
    }
}
