package org.springframework.modulith.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.modulith.events.core.EventPublicationRegistry;
import org.springframework.modulith.events.core.TargetEventPublication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Controller
public class EventsController {

    private static final Logger LOG = LoggerFactory.getLogger(EventsController.class);

    private final IncompleteEventPublications incompleteEventPublications;

    private final EventPublicationRegistry eventPublicationRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public EventsController(IncompleteEventPublications incompleteEventPublications, EventPublicationRegistry eventPublicationRegistry, ApplicationEventPublisher eventPublisher, ObjectMapper objectMapper) {
        this.incompleteEventPublications = incompleteEventPublications;
        this.eventPublicationRegistry = eventPublicationRegistry;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/events")
    public String index(Model model) {
        Collection<TargetEventPublication> incompletePublications = eventPublicationRegistry.findIncompletePublications();
        List<EventViewModel> viewModels = incompletePublications.stream().map(this::toEventViewModel).toList();

        model.addAttribute("events", viewModels);
        return "events";
    }

    // Crude, minimal mapper from generic TargetEventPublication to some form of a ViewModel required by the UI
    private EventViewModel toEventViewModel(TargetEventPublication targetEventPublication) {
        UUID eventId = targetEventPublication.getIdentifier();
        String eventListener = targetEventPublication.getTargetIdentifier().getValue();
        Object event = targetEventPublication.getEvent();
        String eventType = event.getClass().getName();
        String content;
        try {
            content = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return new EventViewModel(eventId, targetEventPublication.getPublicationDate(), eventListener, eventType, content);
    }

    @PostMapping("/resend")
    public String resend(@RequestParam UUID eventId) {
        incompleteEventPublications.resubmitIncompletePublications(eventPublication -> eventPublication.getIdentifier().equals(eventId));

        return "redirect:/events";
    }

    record EventViewModel(UUID id, Instant publicationDate, String listenerId, String eventType, String eventContent) {}

    // Just to trigger an event for demo purposes
    // TODO delete everything below here
    @GetMapping("/addEvent/{text}")
    public String sendError(@PathVariable String text) {
        eventPublisher.publishEvent(new OrderCancelledEvent(text));
        return "events";
    }

    public record OrderCancelledEvent(String orderId) {}

    @ApplicationModuleListener
    void on(OrderCancelledEvent event) throws InterruptedException {

        var orderId = event.orderId();

        LOG.info("Received order completion for {}.", orderId);

        if (orderId.startsWith("fail")) {
            throw new RuntimeException(orderId);
        }

        // Simulate busy work
        Thread.sleep(1000);

        LOG.info("Finished order completion for {}.", orderId);
    }
}
