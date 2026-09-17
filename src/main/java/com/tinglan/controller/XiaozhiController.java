package com.tinglan.controller;


import com.tinglan.assistant.XiaozhiAgent;
import com.tinglan.bean.ChatForm;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@Tag(name = "xiaozhi")
@RestController
@RequestMapping("/xiaozhi")

public class XiaozhiController {


    @Autowired
    private XiaozhiAgent xiaoZhiAgent;


    @Operation(summary = "Dialogue")
    @PostMapping(value = "/chat",produces = "text/stream;charset=utf-8")  //
    public Flux<String> chat(@RequestBody ChatForm chatForm) {
//        public String chat(@RequestBody ChatForm chatForm) {
        return xiaoZhiAgent.chat(chatForm.getMemoryId(), chatForm.getMessage());

    }
}
