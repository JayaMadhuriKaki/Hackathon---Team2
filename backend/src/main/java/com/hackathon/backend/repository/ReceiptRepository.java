package com.hackathon.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hackathon.backend.domain.Receipt;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

	Optional<Receipt> findByLineItemId(UUID lineItemId);

	List<Receipt> findByLineItemIdIn(Collection<UUID> lineItemIds);

	void deleteByLineItemId(UUID lineItemId);

}
