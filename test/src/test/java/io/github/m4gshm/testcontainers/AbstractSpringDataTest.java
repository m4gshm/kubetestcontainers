package io.github.m4gshm.testcontainers;

import io.github.m4gshm.jpa.model.UserEntity;
import io.github.m4gshm.spring.data.repository.UserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled
public class AbstractSpringDataTest {
    @Autowired
    protected UserRepository userRepository;

    @Test
    public void storeUsers() {
        userRepository.save(UserEntity.builder()
                .id(1)
                .name("First")
                .build());

        var user = userRepository.findById(1).get();
        assertEquals("First", user.getName());
    }
}
