package com.customer.controller;

import com.customer.config.JwtUtil;
import com.customer.entity.CsUser;
import com.customer.repository.CsUserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final CsUserRepository csUserRepository;
    private final JwtUtil jwtUtil;

    public UserController(CsUserRepository csUserRepository, JwtUtil jwtUtil) {
        this.csUserRepository = csUserRepository;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        Optional<CsUser> userOpt = csUserRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "用户不存在"));
        }

        CsUser user = userOpt.get();
        if (!user.getPassword().equals(password)) {
            return ResponseEntity.status(401).body(Map.of("error", "密码错误"));
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(Map.of(
            "token", token,
            "userId", user.getUserId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername()
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        String nickname = req.get("nickname");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        if (csUserRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }

        CsUser user = new CsUser();
        user.setUserId("u_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 100000));
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname != null ? nickname : username);

        csUserRepository.save(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());
        return ResponseEntity.ok(Map.of(
            "token", token,
            "userId", user.getUserId(),
            "username", user.getUsername(),
            "nickname", user.getNickname()
        ));
    }

    @GetMapping("/info")
    public ResponseEntity<?> getInfo(@RequestParam String userId) {
        Optional<CsUser> userOpt = csUserRepository.findByUserId(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        CsUser user = userOpt.get();
        return ResponseEntity.ok(Map.of(
            "userId", user.getUserId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername()
        ));
    }
}
