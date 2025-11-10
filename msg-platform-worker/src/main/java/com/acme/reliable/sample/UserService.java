package com.acme.reliable.sample;

import com.acme.reliable.core.PermanentException;
import com.acme.reliable.core.TransientException;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.logging.Logger;

/**
 * Domain service for User operations
 */
@Singleton
public class UserService {
    private static final Logger logger = Logger.getLogger(UserService.class.getName());

    /**
     * Command handler for CreateUserCommand.
     * Auto-discovered by AutoCommandHandlerRegistry.
     */
    public Map<String, Object> handleCreateUser(CreateUserCommand cmd) {
        logger.info("Handling CreateUserCommand for username: " + cmd.username());

        // Check for test failure scenarios
        if (cmd.username() != null && cmd.username().contains("failPermanent")) {
            throw new PermanentException("Invariant broken");
        }
        if (cmd.username() != null && cmd.username().contains("failTransient")) {
            throw new TransientException("Downstream timeout");
        }

        // Simulate user creation
        String userId = "u-123";
        logger.info("User created with ID: " + userId);

        // Return result for process orchestration
        return Map.of("userId", userId, "username", cmd.username());
    }
}
