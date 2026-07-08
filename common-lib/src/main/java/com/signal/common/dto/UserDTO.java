package com.signal.common.dto;

import java.util.UUID;

public record UserDTO(
    UUID id,
    String email,
    String name,
    UUID orgId,
    String role,
    UUID managerId,
    String oauthProvider,
    String pictureUrl
) {}
