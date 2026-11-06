package com.familyconnect.modules.ai.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.ai.dto.ChatRequest;
import com.familyconnect.modules.ai.dto.ChatResponse;
import com.familyconnect.modules.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {
    private final ChatService chatService;
    public AiChatController(ChatService chatService) { this.chatService = chatService; }
    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        return ApiResponse.ok(chatService.chat(CurrentUser.require(), request));
    }
}
