package com.realm.annotation.sample.repository.impl;

import com.realm.annotation.sample.entity.RealmUser;
import com.realm.annotation.sample.entity.User;
import com.realm.annotation.sample.repository.UserRepository;

import io.realm.Realm;

public class UserRepositoryImpl implements UserRepository {
    private Realm realm;

    public UserRepositoryImpl(Realm realm) {
        this.realm = realm;
    }

    @Override
    public User findOne(String username) {
        RealmUser realmUser = realm.where(RealmUser.class).equalTo("username", username).findFirst();
        if(realmUser != null) {
            return new User(realmUser.id, realmUser.username, realmUser.avatar, null);
        }
        return null;
    }

    @Override
    public void save(User user) {
        final RealmUser realmUser = new RealmUser(user.getId(), user.getUsername(), user.getAvatar());

        realm.executeTransaction(realm -> {
            realm.copyToRealmOrUpdate(realmUser);
        });
    }
}
