package com.example.authservice.mapper;

import com.example.authservice.domain.User;
import com.example.authservice.web.dto.UserResponse;
import org.mapstruct.Mapper;

/**
 * Mapping entité {@link User} → DTO de sortie. À invoquer dans la couche service transactionnelle
 * (les collections sont LAZY).
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}