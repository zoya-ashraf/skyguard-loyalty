package com.example.loyalty;

public record InboxItem(int id, int amount, boolean needsFace, String reason, String status) {}
