package com.tinglan.store;

import com.tinglan.bean.XiaozhiChatMessages;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.List;


@Component
public class MongoChatMemoryStore implements ChatMemoryStore {


    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);

        XiaozhiChatMessages chatMessages = mongoTemplate.findOne(query, XiaozhiChatMessages.class);

        if (chatMessages == null){
            return new LinkedList<>();
        }

        /**
         * chatMessages.getContent() 取出存好的 JSON，ChatMessageDeserializer.messagesFromJson(...) 把它还原成 List<ChatMessage>，
         * 这正是 ChatMessageSerializer.messagesToJson(...) 的逆向操作，用于从数据库恢复聊天记录。
         */
        String contentJson = chatMessages.getContent();
        return ChatMessageDeserializer.messagesFromJson(contentJson);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> list) {
        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        Update update = new Update();


        //就是把 聊天消息列表 转成 JSON 字符串，方便存数据库
        update.set("content", ChatMessageSerializer.messagesToJson(list));

        //修改或新增
        /**
         *如果没有就新增记录
         */
        mongoTemplate.upsert(query, update, XiaozhiChatMessages.class);
    }

    @Override
    public void deleteMessages(Object memoryId) {

        Criteria criteria = Criteria.where("memoryId").is(memoryId);
        Query query = new Query(criteria);
        mongoTemplate.remove(query, XiaozhiChatMessages.class);
    }
}
