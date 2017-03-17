package com.realm.annotation.entity;

import com.realm.annotation.api.Entity;
import com.realm.annotation.api.Transient;

@Entity(primaryKey = "id", ignores = {"avatar"})
public class User {
    private long id;
    private String username;
    private String avatar;

    @Transient
    private String birthday;

    public User(long id, String username, String avatar, String birthday) {
        this.id = id;
        this.username = username;
        this.avatar = avatar;
        this.birthday = birthday;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getBirthday() {
        return birthday;
    }

    public void setBirthday(String birthday) {
        this.birthday = birthday;
    }
}
