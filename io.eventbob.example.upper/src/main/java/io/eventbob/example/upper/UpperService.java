package io.eventbob.example.upper;

import io.eventbob.core.Dispatcher;

/**
 * Service that implements uppercase transformation logic.
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
public class UpperService {
    private final Dispatcher dispatcher;

    /**
     * Creates an upper service with the specified dispatcher.
     *
     * @param dispatcher dispatcher for calling other capabilities
     */
    public UpperService(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Transforms input string to uppercase.
     *
     * @param input the string to transform
     * @return uppercase version of input
     */
    public String processUppercase(String input) {
        return input.toUpperCase();
    }
}
