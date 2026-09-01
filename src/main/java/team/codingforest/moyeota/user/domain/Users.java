package team.codingforest.moyeota.user.domain;

public interface Users {
    User save(User user);
    User findById(Long id);
}
