package com.tinglan.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import reactor.core.publisher.Flux;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;


@AiService(
        wiringMode = EXPLICIT,
//        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",  //流式输出
        chatMemoryProvider = "chatMemoryProviderXiaozhi",
        tools = "appointmentTools" , //tools配置
        contentRetriever = "contentRetrieverXiaozhiPincone"


)


public interface XiaozhiAgent {

    @SystemMessage(fromResource = "xiaozhi-promt-template.txt")
    Flux<String>  chat(@MemoryId Long memoryId, @UserMessage String userMessage);
//    String  chat(@MemoryId Long memoryId, @UserMessage String userMessage);
}
