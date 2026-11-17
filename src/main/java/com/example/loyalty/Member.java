package com.example.loyalty;

public record Member(int id, String name, String phone, String homeCity, int points, boolean frozen, String freezeToken) {}
