package com.customer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.customer.config.JwtUtil;
import com.customer.entity.CsUser;
import com.customer.repository.CsUserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
public class UserController {
    private final CsUserMapper csUserMapper;
    private final JwtUtil jwtUtil;

    public UserController(CsUserMapper csUserMapper, JwtUtil jwtUtil) {
        this.csUserMapper = csUserMapper;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> req) {
        String username = req.get("username");
        String password = req.get("password");
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名和密码不能为空"));
        }

        CsUser user = csUserMapper.selectOne(
                new LambdaQueryWrapper<CsUser>().eq(CsUser::getUsername, username));
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "用户不存在"));
        }

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

        if (csUserMapper.selectOne(new LambdaQueryWrapper<CsUser>().eq(CsUser::getUsername, username)) != null) {
            return ResponseEntity.badRequest().body(Map.of("error", "用户名已存在"));
        }

        CsUser user = new CsUser();
        user.setUserId("u_" + System.currentTimeMillis() + "_" + Math.round(Math.random() * 100000));
        user.setUsername(username);
        user.setPassword(password);
        user.setNickname(nickname != null ? nickname : username);

        csUserMapper.insert(user);

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
        CsUser user = csUserMapper.selectOne(
                new LambdaQueryWrapper<CsUser>().eq(CsUser::getUserId, userId));
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
            "userId", user.getUserId(),
            "username", user.getUsername(),
            "nickname", user.getNickname() != null ? user.getNickname() : user.getUsername()
        ));
    }
}
