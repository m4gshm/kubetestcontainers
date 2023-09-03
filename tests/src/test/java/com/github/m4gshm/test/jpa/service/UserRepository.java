package com.github.m4gshm.test.jpa.service;

import com.github.m4gshm.test.jpa.model.UserEntity;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<UserEntity, Integer> {
}
