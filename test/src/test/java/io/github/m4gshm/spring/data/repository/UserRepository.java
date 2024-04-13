package io.github.m4gshm.spring.data.repository;

import io.github.m4gshm.jpa.model.UserEntity;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<UserEntity, Integer> {
}
