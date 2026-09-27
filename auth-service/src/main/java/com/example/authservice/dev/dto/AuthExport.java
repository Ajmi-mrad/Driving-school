package com.example.authservice.dev.dto;

import com.example.authservice.web.dto.UserResponse;

import java.util.List;

/** Export JSON des données auth (utilisateurs). Réutilise le DTO d'exposition existant. */
public record AuthExport(List<UserResponse> users) {
}
