package com.realm.annotation.sample.repository;

import com.realm.annotation.sample.entity.User;

public interface UserRepository {
    User findOne(String username);

    void save(User user);
}
