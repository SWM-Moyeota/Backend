package team.codingforest.moyeota.auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class UserDTO {
    private UUID publicId;
    private String name;
    private String username;
}