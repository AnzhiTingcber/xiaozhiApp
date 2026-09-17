package com.tinglan.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
/**
 * 聊天记录表（xiaozhiApp 模块专用，与公共模块的 chat_messages 集合隔离）
 */
@Document("xiaozhi_chat_messages")
public class XiaozhiChatMessages {

    //唯一标识，映射到 MongoDB 的 _id 字段
    @Id
    private ObjectId messageId;

    private String memoryId;

    private String content; //存储当前聊天记录列表的 JSON 字符串

}
