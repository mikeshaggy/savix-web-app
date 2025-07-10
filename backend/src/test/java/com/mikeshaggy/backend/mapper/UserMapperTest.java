package com.mikeshaggy.backend.mapper;

import com.mikeshaggy.backend.domain.user.User;
import com.mikeshaggy.backend.dto.UserDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class UserMapperTest {

    private UserMapper userMapper;

    private static final int USER_ID = 1;
    private static final String USER_USERNAME = "johndoe";
    private static final String USER_PASSWORD = "test_password";

    private LocalDateTime createdAt;
    private User testUser;

    @BeforeEach
    void setUp() {
        userMapper = UserMapper.INSTANCE;

        createdAt = LocalDateTime.of(2024, 1, 15, 10, 30);

        testUser = User.builder()
                .id(USER_ID)
                .username(USER_USERNAME)
                .password(USER_PASSWORD)
                .createdAt(createdAt)
                .categories(new HashSet<>())
                .transactions(new HashSet<>())
                .build();
    }

    @Test
    @DisplayName("Converting User to UserDTO")
    void entityToDto() {
        // when
        UserDTO userDTO = userMapper.toDTO(testUser);

        // then
        assertAll(
                () -> assertThat(userDTO.getId()).isEqualTo(USER_ID),
                () -> assertThat(userDTO.getUsername()).isEqualTo(USER_USERNAME),
                () -> assertThat(userDTO.getCreatedAt()).isEqualTo(createdAt)
        );
    }

    @Test
    @DisplayName("Converting UserDTO to User")
    void dtoToEntity() {
        // given
        UserDTO userDTO = UserDTO.builder()
                .id(USER_ID)
                .username(USER_USERNAME)
                .createdAt(createdAt)
                .build();

        // when
        User user = userMapper.toEntity(userDTO);

        // then
        assertAll(
                () -> assertThat(user.getId()).isEqualTo(USER_ID),
                () -> assertThat(user.getUsername()).isEqualTo(USER_USERNAME),
                () -> assertThat(user.getCreatedAt()).isEqualTo(createdAt),
                () -> assertThat(user.getPassword()).isNull(),
                () -> assertThat(user.getCategories()).isEmpty(),
                () -> assertThat(user.getTransactions()).isEmpty()
        );
    }

    @Test
    @DisplayName("Converting null User should return null")
    void entityToDto_WithNullUser_ShouldReturnNull() {
        // when
        UserDTO result = userMapper.toDTO(null);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Converting null UserDTO should return null")
    void dtoToEntity_WithNullUserDTO_ShouldReturnNull() {
        // when
        User result = userMapper.toEntity(null);

        // then
        assertThat(result).isNull();
    }
}
