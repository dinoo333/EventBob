package io.eventbob.example.lower;

import io.eventbob.core.Dispatcher;

/**
 * Service that implements lowercase transformation logic.
 * <p>
 * This demonstrates the service layer pattern for extracting business logic
 * from handlers. In real applications, services would contain complex business
 * logic, database access, or external API calls.
 * </p>
 */
public class LowerService {
    /**
     * Transforms input string to lowercase.
     *
     * @param input the string to transform
     * @param dispatcher the dispatcher (not used by this method)
     * @return lowercase version of input
     */
    public String processLowercase(String input, Dispatcher dispatcher) {
        return input.toLowerCase();
    }
}
