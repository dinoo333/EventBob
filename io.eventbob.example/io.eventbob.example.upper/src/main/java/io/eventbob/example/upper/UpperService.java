package io.eventbob.example.upper;

import io.eventbob.core.Dispatcher;

/**
 * Service that implements uppercase transformation logic.
 * <p>
 * This demonstrates the service layer pattern for extracting business logic
 * from handlers. In real applications, services would contain complex business
 * logic, database access, or external API calls.
 * </p>
 */
public class UpperService {
    /**
     * Transforms input string to uppercase.
     *
     * @param input the string to transform
     * @param dispatcher the dispatcher (not used by this method)
     * @return uppercase version of input
     */
    public String processUppercase(String input, Dispatcher dispatcher) {
        return input.toUpperCase();
    }
}
