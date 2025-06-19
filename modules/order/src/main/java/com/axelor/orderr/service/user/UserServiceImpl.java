package com.axelor.orderr.service.user;

import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.UserRepository;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;

    @Inject
    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(rollbackOn = {Exception.class})
    public User registerUser(long telegramId, String name) {
        User user = userRepository.all().filter("self.tg_id = ?", telegramId).fetchOne();

        if (user == null) {
            user = new User();
            user.setTg_id(String.valueOf(telegramId));
            user.setPassword("0000");
            user.setCode("USER_" + telegramId);
        }

        user.setName(name);
        userRepository.save(user);
        return user;
    }

    @Override
    public List<User> getUsersList() {
        return userRepository.all().fetch();
    }

    @Override
    public User getUserByTelegramId(long telegramId) {
        return userRepository.all().filter("self.tg_id = ?", telegramId).fetchOne();
    }

    @Override
    public List<String> getUsersTelegramId() {
        return userRepository.all()
                .fetch()
                .stream()
                .map(User::getTg_id)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
