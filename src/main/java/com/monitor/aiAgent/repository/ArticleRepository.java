package com.monitor.aiAgent.repository;
import com.monitor.aiAgent.model.News;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
@Repository
public interface ArticleRepository extends MongoRepository <News, String> {
    
}
