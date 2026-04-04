package com.game.hub.service;

import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service để lọc các từ ngữ bị cấm trong chat
 * Chuyển đổi các từ cấm thành ***
 */
@Service
public class WordFilterService {
    
    private static final Set<String> BANNED_WORDS = new HashSet<>();
    
    static {
        // Danh sách từ ngữ bị cấm (Vietnamese + English)
        BANNED_WORDS.addAll(Set.of(
            // Tiếng Việt - từ miệt thị, xúc phạm
            "chó",
            "mèo",
            "ngốc",
            "ngu",
            "khốn",
            "éo",
            "hèn",
            "kiếp",
            "địt",
            "buồi",
            "mẹ kiếp",
            "đồ chó",
            "thằng mặt",
            "thằng ngu",
            "con chó",
            "con gái",
            "xấu tính",
            "đểu",
            "bẩn",
            "bốl",
            "chửi rủa",
            "chửi",
            "tục tĩu",
            "sex",
            "porn",
            "xxx",
            
            // English - profanity
            "damn",
            "hell",
            "shit",
            "fuck",
            "bitch",
            "bastard",
            "asshole",
            "crap",
            "piss",
            "ass",
            "dick",
            "cock",
            "pussy",
            "whore",
            "slut",
            "nigger",
            "nigga",
            "faggot",
            "gay",
            "lesbian",
            "xxx",
            "porn",
            "sex",
            "horny",
            "rape",
            "murder",
            "kill",
            "die",
            "death",
            "hate",
            "stupid",
            "idiot",
            "retard",
            "dumb"
        ));
    }
    
    /**
     * Lọc từ ngữ bị cấm, chuyển thành ***
     * Không phân biệt hoa/thường
     * 
     * @param content nội dung cần lọc
     * @return nội dung sau khi lọc
     */
    public String filter(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        String filtered = content;
        for (String bannedWord : BANNED_WORDS) {
            // Tạo regex pattern không phân biệt hoa thường
            // Sử dụng word boundary để tránh lọc substring
            String pattern = "\\b" + Pattern.quote(bannedWord) + "\\b";
            filtered = filtered.replaceAll("(?i)" + pattern, "***");
        }
        
        return filtered;
    }
    
    /**
     * Kiểm tra nội dung có chứa từ ngữ bị cấm không
     * 
     * @param content nội dung cần kiểm tra
     * @return true nếu chứa từ cấm, false nếu không
     */
    public boolean containsBannedWords(String content) {
        if (content == null || content.isEmpty()) {
            return false;
        }
        
        String lowerContent = content.toLowerCase(Locale.ENGLISH);
        for (String bannedWord : BANNED_WORDS) {
            String pattern = "\\b" + Pattern.quote(bannedWord) + "\\b";
            Pattern p = Pattern.compile(pattern);
            Matcher m = p.matcher(lowerContent);
            if (m.find()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Thêm từ ngữ bị cấm vào danh sách
     * 
     * @param word từ cần thêm
     */
    public void addBannedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            BANNED_WORDS.add(word.toLowerCase(Locale.ENGLISH).trim());
        }
    }
    
    /**
     * Xóa từ ngữ bị cấm khỏi danh sách
     * 
     * @param word từ cần xóa
     */
    public void removeBannedWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            BANNED_WORDS.remove(word.toLowerCase(Locale.ENGLISH).trim());
        }
    }
    
    /**
     * Lấy danh sách từ bị cấm hiện tại
     * 
     * @return set của các từ bị cấm
     */
    public Set<String> getBannedWords() {
        return new HashSet<>(BANNED_WORDS);
    }
}
