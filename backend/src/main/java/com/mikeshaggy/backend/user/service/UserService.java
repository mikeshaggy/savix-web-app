package com.mikeshaggy.backend.user.service;

import com.mikeshaggy.backend.common.util.EntityFetcher;
import com.mikeshaggy.backend.user.domain.User;
import com.mikeshaggy.backend.user.dto.UserDTO;
import com.mikeshaggy.backend.user.dto.UserMapper;
import com.mikeshaggy.backend.user.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final EntityFetcher entityFetcher;

    public List<UserDTO> getAllUsers() {
        return mapToDTO(userRepository.findAll());
    }

    public UserDTO getUserById(Integer id) {
        User user = entityFetcher.getUserOrThrow(id);
        return userMapper.toDTO(user);
    }

    public UserDTO getUserByUsername(String username) {
        User user = entityFetcher.getUserOrThrow(username);
        return userMapper.toDTO(user);
    }

    @Transactional
    public UserDTO createUser(UserDTO userDTO) {
        User user = userMapper.toEntity(userDTO);
        User savedUser = userRepository.save(user);
        
        log.info("Created user '{}' with id: {}",
                savedUser.getUsername(), savedUser.getId());
        
        return userMapper.toDTO(savedUser);
    }

    @Transactional
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User existingUser = entityFetcher.getUserOrThrow(id);

        existingUser.setUsername(userDTO.username());
        User updatedUser = userRepository.save(existingUser);
        
        log.info("Updated user id: {} to username: '{}'",
                id, updatedUser.getUsername());
        
        return userMapper.toDTO(updatedUser);
    }

    @Transactional
    public void deleteUser(Integer id) {
        User user = entityFetcher.getUserOrThrow(id);

        log.info("Deleting user '{}' with id: {}",
                user.getUsername(), user.getId());

        userRepository.delete(user);
    }

    private List<UserDTO> mapToDTO(List<User> users) {
        return users.stream()
                .map(userMapper::toDTO)
                .toList();
    }
}
