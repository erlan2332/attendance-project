package com.axelor.orderr.service.user;

import com.axelor.auth.db.User;

import java.util.List;

public interface UserService {
    User registerUser(long telegramId, String name);
    List<User> getUsersList();
    User getUserByTelegramId(long telegramId); // получает пользователя по айди
    List<String> getUsersTelegramId(); // получает айди всех пользователей
}