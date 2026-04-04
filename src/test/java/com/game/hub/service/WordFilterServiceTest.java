package com.game.hub.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test cases for WordFilterService
 */
class WordFilterServiceTest {
    
    private WordFilterService wordFilterService;
    
    @BeforeEach
    void setUp() {
        wordFilterService = new WordFilterService();
    }
    
    @Test
    void filterShouldReplaceBannedWordsWithAsterisks() {
        String filtered = wordFilterService.filter("this is damn bad");
        assertEquals("this is *** bad", filtered);
    }
    
    @Test
    void filterShouldIgnoreCase() {
        String filtered = wordFilterService.filter("This is DAMN bad");
        assertEquals("This is *** bad", filtered);
    }
    
    @Test
    void filterShouldHandleMultipleBannedWords() {
        String filtered = wordFilterService.filter("this is damn and fuck");
        assertEquals("this is *** and ***", filtered);
    }
    
    @Test
    void filterShouldNotReplacePartialMatches() {
        // "damn" should be replaced, but "damnation" should... actually depends on word boundary
        // With word boundaries, "damnation" won't be replaced because "damn" is a separate word
        String filtered = wordFilterService.filter("I love damnation too");
        // "damnation" should NOT be filtered if using word boundaries
        assertEquals("I love damnation too", filtered);
    }
    
    @Test
    void filterShouldHandleNullAndEmptyStrings() {
        assertFalse(wordFilterService.filter(null) != null);
        assertEquals("", wordFilterService.filter(""));
    }
    
    @Test
    void filterShouldPreserveNonBannedWords() {
        String filtered = wordFilterService.filter("Hello world, have a nice day!");
        assertEquals("Hello world, have a nice day!", filtered);
    }
    
    @Test
    void containsBannedWordsShouldReturnTrueWhenContainsBanned() {
        assertTrue(wordFilterService.containsBannedWords("this is damn bad"));
        assertTrue(wordFilterService.containsBannedWords("FUCK"));
        assertTrue(wordFilterService.containsBannedWords("Hello damn world"));
    }
    
    @Test
    void containsBannedWordsShouldReturnFalseWhenNoBanned() {
        assertFalse(wordFilterService.containsBannedWords("hello world"));
        assertFalse(wordFilterService.containsBannedWords("good day"));
        assertFalse(wordFilterService.containsBannedWords(""));
    }
    
    @Test
    void containsBannedWordsShouldIgnoreCase() {
        assertTrue(wordFilterService.containsBannedWords("Hello DAMN world"));
        assertTrue(wordFilterService.containsBannedWords("thiS dAm bad"));
    }
    
    @Test
    void containsBannedWordsShouldHandleNullInput() {
        assertFalse(wordFilterService.containsBannedWords(null));
    }
    
    @Test
    void addBannedWordShouldAddCustomWord() {
        // Create a new instance to test custom words
        wordFilterService.addBannedWord("customword");
        String filtered = wordFilterService.filter("hello customword there");
        assertEquals("hello *** there", filtered);
    }
    
    @Test
    void removeBannedWordShouldRemoveWord() {
        // Remove a default banned word
        wordFilterService.removeBannedWord("damn");
        String filtered = wordFilterService.filter("this is damn bad");
        // "damn" should not be filtered anymore
        assertEquals("this is damn bad", filtered);
    }
    
    @Test
    void getBannedWordsShouldReturnAllBannedWords() {
        Set<String> bannedWords = wordFilterService.getBannedWords();
        assertFalse(bannedWords.isEmpty());
        assertTrue(bannedWords.size() > 0);
    }
    
    @Test
    void filterShouldWorkWithVietnameseBannedWords() {
        String filtered = wordFilterService.filter("Đây là ngốc một con chó");
        assertTrue(filtered.contains("***"));
    }
    
    @Test
    void filterShouldPreserveWhitespaceAndPunctuation() {
        String filtered = wordFilterService.filter("damn!!! what fuck??");
        assertEquals("***!!! what ***??", filtered);
    }
    
    @Test
    void filterShouldHandleWordBoundaries() {
        String filtered = wordFilterService.filter("condemned damn damned");
        // "condemned" and "damned" should not be filtered
        // Only standalone "damn" should be filtered
        assertTrue(filtered.contains("condemned"));
        assertTrue(filtered.contains("***"));
    }
}
