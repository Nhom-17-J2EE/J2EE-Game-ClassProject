package com.game.hub.entity;

public enum FriendshipStatus {
    PENDING,    // Lời mời chưa được chấp nhận
    ACCEPTED;   // Đã chấp nhận

    public boolean isPending() {
        return this == PENDING;
    }

    public boolean isAccepted() {
        return this == ACCEPTED;
    }
}
