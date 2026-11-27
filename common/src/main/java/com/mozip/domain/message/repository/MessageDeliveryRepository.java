package com.mozip.domain.message.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;

public interface MessageDeliveryRepository extends JpaRepository<MessageDelivery, Long> {

	List<MessageDelivery> findAllByMessageInOrderById(Collection<Message> messages);

}
