package com.game.hub.repository;

import com.game.hub.entity.Friendship;
import com.game.hub.entity.FriendshipStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {
    
    // Find specific friendship between two users (both directions)
    Optional<Friendship> findByRequesterIdAndAddresseeId(String requesterId, String addresseeId);
    
    // Check if any friendship exists (both directions)
    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END " +
           "FROM Friendship f " +
           "WHERE (f.requesterId = :user1 AND f.addresseeId = :user2) " +
           "OR (f.requesterId = :user2 AND f.addresseeId = :user1)")
    boolean existsBetween(@Param("user1") String user1, @Param("user2") String user2);
    
    // Get all connections (both sent and received) with any status
    @Query("SELECT f FROM Friendship f " +
           "WHERE (f.requesterId = :userId OR f.addresseeId = :userId)")
    List<Friendship> findAllConnectionsForUser(@Param("userId") String userId);
    
    // Get pending requests received (where user is addressee and status is PENDING)
    List<Friendship> findByAddresseeIdAndStatus(String addresseeId, FriendshipStatus status);
    
    // Get pending requests sent (where user is requester and status is PENDING)
    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);
    
    // Get accepted friendships (both directions)
    @Query("SELECT f FROM Friendship f " +
           "WHERE (f.requesterId = :userId OR f.addresseeId = :userId) " +
           "AND f.status = :status")
    List<Friendship> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") FriendshipStatus status);
    
    // Count friends
    @Query("SELECT COUNT(f) FROM Friendship f " +
           "WHERE (f.requesterId = :userId OR f.addresseeId = :userId) " +
           "AND f.status = 'ACCEPTED'")
    long countFriendsByUserId(@Param("userId") String userId);
    
    // Backward compatibility queries (kept for legacy code)
    @Query("SELECT f FROM Friendship f " +
           "WHERE (f.requesterId = :user1 OR f.addresseeId = :user1)")
    List<Friendship> findByRequesterIdOrAddresseeId(@Param("user1") String user1);
    
    @Query("SELECT f FROM Friendship f " +
           "WHERE f.addresseeId = :userId AND f.status = 'PENDING'")
    List<Friendship> findByAddresseeIdAndAcceptedFalse(@Param("userId") String userId);
    
    @Query("SELECT f FROM Friendship f " +
           "WHERE f.requesterId = :userId AND f.status = 'PENDING'")
    List<Friendship> findByRequesterIdAndAcceptedFalse(@Param("userId") String userId);
}
