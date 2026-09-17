package com.tinglan.bean;


import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
public class ChatForm {

    private Long memoryId;  //对话id
    private String message;   //对话内容
}
