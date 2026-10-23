package com.familyconnect.modules.auth.api;

import java.util.Optional;
import java.util.UUID;

/** Public read-only user data exposed to other modules. */
public interface UserQuery {

    Optional<String> findFullName(UUID userId);
}
