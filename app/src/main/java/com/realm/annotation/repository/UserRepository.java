package com.realm.annotation.repository;

import com.realm.annotation.entity.User;

public interface UserRepository {
    User findOne(String username);

    void save(User user);
}
