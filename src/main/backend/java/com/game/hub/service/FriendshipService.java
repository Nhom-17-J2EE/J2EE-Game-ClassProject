package com.game.hub.service;

import com.game.hub.entity.Friendship;
import com.game.hub.entity.FriendshipStatus;
import com.game.hub.entity.UserAccount;
import com.game.hub.repository.FriendshipRepository;
import com.game.hub.repository.UserAccountRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class FriendshipService {
    private final FriendshipRepository friendshipRepository;
    private final UserAccountRepository userAccountRepository;

    public FriendshipService(FriendshipRepository friendshipRepository,
                             UserAccountRepository userAccountRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * Gửi lời mời kết bạn
     * @param requesterId ID người gửi lời mời
     * @param addresseeId ID người nhận lời mời
     * @return true nếu thành công, false nếu thất bại
     */
    public boolean sendRequest(String requesterId, String addresseeId) {
        // Validate inputs
        if (requesterId == null || addresseeId == null || 
            requesterId.isBlank() || addresseeId.isBlank()) {
            return false;
        }

        // Không thể gửi lời mời cho chính mình
        if (requesterId.equals(addresseeId)) {
            return false;
        }

        // Kiểm tra cả hai người tồn tại
        if (!userAccountRepository.existsById(requesterId) || 
            !userAccountRepository.existsById(addresseeId)) {
            return false;
        }

        // Không gửi nếu đã có relationship rồi (hai chiều)
        if (friendshipRepository.existsBetween(requesterId, addresseeId)) {
            return false;
        }

        // Tạo lời mời mới
        Friendship friendship = new Friendship(requesterId, addresseeId);
        friendship.setStatus(FriendshipStatus.PENDING);
        friendshipRepository.save(friendship);
        return true;
    }

    /**
     * Chấp nhận lời mời kết bạn
     * @param friendshipId ID của lời mời
     * @param actorUserId ID của người chấp nhận (để verify quyền)
     * @return true nếu thành công
     */
    public boolean acceptRequest(Long friendshipId, String actorUserId) {
        if (friendshipId == null || friendshipId <= 0) {
            return false;
        }

        Optional<Friendship> optFriendship = friendshipRepository.findById(friendshipId);
        if (optFriendship.isEmpty()) {
            return false;
        }

        Friendship friendship = optFriendship.get();

        // Không chấp nhận friendship đã accept rồi
        if (friendship.isAccepted()) {
            return false;
        }

        // Verify: người chấp nhận phải là addressee (người nhận lời mời)
        if (actorUserId != null && !actorUserId.isBlank()) {
            if (!actorUserId.equals(friendship.getAddresseeId())) {
                return false;
            }
        }

        // Cập nhật status
        friendship.setStatus(FriendshipStatus.ACCEPTED);
        friendship.setAcceptedAt(LocalDateTime.now());
        friendship.setUpdatedAt(LocalDateTime.now());
        friendshipRepository.save(friendship);
        return true;
    }

    /**
     * Từ chối lời mời kết bạn
     * @param friendshipId ID của lời mời
     * @param actorUserId ID của người từ chối
     * @return true nếu thành công
     */
    public boolean declineRequest(Long friendshipId, String actorUserId) {
        if (friendshipId == null || friendshipId <= 0) {
            return false;
        }

        Optional<Friendship> optFriendship = friendshipRepository.findById(friendshipId);
        if (optFriendship.isEmpty()) {
            return false;
        }

        Friendship friendship = optFriendship.get();

        // Không thể từ chối friendship đã được chấp nhận
        if (friendship.isAccepted()) {
            return false;
        }

        // Verify: chỉ addressee (người nhận lời mời) hoặc requester (người gửi) mới có thể từ chối
        if (actorUserId != null && !actorUserId.isBlank()) {
            boolean isRequester = actorUserId.equals(friendship.getRequesterId());
            boolean isAddressee = actorUserId.equals(friendship.getAddresseeId());
            if (!isRequester && !isAddressee) {
                return false;
            }
        }

        // Xóa lời mời
        friendshipRepository.delete(friendship);
        return true;
    }

    /**
     * Xóa bạn
     * @param userId1 ID người thứ nhất
     * @param userId2 ID người thứ hai
     * @return true nếu thành công
     */
    public boolean removeFriendship(String userId1, String userId2) {
        if (userId1 == null || userId2 == null || 
            userId1.isBlank() || userId2.isBlank() || 
            userId1.equals(userId2)) {
            return false;
        }

        // Tìm friendship (hai chiều)
        Optional<Friendship> optFriendship = friendshipRepository.findByRequesterIdAndAddresseeId(userId1, userId2)
                .or(() -> friendshipRepository.findByRequesterIdAndAddresseeId(userId2, userId1));

        if (optFriendship.isEmpty()) {
            return false;
        }

        // Chỉ xóa nếu đã được chấp nhận
        Friendship friendship = optFriendship.get();
        if (!friendship.isAccepted()) {
            return false;
        }

        friendshipRepository.delete(friendship);
        return true;
    }

    /**
     * Lấy danh sách bạn bè (những người đã chấp nhận lời mời)
     * @param userId ID người dùng
     * @return Danh sách UserAccount là bạn bè
     */
    public List<UserAccount> getFriends(String userId) {
        List<Friendship> links = friendshipRepository.findByUserIdAndStatus(userId, FriendshipStatus.ACCEPTED);
        Set<String> friendIds = new HashSet<>();

        for (Friendship friendship : links) {
            if (userId.equals(friendship.getRequesterId())) {
                friendIds.add(friendship.getAddresseeId());
            } else {
                friendIds.add(friendship.getRequesterId());
            }
        }

        return userAccountRepository.findAllById(friendIds);
    }

    /**
     * Lấy danh sách lời mời chưa được chấp nhận (mà người dùng nhận được)
     * @param userId ID người dùng
     * @return Danh sách Friendship với status PENDING
     */
    public List<Friendship> getPendingRequests(String userId) {
        return friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    /**
     * Lấy danh sách lời mời gửi đi chưa được chấp nhận
     * @param userId ID người dùng
     * @return Danh sách Friendship với status PENDING
     */
    public List<Friendship> getSentRequests(String userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING);
    }

    /**
     * Kiểm tra hai người có phải bạn bè không
     * @param userId1 ID người thứ nhất
     * @param userId2 ID người thứ hai
     * @return true nếu đã chấp nhận, false otherwise
     */
    public boolean areFriends(String userId1, String userId2) {
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            return false;
        }

        return friendshipRepository.findByRequesterIdAndAddresseeId(userId1, userId2)
                .or(() -> friendshipRepository.findByRequesterIdAndAddresseeId(userId2, userId1))
                .map(Friendship::isAccepted)
                .orElse(false);
    }

    /**
     * Kiểm tra có lời mời chưa được chấp nhận từ user A tới user B
     * @param fromId ID người gửi
     * @param toId ID người nhận
     * @return true nếu có lời mời pending
     */
    public boolean hasPendingRequest(String fromId, String toId) {
        if (fromId == null || toId == null || fromId.isBlank() || toId.isBlank()) {
            return false;
        }

        return friendshipRepository.findByRequesterIdAndAddresseeId(fromId, toId)
                .map(f -> f.getStatus() == FriendshipStatus.PENDING)
                .orElse(false);
    }

    /**
     * Lấy trạng thái relationship giữa hai người
     * @param userId1 ID người thứ nhất
     * @param userId2 ID người thứ hai  
     * @return "friends", "pending_received", "pending_sent", hoặc "none"
     */
    public String getRelationshipStatus(String userId1, String userId2) {
        if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
            return "none";
        }

        Optional<Friendship> friendship = friendshipRepository.findByRequesterIdAndAddresseeId(userId1, userId2)
                .or(() -> friendshipRepository.findByRequesterIdAndAddresseeId(userId2, userId1));

        if (friendship.isEmpty()) {
            return "none";
        }

        Friendship f = friendship.get();
        if (f.isAccepted()) {
            return "friends";
        }

        // Xác định ai là requester
        if (userId1.equals(f.getRequesterId())) {
            return "pending_sent";      // userId1 đã gửi lời mời
        } else {
            return "pending_received";  // userId1 nhận được yêu cầu
        }
    }

    /**
     * Đếm số lượng bạn bè của người dùng
     * @param userId ID người dùng
     * @return Số lượng bạn bè
     */
    public long countFriends(String userId) {
        return friendshipRepository.countFriendsByUserId(userId);
    }
}
