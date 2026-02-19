package io.eventbob.example.lower;

import io.eventbob.core.Dispatcher;

/**
 * Service that implements lowercase transformation logic.
 * <p>
 * This demonstrates the service layer pattern for extracting business logic
 * from handlers. In real applications, services would contain complex business
 * logic, database access, or external API calls.
 * </p>
 * <p>
 * Note: This service doesn't actually use the dispatcher, but it's included
 * for consistency with the echo example pattern.
 * </p>
 */
public class LowerService {
    private final Dispatcher dispatcher;

    /**
     * Creates a lower service with the specified dispatcher.
     *
     * @param dispatcher dispatcher for calling other capabilities
     */
    public LowerService(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Transforms input string to lowercase.
     *
     * @param input the string to transform
     * @return lowercase version of input
     */
    public String processLowercase(String input) {
        return input.toLowerCase();
    }
}
