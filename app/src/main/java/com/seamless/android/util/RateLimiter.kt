package com.seamless.android.util

import java.util.concurrent.ConcurrentHashMap

/**
 * PRODUCTION FIX: Rate limiter to prevent message flooding and denial-of-service attacks.
 * Uses a sliding window algorithm to limit requests per peer.
 */
class RateLimiter(
    private val maxRequests: Int = 50,
    private val windowMs: Long = 60_000L // 1 minute default
) {
    private val requestCounts = ConcurrentHashMap<String, MutableList<Long>>()
    
    /**
     * Try to acquire permission for a request from the given peer.
     * Returns true if allowed, false if rate limited.
     */
    @Synchronized
    fun tryAcquire(peerId: String): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs
        
        // Get or create request list for this peer
        val requests = requestCounts.getOrPut(peerId) { mutableListOf() }
        
        // Remove expired requests outside the window
        requests.removeAll { it < windowStart }
        
        // Check if under limit
        return if (requests.size < maxRequests) {
            requests.add(now)
            true
        } else {
            false
        }
    }
    
    /**
     * Clear rate limit state for a peer (e.g., when they disconnect)
     */
    fun clear(peerId: String) {
        requestCounts.remove(peerId)
    }
    
    /**
     * Clear all rate limit state
     */
    fun clearAll() {
        requestCounts.clear()
    }
    
    /**
     * Get current request count for a peer in the window
     */
    fun getRequestCount(peerId: String): Int {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs
        return requestCounts[peerId]?.count { it >= windowStart } ?: 0
    }
}
